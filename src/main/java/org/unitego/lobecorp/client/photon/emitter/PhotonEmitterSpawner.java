package org.unitego.lobecorp.client.photon.emitter;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonEmitterSpawnRuntime;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonEmitterSpawnSchedule;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonSpawnOverrides;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonSpawnedEffects;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEmitterTypes;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 自身不创建粒子，只按独立调度发射项目模板或完整 FX 的隔离实例。
 */
@ParametersAreNonnullByDefault
public class PhotonEmitterSpawner extends Emitter implements PhotonEmissionControlAccess {
	@Persisted(subPersisted = true)
	public final PhotonEmitterSpawnConfig config = new PhotonEmitterSpawnConfig();
	private final PhotonEmitterSpawnRuntime runtime = new PhotonEmitterSpawnRuntime(config);
	private final PhotonEmitterSpawnSchedule schedule = new PhotonEmitterSpawnSchedule();
	private final PhotonSpawnedEffects effects = new PhotonSpawnedEffects(this);
	private final RendererSetting.Runtime renderer = new RendererSetting.Runtime(new RendererSetting()) {
	};
	private Set<String> sourcePath = Set.of();
	@Nullable
	private IScene sourceScope;
	@Nullable
	private PhotonSpawnOverrides parameterOverride;
	private boolean activationStopped;
	private boolean manuallyStopped;
	private boolean preserveOnReset;
	private boolean paused;
	private int budget = PhotonEmitterSpawnConfig.DEFAULT_MAX_PER_TICK;
	private long generated;
	private String spawnStatus = PhotonEmitterSpawnerTextUtil.STATUS_OK;

	public PhotonEmitterSpawnRuntime runtime() {
		return runtime;
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonEmitterTypes.EMITTER_SPAWNER;
	}

	@Override
	public IGuiTexture getIcon() {
		return ParticleEmitter.ICON;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		super.buildConfigurator(father);
		config.buildConfigurator(father);
	}

	@Override
	protected void onTickBegin() {
		super.onTickBegin();
		budget = Math.max(1, config.maxPerTick);
		effects.prune();
	}

	@Override
	protected void update(float dt) {
		if (paused || !Float.isFinite(dt) || dt <= 0) {
			return;
		}

		t = fraction(ageF);
		if (!lobecorp$isEmissionStopped() && runtime.enabled.get()) {
			schedule.update(this, dt);
		}

		ageF += dt;
		age = (int) ageF;
		t = fraction(ageF);
		if (!removed && !isLooping() && ageF - getStartDelay() >= getLifetime()) {
			remove(false);
		}
	}

	private float fraction(float age) {
		var time = Math.max(0, age - getStartDelay());
		return isLooping() ? time % getLifetime() / getLifetime() : Math.clamp(time / getLifetime(), 0, 1);
	}

	@Override
	public float getT(float partialTicks) {
		return fraction(ageF + (paused ? 0 : Math.clamp(partialTicks, 0, 1)));
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
		return effects.size();
	}

	@SuppressWarnings("unused")
	public long generatedCount() {
		return generated;
	}

	public List<UUID> instanceHandles() {
		effects.prune();
		return effects.handles();
	}

	public boolean stopInstance(UUID handle, boolean force) {
		return effects.stop(handle, force);
	}

	public void emitConfigured() {
		var count = runtime.count.get().get(getRandomSource(), getT()).doubleValue();
		if (!Double.isFinite(count) || count < 0) {
			setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
			return;
		}
		emitNow((int) Math.min(count, Integer.MAX_VALUE));
	}

	public int emitNow(int count) {
		if (count <= 0 || paused || !isActive() || getEffectExecutor() == null || lobecorp$isEmissionStopped()) {
			return 0;
		}

		if (getScene() instanceof FXRuntime source && !source.isValid()) {
			return 0;
		}

		if (budget <= 0) {
			setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_CAPACITY);
			return 0;
		}

		effects.prune();
		var spawned = 0;
		var probability = runtime.probability.get().get(getRandomSource(), getT()).floatValue();
		if (!Float.isFinite(probability) || probability < 0 || probability > 1) {
			setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
			return 0;
		}

