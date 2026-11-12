package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation.Phase;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;

import java.util.*;

/// 自有激光的生产适配；保留旧路径，默认不启用尚未运行验收的组合版本。
@SuppressWarnings("resource") // 世界和命名运行时均为借用，组合统一释放子租约。
public class QueenOwnedLaserEffects {
	private static final boolean ENABLED = Boolean.getBoolean("lobecorp.photon.ownedLaser");
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	private static final Map<UUID, Long> LEGACY = new HashMap<>();
	private static final List<PhotonOwnedLaser> TAILS = new ArrayList<>();
	private static final boolean STATISTICS_ENABLED = Boolean.getBoolean("lobecorp.photon.bench.ownedStatistics")
			|| Set.of("lobecorp.photon-05-hybrid-queen25",
					"lobecorp.photon-05-hybrid-laser3", "lobecorp.photon-05-hybrid-blank25",
					"lobecorp.photon-06-migration-laser3")
			.contains(System.getProperty("modBench.scenarios", ""));
	private static final List<ComponentStatistics> COMPONENT_HISTORY = new ArrayList<>();
	private static long startedCount;
	private static long fallbackCount;
	private static long errorCount;
	private static int maximumConcurrent;
	private static long overlapCount;

	/// 客户端线程只读快照；计数跨世界清理保留，供采集者按起始快照作差。
	public static Statistics statistics() {
		var components = new ArrayList<>(COMPONENT_HISTORY);
		PLAYING.values().forEach(value -> components.add(new ComponentStatistics(value.ordinal, value.created, value.activeObserved)));
		return new Statistics(startedCount, fallbackCount, errorCount, PLAYING.size(), TAILS.size(),
				maximumConcurrent, overlapCount, List.copyOf(components));
	}

	private static void observeConcurrency() {
		if (!STATISTICS_ENABLED) {
			return;
		}
		int count = PLAYING.size() + (int) TAILS.stream().filter(effect -> !effect.finished()).count();
		maximumConcurrent = Math.max(maximumConcurrent, count);
		if (count > 1) {
			overlapCount++;
		}
	}

	public static boolean enabled() {
		return ENABLED;
	}

	public static boolean handle(TheQueenOfHatred owner, LcAnimationSnapshot snapshot) {
		if (!ENABLED || TheQueenOfHatredAnimationDefinitions.isStarBeam(snapshot.definitionId())) {
			return false;
		}

		if (Long.valueOf(snapshot.playbackRevision()).equals(LEGACY.get(snapshot.instanceId()))) {
			return false;
		}
		var playing = PLAYING.get(snapshot.instanceId());
		if (playing != null && (playing.revision != snapshot.playbackRevision() || !playing.effect.current())) {
			playing.effect.close();
			PLAYING.remove(snapshot.instanceId());
			playing = null;
		}

		if (playing == null) {
			var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
					PhotonOwnedEffectResources.SourceKind.EXPORTED_FX, PhotonOwnedEffectResources.resourceRevision());
			if (PhotonOwnedEffectResources.lookup(key).readiness() != PhotonOwnedEffectResources.Readiness.READY) {
				fallbackCount++;
				LEGACY.put(snapshot.instanceId(), snapshot.playbackRevision());
				return false;
			}

			LEGACY.put(snapshot.instanceId(), snapshot.playbackRevision());
			playing = new Playing(owner, snapshot, new PhotonOwnedLaser(key, owner.level()));
			LEGACY.remove(snapshot.instanceId());
			PLAYING.put(snapshot.instanceId(), playing);
			observeConcurrency();
		}

