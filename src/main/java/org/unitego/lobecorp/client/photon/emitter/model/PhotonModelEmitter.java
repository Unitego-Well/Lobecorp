package org.unitego.lobecorp.client.photon.emitter.model;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.render.PhotonCameraUtils;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.CardinalLighting;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelEditor;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig.Source;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.registry.photon.client.LcPhotonModelEmitterTypes;
import org.unitego.lobecorp.util.photon.model.PhotonModelAnimationTimelineUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelCullUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.PreparedModel;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampleParameters;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampledModel;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPreviewMaterialUtil;

import java.io.IOException;
import java.util.function.Function;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

import javax.annotation.ParametersAreNonnullByDefault;

/// 独立播放一个可编辑模型，复用 Photon 生命周期、时间轴和材质批次。
@ParametersAreNonnullByDefault
public class PhotonModelEmitter extends Emitter implements PhotonEmissionControlAccess {
	/// 模型载入状态使用稳定翻译子键。
	private static final String STATUS_OK = "ok";
	/// 未选择模型或模型没有面的状态子键。
	private static final String STATUS_MISSING = "missing";
	/// 来源或动画参数无法形成有效几何的状态子键。
	private static final String STATUS_INVALID = "invalid";
	/// ARGB 色通道的最大值。
	private static final float COLOR_CHANNEL_MAX = 255;
	/// 欧拉角检视以度为单位。
	private static final float DEGREES_PER_RADIAN = 180f / (float) Math.PI;
	/// 配置和外部来源快照的分隔符，仅属于内部持久化标识。
	private static final String SOURCE_SEPARATOR = "|";

	@Persisted(subPersisted = true)
	public final PhotonModelEmitterConfig config = new PhotonModelEmitterConfig();
	private final PhotonModelEmitterRuntime runtime = new PhotonModelEmitterRuntime(config);
	private PhotonModelAsset asset = new PhotonModelAsset();
	private @Nullable PreparedModel preparedAsset;
	private @Nullable SampleKey sampledKey;
	private @Nullable SampledModel sampledModel;

	private record SampleKey(PreparedModel asset, String clip, float time, float length, float width,
	                         boolean loop, Anchor anchor) {
	}
	/// 仅独立模型编辑器安装的临时采样回调，不参与配置序列化。
	private @Nullable Function<Float, SampledModel> previewSampler;
	private String loadedJson;
	private String loadedSource;
	private String status = STATUS_MISSING;
	private boolean loadFailed;
	private boolean started;
	private boolean cleared;
	private boolean activationStopped;
	private boolean preserveOnReset;
	private float animationTime;
	private float stopAge = Float.POSITIVE_INFINITY;

	public PhotonModelEmitterRuntime runtime() {
		ensureAsset();
		return runtime;
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonModelEmitterTypes.MODEL_EMITTER;
	}

	@Override
	public IGuiTexture getIcon() {
		return ParticleEmitter.ICON;
	}

	/// 编辑器拿到工作副本，只有提交时才替换当前定义。
	public PhotonModelAsset getAsset() {
		ensureAsset();
		return asset.copy();
	}

	public boolean hasAnimationClip(String id) {
		ensureAsset();
		return !id.isEmpty() && asset.clips.stream().anyMatch(clip -> clip.id.equals(id));
	}

	public void setAsset(PhotonModelAsset value) {
		var copy = value.copy();
		copy.validate();
		config.source = Source.EMBEDDED;
		setLoadedAsset(copy, sourceKey());
	}

	public void setPreviewSampler(@Nullable Function<Float, SampledModel> sampler) {
		previewSampler = sampler;
	}

	public void reloadSource() {
		loadAsset(true);
	}

	private void ensureAsset() {
		if (!Objects.equals(loadedJson, config.assetJson) || !Objects.equals(loadedSource, sourceKey())) {
			loadAsset(false);
		}
	}

	private String sourceKey() {
		return config.source.name() + SOURCE_SEPARATOR + config.resource + SOURCE_SEPARATOR
				+ config.file + SOURCE_SEPARATOR + config.flipObjV;
	}

