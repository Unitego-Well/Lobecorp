package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;

import java.util.ArrayDeque;
import java.io.IOException;

import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;

import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import org.unitego.lobecorp.Lobecorp;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectDefinitionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.stream.Collectors;

/// 每个组成件按需独立创建；作者快照与资源快照共用执行核心，模拟仍由 Photon 宿主推进。
public class PhotonOwnedLaser implements AutoCloseable {
	public static final Identifier V1_ID = Identifier.fromNamespaceAndPath("lobecorp", "the_queen_of_hatred/laser_composition_v1");
	public static final Identifier ID = Identifier.fromNamespaceAndPath("lobecorp", "the_queen_of_hatred/laser_composition_v2");
	private static final List<String> OUTER_COMPONENTS = List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle", "laser");
	public static final List<String> COMPONENTS = List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle",
			"laser_body", "dust_column", "source_dust", "pink_sequence_stars", "yellow_sequence_stars",
			"blue_sequence_stars", "flower_muzzle_spawner", "launch_spikes", "white_lines", "laser_tail");
	public static final List<String> BEAM_COMPONENTS = List.of("laser_body", "dust_column", "source_dust",
			"pink_sequence_stars", "yellow_sequence_stars", "blue_sequence_stars", "flower_muzzle_spawner",
			"launch_spikes", "white_lines", "laser_tail");
	private PhotonOwnedLaserComposition.Definition composition = PhotonOwnedLaserComposition.defaults();
	private PhotonOwnedLaserComposition.Phase phase;
	private double phaseDuration;
	private final Set<Integer> fired = new HashSet<>();
	private final Map<String, PhotonOwnedLaserComposition.Binding> bindings = new LinkedHashMap<>();
	private final Vector3f beamPosition = new Vector3f();
	private final Quaternionf beamRotation = new Quaternionf();
	private Map<String, List<Float>> beamInputs = Map.of();
	private Consumer<Float> frameAnchor;
	private final PhotonOwnedEffectResources.SourceKey source;
	private final Level level;
	private static final Map<PhotonOwnedEffectResources.SourceKey, PreparedDefinition> PREPARED = new LinkedHashMap<>();
	private final Map<String, FX> definitions;
	private final BeamSpace space;

	private record PreparedDefinition(Map<String, FX> definitions, BeamSpace space,
	                                  PhotonOwnedLaserComposition.Definition composition) {
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
		OUTER_COMPONENTS.forEach(name -> ids.put(name, Identifier.fromNamespaceAndPath("lobecorp",
				"the_queen_of_hatred/owned_laser_v1_" + name)));
		PhotonOwnedEffectResources.register(ID, ids);
		var finalIds = new LinkedHashMap<String, Identifier>();
		COMPONENTS.forEach(name -> finalIds.put(name, Identifier.fromNamespaceAndPath(ID.getNamespace(),
				"the_queen_of_hatred/owned_laser_v2_" + name)));
		PhotonOwnedEffectResources.registerSnapshotComponents(ID, finalIds);
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
		composition = prepared.composition();
	}

	/// 仅由资源 apply 或作者修订发布调用；成果不暴露可变 FX，不持世界或租约。
	private static void prepareDefinitions(PhotonOwnedEffectResources.Prepared prepared) {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("组合定义恢复必须在客户端线程");
		}
		var definitions = new LinkedHashMap<String, FX>();
		for (var entry : prepared.components().entrySet()) {
			definitions.put(entry.getKey(), restoreComponent(entry.getValue().definition()));
		}
		BeamSpace space;
		if (definitions.containsKey("laser")) {
			space = prepareSpace(definitions, prepared.source());
			var spawner = definitions.get("laser").getFxData().objects().stream().filter(PhotonChildSpawner.class::isInstance)
					.map(PhotonChildSpawner.class::cast).filter(value -> value.getName().equals("laser")).findFirst().orElseThrow();
			var childLocation = spawner.config.fxLocation;
			if (childLocation == null) {
				throw new IllegalArgumentException("光束组成件缺少子资源");
			}
			var beam = PhotonOwnedEffectResources.resolveDefinition(childLocation, prepared.source());
			for (var part : split(beam, BEAM_COMPONENTS).entrySet()) {
				var fx = new FX();
				var partTag = part.getValue();
				for (var value : partTag.getCompoundOrEmpty("fxData").getListOrEmpty("fxObjects")) {
					var data = ((CompoundTag) value).getCompoundOrEmpty("data");
					if (data.getStringOr("name", "").equals("laser_anchor")) {
						var transform = data.getCompoundOrEmpty("transform");
						transform.put("localPosition", vectorTag(0, 0, 0));
						transform.put("localScale", vectorTag(1, 1, 1));
					}
				}
				fx.deserializeNBT(Platform.getFrozenRegistry(), partTag);
				finalizeDefinition(fx);
				definitions.put(part.getKey(), fx);
			}
			definitions.remove("laser");
		} else {
			var metadata = prepared.components().values().iterator().next().definition().getCompoundOrEmpty("owned_beam_space");
			space = new BeamSpace(readVector(metadata, "circle"), readVector(metadata, "offset"),
					readVector(metadata, "scale"), metadata.getFloatOr("minimum", Float.NaN));
			if (!Float.isFinite(space.minimumLength()) || !space.beamScale().isFinite()
					|| space.beamScale().x == 0 || space.beamScale().z == 0) {
				throw new IllegalArgumentException("导出组合空间元数据缺失或无效");
			}
		}
		for (var fx : definitions.values()) {
			PhotonOwnedEffectAssets.prepare(fx, prepared.source());
		}
		PREPARED.keySet().removeIf(key -> key.effectId().equals(prepared.source().effectId()) && key.kind() == prepared.source().kind());
		PREPARED.put(prepared.source(), new PreparedDefinition(definitions, space, loadComposition(prepared)));
	}

	private static FX restoreComponent(CompoundTag tag) {
		int version = tag.getIntOr("version", FXProject.VERSION);
		if (version > FXProject.VERSION) {
			throw new IllegalArgumentException("自有组件版本高于当前Photon：" + version);
		}
		if (version < FXProject.VERSION) {
			var wrapper = new CompoundTag();
			wrapper.put("fx", tag);
			tag = PhotonFXProjectDataFixer.INSTANCE.applyFixes(version, FXProject.VERSION, wrapper).getCompoundOrEmpty("fx");
		}
		removeActivation(tag.getCompoundOrEmpty("fxData").getCompoundOrEmpty("timeline").getListOrEmpty("tracks"));
		var fx = new FX();
		fx.deserializeNBT(Platform.getFrozenRegistry(), tag);
		finalizeDefinition(fx);
		return fx;
	}

	private static PhotonOwnedLaserComposition.Definition loadComposition(PhotonOwnedEffectResources.Prepared prepared) {
		var metadata = prepared.components().values().iterator().next().definition();
		if (metadata.contains("owned_composition")) {
			return PhotonOwnedLaserComposition.deserialize(metadata.getCompoundOrEmpty("owned_composition"));
		}
		if (prepared.source().kind() == PhotonOwnedEffectResources.SourceKind.AUTHORING) {
			return PhotonOwnedLaserComposition.defaults();
		}
		var path = Identifier.fromNamespaceAndPath(ID.getNamespace(), "fx/" + ID.getPath() + ".json");
		var resource = Minecraft.getInstance().getResourceManager().getResource(path);
		if (resource.isEmpty()) {
			return PhotonOwnedLaserComposition.defaults();
		}
		try (var reader = resource.get().openAsReader()) {
			var tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonParser.parseReader(reader));
			if (!(tag instanceof CompoundTag compound)) {
				throw new IllegalArgumentException("组合定义必须为对象");
			}
			return PhotonOwnedLaserComposition.deserialize(compound);
		} catch (IOException failure) {
			throw new IllegalArgumentException("组合定义读取失败", failure);
		}
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

	private static BeamSpace prepareSpace(Map<String, FX> definitions, PhotonOwnedEffectResources.SourceKey source) {
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
		var child = PhotonOwnedEffectResources.resolveDefinition(spawner.config.fxLocation, source);
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
		return split(project, OUTER_COMPONENTS);
	}

	public static Map<String, CompoundTag> split(FX project, List<String> components) {
		var source = project.serializeNBT(Platform.getFrozenRegistry());
		var objects = source.getCompoundOrEmpty("fxData").getListOrEmpty("fxObjects");
		var result = new LinkedHashMap<String, CompoundTag>();
		for (var name : components) {
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
			var config = body.getCompoundOrEmpty("config");
			if (config.getStringOr("source", "").endsWith(".PROJECT")) {
				var template = config.getCompoundOrEmpty("template").getStringOr("uuid", "");
				var dependency = objects.stream().map(CompoundTag.class::cast)
						.filter(value -> value.getCompoundOrEmpty("data").getCompoundOrEmpty("transform")
								.getStringOr("id", "").equals(template)).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("组成件模板依赖缺失：" + template));
				if (!keep.contains(template)) {
					pending.addLast(dependency);
				}
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
		if (!current()) {
			throw new IllegalStateException("组合已结束或资源已失效");
		}

		if (name.equals("laser")) {
			PhotonNamedRuntime first = null;
			for (var part : BEAM_COMPONENTS) {
				bindings.put(part, PhotonOwnedLaserComposition.Binding.BEAM);
				var runtime = activate(part, animation, duration, inputs);
				if (first == null) {
					first = runtime;
				}
			}
			return first;
		}
		var effect = active.get(name);
		if (effect == null) {
			var definition = definitions.get(name);
			if (definition == null) {
				throw new IllegalArgumentException("未登记组成件：" + name);
			}

			if (PhotonRuntimeFactory.mode() != PhotonRuntimeFactory.Mode.NATIVE
					&& !PhotonRuntimeFactory.preparedSourceCurrent(definition)) {
				throw new IllegalStateException("自有组件编译准备已失效，禁止临场重复编译");
			}
			effect = new PhotonGeoEffect(definition, level) {
				@Override
				public PostEffectStack postEffectSink() {
					return editorPreview ? PostEffectStack.EDITOR_SCENE : super.postEffectSink();
				}
			};
			try {
				for (var object : effect.runtime().objects.values()) {
					if (object instanceof PhotonChildSpawner spawner) {
						spawner.ownedDefinitionResolver(id -> PhotonOwnedEffectResources.requireResolvedDefinition(source, id));
					}
				}
				active.put(name, effect);
				applyAnchors();
				if (name.equals("flower_muzzle_spawner")) {
					var template = effect.runtime().findObject("reverse_muzzle");
					if (template != null) {
						template.setSelfActive(false);
						template.setSelfVisible(false);
					}
				}
				effect.start();
				var rootObject = (FXObject) effect.runtime().root;
				if (rootObject != null && frameAnchor != null) {
					var previous = rootObject.getOnUpdateFrame();
					rootObject.setOnUpdateFrame(partial -> {
						if (!closed) {
							try {
								frameAnchor.accept(partial);
							} catch (RuntimeException failure) {
								close();
								Lobecorp.LOGGER.warn("自有组合帧更新失败", failure);
							}
						}
						if (previous != null) {
							previous.accept(partial);
						}
					});
				}
				var component = effect.runtime().findObject(name);
				if (component != null) {
					component.setSelfActive(true);
					component.setSelfVisible(true);
				}
				active.put(name, effect);
			} catch (RuntimeException failure) {
				active.remove(name, effect);
				effect.close();
				throw failure;
			}
		}

		var runtime = ((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime();
		if (runtime == null) {
			throw new IllegalStateException("组成件命名控制器缺失：" + name);
		}

		var definition = runtime.library().animations().stream().filter(value -> value.name().equals(animation))
				.findFirst().orElseThrow(() -> new IllegalArgumentException("命名动画不存在：" + animation));
		runtime.play(animation, duration, true,
				bindings.get(name) == PhotonOwnedLaserComposition.Binding.BEAM ? componentInputs(definition, inputs) : inputs);
		return runtime;
	}


	/// 组合公共输入只投影到该局部动画的形参；显式组件覆盖仍由命名系统严格校验。
	private static Map<String, List<Float>> componentInputs(Definition definition, Map<String, List<Float>> inputs) {
		var projected = new LinkedHashMap<String, List<Float>>();
		for (var parameter : definition.parameters()) {
			var value = inputs.get(parameter.name());
			if (value != null) {
				projected.put(parameter.name(), value);
			}
		}
		return Map.copyOf(projected);
	}

	private static ListTag vectorTag(float x, float y, float z) {
		var values = new ListTag();
		values.add(FloatTag.valueOf(x));
		values.add(FloatTag.valueOf(y));
		values.add(FloatTag.valueOf(z));
		return values;
	}

	private static Vector3f readVector(CompoundTag tag, String name) {
		var values = tag.getListOrEmpty(name);
		if (values.size() != 3) {
			throw new IllegalArgumentException("组合空间向量缺失：" + name);
		}
		return new Vector3f(values.get(0).asFloat().orElseThrow(), values.get(1).asFloat().orElseThrow(),
				values.get(2).asFloat().orElseThrow());
	}

	public static PhotonOwnedLaser previewComponents(Map<String, CompoundTag> components, long revision, Level level) {
		register();
		var key = new PhotonOwnedEffectResources.SourceKey(ID, PhotonOwnedEffectResources.SourceKind.PUBLISHED_PREVIEW, revision);
		PhotonOwnedEffectResources.publishPublishedPreview(key, components);
		var effect = new PhotonOwnedLaser(key, level);
		effect.editorPreview = true;
		return effect;
	}

	public static Map<String, CompoundTag> exportComponents(FX project) {
		var parts = splitComponents(project);
		var key = new PhotonOwnedEffectResources.SourceKey(ID, PhotonOwnedEffectResources.SourceKind.AUTHORING, 0);
		var space = PREPARED.get(key).space();
		return serializeComponents(parts, space);
	}

	public Map<String, CompoundTag> exportComponents() {
		if (!current()) {
			throw new IllegalStateException("发布组合已失效，请重新载入");
		}
		return serializeComponents(definitions, space);
	}

	private static Map<String, CompoundTag> serializeComponents(Map<String, FX> parts, BeamSpace space) {
		var metadata = new CompoundTag();
		metadata.put("circle", vectorTag(space.circleOffset().x, space.circleOffset().y, space.circleOffset().z));
		metadata.put("offset", vectorTag(space.beamOffset().x, space.beamOffset().y, space.beamOffset().z));
		metadata.put("scale", vectorTag(space.beamScale().x, space.beamScale().y, space.beamScale().z));
		metadata.putFloat("minimum", space.minimumLength());
		var result = new LinkedHashMap<String, CompoundTag>();
		parts.forEach((name, fx) -> {
			var tag = fx.serializeNBT(Platform.getFrozenRegistry());
			tag.put("owned_beam_space", metadata.copy());
			result.put(name, tag);
		});
		return Map.copyOf(result);
	}

	public static Map<String, FX> splitComponents(FX project) {
		var key = new PhotonOwnedEffectResources.SourceKey(ID, PhotonOwnedEffectResources.SourceKind.AUTHORING, 0);
		PhotonOwnedEffectResources.publishAuthoring(key, split(project));
		var prepared = PREPARED.get(key);
		if (prepared == null) {
			throw new IllegalStateException("作者组合尚未准备");
		}
		var result = new LinkedHashMap<String, FX>();
		prepared.definitions().forEach((name, fx) -> {
			var copy = new FX();
			copy.deserializeNBT(Platform.getFrozenRegistry(), fx.serializeNBT(Platform.getFrozenRegistry()));
			result.put(name, copy);
		});
		return Map.copyOf(result);
	}

	public void componentPose(String name, Vector3f offset, Quaternionf rotation, Map<String, List<Float>> inputs) {
		var effect = active.get(name);
		if (effect == null) {
			return;
		}
		boolean beam = bindings.get(name) == PhotonOwnedLaserComposition.Binding.BEAM;
		var orientation = new Quaternionf(beam ? beamRotation : this.rotation);
		var origin = new Vector3f(beam ? beamPosition : position).add(orientation.transform(new Vector3f(offset)));
		effect.anchor(origin, orientation.mul(rotation), beam ? space.beamScale() : new Vector3f(1));
		var runtime = ((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime();
		var handle = runtime == null ? null : runtime.playback("laser_start");
		if (handle != null) {
			var merged = new LinkedHashMap<String, List<Float>>(beam ? componentInputs(handle.definition(), beamInputs) : Map.of());
			merged.putAll(inputs);
			runtime.stageParameters(handle, merged);
		}
	}

	private static void finalizeDefinition(FX fx) {
		var targets = fx.getFxData().objects().stream().filter(object -> !(object instanceof PhotonNamedController))
				.map(IFXObject::id).collect(Collectors.toSet());
		for (var object : fx.getFxData().objects()) {
			if (object instanceof PhotonNamedController controller) {
				var definition = PhotonProjectDefinitionUtil.filter(controller.definition(), targets);
				for (var value : definition.getListOrEmpty("animations")) {
					removeActivation(((CompoundTag) value).getListOrEmpty("tracks"));
				}
				controller.definition(definition);
				controller.preparePlaybackDefinition();
				try {
					PhotonNamedController.compileDefinition(controller.definition(), controller.source()).install(controller);
				} catch (IOException failure) {
					throw new IllegalArgumentException("光束组成件动画准备失败", failure);
				}
			}
		}
	}

	public void composition(PhotonOwnedLaserComposition.Definition definition) {
		if (!active.isEmpty()) {
			throw new IllegalStateException("运行中不可替换组合定义");
		}
		for (var cue : definition.cues()) {
			if (!definitions.containsKey(cue.component())) {
				throw new IllegalArgumentException("未登记组成件：" + cue.component());
			}
		}
		composition = definition;
	}

	public void phase(PhotonOwnedLaserComposition.Phase next, double duration) {
		if (phase == next) {
			return;
		}
		phase = next;
		phaseDuration = duration;
		fired.clear();
		if (next == PhotonOwnedLaserComposition.Phase.OUTPUT) {
			stop("accumulate_particle", true);
		}
		advance(0);
	}

	public void advance(double phaseTicks) {
		if (closed || phase == null || !Double.isFinite(phaseTicks) || phaseTicks < 0) {
			return;
		}
		for (int i = 0; i < composition.cues().size(); i++) {
			var cue = composition.cues().get(i);
			double at = cue.scaleToPhase() ? cue.delayTicks() * phaseDuration / PhotonOwnedLaserComposition.AUTHORED_PHASE_TICKS : cue.delayTicks();
			if (cue.phase() != phase || at > phaseTicks || !fired.add(i)) {
				continue;
			}
			bindings.put(cue.component(), cue.binding());
			if (cue.animation().isEmpty()) {
				stop(cue.component(), cue.stopPolicy() == PhotonOwnedLaserComposition.StopPolicy.IMMEDIATE);
			} else if (phase != PhotonOwnedLaserComposition.Phase.RECOVERY || active.containsKey(cue.component())) {
				activate(cue.component(), cue.animation(), Math.max(0, phaseDuration - at),
						cue.binding() == PhotonOwnedLaserComposition.Binding.BEAM ? beamInputs : Map.of());
			}
		}
		applyAnchors();
	}

	public void frameAnchor(Consumer<Float> callback) {
		frameAnchor = callback;
	}

	public void beam(Vector3f position, Quaternionf rotation, Map<String, List<Float>> inputs) {
		beamPosition.set(position);
		beamRotation.set(rotation);
		beamInputs = Map.copyOf(inputs);
		for (var entry : active.entrySet()) {
			if (bindings.get(entry.getKey()) != PhotonOwnedLaserComposition.Binding.BEAM) {
				continue;
			}
			var runtime = ((PhotonNamedAccess) entry.getValue().runtime()).lobecorp$namedRuntime();
			var handle = runtime == null ? null : runtime.playback("laser_start");
			if (handle != null) {
				runtime.stageParameters(handle, componentInputs(handle.definition(), beamInputs));
			}
		}
		applyAnchors();
	}

	private void applyAnchors() {
		active.forEach((name, effect) -> {
			boolean beam = bindings.get(name) == PhotonOwnedLaserComposition.Binding.BEAM;
			effect.anchor(beam ? beamPosition : position, beam ? beamRotation : rotation,
					beam ? space.beamScale() : new Vector3f(1));
		});
		composition.poses().forEach((name, pose) -> componentPose(name, pose.offset(), pose.rotation(), pose.inputs()));
	}

	public void anchor(Vector3f position, Quaternionf rotation) {
		this.position.set(position);
		this.rotation.set(rotation);
		applyAnchors();
	}

	public PhotonGeoEffect component(String name) {
		return active.get(name);
	}

	public void stop(String name, boolean force) {
		if (name.equals("laser")) {
			BEAM_COMPONENTS.forEach(part -> stop(part, force));
			return;
		}
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
		if (phase == PhotonOwnedLaserComposition.Phase.RECOVERY) {
			// 权威快照可在阶段末tick前移除，仍须消费已声明的末端清理cue。
			advance(phaseDuration);
		}
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
		var prepared = PREPARED.get(source);
		return !closed && prepared != null && prepared.definitions() == definitions
				&& PhotonOwnedEffectResources.lookup(source).readiness() == PhotonOwnedEffectResources.Readiness.READY;
	}

	@Override
	public void close() {
		closed = true;
		active.values().forEach(PhotonGeoEffect::close);
		active.clear();
	}
}
