package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;

import java.util.ArrayDeque;
import java.io.IOException;

import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectDefinitionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.stream.Collectors;

/// 每个组成件按需独立创建；作者快照与资源快照共用执行核心，模拟仍由 Photon 宿主推进。
public class PhotonOwnedLaser implements AutoCloseable {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("lobecorp", "the_queen_of_hatred/laser_composition_v1");
	public static final List<String> COMPONENTS = List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle", "laser");
	private final PhotonOwnedEffectResources.SourceKey source;
	private final Level level;
	private static final Map<PhotonOwnedEffectResources.SourceKey, PreparedDefinition> PREPARED = new LinkedHashMap<>();
	private final Map<String, FX> definitions;
	private final BeamSpace space;

	private record PreparedDefinition(Map<String, FX> definitions, BeamSpace space) {
		public PreparedDefinition {
			definitions = Map.copyOf(definitions);
		}
	}

	public record BeamSpace(Vector3f circleOffset, Vector3f beamOffset, Vector3f beamScale, float minimumLength) {
		public BeamSpace {
			circleOffset = new Vector3f(circleOffset);
			beamOffset = new Vector3f(beamOffset);
			beamScale = new Vector3f(beamScale);
		}

		@Override
		public Vector3f circleOffset() {
			return new Vector3f(circleOffset);
		}

		@Override
		public Vector3f beamOffset() {
			return new Vector3f(beamOffset);
		}

		@Override
		public Vector3f beamScale() {
			return new Vector3f(beamScale);
		}
	}

	public BeamSpace space() {
		return space;
	}

	private final Map<String, PhotonGeoEffect> active = new LinkedHashMap<>();
	private final Vector3f position = new Vector3f();
	private final Quaternionf rotation = new Quaternionf();
	private boolean closed;
	private boolean editorPreview;

	public static void register() {
		var ids = new LinkedHashMap<String, Identifier>();
		COMPONENTS.forEach(name -> ids.put(name, Identifier.fromNamespaceAndPath("lobecorp",
				"the_queen_of_hatred/owned_laser_v1_" + name)));
		PhotonOwnedEffectResources.register(ID, ids);
		PhotonOwnedEffectResources.registerDefinitionPreparation(ID, PhotonOwnedLaser::prepareDefinitions, PREPARED::remove);
	}

	public PhotonOwnedLaser(PhotonOwnedEffectResources.SourceKey source, Level level) {
		this.source = source;
		this.level = level;
		var result = PhotonOwnedEffectResources.lookup(source);
		if (result.readiness() != PhotonOwnedEffectResources.Readiness.READY || result.prepared() == null) {
			throw new IllegalStateException("组合数据尚未就绪：" + result.readiness());
		}

		var prepared = PREPARED.get(source);
		if (prepared == null) {
			throw new IllegalStateException("组合静态定义尚未准备，不允许临场同步恢复");
		}
		definitions = prepared.definitions();
		space = prepared.space();
	}