	private void loadAsset(boolean reload) {
		var source = sourceKey();

		try {
			if (!config.assetJson.isBlank()
					&& (config.source == Source.EMBEDDED || !reload && source.equals(config.snapshotSource))) {
				setLoadedAsset(PhotonModelIOUtil.readJson(config.assetJson), source);
				return;
			}

			if (config.source == Source.EMBEDDED) {
				asset = new PhotonModelAsset();
				runtime.syncDefinition(asset);
				loadFailed = false;
				status = STATUS_MISSING;
				rememberSource(source);
				return;
			}

			var text = readSource();
			var location = (config.source == Source.RESOURCE ? Objects.toString(config.resource, "") : config.file).toLowerCase(Locale.ROOT);

			if (!location.endsWith(PhotonModelEmitterConfig.OBJ_SUFFIX) && !location.endsWith(PhotonModelEmitterConfig.MODEL_SUFFIX)) {
				throw new IOException(PhotonModelTextUtil.key("load_failed"));
			}

			var value = location.endsWith(PhotonModelEmitterConfig.OBJ_SUFFIX)
					? PhotonModelIOUtil.readObj(text, config.flipObjV)
					: PhotonModelIOUtil.readJson(text);
			setLoadedAsset(value, source);
		} catch (IOException | RuntimeException exception) {
			restoreEmbeddedSnapshot();
			loadFailed = true;
			status = STATUS_INVALID;
			rememberSource(source);
			Photon.LOGGER.warn("{}", PhotonModelTextUtil.key("load_failed"), exception);
		}
	}

	private void restoreEmbeddedSnapshot() {
		if (!asset.faces.isEmpty() || config.assetJson.isBlank()) {
			return;
		}

		try {
			var snapshot = PhotonModelIOUtil.readJson(config.assetJson);
			snapshot.validate();
			asset = snapshot.copy();
			config.setReferenceDimensions(asset.referenceLength, asset.referenceWidth);
			runtime.syncDefinition(asset);
		} catch (RuntimeException _) {
			// 失败的用户输入保留原文，已有有效模型继续使用。
		}
	}

	private String readSource() throws IOException {
		if (config.source == Source.FILE) {
			return Files.readString(Path.of(config.file));
		}

		if (config.resource == null) {
			throw new IOException(PhotonModelTextUtil.key("status.missing"));
		}

		var resource = Minecraft.getInstance().getResourceManager().getResource(config.resource)
				.orElseThrow(() -> new IOException(PhotonModelTextUtil.key("status.missing")));

		try (var reader = resource.openAsReader()) {
			var text = new StringWriter();
			reader.transferTo(text);
			return text.toString();
		}
	}

	private void setLoadedAsset(PhotonModelAsset value, String source) {
		var copy = value.copy();
		copy.validate();
		var json = PhotonModelIOUtil.writeJson(copy);
		asset = copy;
		config.setReferenceDimensions(asset.referenceLength, asset.referenceWidth);
		config.assetJson = json;
		config.snapshotSource = source;
		runtime.syncDefinition(asset);
		loadFailed = false;
		status = asset.faces.isEmpty() ? STATUS_MISSING : STATUS_OK;
		rememberSource(source);
	}

	private void rememberSource(String source) {
		preparedAsset = null;
		sampledKey = null;
		sampledModel = null;
		loadedSource = source;
		loadedJson = config.assetJson;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		super.buildConfigurator(father);
		ensureAsset();
		config.buildConfigurator(father);
		var tools = new Configurator(PhotonModelTextUtil.key("edit"));
		tools.addInlineChildren(
				new Button().setText(PhotonModelTextUtil.key("edit")).setOnClick(_ -> PhotonModelEditor.open(this, father)),
				new Button().setText(PhotonModelTextUtil.key("reload")).setOnClick(_ -> {
					reloadSource();
					tools.notifyChanges();
				}));
		father.addConfigurator(tools);
		buildTransforms(father, false);
		buildTransforms(father, true);
		var morphs = new ConfiguratorGroup(PhotonModelTextUtil.key("morphs"), true);

		for (var morph : asset.morphs) {
			var id = morph.id;
			var control = new NumberConfigurator(id, () -> morphWeight(id),
					value -> editMorph(id, value.floatValue()), morph.weight, true);
			morphs.addConfigurator(control);
		}

		father.addConfigurator(morphs);
	}

