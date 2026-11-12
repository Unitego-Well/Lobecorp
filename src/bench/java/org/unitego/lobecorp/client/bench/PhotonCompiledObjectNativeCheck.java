package org.unitego.lobecorp.client.bench;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter;
import com.lowdragmc.photon.client.gameobject.forcefield.ForceFieldObject;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonCompiledObject;

import java.lang.reflect.Field;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// 已注册宿主中的独立原生对照入口；只构造和准备，不 emit、不推进游戏，不自动接入旧测试。
@SuppressWarnings("unused")
public class PhotonCompiledObjectNativeCheck {
	public record Case(String name, int fallbackFields, List<String> fallbackReasons) {
		public Case {
			fallbackReasons = List.copyOf(fallbackReasons);
		}
	}

	public static List<Case> verify(List<? extends IFXObject> supplied) throws Exception {
		var objects = new ArrayList<IFXObject>(List.of(new EmptyFXObject(), new ParticleEmitter(),
				new BeamEmitter(), new TrailEmitter(), new AraTrailEmitter(), new ForceFieldObject(), new PhotonChildSpawner()));
		objects.addAll(supplied);
		var cases = new ArrayList<Case>();

		for (var source : objects) {
			var owned = nativeCopy(source);
			checkEntries(owned, cases);

		}

		checkOpaque(cases);
		return List.copyOf(cases);
	}

	private static void checkEntries(IFXObject source, List<Case> cases) {
		source.transform()._setInternalParentID(UUID.randomUUID());
		source.transform()._setInternalChildID(List.of(UUID.randomUUID(), UUID.randomUUID()));
		var expected = encode(nativeCopy(source));
		var plan = PhotonCompiledObject.compile(source);
		equal(source.transform().id(), plan.id());
		equal(expected.getStringOr("type", ""), plan.type());
		var exported = plan.snapshot();
		exported.remove("data");
		equal(expected, encode(plan.instantiate()));
		var target = source.getFXObjectType().create();
		plan.initialize(target);
		equal(expected, encode(target));

		source.setName("current_author");
		source.transform().localPosition(new Vector3f(2, 3, 4));

		if (source instanceof PhotonChildSpawner spawner) {
			spawner.config.enabled = false;
			spawner.config.maxInstances = 11;
		}

		var current = encode(nativeCopy(source));
		equal(current, encode(plan.instantiateFrom(source)));
		var into = source.getFXObjectType().create();
		plan.initializeFrom(source, into);
		equal(current, encode(into));
		equal(expected, encode(plan.instantiate()));
		require(into.transform().parent() == null && into.transform().children().isEmpty(), "不能连接活父子节点");
		require(into.getScene() == null && into.getEffectExecutor() == null, "不能复制场景或执行器");
		source.transform().localPosition().x = 99;
		equal(new Vector3f(2, 3, 4), into.transform().localPosition());
		cases.add(new Case(source.getClass().getSimpleName() + ":four_entries_and_live_edit", plan.fallbackFields(), plan.fallbackReasons()));
	}





	private static void checkOpaque(List<Case> cases) {
		var registry = PhotonRegistries.FX_OBJECTS;
		var key = "compiled_object_test_" + UUID.randomUUID().toString().replace('-', '_');
		boolean frozen = registry.isFrozen();
		if (frozen) {
			registry.unfreeze();
		}
		boolean registered = false;

		try {
			registry.register(key, OpaqueNode.TYPE);
			registered = true;
			var source = new OpaqueNode();
			source.extra.putInt("unknown", 4);
			var plan = PhotonCompiledObject.compile(source);
			require(plan.fallbackFields() > 0 && !plan.fallbackReasons().isEmpty(), "未知对象必须记录原生回退");
			int before = OpaqueNode.decodes.get();
			equal(encode(nativeCopy(source)), encode(plan.instantiate()));
			require(OpaqueNode.decodes.get() > before, "未知节点实例必须经过原生 codec");
			source.extra.putInt("unknown", 9);
			equal(encode(nativeCopy(source)), encode(plan.instantiateFrom(source)));
			var target = new OpaqueNode();
			plan.initializeFrom(source, target);
			equal(source.extra, target.extra);
			source.extra.putInt("unknown", 10);
			require(target.extra.getIntOr("unknown", 0) == 9, "原生回退字段必须独占");
			cases.add(new Case("opaque:native_snapshot_and_current_source", plan.fallbackFields(), plan.fallbackReasons()));
		} finally {
			try {
				if (registered && registry.get(key) == OpaqueNode.TYPE) {
					registry.remove(key);
				}
			} finally {
				if (frozen && !registry.isFrozen()) {
					registry.freeze();
				}
			}
		}
	}

	public static class OpaqueNode extends EmptyFXObject {
		private static final AtomicInteger decodes = new AtomicInteger();
		public static final FXObjectType TYPE = new FXObjectType() {
			@Override
			public IFXObject create() {
				decodes.incrementAndGet();
				return new OpaqueNode();
			}

			@Override
			public IGuiTexture icon() {
				return IGuiTexture.EMPTY;
			}
		};
		@Persisted
		public CompoundTag extra = new CompoundTag();

		@Override
		public FXObjectType getFXObjectType() {
			return TYPE;
		}
	}

	private static IFXObject nativeCopy(IFXObject source) {
		return IFXObject.CODEC.parse(NbtOps.INSTANCE, encode(source)).getOrThrow();
	}

	private static CompoundTag encode(IFXObject source) {
		return (CompoundTag) IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow();
	}



	private static void equal(Object expected, Object actual) {
		if (Objects.equals(expected, actual)) {
			return;
		}

		if (expected instanceof Tag left && actual instanceof Tag right) {
			var path = new StringBuilder("$");
			while (left.getId() == right.getId()) {
				if (left instanceof CompoundTag a && right instanceof CompoundTag b) {
					var keys = new TreeSet<>(a.keySet());
					keys.addAll(b.keySet());
					String different = null;
					for (var key : keys) {
						if (!Objects.equals(a.get(key), b.get(key))) {
							different = key;
							break;
						}
					}
					if (different == null) {
						break;
					}
					var keyPreview = different.length() > 96 ? different.substring(0, 96) + "..." : different;
					path.append("[\"").append(keyPreview.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]");
					left = a.get(different);
					right = b.get(different);
					if (left == null || right == null) {
						break;
					}
				} else if (left instanceof CollectionTag a && right instanceof CollectionTag b) {
					int index = 0;
					while (index < Math.min(a.size(), b.size()) && Objects.equals(a.get(index), b.get(index))) {
						index++;
					}
					path.append('[').append(index).append(']');
					left = index < a.size() ? a.get(index) : null;
					right = index < b.size() ? b.get(index) : null;
					if (left == null || right == null) {
						break;
					}
				} else {
					break;
				}
			}

			var previews = new ArrayList<String>();
			for (var value : new Tag[]{left, right}) {
				var summary = switch (value) {
					case null -> "<missing>";
					case CompoundTag compound -> "CompoundTag(keys=" + compound.size() + ")";
					case CollectionTag collection ->
							value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
					case StringTag string -> {
						var text = string.value();
						yield "StringTag(length=" + text.length() + ", value=" + text.substring(0, Math.min(160, text.length())) + ")";
					}
					default -> value.getClass().getSimpleName() + "(" + value + ")";
				};
				previews.add(summary);
			}
			throw new AssertionError("Tag first-difference path=" + path + "; expected=" + previews.get(0)
					+ "; actual=" + previews.get(1));
		}

		throw new AssertionError("expected=" + expected + "; actual=" + actual);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