		try {
			playing.update(snapshot);
			for (var name : STATISTICS_ENABLED ? PhotonOwnedLaser.COMPONENTS : List.<String>of()) {
				if (playing.effect.component(name) != null) {
					playing.created.add(name);
					if (playing.effect.component(name).isAlive()) {
						playing.activeObserved.add(name);
					}
				}
			}
			return true;
		} catch (RuntimeException failure) {
			errorCount++;
			LEGACY.put(snapshot.instanceId(), snapshot.playbackRevision());
			playing.effect.close();
			PLAYING.remove(snapshot.instanceId());
			throw failure;
		}
	}

	public static void retain(Set<UUID> visible) {
		LEGACY.keySet().retainAll(visible);
		TAILS.removeIf(effect -> {
			if (!effect.finished()) {
				return false;
			}
			effect.close();
			return true;
		});
		PLAYING.entrySet().removeIf(entry -> {
			if (visible.contains(entry.getKey())) {
				return false;
			}

			if (STATISTICS_ENABLED) {
				COMPONENT_HISTORY.add(new ComponentStatistics(entry.getValue().ordinal, entry.getValue().created, entry.getValue().activeObserved));
			}
			entry.getValue().effect.retire();
			TAILS.add(entry.getValue().effect);
			return true;
		});
	}

	public static void clear() {
		PLAYING.values().forEach(value -> value.effect.close());
		PLAYING.clear();
		LEGACY.clear();
		TAILS.forEach(PhotonOwnedLaser::close);
		TAILS.clear();
	}

	public record ComponentStatistics(long ordinal, Set<String> created, Set<String> activeObserved) {
		public ComponentStatistics {
			created = Set.copyOf(created);
			activeObserved = Set.copyOf(activeObserved);
		}
	}

	public record Statistics(long started, long fallbacks, long errors, int playing, int tails,
	                         int maximumConcurrent, long overlaps, List<ComponentStatistics> components) {
	}

	private static class Playing {
		private final long ordinal = STATISTICS_ENABLED ? ++startedCount : 0;
		private final Set<String> created = STATISTICS_ENABLED ? new HashSet<>() : Set.of();
		private final Set<String> activeObserved = STATISTICS_ENABLED ? new HashSet<>() : Set.of();
		private final TheQueenOfHatred owner;
		private final long revision;
		private final PhotonOwnedLaser effect;
		private Phase phase;
		private Vec3 frozenOrigin;
		private Vec3 frozenEnd;
		private LcAnimationSnapshot latest;

		private Playing(TheQueenOfHatred owner, LcAnimationSnapshot snapshot, PhotonOwnedLaser effect) {
			this.owner = owner;
			revision = snapshot.playbackRevision();
			this.effect = effect;
			effect.frameAnchor(partial -> anchor(latest, partial));
		}

		private static double parameter(LcAnimationSnapshot snapshot, String key) {
			return snapshot.parameters().getOrDefault(key, 0.0);
		}

		private void update(LcAnimationSnapshot snapshot) {
			latest = snapshot;
			var value = parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE);
			var next = value == Phase.OUTPUT.ordinal() ? Phase.OUTPUT : value == Phase.RECOVERY.ordinal() ? Phase.RECOVERY : Phase.CHARGE;
			anchor(snapshot, 1);
			if (next != phase) {
				phase = next;
				double duration = parameter(snapshot, switch (phase) {
					case CHARGE -> TheQueenOfHatredBeamAnimation.WINDUP_TICKS;
					case OUTPUT -> TheQueenOfHatredBeamAnimation.ENTRY_TICKS;
					case RECOVERY -> TheQueenOfHatredBeamAnimation.RECOVERY_TICKS;
				});
				effect.phase(PhotonOwnedLaserComposition.Phase.valueOf(phase.name()), duration);
			}
			effect.advance(Math.max(0, snapshot.serverTick()
					- parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE_STARTED_TICK)));
		}

		private void anchor(LcAnimationSnapshot snapshot, float partial) {
			if (snapshot == null || !effect.current()) {
				return;
			}
			var phaseValue = parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE);
			boolean recovery = phaseValue == Phase.RECOVERY.ordinal();
			var offset = StarBeamSkill.origin(owner).subtract(owner.getEyePosition())
					.yRot(owner.yBodyRot * Mth.DEG_TO_RAD).xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
					.xRot(owner.getViewXRot(partial) * Mth.DEG_TO_RAD)
					.yRot(-Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD);
			var staff = owner.getEyePosition(partial).add(offset);
			var look = owner.getViewVector(partial);
			var origin = frozenOrigin == null ? staff.add(look) : frozenOrigin;
			var target = staff.add(look.scale(LaserSkill.RANGE));
			var end = frozenEnd != null ? frozenEnd : phaseValue == Phase.CHARGE.ordinal() ? target
					: owner.level().clip(new ClipContext(staff, target, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, owner)).getLocation();
			if (frozenEnd == null && end.subtract(origin).dot(look) <= 0) {
				end = origin;
			}
			if (recovery && frozenOrigin == null) {
				frozenOrigin = origin;
				frozenEnd = end;
			}
			var direction = end.subtract(origin);
			if (direction.lengthSqr() == 0) {
				direction = look;
			}
			direction = direction.normalize();
			var rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.toVector3f());
			var position = origin.toVector3f().sub(rotation.transform(effect.space().circleOffset()));
			var beamStart = new Vec3(rotation.transform(effect.space().beamOffset()).add(position));
			double distance = Math.max(0, end.subtract(beamStart).dot(direction));
			effect.anchor(position, rotation);
			var inputs = Map.of("length", List.of(Math.max(effect.space().minimumLength(),
							(float) distance / Math.abs(effect.space().beamScale().z))),
					"width", List.of((float) LaserSkill.WIDTH / Math.abs(effect.space().beamScale().x)),
					"enabled", List.of(distance > 0 ? 1f : 0f));
			effect.beam(beamStart.toVector3f(), rotation, inputs);
		}
	}
}
