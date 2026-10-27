package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedHandle;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.client.photon.performance.PhotonRenderPrewarm;
import org.unitego.lobecorp.client.photon.render.PhotonFixedRenderAccess;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.unitego.lobecorp.registry.animation.LcAnimationDefinitions;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation.Phase;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 只消费现有权威动画快照；不执行服务端回调、不改动作姿态、伤害或技能时序。
@SuppressWarnings("resource") // 命名接口为借用，实际运行实例由PhotonGeoEffect统一释放。
public class QueenBeamEffects {
	private static final Map<UUID, Beam> PLAYING = new HashMap<>();
	private static final Map<UUID, Long> RETIRED = new HashMap<>();
	/// 首次施放预热：仅预加载 FX 定义（含子 FX 定义），运行态仍按原路径创建；默认开启，可用属性关闭。
	private static final boolean PREWARM = !"false".equals(System.getProperty("lobecorp.photon.prewarmBeam", "true"));
	private static @Nullable Level prewarmedLevel;
	private static boolean prewarmed;
	/// 预热好的首次运行态（含已准备的子实例）；只给首个同定义施放使用，用后即移除。
	private static final Map<Identifier, PhotonGeoEffect> PREPARED = new HashMap<>();
	/// 运行态复用：默认关闭的实验；通过后同一内容的下一次施放复用已销毁对象树并重新 emit。
	private static final boolean POOL = Boolean.getBoolean("lobecorp.photon.poolBeam");
	private static final int MAX_IDLE = 2;
	/// 渲染预热的子模板递归深度（光束 → 命中特效 → 更深子特效）。
	/// A/B 实测：降到 1 会让深层子模板退回施放时按需构建，帧均值反而上升，因此默认保持 2。
	private static final int PREWARM_DEPTH = Math.clamp(
			Integer.getInteger("lobecorp.photon.prewarmDepth", 2), 0, 3);
	/// 渲染预热的逐 tick 任务队列；预热完成后置空，施放采用壳时不影响。
	private static @Nullable PhotonRenderPrewarm.Queue prewarmQueue;
	private static final Map<Identifier, Deque<PhotonGeoEffect>> IDLE = new HashMap<>();
	/// 历史法阵起点相对身体中心前移一格，服务端命中原点保持原值。
	private static final double VISUAL_FORWARD = 1;