	private void buildTransforms(ConfiguratorGroup father, boolean bones) {
		var group = new ConfiguratorGroup(PhotonModelTextUtil.key(bones ? "bones" : "parts"), true);
		var ids = bones ? asset.bones.stream().map(bone -> bone.id).toList()
				: asset.parts.stream().map(part -> part.id).toList();

		for (var id : ids) {
			var item = new ConfiguratorGroup(id, true);

			for (var channel : TransformChannel.values()) {
				var vector = new ConfiguratorGroup(PhotonModelTextUtil.key(channel.key), true);

				for (int axis = 0; axis < 3; axis++) {
					var component = axis;
					vector.addConfigurator(new NumberConfigurator(ComponentAxis.values()[axis].name(),
							() -> authoredVector(id, bones, channel).get(component),
							value -> editTransform(id, bones, channel, component, value.floatValue()),
							channel == TransformChannel.SCALE ? 1f : 0f, true));
				}

				item.addConfigurator(vector);
			}

			group.addConfigurator(item);
		}

		father.addConfigurator(group);
	}

	private Transform authoredTransform(String id, boolean bones) {
		return bones ? asset.bones.stream().filter(bone -> bone.id.equals(id)).findFirst().map(bone -> bone.bind).orElseGet(Transform::new)
				: asset.parts.stream().filter(part -> part.id.equals(id)).findFirst().map(part -> part.transform).orElseGet(Transform::new);
	}

	private Vector3f authoredVector(String id, boolean bones, TransformChannel channel) {
		var transform = authoredTransform(id, bones);
		return switch (channel) {
			case POSITION -> new Vector3f(transform.translation);
			case ROTATION -> transform.rotation.getEulerAnglesXYZ(new Vector3f()).mul(DEGREES_PER_RADIAN);
			case SCALE -> new Vector3f(transform.scale);
		};
	}

	private void editTransform(String id, boolean bones, TransformChannel channel, int axis, float value) {
		var copy = getAsset();
		var transform = bones ? copy.bones.stream().filter(bone -> bone.id.equals(id)).findFirst().map(bone -> bone.bind).orElse(null)
				: copy.parts.stream().filter(part -> part.id.equals(id)).findFirst().map(part -> part.transform).orElse(null);

		if (transform == null || !Float.isFinite(value)) {
			return;
		}

		var vector = authoredVector(id, bones, channel).setComponent(axis, value);

		switch (channel) {
			case POSITION -> transform.translation.set(vector);
			case ROTATION -> transform.rotation.rotationXYZ(vector.x / DEGREES_PER_RADIAN,
					vector.y / DEGREES_PER_RADIAN, vector.z / DEGREES_PER_RADIAN);
			case SCALE -> transform.scale.set(vector);
		}

		setAsset(copy);
	}

	private float morphWeight(String id) {
		return asset.morphs.stream().filter(morph -> morph.id.equals(id)).findFirst().map(morph -> morph.weight).orElse(0f);
	}

	private void editMorph(String id, float value) {
		var copy = getAsset();
		copy.morphs.stream().filter(morph -> morph.id.equals(id)).findFirst().ifPresent(morph -> morph.weight = value);
		setAsset(copy);
	}

	@Override
	protected void update(float dt) {
		if (!Float.isFinite(dt) || dt <= 0) {
			return;
		}

		ensureAsset();

		if (ageF >= getStartDelay() && !started && !cleared && !lobecorp$isEmissionStopped()) {
			started = true;
		}

		if (runtime.paused.get()) {
			return;
		}

		var previousAge = ageF;
		ageF += dt;
		age = (int) ageF;

		if (ageF >= getStartDelay() && !started && !cleared && !lobecorp$isEmissionStopped()) {
			started = true;
		}

		if (started) {
			var activeDelta = Math.max(0, ageF - getStartDelay()) - Math.max(0, previousAge - getStartDelay());
			animationTime += activeDelta * finiteNonnegative(runtime.playbackSpeed.get());
		}

		t = progress(ageF);

		if ((!isLooping() && ageF - getStartDelay() >= getLifetime()) || ageF >= stopAge) {
			cleared = true;
			started = false;
			super.remove(false);
		}
	}

	private float progress(float time) {
		var activeTime = Math.max(0, time - getStartDelay());
		return isLooping() ? activeTime % getLifetime() / getLifetime() : Math.clamp(activeTime / getLifetime(), 0, 1);
	}

	@Override
	public float getT(float partialTicks) {
		return progress(ageF + frameDelta(partialTicks));
	}

