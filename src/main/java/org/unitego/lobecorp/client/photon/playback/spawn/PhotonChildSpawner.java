package org.unitego.lobecorp.client.photon.playback.spawn;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonEmissionAccess;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.registry.photon.client.LcPhotonSpawnTypes;
import org.unitego.lobecorp.util.photon.playback.PhotonSpawnTemplateUtil;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/// 调度已有原生FX实例，自身没有几何输出；每实例独占槽、随机数、预算和子实例。
public class PhotonChildSpawner extends Emitter implements PhotonEmissionAccess {
	@Persisted(subPersisted = true)
	public final PhotonSpawnConfig config = new PhotonSpawnConfig();
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private final Map<UUID, PhotonSpawnInstance> instances = new LinkedHashMap<>();
	private final PhotonSpawnSchedule schedule = new PhotonSpawnSchedule();
	private final RendererSetting.Runtime renderer = new RendererSetting.Runtime(new RendererSetting()) {
	};
	private CompoundTag original = new CompoundTag();
	private @Nullable IScene scope;
	private Set<String> path = Set.of();
	private boolean paused;
	private boolean stopped;
	private boolean preserveParticles;
	private int budget;
	private long generated;
	private @Nullable String failure;
	private @Nullable PhotonSpawnTemplateUtil.Template prepared;
	private @Nullable FX preparedDefinition;

