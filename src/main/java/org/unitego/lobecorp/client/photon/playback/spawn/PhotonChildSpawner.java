package org.unitego.lobecorp.client.photon.playback.spawn;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;
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
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTemplateProgram;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.PreloadContext;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.ArrayDeque;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.resources.Identifier;
import java.util.function.Function;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

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
	private @Nullable PhotonTemplateProgram preparedProgram;
	private @Nullable Function<Identifier, FX> ownedDefinitionResolver;


	private @Nullable WeakReference<FXData> preloadDefinition;
	private static final ThreadLocal<Set<IFXObject>> PRELOAD_PATH = ThreadLocal.withInitial(HashSet::new);

	/// 定义预加载不构造运行树；作者内存发布也应调用此入口。
	public static void bindPreloadDefinition(FXData definition) {
		for (var object : definition.objects()) {
			if (object instanceof PhotonChildSpawner child) {
				child.preloadDefinition = new WeakReference<>(definition);
			}
		}
	}

	@Override
	public void preload(PreloadContext context) {
		if (!config.enabled && !context.isAnimated(this, "enabled")) {
			return;
		}

		var visiting = PRELOAD_PATH.get();
		if (!visiting.add(this)) {
			return;
		}

		try {
			if (config.source == PhotonSpawnConfig.Source.FX) {
				if (config.fxLocation != null) {
					context.fx(config.fxLocation);
				}
				return;
			}

			var definition = preloadDefinition == null ? null : preloadDefinition.get();
			if (definition == null) {
				throw new IllegalStateException("PROJECT 预加载缺少所属定义，需先绑定静态定义。");
			}
			var id = config.template.getTransformId();
			var target = definition.objects().stream().filter(object -> object.id().equals(id)).findFirst().orElse(null);
			if (!(target instanceof Emitter) || target == this) {
				throw new IllegalArgumentException("PROJECT 预加载模板缺失或递归。");
			}

			var pending = new ArrayDeque<IFXObject>();
			var seen = new HashSet<UUID>();
			pending.add(target);
			while (!pending.isEmpty()) {
				var object = pending.removeFirst();
				if (!seen.add(object.id()) || seen.size() > PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
					throw new IllegalArgumentException("PROJECT 预加载模板循环或超出对象预算。");
				}
				// 模板可在原工程中禁用；资源准备不能沿用外层父级禁用过滤。
				object.preload(context);
				if (config.includeChildren) {
					for (var child : definition.objects()) {
						if (object.id().equals(child.transform()._getInternalParentID())) {
							pending.add(child);
						}
					}
				}
			}
		} finally {
			visiting.remove(this);
			if (visiting.isEmpty()) {
				PRELOAD_PATH.remove();
			}
		}
	}

	/// 仅显式绑定的自有组合使用私有准备成果；未绑定时保持Photon原生解析。
	public void ownedDefinitionResolver(Function<Identifier, FX> resolver) {
		ownedDefinitionResolver = resolver;
	}

	public void inheritDefinitionResolver(PhotonChildSpawner owner) {
		ownedDefinitionResolver = owner.ownedDefinitionResolver;
	}

	public @Nullable FX resolveDefinition(Identifier id) {
		return ownedDefinitionResolver == null ? FXHelper.getFX(id) : ownedDefinitionResolver.apply(id);
	}

	/// 静态准备只确保当前不可变程序已编译并递归准备；不取得运行租约，也不提前求值动态值。
	public boolean prepareNext() {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		long programNs = 0;
		var diagnosticScope = diagnostic ? PhotonFXLoadDiagnosticsUtil.begin(Stage.TEMPLATE_PREPARE, config.fxLocation, false) : null;
		boolean completed = false;
		String reason = "check_prepared";
		try {
			if (PhotonSpawnTemplateUtil.matches(this, preparedProgram)) {
				reason = preparedProgram.transientFx() ? "transient_refresh" : "already_prepared";
				completed = true;
				return !preparedProgram.transientFx() || refreshTransientProgram();
			}
			if (preparedProgram != null) {
				preparedProgram = null;
			}
			reason = "source_or_state_ineligible";
			if (stopped || removed) {
				completed = true;
				return false;
			}
			reason = "source_ineligible";
			if (config.source == PhotonSpawnConfig.Source.FX && config.fxLocation == null) {
				completed = true;
				return false;
			}
			if (config.source == PhotonSpawnConfig.Source.PROJECT && config.template.getTransformId() == null) {
				completed = true;
				return false;
			}
			reason = "program";
			long programStarted = diagnostic ? System.nanoTime() : 0;
			preparedProgram = PhotonSpawnTemplateUtil.program(this);
			if (diagnostic) {
				programNs = System.nanoTime() - programStarted;
			}
			if (preparedProgram == null) {
				reason = "native_project";
				completed = true;
				return false;
			}
			if (preparedProgram.transientFx()) {
				reason = "transient_fallback";
				completed = true;
				return false;
			}
			reason = "prepared";
			completed = true;
			return true;
		} finally {
			if (diagnosticScope != null) {
				long ended = System.nanoTime();
				if (completed) {
					diagnosticScope.succeeded();
				}
				PhotonFXLoadDiagnosticsUtil.finish(diagnosticScope);
				Lobecorp.LOGGER.info("CASTPROF prepareNext spawner={} program={} startNs={} endNs={} elapsedNs={} programNs={} diagnosticId={} reason={} completed={}",
						System.identityHashCode(this), preparedProgram == null ? 0 : System.identityHashCode(preparedProgram),
						started, ended, ended - started, programNs, diagnosticScope.id(), reason, completed);
			}
		}
	}

	/// 未登记可变 FX 的定义只在 prepare 调用内有效；保留程序类型/结构供预热读取，消费按当前内容重建。
	private boolean refreshTransientProgram() {
		var location = config.fxLocation;
		var fx = location == null ? null : resolveDefinition(location);
		if (fx == null) {
			preparedProgram = null;
			return false;
		}
		var path = PhotonSpawnTemplateUtil.path(this);
		if (path == null) {
			preparedProgram = null;
			return false;
		}
		preparedProgram = PhotonTemplateProgram.fx(PhotonRuntimeFactory.prepareTransient(fx), location,
				path, fx, fx.getFxData(), false);
		return true;
	}

	private PhotonSpawnTemplateUtil.Template nextTemplate() {
		boolean diagnostic = PhotonCastProfiler.enabled();
		long started = diagnostic ? System.nanoTime() : 0;
		boolean completed = false;
		String reason = "program_missing";
		var diagnosticScope = diagnostic ? PhotonFXLoadDiagnosticsUtil.begin(Stage.TEMPLATE_NEXT, config.fxLocation, false) : null;
		PhotonSpawnTemplateUtil.Template candidate = null;
		try {
			var program = preparedProgram;
			if (program == null) {
				reason = "program_missing";
			} else if (path.size() >= Math.clamp(config.maxDepth, 1, PhotonSpawnConfig.MAX_DEPTH)) {
				reason = "depth_limit";
			} else if (!PhotonSpawnTemplateUtil.matches(this, program)) {
				reason = "program_stale";
				preparedProgram = null;
			} else {
				reason = "program";
				candidate = PhotonSpawnTemplateUtil.instantiate(this, program);
				if (!program.reusable()) {
					preparedProgram = null;
				}
				completed = true;
				return candidate;
			}
			var createScope = diagnostic ? PhotonFXLoadDiagnosticsUtil.begin(Stage.TEMPLATE_CREATE) : null;
			try {
				program = PhotonSpawnTemplateUtil.program(this);
				candidate = PhotonSpawnTemplateUtil.instantiate(this, program);

				if (program != null && program.reusable()) {
					preparedProgram = program;
				}

				if (diagnostic) {
					createScope.returned(candidate);
				}
			} finally {
				PhotonFXLoadDiagnosticsUtil.finish(createScope);
			}
			completed = true;
			return candidate;
		} finally {
			if (diagnosticScope != null) {
				long ended = System.nanoTime();
				if (completed) {
					diagnosticScope.returned(candidate);
				}
				PhotonFXLoadDiagnosticsUtil.finish(diagnosticScope);
				Lobecorp.LOGGER.info("CASTPROF nextTemplate spawner={} runtime={} startNs={} endNs={} elapsedNs={} reason={} completed={}",
						System.identityHashCode(this), candidate == null ? 0 : System.identityHashCode(candidate.runtime()),
						started, ended, ended - started, reason, completed);
			}
		}
	}

	/// 预热用：读取当前不可变程序；真实运行租约尚未创建。
	public @Nullable PhotonTemplateProgram preparedProgram() {
		return preparedProgram;
	}

	private void discardPrepared() {
		preparedProgram = null;
	}

	/// 运行期来源变化只失效当前结构程序，不销毁任何已租运行实例。
	public void invalidatePreparedProgram() {
		preparedProgram = null;
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
	public @Nullable IFXObject deepCopy() {
		return PhotonRuntimeCopyUtil.deepCopy(this);
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonSpawnTypes.SPAWNER;
	}

	public void rememberSource(CompoundTag source) {
		original = source.copy();
		preparedProgram = null;
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
		preparedProgram = null;
	}

	@Override
	protected void onTickBegin() {
		super.onTickBegin();
		budget = Math.max(1, config.maxPerTick);
		prune();
	}

	@Override
	protected void update(float delta) {
        prune();
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
					template.close(true);
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
		if (force) {
			ownedDefinitionResolver = null;
		}
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
		prune();
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