	/// 仅由资源 apply 或作者修订发布调用；成果不暴露可变 FX，不持世界或租约。
	private static void prepareDefinitions(PhotonOwnedEffectResources.Prepared prepared) {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("组合定义恢复必须在客户端线程");
		}
		var definitions = new LinkedHashMap<String, FX>();
		for (var entry : prepared.components().entrySet()) {
			var fx = new FX();
			var tag = entry.getValue().definition();
			int version = tag.getIntOr("version", FXProject.VERSION);
			if (version < FXProject.VERSION) {
				var wrapper = new CompoundTag();
				wrapper.put("fx", tag);
				tag = PhotonFXProjectDataFixer.INSTANCE.applyFixes(version, FXProject.VERSION, wrapper).getCompoundOrEmpty("fx");
			}
			if (version > FXProject.VERSION) {
				throw new IllegalArgumentException("自有组件版本高于当前 Photon：" + version);
			}
			removeActivation(tag.getCompoundOrEmpty("fxData").getCompoundOrEmpty("timeline").getListOrEmpty("tracks"));
			fx.deserializeNBT(Platform.getFrozenRegistry(), tag);
			var targets = fx.getFxData().objects().stream()
					.filter(object -> !(object instanceof PhotonNamedController))
					.map(IFXObject::id).collect(Collectors.toSet());
			for (var object : fx.getFxData().objects()) {
				if (object instanceof PhotonNamedController controller) {
					var definition = PhotonProjectDefinitionUtil.filter(controller.definition(), targets);
					for (var animation : definition.getListOrEmpty("animations")) {
						removeActivation(((CompoundTag) animation).getListOrEmpty("tracks"));
					}
					controller.definition(definition);
					controller.preparePlaybackDefinition();
					try {
						PhotonNamedController.compileDefinition(controller.definition(), controller.source()).install(controller);
					} catch (IOException invalid) {
						throw new IllegalArgumentException("组成件命名定义准备失败", invalid);
					}
				}
			}
			definitions.put(entry.getKey(), fx);
		}
		PREPARED.keySet().removeIf(key -> key.effectId().equals(prepared.source().effectId()) && key.kind() == prepared.source().kind());
		PREPARED.put(prepared.source(), new PreparedDefinition(definitions, prepareSpace(definitions)));
	}

	private record LocalPose(Vector3f position, Quaternionf rotation, Vector3f scale) {
	}

	private static LocalPose localPose(FX fx, IFXObject object) {
		var chain = new ArrayDeque<IFXObject>();
		var seen = new HashSet<UUID>();
		var next = object;
		while (next != null) {
			if (!seen.add(next.id())) {
				throw new IllegalArgumentException("组成件变换循环");
			}
			chain.addFirst(next);
			var parent = next.transform()._getInternalParentID();
			next = fx.getFxData().objects().stream().filter(value -> value.id().equals(parent)).findFirst().orElse(null);
		}

		var matrix = new Matrix4f();
		var rotation = new Quaternionf();
		var scale = new Vector3f(1);
		for (var value : chain) {
			var transform = value.transform();
			matrix.translate(transform.localPosition()).rotate(transform.localRotation()).scale(transform.localScale());
			rotation.mul(transform.localRotation());
			scale.mul(transform.localScale());
		}
		return new LocalPose(matrix.transformPosition(new Vector3f()), rotation, scale);
	}

	private static BeamSpace prepareSpace(Map<String, FX> definitions) {
		var circleFX = definitions.get("circle");
		var circle = circleFX.getFxData().objects().stream().filter(object -> object.getName().equals("circle"))
				.findFirst().orElseThrow();
		var laserFX = definitions.get("laser");
		var spawner = laserFX.getFxData().objects().stream().filter(object -> object.getName().equals("laser"))
				.filter(PhotonChildSpawner.class::isInstance).map(PhotonChildSpawner.class::cast).findFirst().orElseThrow();
		if (spawner.config.fxLocation == null) {
			throw new IllegalArgumentException("光束组成件缺少子资源");
		}
		// 元数据在客户端发布阶段读取；不在每次技能构造时解析子定义。
		var child = FXHelper.getFX(spawner.config.fxLocation, false);
		if (child == null) {
			throw new IllegalArgumentException("光束子资源缺失");
		}
		var anchor = child.getFxData().objects().stream().filter(object -> object.getName().equals("laser_anchor"))
				.findFirst().orElseThrow();
		var pose = localPose(laserFX, spawner);
		var offset = new Vector3f(anchor.transform().localPosition()).mul(pose.scale());
		pose.rotation().transform(offset);
		offset.add(pose.position());
		var scale = new Vector3f(pose.scale()).mul(anchor.transform().localScale());
		if (!scale.isFinite() || scale.x == 0 || scale.z == 0) {
			throw new IllegalArgumentException("光束组成件缩放无效");
		}
		float minimum = Float.NaN;
		for (var object : laserFX.getFxData().objects()) {
			if (object instanceof PhotonNamedController controller) {
				for (var value : controller.definition().getListOrEmpty("animations")) {
					var animation = (CompoundTag) value;
					if (!animation.getStringOr("name", "").equals("laser_start")) {
						continue;
					}
					for (var parameter : animation.getListOrEmpty("parameters")) {
						var data = (CompoundTag) parameter;
						if (data.getStringOr("name", "").equals("length")) {
							minimum = data.getFloatOr("minimum", Float.NaN);
						}
					}
				}
			}
		}
		if (!Float.isFinite(minimum)) {
			throw new IllegalArgumentException("光束长度参数下限缺失");
		}
		return new BeamSpace(localPose(circleFX, circle).position(), offset, scale, minimum);
	}

	private static void removeActivation(ListTag tracks) {
		tracks.removeIf(value -> ((CompoundTag) value).getStringOr("type", "").equals("activator"));
		for (var value : tracks) {
			var track = (CompoundTag) value;
			if (track.getStringOr("type", "").equals("group")) {
				removeActivation(track.getCompoundOrEmpty("data").getListOrEmpty("children"));
			}
		}
	}

	public static PhotonOwnedLaser preview(FX currentProject, long revision, Level level) {
		register();
		var key = new PhotonOwnedEffectResources.SourceKey(ID, PhotonOwnedEffectResources.SourceKind.AUTHORING, revision);
		PhotonOwnedEffectResources.publishAuthoring(key, split(currentProject));
		var preview = new PhotonOwnedLaser(key, level);
		preview.editorPreview = true;
		return preview;
	}

	/// 从当前内存序列化独立组成件，保留祖先变换；不重新读取已保存工程。
	public static Map<String, CompoundTag> split(FX project) {
		var source = project.serializeNBT(Platform.getFrozenRegistry());
		var objects = source.getCompoundOrEmpty("fxData").getListOrEmpty("fxObjects");
		var result = new LinkedHashMap<String, CompoundTag>();
		for (var name : COMPONENTS) {
			var selected = objects.stream().map(CompoundTag.class::cast)
					.filter(object -> object.getCompoundOrEmpty("data").getStringOr("name", "").equals(name))
					.findFirst().orElseThrow(() -> new IllegalArgumentException("组成件缺失：" + name));
			var keep = new HashSet<String>();
			keepDescendants(objects, selected, keep);
			var ancestry = new HashSet<String>();
			var cursor = selected;
			while (cursor != null) {
				var transform = cursor.getCompoundOrEmpty("data").getCompoundOrEmpty("transform");
				if (!ancestry.add(transform.getStringOr("id", ""))) {
					throw new IllegalArgumentException("组成件祖先循环：" + name);
				}
				keep.add(transform.getStringOr("id", ""));
				var parent = transform.getStringOr("_parentId", "");
				cursor = objects.stream().map(CompoundTag.class::cast)
						.filter(object -> object.getCompoundOrEmpty("data").getCompoundOrEmpty("transform")
								.getStringOr("id", "").equals(parent)).findFirst().orElse(null);
			}

			var tag = source.copy();
			var data = tag.getCompoundOrEmpty("fxData");
			var copies = data.getListOrEmpty("fxObjects");
			copies.removeIf(value -> {
				var object = (CompoundTag) value;
				var body = object.getCompoundOrEmpty("data");
				return !keep.contains(body.getCompoundOrEmpty("transform").getStringOr("id", ""))
						&& !object.getStringOr("type", "").equals("lobecorp_animation_controller");
			});
			for (var value : copies) {
				var transform = ((CompoundTag) value).getCompoundOrEmpty("data").getCompoundOrEmpty("transform");
				transform.getListOrEmpty("_childrenId").removeIf(child -> !keep.contains(child.asString().orElse("")));
			}

			var targets = keep.stream().map(UUID::fromString).collect(Collectors.toSet());
			var timeline = data.getCompoundOrEmpty("timeline");
			timeline.getListOrEmpty("tracks").removeIf(value ->
					!PhotonProjectDefinitionUtil.retainTrack((CompoundTag) value, targets));
			result.put(name, tag);
		}

		return Map.copyOf(result);
	}

	private static void keepDescendants(ListTag objects, CompoundTag selected, HashSet<String> keep) {
		var pending = new ArrayDeque<CompoundTag>();
		pending.add(selected);
		while (!pending.isEmpty()) {
			var object = pending.removeFirst();
			var body = object.getCompoundOrEmpty("data");
			if (body.getCompoundOrEmpty("config").getStringOr("source", "").endsWith(".PROJECT")) {
				throw new IllegalArgumentException("组成件含工程外部模板引用，须先明确独立资源边界");
			}
			var transform = body.getCompoundOrEmpty("transform");
			var id = transform.getStringOr("id", "");
			if (!keep.add(id)) {
				throw new IllegalArgumentException("组成件子树存在循环或多父引用：" + id);
			}
			var children = new HashSet<String>();
			for (var value : transform.getListOrEmpty("_childrenId")) {
				children.add(value.asString().orElseThrow());
			}
			for (var value : objects) {
				var child = ((CompoundTag) value).getCompoundOrEmpty("data").getCompoundOrEmpty("transform");
				if (child.getStringOr("_parentId", "").equals(id)) {
					children.add(child.getStringOr("id", ""));
				}
			}
			for (var childId : children) {
				var child = objects.stream().map(CompoundTag.class::cast)
						.filter(value -> value.getCompoundOrEmpty("data").getCompoundOrEmpty("transform")
								.getStringOr("id", "").equals(childId)).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("组成件子引用缺失：" + childId));
				pending.addLast(child);
			}
		}
	}

	public PhotonNamedRuntime activate(String name, String animation, double duration, Map<String, List<Float>> inputs) {
		if (closed) {
			throw new IllegalStateException("组合已结束");
		}

		var effect = active.get(name);
		if (effect == null) {
			var definition = definitions.get(name);
			if (definition == null) {
				throw new IllegalArgumentException("未登记组成件：" + name);
			}

			effect = new PhotonGeoEffect(definition, level) {
				@Override
				public PostEffectStack postEffectSink() {
					return editorPreview ? PostEffectStack.EDITOR_SCENE : super.postEffectSink();
				}
			};
			try {
				effect.anchor(position, rotation, new Vector3f(1));
				effect.start();
				for (var object : effect.runtime().objects.values()) {
					// 仅此组成件的已筛选子树及祖先，不包含其他组成件。
					object.setSelfActive(true);
					object.setSelfVisible(true);
				}
				var component = effect.runtime().findObject(name);
				if (component != null) {
					component.setSelfActive(true);
					component.setSelfVisible(true);
				}
				active.put(name, effect);
			} catch (RuntimeException failure) {
				effect.close();
				throw failure;
			}
		}

		var runtime = ((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime();
		if (runtime == null) {
			throw new IllegalStateException("组成件命名控制器缺失：" + name);
		}

		runtime.play(animation, duration, true, inputs);
		return runtime;
	}

	public void anchor(Vector3f position, Quaternionf rotation) {
		this.position.set(position);
		this.rotation.set(rotation);
		active.values().forEach(effect -> effect.anchor(position, rotation, new Vector3f(1)));
	}

	public PhotonGeoEffect component(String name) {
		return active.get(name);
	}

	public void stop(String name, boolean force) {
		var effect = active.remove(name);
		if (effect != null) {
			if (force) {
				effect.close();
			} else {
				// 自然尾迹仍保留所有权，直至完成或组合被显式关闭。
				effect.stop(false);
				active.put(name, effect);
			}
		}
	}

	public void retire() {
		active.values().forEach(effect -> effect.stop(false));
	}

	public boolean finished() {
		if (!current()) {
			return true;
		}

		try {
			return active.values().stream().allMatch(effect -> effect.runtime().isFinished());
		} catch (IllegalStateException staleLease) {
			return true;
		}
	}

	public boolean current() {
		return !closed && PhotonOwnedEffectResources.lookup(source).readiness() == PhotonOwnedEffectResources.Readiness.READY;
	}

	@Override
	public void close() {
		closed = true;
		active.values().forEach(PhotonGeoEffect::close);
		active.clear();
	}
}
