package org.unitego.lobecorp.client.photon.runtime.validation;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.client.FXSceneOptions;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPlayerAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonPendingParticlePrimeAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;

import java.util.List;
import java.util.Collection;
import java.util.HashSet;

/// 已加载客户端中的真实发射与局部引擎检查；不提交绘制、不推进当前世界或全局粒子引擎。
public class PhotonEmissionPrimeRegression {
	private int assertions;

	/// 需在客户端线程且已进入世界后由编辑器检查入口调用。
	public static int runLoaded() {
		var minecraft = Minecraft.getInstance();

		if (!minecraft.isSameThread() || minecraft.level == null) {
			throw new IllegalStateException("粒子初始化检查需要已进入世界的客户端线程");
		}

		var checks = new PhotonEmissionPrimeRegression();
		checks.initialBurst(minecraft.level);
		checks.zeroRate(minecraft.level);
		checks.continuousRate(minecraft.level);
		checks.positiveDelay(minecraft.level);
		checks.prewarm(minecraft.level);
		checks.reset(minecraft.level);
		checks.limits(minecraft.level);
		checks.laterCycles(minecraft.level);
		checks.nextLoop(minecraft.level);
		checks.stoppedEmission(minecraft.level);
		checks.loopingStop(minecraft.level, false);
		checks.loopingStop(minecraft.level, true);
		checks.globalEngineGuard();
		return checks.assertions;
	}

	private void initialBurst(ClientLevel level) {
		try (var fixture = fixture(level, emitter(), 0)) {
			equal(0, fixture.manager.getParticleAmount(), "局部引擎尚未接入待加入对象");
			var generation = fixture.manager.generation();
			fixture.prime();
			equal(3, fixture.emitter.getParticleAmount(), "零时刻 Burst 数量");
			equal(0, fixture.emitter.getAgeF(), "发射器零时刻年龄");
			equal(0, fixture.manager.getTime(), "初始化未推进场景时间");
			equal(0, fixture.manager.tickCount(), "初始化未推进场景心跳");
			equal(generation, fixture.manager.generation(), "初始化未改变场景代次");
			equal(2, fixture.manager.getParticleAmount(), "根与发射器已进入原生分组");

			for (var particle : particles(fixture.emitter)) {
				equal(0, particle.getAge(), "粒子零时刻年龄");
				vector(new Vector3f(2, 3, 4), particle.getSimPosWithoutNoise(), "出生位置未预移动");
				vector(particle.getSimPos(1), particle.getSimPos(0), "出生插值原点已同步");
				check(particle.getInternalVelocity().lengthSquared() > 0, "夹具具有真实非零速度");
			}

			fixture.prime();
			equal(3, fixture.emitter.getParticleAmount(), "重复初始化不重发");
			fixture.manager.tickInternal();
			equal(3, fixture.emitter.getParticleAmount(), "首个正步不重复零 Burst");
			equal(1, fixture.emitter.getAgeF(), "首个正步发射器年龄");

			for (var particle : particles(fixture.emitter)) {
				equal(1, particle.getAge(), "首个正步粒子年龄");
				vector(new Vector3f(2, 4, 4), particle.getSimPosWithoutNoise(), "正步按原速度移动");
			}
		}
	}