		var attempts = Math.min(count, budget);
		for (int i = 0; i < attempts; i++) {
			if (effects.size() >= Math.max(0, runtime.maxInstances.get())
					|| config.maxTotal > 0 && generated >= config.maxTotal) {
				setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_CAPACITY);
				break;
			}

			budget--;
			if (getRandomSource().nextFloat() < probability && effects.spawn(parameterOverride)) {
				generated++;
				spawned++;
			}
		}
		return spawned;
	}

	public void stopEmission() {
		manuallyStopped = true;
	}

	public boolean resumeEmission() {
		if (removed) {
			return false;
		}

		manuallyStopped = false;
		return true;
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean value) {
		paused = value;
		effects.syncState();
	}

	public void setParameterOverride(@Nullable PhotonSpawnOverrides values) {
		parameterOverride = values;
	}

	public Set<String> sourcePath() {
		return sourcePath;
	}

	@Nullable
	public IScene templateScope() {
		return sourceScope == null ? getScene() : sourceScope;
	}

	public void bindSourceScope(@Nullable IScene scope, Set<String> path) {
		sourceScope = scope;
		sourcePath = Set.copyOf(path);
	}

	public void setSpawnStatus(String status) {
		spawnStatus = status;
	}

	@Override
	public void reset() {
		if (!preserveOnReset && config.clearOnRestart) {
			effects.stop(true);
		}

		super.reset();
		runtime.clear();
		schedule.reset();
		paused = false;
		activationStopped = false;
		manuallyStopped = false;
		generated = 0;
		budget = Math.max(1, config.maxPerTick);
		spawnStatus = PhotonEmitterSpawnerTextUtil.STATUS_OK;
		effects.syncState();
	}

	public void restart(boolean clear) {
		var previous = preserveOnReset;
		preserveOnReset = !clear;
		if (clear) {
			effects.stop(true);
		}

		try {
			reset();
		} finally {
			preserveOnReset = previous;
		}
	}

	@Override
	public void remove(boolean force) {
		super.remove(force);
		if (force || config.endBehavior == PhotonEmitterSpawnConfig.EndBehavior.CLEAR) {
			effects.stop(true);
			return;
		}

		if (config.endBehavior == PhotonEmitterSpawnConfig.EndBehavior.STOP_EMISSION) {
			effects.stop(false);
		}
	}

	@Override
	public boolean lobecorp$isEmissionStopped() {
		return activationStopped || manuallyStopped || removed || !runtime.enabled.get();
	}

	@Override
	public void lobecorp$setEmissionStopped(boolean stopped) {
		activationStopped = stopped;
	}

	@Override
	public void lobecorp$clearParticles() {
		effects.stop(true);
	}

	@Override
	public void lobecorp$setPreserveOnReset(boolean preserve) {
		preserveOnReset = preserve;
	}

	@Override
	public RendererSetting.Runtime rendererRuntime() {
		return renderer;
	}

	@Override
	protected void bakeBatches(PhotonViewSettings settings, List<DrawJob> out, Camera camera, float partialTicks) {
		// 本类型只调度其他 FX，不提交自己的几何或材质。
	}

	@Override
	protected void bakeGeometry(VertexConsumer geometry, Camera camera, float partialTicks) {
		// 生成实例使用各自原生发射器的渲染路径。
	}

	@Override
	public void inspectSceneInformation(SceneView view, UIElement container) {
		container.addChildren(
				view.fxObjectInfoView.createInformation(Component.translatable(PhotonEmitterSpawnerTextUtil.INSTANCES),
						() -> Component.literal(Integer.toString(effects.size()))),
				view.fxObjectInfoView.createInformation(Component.translatable(PhotonEmitterSpawnerTextUtil.GENERATED),
						() -> Component.literal(Long.toString(generated))),
				view.fxObjectInfoView.createInformation(Component.translatable(PhotonEmitterSpawnerTextUtil.FAILURE),
						() -> Component.translatable(PhotonEmitterSpawnerTextUtil.PREFIX + "status." + spawnStatus)));
	}
}