	private float frameDelta(float partialTicks) {
		return runtime.paused.get() ? 0 : Math.clamp(partialTicks, 0, 1) * finiteNonnegative(timeScale());
	}

	@Override
	public int getLifetime() {
		return Math.max(1, runtime.duration.get());
	}

	@Override
	public int getStartDelay() {
		return Math.max(0, runtime.startDelay.get());
	}

	@Override
	public boolean isLooping() {
		return runtime.looping.get();
	}

	@Override
	public int getParticleAmount() {
		return started && !cleared && !asset.faces.isEmpty() ? 1 : 0;
	}

	@Override
	public void reset() {
		var preserve = preserveOnReset && started && !cleared;
		var preservedTime = animationTime;
		super.reset();
		PhotonModelAnimationTimelineUtil.clear(this);
		runtime.clear();
		activationStopped = false;
		started = preserve;
		cleared = false;
		animationTime = preserve ? preservedTime : 0;
		stopAge = Float.POSITIVE_INFINITY;
		ensureAsset();
		started = preserve || runtime.enabled.get() && getStartDelay() == 0 && !asset.faces.isEmpty();
	}

	@Override
	public void remove(boolean force) {
		super.remove(force);

		if (force) {
			lobecorp$clearParticles();
			return;
		}

		stopAge = Math.min(stopAge, cycleEnd());
	}

	private float cycleEnd() {
		var active = Math.max(0, ageF - getStartDelay());
		return getStartDelay() + ((float) Math.floor(active / getLifetime()) + 1) * getLifetime();
	}

	@Override
	public boolean lobecorp$isEmissionStopped() {
		return activationStopped || removed || !runtime.enabled.get();
	}

	@Override
	public void lobecorp$setEmissionStopped(boolean stopped) {
		if (stopped && !activationStopped && started && !cleared) {
			stopAge = Math.min(stopAge, cycleEnd());
		} else if (!stopped && !removed) {
			stopAge = Float.POSITIVE_INFINITY;
		}

		activationStopped = stopped;
	}

	@Override
	public void lobecorp$clearParticles() {
		PhotonModelAnimationTimelineUtil.clear(this);
		cleared = true;
		started = false;
	}

	@Override
	public void lobecorp$setPreserveOnReset(boolean preserve) {
		preserveOnReset = preserve;
	}

	@Override
	public RendererSetting.Runtime rendererRuntime() {
		return runtime.renderer();
	}

	@Override
	@Nullable
	public AABB getCullBox(float partialTicks) {
		var cull = runtime.renderer().getCull();
		return cull.isEnable() ? cull.getCullAABB(this, partialTicks) : null;
	}

	@Nullable
	private SampledModel sample(float partialTicks) {
		if (previewSampler != null) {
			return previewSampler.apply(partialTicks);
		}

		ensureAsset();
		var time = animationTime + frameDelta(partialTicks) * finiteNonnegative(runtime.playbackSpeed.get())
				+ runtime.animationOffset.get();

		try {
			var parameters = runtime.parameters(asset, config, getRandomSource(), getT(partialTicks));
			var controlled = PhotonModelAnimationTimelineUtil.sample(this);
			var clip = config.clip;

			if (controlled != null) {
				clip = controlled.animation();
				time = controlledAnimationTime(controlled);
				parameters.loop = controlled.loop() && !controlled.holdEnd();
			}

			status = loadFailed ? STATUS_INVALID : asset.faces.isEmpty() ? STATUS_MISSING : STATUS_OK;

			if (preparedAsset == null) {
				preparedAsset = !loadFailed && !config.assetJson.isBlank()
						? PhotonModelIOUtil.prepareJson(config.assetJson) : PhotonModelSamplerUtil.prepare(asset);
			}

			if (parameters.opacity * PhotonModelSamplerUtil.opacity(preparedAsset, clip, time, parameters.loop) <= 0f) {
				return null;
			}

			return samplePrepared(clip, time, parameters).withOpacity(parameters.opacity
					* PhotonModelSamplerUtil.opacity(preparedAsset, clip, time, parameters.loop));
		} catch (RuntimeException _) {
			status = STATUS_INVALID;
			return null;
		}
	}