	private void zeroRate(ClientLevel level) {
		var emitter = emitter();
		emitter.config.emission.setBursts(List.of());

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			equal(0, emitter.getParticleAmount(), "零速率无 Burst 不生成");
			equal(0, emitter.getEmissionRateAccum(), "零时刻不修改速率累计");
			fixture.manager.tickInternal();
			equal(0, emitter.getParticleAmount(), "零速率正步不生成");
		}
	}

	private void continuousRate(ClientLevel level) {
		var emitter = emitter();
		emitter.config.emission.setBursts(List.of());
		emitter.config.emission.setEmissionRate(NumberFunction.constant(2));

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			equal(0, emitter.getParticleAmount(), "持续速率不预发");
			equal(0, emitter.getEmissionRateAccum(), "持续速率累计未预推进");
			fixture.manager.tickInternal();
			equal(2, emitter.getParticleAmount(), "持续速率在首个正步发射");
		}
	}

	private void positiveDelay(ClientLevel level) {
		try (var fixture = fixture(level, emitter(), 2)) {
			fixture.prime();
			equal(0, fixture.emitter.getParticleAmount(), "正延迟跳过初始化");
			equal(2, fixture.emitter.getDelay(), "初始化不减少延迟");
			fixture.manager.tickInternal();
			fixture.manager.tickInternal();
			equal(0, fixture.emitter.getParticleAmount(), "原生延迟阶段尚未生成");
			equal(0, fixture.emitter.getDelay(), "原生延迟已结束");
			fixture.prime();
			equal(3, fixture.emitter.getParticleAmount(), "延迟结束后可初始化");
			equal(0, fixture.emitter.getAgeF(), "延迟结束初始化仍为零年龄");
			fixture.manager.tickInternal();
			equal(3, fixture.emitter.getParticleAmount(), "延迟后的首步不重复发射");
		}
	}

	private void prewarm(ClientLevel level) {
		var emitter = emitter();
		emitter.config.setPrewarm(2);

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			equal(0, emitter.getParticleAmount(), "有效预热保持原生初始化入口");
			equal(0, emitter.getAgeF(), "初始化不执行预热");
			fixture.manager.tickInternal();
			equal(3, emitter.getParticleAmount(), "原生预热只发射一次零 Burst");
			equal(3, emitter.getAgeF(), "预热两步加首个正步");
		}
	}

	private void reset(ClientLevel level) {
		try (var fixture = fixture(level, emitter(), 0)) {
			fixture.prime();
			fixture.manager.tickInternal();
			fixture.emitter.reset();
			equal(0, fixture.emitter.getParticleAmount(), "原生重置清理旧粒子");
			PhotonEmissionPrimeUtil.primeSubtree(fixture.emitter);
			equal(3, fixture.emitter.getParticleAmount(), "重置清理消费状态后可重新初始化");
			equal(0, fixture.emitter.getAgeF(), "重新初始化年龄为零");
			fixture.manager.tickInternal();
			equal(3, fixture.emitter.getParticleAmount(), "重置后的首步不重复发射");
		}
	}

	private void limits(ClientLevel level) {
		var emitter = emitter();
		emitter.config.setMaxParticles(2);

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			equal(2, emitter.getParticleAmount(), "初始 Burst 遵循发射器限额");
			fixture.manager.tickInternal();
			equal(2, emitter.getParticleAmount(), "限额已满时不追加零 Burst");
		}
	}

	private void laterCycles(ClientLevel level) {
		var emitter = emitter();
		var burst = emitter.config.emission.getBursts().getFirst();
		burst.cycles = 2;
		burst.interval = 2;

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			fixture.manager.tickInternal();
			fixture.manager.tickInternal();
			equal(3, emitter.getParticleAmount(), "半开区间尚未触发后续周期");
			fixture.manager.tickInternal();
			equal(6, emitter.getParticleAmount(), "后续正时间周期保留原生触发");
		}
	}

	private void nextLoop(ClientLevel level) {
		var emitter = emitter();
		emitter.config.setLooping(true);
		emitter.config.setDuration(2);

		try (var fixture = fixture(level, emitter, 0)) {
			fixture.prime();
			fixture.manager.tickInternal();
			fixture.manager.tickInternal();
			fixture.manager.tickInternal();
			equal(6, emitter.getParticleAmount(), "下一轮零相位 Burst 仍正常触发");
		}
	}

	private void stoppedEmission(ClientLevel level) {
		try (var fixture = fixture(level, emitter(), 0)) {
			if (!(fixture.emitter instanceof PhotonEmissionControlAccess control)) {
				throw new AssertionError("粒子初始化检查需要发射控制 Mixin");
			}

			control.lobecorp$setEmissionStopped(true);
			fixture.prime();
			equal(0, fixture.emitter.getParticleAmount(), "仅停止生成状态不会预发");
			control.lobecorp$setEmissionStopped(false);
			fixture.prime();
			equal(3, fixture.emitter.getParticleAmount(), "恢复生成后零 Burst 尚可消费");
		}
	}

	private void loopingStop(ClientLevel level, boolean continuous) {
		var emitter = emitter();
		emitter.config.setLooping(true);
		emitter.config.setDuration(2);

		if (continuous) {
			emitter.config.emission.setBursts(List.of());
			emitter.config.emission.setEmissionRate(NumberFunction.constant(2));
		}

		try (var fixture = fixture(level, emitter, 0)) {
			// 夹具只推进自己的粒子引擎，制作时间由真实原生播放器显式求值。
			((FXObject) fixture.runtime.getRoot()).setOnUpdateTick(null);
			fixture.runtime.timelinePlayer.setAudioDispatch(false);
			activation(fixture, true, true, new Clip(0, 20, 1));
			activation(fixture, false, true, new Clip(1, 1, 1));
			activation(fixture, true, false, new Clip(8, 0, 1));
			var player = (PhotonAnimationPlayerAccess) fixture.runtime.timelinePlayer;
			var control = (PhotonEmissionControlAccess) emitter;
			player.lobecorp$evaluateAt(0);
			fixture.prime();
			fixture.manager.tickInternal();
			check(emitter.getParticleAmount() > 0, "循环停止夹具已有粒子");
			player.lobecorp$evaluateAt(1);
			check(!control.lobecorp$isEmissionStopped(), "仅结束事件点之前继续生成");
			player.lobecorp$evaluateAt(2);
			check(control.lobecorp$isEmissionStopped(), "仅结束停止覆盖持续区间");
			check(emitter.isSelfActive() && emitter.isSelfTimelineVisible(), "仅停止生成保留活动与可见");
			var retained = particles(emitter);
			var age = retained.getFirst().getAge();
			var count = emitter.getParticleAmount();

			for (var time = 3; time <= 7; time++) {
				player.lobecorp$evaluateAt(time);
				fixture.manager.tickInternal();
				check(control.lobecorp$isEmissionStopped(), "循环后停止状态保持");
				equal(count, emitter.getParticleAmount(), "循环后不追加粒子");
			}

			equal(age + 5, retained.getFirst().getAge(), "停止后已有粒子继续更新");
			check(new HashSet<>(particles(emitter)).containsAll(retained), "停止后保留同一批粒子实例");
			player.lobecorp$evaluateAt(8);
			check(!control.lobecorp$isEmissionStopped(), "新的仅开始事件恢复生成");
			fixture.manager.tickInternal();
			check(emitter.getParticleAmount() > count, "恢复后循环爆发或持续速率重新生成");
			check(new HashSet<>(particles(emitter)).containsAll(retained), "继续行为不清理已有粒子");
		}
	}

	private static void activation(Fixture fixture, boolean start, boolean end, Clip clip) {
		var type = PhotonRegistries.TIMELINE_TRACKS.get("activator");

		if (type == null) {
			throw new AssertionError("循环停止检查需要激活轨道注册");
		}

		Track track = type.create();
		track.targetId(fixture.emitter.id());
		track.clips().add(clip);
		((PhotonActivatorTrackAccess) track).lobecorp$setActivationOptions(new PhotonActivatorOptions(start,
				PhotonActivatorOptions.StartBehavior.CONTINUE, end, PhotonActivatorOptions.EndBehavior.STOP_EMISSION, false));
		fixture.runtime.fxData.timeline().tracks().add(track);
	}

	private void globalEngineGuard() {
		var engine = Minecraft.getInstance().particleEngine;

		if (!(engine instanceof PhotonPendingParticlePrimeAccess access)) {
			throw new AssertionError("粒子初始化检查需要引擎待加入桥接 Mixin");
		}

		// 不向全局队列放入探针；只核对显式误调用被拒绝且现有可见计数未改变。
		var before = engine.countParticles();
		access.lobecorp$flushPendingParticles();
		check(before.equals(engine.countParticles()), "全局引擎现有计数未改变");
		check(engine == Minecraft.getInstance().particleEngine, "全局引擎未被替换");
	}

	private static ParticleEmitter emitter() {
		var emitter = new ParticleEmitter();
		var config = emitter.config;
		config.setLooping(false);
		config.setDuration(20);
		config.setMaxParticles(20);
		config.setPrewarm(0);
		config.setStartDelay(NumberFunction.constant(0));
		config.setStartLifetime(NumberFunction.constant(100));
		config.setStartSpeed(NumberFunction.constant(20));
		config.setParallelUpdate(false);
		config.emission.setEmissionRate(NumberFunction.constant(0));
		config.emission.setDistanceRate(NumberFunction.constant(0));
		config.emission.setEmissionMode(EmissionSetting.Mode.Exacting);
		var burst = new EmissionSetting.Burst();
		burst.setCount(NumberFunction.constant(3));
		config.emission.setBursts(List.of(burst));
		var cone = new Cone();
		cone.setRadius(0);
		cone.setAngle(0);
		config.shape.setShape(cone);
		config.shape.setPosition(new NumberFunction3(2, 3, 4));
		config.physics.setEnable(false);
		config.noise.setEnable(false);
		config.trails.setEnable(false);
		config.subEmitters.setEnable(false);
		return emitter;
	}

	private static Fixture fixture(ClientLevel level, ParticleEmitter emitter, int delay) {
		return new Fixture(level, emitter, delay);
	}

	private static List<TileParticle> particles(ParticleEmitter emitter) {
		return emitter.getParticles().values().stream().flatMap(Collection::stream)
				.map(TileParticle.class::cast).toList();
	}

	private void vector(Vector3f expected, Vector3f actual, String name) {
		check(expected.distanceSquared(actual) < 0.000001f, name + ": " + expected + " / " + actual);
	}

	private void equal(double expected, double actual, String name) {
		check(Double.compare(expected, actual) == 0, name + ": " + expected + " / " + actual);
	}

	private void check(boolean value, String name) {
		assertions++;

		if (!value) {
			throw new AssertionError(name);
		}
	}

	private static class Fixture implements AutoCloseable {
		private final PhotonParticleManager manager;
		private final ParticleEmitter emitter;
		private final FXRuntime runtime;

		private Fixture(ClientLevel level, ParticleEmitter emitter, int delay) {
			var world = new DummyWorld(level.registryAccess());
			manager = new PhotonParticleManager(FXSceneOptions.DEFAULT);
			world.setParticleManager(manager);
			manager.setLevel(DummyWorld.ClientSupport.asClientWorld(world));
			this.emitter = emitter;
			runtime = new FXRuntime(new FXData(List.of(emitter)));
			runtime.emit(new SceneEffect(world), delay);
		}

		private void prime() {
			PhotonEmissionPrimeUtil.prime(runtime);
			PhotonEmissionPrimeUtil.flushScene(manager);
		}

		@Override
		public void close() {
			try {
				runtime.destroy(true);
			} finally {
				manager.clear();
			}
		}
	}

	private static class SceneEffect implements IEffectExecutor {
		private final DummyWorld world;
		private final RandomSource random = RandomSource.create(1);

		private SceneEffect(DummyWorld world) {
			this.world = world;
		}

		@Override
		public Level getLevel() {
			return world;
		}

		@Override
		public RandomSource getRandomSource() {
			return random;
		}
	}
}