	public static void tick() {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			clear();
			return;
		}
		if (PREWARM) {
			if (level != prewarmedLevel) {
				discardPrepared();
				prewarmedLevel = level;
				prewarmed = false;
			}
			if (!prewarmed && level.getGameTime() % 20 == 0 && hasQueen(level)) {
				prewarm();
			}
		}
		var visible = new HashSet<UUID>();
		for (var snapshot : LcClientAnimations.snapshots()) {
			if (!TheQueenOfHatredAnimationDefinitions.isBeam(snapshot.definitionId())
					|| !(level.getEntity(snapshot.ownerEntityId()) instanceof TheQueenOfHatred owner)
					|| owner.isRemoved() || !owner.isAlive()) {
				continue;
			}
			var id = snapshot.instanceId();
			visible.add(id);
			if (Objects.equals(RETIRED.get(id), snapshot.playbackRevision())) {
				continue;
			}
			try {
				var beam = PLAYING.get(id);
				if (beam != null && beam.revision != snapshot.playbackRevision()) {
					finish(id);
					beam = null;
				}
				if (beam == null) {
					PhotonCastProfiler.begin("beam:" + snapshot.definitionId());
					beam = new Beam(owner, snapshot);
					PLAYING.put(id, beam);
					beam.update(snapshot);
					PhotonCastProfiler.end("cast-tick");
				} else {
					beam.update(snapshot);
				}
				if (!beam.effect.isAlive()) {
					finish(id);
					RETIRED.put(id, snapshot.playbackRevision());
				}
			} catch (RuntimeException invalid) {
				finishAndClose(id);
				RETIRED.put(id, snapshot.playbackRevision());
				Lobecorp.LOGGER.warn("无法接入女皇特效实例 {} / {}", id, snapshot.definitionId(), invalid);
			}
		}
		for (var id : List.copyOf(PLAYING.keySet())) {
			if (!visible.contains(id)) {
				finish(id);
			}
		}
		RETIRED.keySet().retainAll(visible);
		drainPrewarm();
	}

	/// 只有当前世界存在女皇时才预热，避免每次进世界都为未出现的技能付出解析与内存。
	private static boolean hasQueen(Level level) {
		var player = Minecraft.getInstance().player;

		if (player == null) {
			return false;
		}

		return !level.getEntitiesOfClass(TheQueenOfHatred.class, player.getBoundingBox().inflate(128)).isEmpty();
	}

	/// 预加载首次施放需要的 FX 运行态；子模板与命名运行时按逐 tick 预算推进，避免女皇出现时一次冻结。
	///
	/// 壳本身很轻（定义已缓存），真正昂贵的是子模板准备与命名运行时构建，因此把后者整体排进队列首位执行，
	/// 保证首次施放优先拿到可用的运行态；队列未完成时施放回退到原有按需构建路径。
	private static void prewarm() {
		prewarmed = true;
		long started = System.nanoTime();
		var shells = new HashMap<Identifier, PhotonGeoEffect>();
		var queue = PhotonRenderPrewarm.enabled() ? new PhotonRenderPrewarm.Queue() : null;
		// 分段账本：解析定义、建壳、取子定义各自耗时，用于定位同步段里真正的大头。
		var timing = new long[3];

		try {
			for (var animationId : List.of(
					TheQueenOfHatredAnimationDefinitions.LASER,
					TheQueenOfHatredAnimationDefinitions.LASER_PHASE_TWO,
					TheQueenOfHatredAnimationDefinitions.STAR_BEAM,
					TheQueenOfHatredAnimationDefinitions.STAR_BEAM_PHASE_TWO)) {
				long phase = System.nanoTime();
				var animation = LcAnimationDefinitions.resolve(animationId);
				var effectId = animation == null ? null : animation.effectId();

				if (effectId == null || shells.containsKey(effectId)) {
					continue;
				}

				var effect = FXHelper.getFX(effectId);
				timing[0] += System.nanoTime() - phase;
				phase = System.nanoTime();

				if (effect == null) {
					continue;
				}

				var shell = new PhotonGeoEffect(effect, prewarmedLevel);

				if (!(shell.runtime().findObject("laser") instanceof PhotonChildSpawner spawner)) {
					shell.close();
					continue;
				}

				timing[1] += System.nanoTime() - phase;
				phase = System.nanoTime();

				var runtime = shell.runtime();

				// 子定义只用于预热缓存，真正消费在 prepareNext 内部；排在队列里做，避免同步段重复解析一次几MB的定义。
				if (queue != null) {
					var childLocation = spawner.config.fxLocation;
					queue.prepend(() -> {
						if (childLocation != null && !runtime.isDestroyed()) {
							FXHelper.getFX(childLocation);
						}
					});
				} else if (spawner.config.fxLocation != null) {
					FXHelper.getFX(spawner.config.fxLocation);
				}

				timing[2] += System.nanoTime() - phase;

				if (queue == null) {
					spawner.prepareNext();
					((PhotonNamedAccess) runtime).lobecorp$prepareNamedRuntime();
				} else {
					queue.addShell(runtime, () -> {
						try {
							spawner.prepareNext();
							((PhotonNamedAccess) runtime).lobecorp$prepareNamedRuntime();
						} catch (RuntimeException invalid) {
							Lobecorp.LOGGER.warn("壳预热失败：{}", effectId, invalid);
						}
					}, PREWARM_DEPTH);
				}

				shells.put(effectId, shell);
			}

			PREPARED.putAll(shells);
			shells.clear();
			prewarmQueue = queue;
		} catch (RuntimeException failure) {
			Lobecorp.LOGGER.warn("光束 FX 预热失败，首次施放将回退原加载路径", failure);
		} finally {
			shells.values().forEach(PhotonGeoEffect::close);
		}

		Lobecorp.LOGGER.info("CASTPREWARM beamDefinitions {}ms, prepared={}, queued={}, depth={}, define={}ms, shell={}ms, child={}ms",
				(System.nanoTime() - started) / 1_000_000, PREPARED.size(), queue != null, PREWARM_DEPTH,
				timing[0] / 1_000_000, timing[1] / 1_000_000, timing[2] / 1_000_000);
	}

	/// 预热渲染侧：把剩余预热任务按每 tick 固定预算推进；完成后记录一次汇总。
	private static void drainPrewarm() {
		if (prewarmQueue == null) {
			return;
		}

		prewarmQueue.drain();

		if (prewarmQueue.done()) {
			Lobecorp.LOGGER.info("CASTPREWARM render done, built={}, spawners={}, failed={}",
					prewarmQueue.built(), prewarmQueue.spawners(), prewarmQueue.failed());
			prewarmQueue = null;
		}
	}

	private static void finish(UUID id) {
		var beam = PLAYING.remove(id);
		if (beam != null) {
			beam.release(true);
		}
	}

	private static void finishAndClose(UUID id) {
		var beam = PLAYING.remove(id);
		if (beam != null) {
			beam.release(false);
		}
	}

	public static void clear() {
		PLAYING.values().forEach(value -> value.effect.close());
		PLAYING.clear();
		RETIRED.clear();
		discardPrepared();
	}

	/// 释放未使用的预热/复用运行态；已采用的在 Beam 生命周期内由 effect 自己关闭或归还。
	private static void discardPrepared() {
		if (prewarmQueue != null) {
			prewarmQueue.clear();
			prewarmQueue = null;
		}

		PREPARED.values().forEach(PhotonGeoEffect::close);
		PREPARED.clear();
		closeIdle();
	}

	private static @Nullable PhotonGeoEffect borrow(Identifier effectId) {
		if (!POOL || effectId == null) {
			return null;
		}

		var queue = IDLE.get(effectId);
		var effect = queue == null ? null : queue.pollFirst();

		if (effect != null) {
			PhotonCastProfiler.mark("borrow");
		}

		return effect;
	}

	private static void recycle(Identifier effectId, PhotonGeoEffect effect) {
		if (!POOL || effectId == null) {
			effect.close();
			return;
		}

		effect.stop(true);
		var queue = IDLE.computeIfAbsent(effectId, _ -> new ArrayDeque<>());

		if (queue.size() >= MAX_IDLE) {
			effect.close();
			return;
		}

		queue.addLast(effect);
		PhotonCastProfiler.mark("recycle");
	}

	private static void closeIdle() {
		IDLE.values().forEach(queue -> queue.forEach(PhotonGeoEffect::close));
		IDLE.clear();
	}

	private static class Beam {
		private final TheQueenOfHatred owner;
		private final PhotonGeoEffect effect;
		private final Identifier effectId;
		private final PhotonChildSpawner emitter;
		private final boolean star;
		private final UUID instanceId;
		private final long revision;
		private final Vector3f circleOffset;
		private final Vector3f beamOffset;
		private final Vector3f beamScale;
		private Phase phase = Phase.CHARGE;
		private Vec3 start = Vec3.ZERO;
		private Vec3 end = Vec3.ZERO;
		private Vec3 frozenStart;
		private Vec3 frozenEnd;
		private boolean active;
		private boolean witnessedCharge;
		private boolean preparedOutput;
		private PhotonNamedHandle inputHandle;
		private float minimumLength;
		private Map<String, List<Float>> beamInputs = Map.of();

		private Beam(TheQueenOfHatred owner, LcAnimationSnapshot snapshot) {
			this.owner = owner;
			instanceId = snapshot.instanceId();
			revision = snapshot.playbackRevision();
			star = TheQueenOfHatredAnimationDefinitions.isStarBeam(snapshot.definitionId());
			var id = LcAnimationDefinitions.resolve(snapshot.definitionId()).effectId();
			effectId = id;
			var definition = id == null ? null : FXHelper.getFX(id);
			PhotonCastProfiler.mark("fxDefinition");
			if (definition == null) {
				throw new IllegalArgumentException("特效定义缺失或受保护：" + id);
			}
			var prepared = PREPARED.remove(id);
			var borrowed = prepared == null ? borrow(id) : null;
			boolean pooled = borrowed != null;

			if (prepared != null) {
				effect = prepared;
				PhotonCastProfiler.mark("adoptPrepared");
			} else if (pooled) {
				effect = borrowed;
				PhotonCastProfiler.mark("reuseRuntime");
			} else {
				effect = new PhotonGeoEffect(definition, owner.level());
				PhotonCastProfiler.mark("createRuntime");
			}
			try {
				if (!(effect.runtime().findObject("laser") instanceof PhotonChildSpawner source)) {
					throw new IllegalArgumentException("光束生成器缺失。");
				}
				emitter = source;
				var child = emitter.config.fxLocation == null ? null : FXHelper.getFX(emitter.config.fxLocation);
				PhotonCastProfiler.mark("childDefinition");
				var anchor = child == null ? null : child.getFxData().objects().stream().filter(value -> value.getName().equals("laser_anchor")).findFirst().orElse(null);
				var circle = effect.runtime().findObject("circle");
				if (anchor == null || circle == null) {
					throw new IllegalArgumentException("光束或法阵锚点缺失。");
				}
				var root = effect.runtime().root.transform().position();
				circleOffset = new Vector3f(circle.transform().position()).sub(root);
				beamOffset = new Vector3f(anchor.transform().localPosition()).mul(emitter.transform().scale());
				emitter.transform().rotation().transform(beamOffset);
				beamOffset.add(emitter.transform().position()).sub(root);
				beamScale = new Vector3f(emitter.transform().scale()).mul(anchor.transform().localScale());
				if (!beamScale.isFinite() || beamScale.x == 0 || beamScale.z == 0) {
					throw new IllegalArgumentException("光束锚点缩放为零。");
				}
				for (var object : effect.runtime().objects.values()) {
					if (object instanceof Emitter value) {
						((PhotonFixedRenderAccess) value.rendererRuntime()).lobecorp$useFixedRendering();
					}
					if (object instanceof PhotonModelOutput value) {
						((PhotonFixedRenderAccess) value.config.renderer).lobecorp$useFixedRendering();
						((PhotonFixedRenderAccess) value.config.modelRenderer).lobecorp$useFixedRendering();
					}
					if (object instanceof PhotonNamedController controller) {
						var library = controller.definition();
						library.putString("defaultAnimation", "");
						for (var animation : library.getListOrEmpty("animations")) {
							controlled(((CompoundTag) animation).getListOrEmpty("tracks"));
						}
						controller.definition(library);
					}
				}
				PhotonCastProfiler.mark("controllerSetup");
				anchor();
				PhotonCastProfiler.mark("anchor");
				if (pooled) {
					if (!effect.restart()) {
						throw new IllegalStateException("复用运行态重启失败。");
					}
					PhotonCastProfiler.mark("reuseEmit");
				} else {
					effect.start();
					PhotonCastProfiler.mark("emit");
				}
				var rootObject = (FXObject) effect.runtime().root;
				var previousFrame = rootObject.getOnUpdateFrame();
				rootObject.setOnUpdateFrame(partial -> {
					try {
						if (effect.isAlive()) {
							anchor(partial);
							if (active && phase != Phase.RECOVERY) {
								inputs();
							}
						}
					} catch (RuntimeException invalid) {
						finishAndClose(instanceId);
						RETIRED.put(instanceId, revision);
						Lobecorp.LOGGER.warn("女皇特效帧更新失败 {}", instanceId, invalid);
					}
					if (previousFrame != null) {
						previousFrame.accept(partial);
					}
				});
				var charge = animations().play("charge_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.WINDUP_TICKS), true, Map.of());
				PhotonCastProfiler.mark("namedBegin");
				if (parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE) != Phase.CHARGE.ordinal()) {
					animations().seek(charge, charge.authoredDuration());
					PhotonCastProfiler.mark("namedSeek");
				}
			} catch (RuntimeException invalid) {
				effect.close();
				throw invalid;
			}
		}

		private static void controlled(ListTag tracks) {
			for (var tag : tracks) {
				var track = (CompoundTag) tag;
				var data = track.getCompoundOrEmpty("data");
				if (track.getStringOr("type", "").equals("group")) {
					controlled(data.getListOrEmpty("children"));
				} else if (track.getStringOr("type", "").equals("lobecorp_child_animation")) {
					for (var clip : data.getListOrEmpty("clips")) {
						((CompoundTag) clip).putString("mode", "CONTROLLED");
					}
				}
			}
		}

		private void release(boolean recycle) {
			if (recycle && POOL) {
				QueenBeamEffects.recycle(effectId, effect);
			} else {
				effect.close();
			}
		}

		private PhotonNamedRuntime animations() {
			return Objects.requireNonNull(((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime());
		}

		private static double parameter(LcAnimationSnapshot snapshot, String key) {
			return snapshot.parameters().getOrDefault(key, 0.0);
		}

		private Vec3 sourceStart() {
			return sourceStart(1);
		}

		private Vec3 sourceStart(float partial) {
			if (frozenStart != null) {
				return frozenStart;
			}
			var offset = StarBeamSkill.origin(owner).subtract(owner.getEyePosition())
					.yRot(owner.yBodyRot * Mth.DEG_TO_RAD).xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
					.xRot(owner.getViewXRot(partial) * Mth.DEG_TO_RAD)
					.yRot(-Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD);
			var origin = owner.getEyePosition(partial).add(offset);
			return star ? origin : origin.add(owner.getViewVector(partial).scale(VISUAL_FORWARD));
		}

		private Vec3 sourceEnd(Vec3 origin) {
			if (frozenEnd != null) {
				return frozenEnd;
			}
			return sourceEnd(origin, 1);
		}

		private Vec3 sourceEnd(Vec3 origin, float partial) {
			if (frozenEnd != null) {
				return frozenEnd;
			}
			var direction = owner.getViewVector(partial);
			var staff = star ? origin : origin.subtract(direction.scale(VISUAL_FORWARD));
			var target = staff.add(direction.scale(star ? StarBeamSkill.RANGE : LaserSkill.RANGE));
			var result = phase == Phase.CHARGE ? target : owner.level().clip(new ClipContext(staff, target,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
			return result.subtract(origin).dot(direction) > 0 ? result : origin;
		}

		private void anchor() {
			anchor(1);
		}

		private void anchor(float partial) {
			var origin = sourceStart(partial);
			end = sourceEnd(origin, partial);
			var direction = end.subtract(origin);
			if (direction.lengthSqr() == 0) {
				direction = owner.getViewVector(partial);
			}
			direction = direction.normalize();
			var rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.toVector3f());
			var position = origin.toVector3f().sub(rotation.transform(new Vector3f(circleOffset)));
			start = new Vec3(rotation.transform(new Vector3f(beamOffset)).add(position));
			end = start.add(direction.scale(Math.max(0, end.subtract(start).dot(direction))));
			effect.anchor(position, rotation, new Vector3f(1));
		}

		private void update(LcAnimationSnapshot snapshot) {
			var value = parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE);
			var next = value == Phase.OUTPUT.ordinal() ? Phase.OUTPUT : value == Phase.RECOVERY.ordinal() ? Phase.RECOVERY : Phase.CHARGE;
			witnessedCharge |= next == Phase.CHARGE;
			if (next == Phase.CHARGE && !preparedOutput) {
				PhotonCastProfiler.mark("prepareNextStart");
				preparedOutput = emitter.prepareNext();
				PhotonCastProfiler.mark("prepareNext");
			}
			// 瞬时技能同刻进入恢复；只为已见蓄力的实例保留一次发射呈现，不补播晚追踪的历史输出。
			if (star && next == Phase.RECOVERY && !active && witnessedCharge) {
				phase = Phase.OUTPUT;
				beginOutput(snapshot);
				return;
			}

			if (next != phase) {
				phase = next;
				if (next == Phase.OUTPUT) {
					beginOutput(snapshot);
				} else if (next == Phase.RECOVERY) {
					frozenStart = sourceStart();
					frozenEnd = sourceEnd(frozenStart);
					if (active) {
						animations().play("laser_end", parameter(snapshot, TheQueenOfHatredBeamAnimation.RECOVERY_TICKS), true, Map.of());
					}
					animations().play("charge_end", parameter(snapshot, TheQueenOfHatredBeamAnimation.RECOVERY_TICKS), true, Map.of());
					anchor();
				}
			}
			if (active && phase != Phase.RECOVERY) {
				inputs();
			}
		}

		private void beginOutput(LcAnimationSnapshot snapshot) {
			active = true;
			if (star) {
				frozenStart = StarBeamSkill.origin(owner);
				frozenEnd = StarBeamSkill.endpoint(owner, frozenStart.add(owner.getLookAngle()));
			}

			if (effect.runtime().findObject("accumulate_particle") instanceof PhotonChildSpawner charge) {
				charge.clearInstances();
			}

			anchor();
			animations().play("laser_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.ENTRY_TICKS), true, Map.of());
			inputs();
		}

		private void inputs() {
			var source = animations().playback("laser_start");
			if (source == null) {
				return;
			}
			if (inputHandle != source) {
				var signature = source.definition().parameters().stream().filter(value -> value.name().equals("length")).findFirst()
						.orElseThrow(() -> new IllegalArgumentException("光束长度参数缺失。"));
				inputHandle = source;
				minimumLength = signature.minimum();
			}
			float distance = (float) end.distanceTo(start);
			float length = Math.max(minimumLength, distance / Math.abs(beamScale.z));
			float width = (float) (star ? StarBeamSkill.WIDTH : LaserSkill.WIDTH) / Math.abs(beamScale.x);
			float enabled = distance > 0 ? 1f : 0f;
			if (beamInputs.isEmpty() || Float.compare(beamInputs.get("length").getFirst(), length) != 0
					|| Float.compare(beamInputs.get("width").getFirst(), width) != 0
					|| Float.compare(beamInputs.get("enabled").getFirst(), enabled) != 0) {
				beamInputs = Map.of("length", List.of(length), "width", List.of(width), "enabled", List.of(enabled));
			}
			animations().stageParameters(source, beamInputs);
			for (var id : emitter.instanceIds()) {
				var child = emitter.animations(id);
				var handle = child == null ? null : child.playback("laser_start");
				if (handle != null) {
					child.stageParameters(handle, beamInputs);
				}
			}
		}
	}
}