	/// 只复用无动态部件覆盖的相同姿态；世界变换和材质设置仍在绘制时读取。
	private SampledModel samplePrepared(String clip, float time, SampleParameters parameters) {
		var key = parameters.partOverrides.isEmpty() && parameters.boneOverrides.isEmpty() && parameters.morphOverrides.isEmpty()
				? new SampleKey(preparedAsset, clip, PhotonModelSamplerUtil.geometryTime(preparedAsset, clip, time),
				parameters.length, parameters.width, parameters.loop, parameters.anchor)
				: null;

		if (key != null && key.equals(sampledKey) && sampledModel != null) {
			return sampledModel;
		}

		var sampled = PhotonModelSamplerUtil.sampleForRender(preparedAsset, clip, time, parameters);
		sampledKey = key;
		sampledModel = key == null ? null : sampled;
		return sampled;
	}

	/// 循环片段保持末帧时固定周期内相位，完整周期末不再折回第一帧。
	private float controlledAnimationTime(PhotonModelAnimationTimelineUtil.Sample controlled) {
		if (!controlled.loop() || !controlled.holdEnd()) {
			return controlled.time();
		}

		var clip = asset.clips.stream().filter(value -> value.id.equals(controlled.animation())).findFirst().orElse(null);

		if (clip == null || !clip.loop) {
			return controlled.time();
		}

		var phase = controlled.time() % clip.duration;
		return phase == 0 ? clip.duration : phase < 0 ? phase + clip.duration : phase;
	}

	@Override
	protected void bakeBatches(PhotonViewSettings settings, List<DrawJob> out, Camera camera, float partialTicks) {
		if (getParticleAmount() == 0 || !runtime.enabled.get()) {
			return;
		}

		var sampled = sample(partialTicks);

		if (sampled == null) {
			return;
		}

		var color = runtime.color.get().get(getRandomSource(), getT(partialTicks)).intValue();

		if (!asset.materials.isEmpty()) {
			for (var entry : PhotonModelPreviewMaterialUtil.orderedGroups(sampled, asset)) {
				var group = entry.getKey();
				var part = modelPart(group.partId());
				var material = asset.materials.get(group.materialId());
				var opacity = sampled.opacity() * sampled.partOpacity().getOrDefault(group.partId(), 1f);
				var groupColor = PhotonModelPreviewMaterialUtil.tint(color, material);
				var renderer = runtime.rendererFor(material, part.doubleSided);
				var cullFaces = cullFaces(settings, renderer, part);
				bakeGroup(settings, out, camera, partialTicks, renderer, geometryMode(),
						(vertices, view, _) -> writeMesh(vertices, entry.getValue(), view, groupColor, opacity, part.shading, cullFaces));
			}

			return;
		}

		for (var entry : sampled.partMeshes().entrySet()) {
			var part = modelPart(entry.getKey());
			var opacity = sampled.opacity() * sampled.partOpacity().getOrDefault(entry.getKey(), 1f);
			var renderer = runtime.rendererFor(null, part.doubleSided);
			var cullFaces = cullFaces(settings, renderer, part);
			bakeGroup(settings, out, camera, partialTicks, renderer, geometryMode(),
					(vertices, view, _) -> writeMesh(vertices, entry.getValue(), view, color, opacity, part.shading, cullFaces));
		}
	}

	@Override
	protected void bakeGeometry(VertexConsumer geometry, Camera camera, float partialTicks) {
		var sampled = sample(partialTicks);

		if (sampled == null) {
			return;
		}

		var color = runtime.color.get().get(getRandomSource(), getT(partialTicks)).intValue();

		if (!sampled.materialMeshes().isEmpty()) {
			for (var entry : sampled.materialMeshes().entrySet()) {
				writeMesh(geometry, entry.getValue(), camera, color, sampled.opacity(), modelPart(entry.getKey().partId()).shading, false);
			}

			return;
		}

		for (var entry : sampled.partMeshes().entrySet()) {
			writeMesh(geometry, entry.getValue(), camera, color, sampled.opacity(), modelPart(entry.getKey()).shading, false);
		}
	}

	private Part modelPart(String id) {
		return asset.parts.stream().filter(part -> part.id.equals(id)).findFirst().orElseThrow();
	}

	/// 仅无顶点位移的普通贴图材质提前剔除；双面、线框和自定义着色器保留原生管线判断。
	private static boolean cullFaces(PhotonViewSettings settings, RendererSetting.Runtime renderer, Part part) {
		return !part.doubleSided && !settings.wireframe() && !renderer.getMaterials().isEmpty()
				&& renderer.getMaterials().stream().allMatch(material ->
				material.isCull() && material.getMaterial().getClass() == TextureMaterial.class);
	}