	/// 每个生成器只持有下一次独占实例；准备不发射粒子、音频或推进时间线。
	public boolean prepareNext() {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		long createNs = 0;
		boolean completed = false;
		String reason = "check_prepared";
		try {
			if (prepared != null) {
				reason = "already_prepared";
				completed = true;
				return true;
			}
			reason = "source_or_state_ineligible";
			if (config.source != PhotonSpawnConfig.Source.FX || config.fxLocation == null || stopped || removed) {
				completed = true;
				return false;
			}
			reason = "definition_lookup";
			var definition = FXHelper.getFX(config.fxLocation);
			if (definition == null) {
				reason = "definition_missing";
				completed = true;
				return false;
			}
			reason = "create";
			long createStarted = diagnostic ? System.nanoTime() : 0;
			PhotonSpawnTemplateUtil.Template candidate;
			try {
				candidate = PhotonSpawnTemplateUtil.create(this);
			} finally {
				if (diagnostic) {
					createNs = System.nanoTime() - createStarted;
				}
			}
			try {
				reason = "prepare_named";
				((PhotonNamedAccess) candidate.runtime()).lobecorp$prepareNamedRuntime();
				prepared = candidate;
				preparedDefinition = definition;
				reason = "prepared";
				completed = true;
				return true;
			} catch (RuntimeException invalid) {
				candidate.runtime().destroy(true);
				throw invalid;
			}
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF prepareNext spawner={} runtime={} startNs={} endNs={} elapsedNs={} createNs={} reason={} completed={}",
						System.identityHashCode(this), prepared == null ? 0 : System.identityHashCode(prepared.runtime()),
						started, ended, ended - started, createNs, reason, completed);
			}
		}
	}

	private PhotonSpawnTemplateUtil.Template nextTemplate() {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		boolean completed = false;
		String reason = "no_prepared";
		var candidate = prepared;
		try {
			var definition = preparedDefinition;
			prepared = null;
			preparedDefinition = null;
			if (candidate != null) {
				if (config.source != PhotonSpawnConfig.Source.FX) {
					reason = "source_changed";
				} else if (config.fxLocation == null) {
					reason = "location_missing";
				} else if (FXHelper.getFX(config.fxLocation) != definition) {
					reason = "definition_changed";
				} else if (!candidate.path().contains("fx:" + config.fxLocation)) {
					reason = "location_changed";
				} else if (candidate.path().size() != path.size() + 1) {
					reason = "path_size_changed";
				} else if (!candidate.path().containsAll(path)) {
					reason = "path_changed";
				} else if (path.size() >= Math.clamp(config.maxDepth, 1, PhotonSpawnConfig.MAX_DEPTH)) {
					reason = "depth_limit";
				} else {
					reason = "reuse";
					completed = true;
					return candidate;
				}
				candidate.runtime().destroy(true);
			}
			candidate = PhotonSpawnTemplateUtil.create(this);
			completed = true;
			return candidate;
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF nextTemplate spawner={} runtime={} startNs={} endNs={} elapsedNs={} reason={} completed={}",
						System.identityHashCode(this), candidate == null ? 0 : System.identityHashCode(candidate.runtime()),
						started, ended, ended - started, reason, completed);
			}
		}
	}

	/// 预热用：读取已准备但未消费的子模板；所有权仍在本生成器，调用方只可读、不得销毁或转移。
	public @Nullable PhotonSpawnTemplateUtil.Template preparedTemplate() {
		return prepared;
	}

	private void discardPrepared() {
		if (prepared != null) {
			prepared.runtime().destroy(true);
			prepared = null;
			preparedDefinition = null;
		}
	}

	public PhotonChildSpawner() {
		bind("enabled", () -> config.enabled);
		bind("duration", () -> config.duration);
		bind("looping", () -> config.looping);
		bind("startDelay", () -> config.startDelay);
		bind("interval", () -> config.interval);
		bind("count", () -> config.count);
		bind("rate", () -> config.rate);
		bind("probability", () -> config.probability);
		bind("maxInstances", () -> config.maxInstances);
		bind("launchSpeed", () -> config.launchSpeed);
		bind("parameters.lifetime", () -> config.parameters.lifetime);
		bind("parameters.size", () -> config.parameters.size);
		bind("parameters.color", () -> config.parameters.color);
		bind("parameters.speed", () -> config.parameters.speed);
		bind("parameters.beamLength", () -> config.parameters.beamLength);
		bind("parameters.beamWidth", () -> config.parameters.beamWidth);
		bind("parameters.playbackSpeed", () -> config.parameters.playbackSpeed);
		budget = config.maxPerTick;
	}

	private void bind(String path, Supplier<?> fallback) {
		slots.put(path, new RuntimeValue<>(fallback));
	}

	public RuntimeValue<?> slot(String path) {
		var slot = slots.get(path);

		if (slot == null) {
			throw new IllegalArgumentException("子发射运行槽不存在：" + path);
		}

		return slot;
	}

	public NumberFunction function(String path) {
		return (NumberFunction) slot(path).get();
	}

	public int integer(String path) {
		return ((Number) slot(path).get()).intValue();
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonSpawnTypes.SPAWNER;
	}

	public void rememberSource(CompoundTag source) {
		original = source.copy();
	}

	public CompoundTag source() {
		return original.copy();
	}

	public IScene sourceScope() {
		var source = scope == null ? getScene() : scope;

		if (source == null) {
			throw new IllegalStateException("子发射没有项目来源。");
		}

		return source;
	}

	public Set<String> sourcePath() {
		return path;
	}

	public void source(IScene scope, Set<String> path) {
		this.scope = scope;
		this.path = Set.copyOf(path);
	}

	@Override
	protected void onTickBegin() {
		super.onTickBegin();
		budget = Math.max(1, config.maxPerTick);
		prune();
	}

	@Override
	protected void update(float delta) {
		if (paused) {
			return;
		}

		try {
			if (!removed && !stopped && (boolean) slot("enabled").get()) {
				double rate = function("rate").get(getRandomSource(), getT()).doubleValue();
				schedule.advance(ageF, delta, getLifetime(), getStartDelay(), isLooping(), config.mode,
						Math.max(1, integer("interval")), rate, this::emitConfigured, this::emitNow);
			}

			ageF += delta;
			age = (int) ageF;
			t = getT(0);

			if (!removed && !isLooping() && ageF - getStartDelay() >= getLifetime()) {
				remove(false);
			}
		} catch (RuntimeException invalid) {
			failure = invalid.toString();
			stopped = true;
		}
	}

	public void emitConfigured() {
		double count = function("count").get(getRandomSource(), getT()).doubleValue();

		if (!Double.isFinite(count) || count < 0) {
			throw new IllegalArgumentException("子发射数量无效。");
		}

		emitNow((int) Math.min(Integer.MAX_VALUE, count));
	}

	public int emitNow(int count) {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		long templateNs = 0;
		long createNs = 0;
		long startNs = 0;
		int attempts = 0;
		int emitted = 0;
		int attemptLimit = 0;
		boolean completed = false;
		String phase = "eligibility";
		try {
			if (count <= 0 || paused || stopped || removed || !isActive() || getEffectExecutor() == null) {
				completed = true;
				return 0;
			}

			phase = "probability";
			prune();
			double probability = function("probability").get(getRandomSource(), getT()).doubleValue();

			if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
				throw new IllegalArgumentException("子发射概率无效。");
			}

			attemptLimit = Math.min(count, budget);
			for (int attempt = 0; attempt < attemptLimit; attempt++) {
				phase = "capacity";
				if (instances.size() >= Math.max(0, integer("maxInstances")) || config.maxTotal > 0 && generated >= config.maxTotal) {
					break;
				}

				budget--;
				attempts++;
				phase = "random";
				if (getRandomSource().nextFloat() >= probability) {
					continue;
				}

				phase = "template";
				long phaseStarted = diagnostic ? System.nanoTime() : 0;
				PhotonSpawnTemplateUtil.Template template;
				try {
					template = nextTemplate();
				} finally {
					if (diagnostic) {
						templateNs += System.nanoTime() - phaseStarted;
					}
				}
				PhotonSpawnInstance instance;
				try {
					phase = "create";
					phaseStarted = diagnostic ? System.nanoTime() : 0;
					try {
						instance = new PhotonSpawnInstance(this, template);
					} finally {
						if (diagnostic) {
							createNs += System.nanoTime() - phaseStarted;
						}
					}
					phase = "start";
					phaseStarted = diagnostic ? System.nanoTime() : 0;
					try {
						instance.start();
					} finally {
						if (diagnostic) {
							startNs += System.nanoTime() - phaseStarted;
						}
					}
				} catch (RuntimeException invalid) {
					template.runtime().destroy(true);
					throw invalid;
				}

				phase = "register";
				instances.put(instance.id(), instance);
				generated++;
				emitted++;
			}

			completed = true;
			return emitted;
		} finally {
			if (diagnostic) {
				long ended = System.nanoTime();
				Lobecorp.LOGGER.info("CASTPROF emitNow spawner={} startNs={} endNs={} elapsedNs={} requested={} attemptLimit={} attempts={} emitted={} templateNs={} createNs={} startDurationNs={} phase={} completed={}",
						System.identityHashCode(this), started, ended, ended - started, count, attemptLimit,
						attempts, emitted, templateNs, createNs, startNs, phase, completed);
			}
		}
	}

	private void prune() {
		instances.values().removeIf(PhotonSpawnInstance::finished);
	}

	public List<UUID> instanceIds() {
		prune();
		return List.copyOf(instances.keySet());
	}

	public @Nullable PhotonNamedRuntime animations(UUID id) {
		var instance = instances.get(id);
		return instance == null ? null : instance.animations();
	}

	public void stopEmission(boolean value) {
		stopped = value;
	}

	@Override
	public boolean lobecorp$emissionStopped() {
		return stopped;
	}

	@Override
	public void lobecorp$emissionStopped(boolean stopped) {
		stopEmission(stopped);
	}

	@Override
	public void lobecorp$preserveParticles(boolean preserve) {
		preserveParticles = preserve;
	}

	@Override
	public void lobecorp$clearParticles() {
		clearPlayingInstances();
	}

	public boolean paused() {
		return paused;
	}

	public void paused(boolean value) {
		paused = value;
		instances.values().forEach(PhotonSpawnInstance::sync);
	}

	public void clearInstances() {
		discardPrepared();
		clearPlayingInstances();
	}

	private void clearPlayingInstances() {
		instances.values().forEach(instance -> instance.stop(true));
		instances.clear();
	}

	@Override
	public void reset() {
		if (config.clearOnRestart && !preserveParticles) {
			clearPlayingInstances();
		}

		super.reset();
		slots.values().forEach(RuntimeValue::clear);
		schedule.reset();
		paused = false;
		stopped = false;
		generated = 0;
		budget = Math.max(1, config.maxPerTick);
		failure = null;
	}

	@Override
	public void remove(boolean force) {
		discardPrepared();
		super.remove(force);

		if (force || config.endBehavior == PhotonSpawnConfig.EndBehavior.CLEAR) {
			clearInstances();
		} else if (config.endBehavior == PhotonSpawnConfig.EndBehavior.STOP_EMISSION) {
			instances.values().forEach(instance -> instance.stop(false));
		}
	}

	@Override
	public int getLifetime() {
		return Math.max(1, integer("duration"));
	}

	@Override
	public int getStartDelay() {
		return Math.max(0, integer("startDelay"));
	}

	@Override
	public boolean isLooping() {
		return (boolean) slot("looping").get();
	}

	@Override
	public float getT(float partialTicks) {
		float position = Math.max(0, ageF - getStartDelay() + (paused ? 0 : Math.clamp(partialTicks, 0, 1)));
		return isLooping() ? position % getLifetime() / getLifetime() : Math.clamp(position / getLifetime(), 0, 1);
	}

	@Override
	public int getParticleAmount() {
		return instances.size();
	}

	public @Nullable String failure() {
		return failure;
	}

	@Override
	public RendererSetting.Runtime rendererRuntime() {
		return renderer;
	}

	@Override
	protected void bakeBatches(PhotonViewSettings settings, List<DrawJob> out, Camera camera, float partialTicks) {
		// 子实例各自走原生输出，调度对象不提交几何。
	}

	@Override
	protected void bakeGeometry(VertexConsumer geometry, Camera camera, float partialTicks) {
		// 此入口不生成载体粒子。
	}
}