	private void writeMesh(VertexConsumer geometry, PhotonMesh mesh, Camera camera, int color, float opacity,
	                       boolean shading, boolean cullFaces) {
		var origin = PhotonCameraUtils.renderOrigin(camera);
		var world = new Matrix4f().translation(-origin.x, -origin.y, -origin.z).mul(transform().localToWorldMatrix());
		var normalMatrix = world.normal(new Matrix3f());
		var determinant = world.determinant3x3();
		Vector3f localEye = null;

		if (cullFaces && Float.isFinite(determinant) && determinant != 0f) {
			localEye = new Matrix4f(transform().localToWorldMatrix()).invert()
					.transformPosition(PhotonCameraUtils.facingEye(camera));
		}
		var light = getLightColor(BlockPos.containing(transform().position().x, transform().position().y, transform().position().z));
		var red = ((color >>> 16) & 0xFF) / COLOR_CHANNEL_MAX;
		var green = ((color >>> 8) & 0xFF) / COLOR_CHANNEL_MAX;
		var blue = (color & 0xFF) / COLOR_CHANNEL_MAX;
		var alpha = ((color >>> 24) & 0xFF) / COLOR_CHANNEL_MAX * Math.clamp(opacity, 0, 1);
		var positions = mesh.geometry();
		var attributes = mesh.attributes();
		var indices = mesh.indices();
		int triangle = 0;
		var position = new Vector3f();
		var normal = new Vector3f();

		while (triangle < mesh.triangleCount()) {
			var indexOffset = triangle * 3;
			var paired = mesh.quadPaired(triangle);

			if (!paired && localEye != null && PhotonModelCullUtil.backFacing(mesh, triangle, localEye, determinant < 0f)) {
				triangle++;
				continue;
			}

			// QUADS 缓冲保留原生四边面；独立三角面重复末角。
			for (int corner = 0; corner < 4; corner++) {
				var vertex = indices[indexOffset + (corner < 3 ? corner : paired ? 4 : 2)];
				var offset = PhotonMesh.geometryOffset(vertex);
				var attributeOffset = PhotonMesh.attributeOffset(vertex);
				world.transformPosition(position.set(positions[offset], positions[offset + 1], positions[offset + 2]));
				normal.set(positions[offset + 3], positions[offset + 4], positions[offset + 5]).mul(normalMatrix);

				if (normal.lengthSquared() > 0 && Float.isFinite(normal.lengthSquared())) {
					normal.normalize();
				} else {
					normal.set(0, 0, 1);
				}

				float shade = shading ? directionalShade(normal) : 1f;
				geometry.addVertex(position.x, position.y, position.z).setUv(attributes[attributeOffset], attributes[attributeOffset + 1])
						.setColor(red * shade, green * shade, blue * shade, alpha).setLight(light).setNormal(normal.x, normal.y, normal.z);
			}

			triangle += paired ? 2 : 1;
		}
	}

	private static float directionalShade(Vector3f normal) {
		var lighting = CardinalLighting.DEFAULT;
		return normal.x * normal.x * (normal.x < 0 ? lighting.west() : lighting.east())
				+ normal.y * normal.y * (normal.y < 0 ? lighting.down() : lighting.up())
				+ normal.z * normal.z * (normal.z < 0 ? lighting.north() : lighting.south());
	}

	private static float finiteNonnegative(float value) {
		return Float.isFinite(value) ? Math.max(0, value) : PhotonModelEmitterConfig.DEFAULT_PLAYBACK_SPEED;
	}

	@Override
	public void inspectSceneInformation(SceneView view, UIElement container) {
		super.inspectSceneInformation(view, container);
		container.addChild(view.fxObjectInfoView.createInformation(Component.translatable(PhotonModelTextUtil.key("model_status")),
				() -> Component.translatable(PhotonModelTextUtil.key("status." + status))));
	}

	private enum TransformChannel {
		POSITION("position"), ROTATION("rotation"), SCALE("scale");

		private final String key;

		@SuppressWarnings("UnnecessaryModifier")
		private TransformChannel(String key) {
			this.key = key;
		}
	}

	private enum ComponentAxis {
		X, Y, Z
	}
}
