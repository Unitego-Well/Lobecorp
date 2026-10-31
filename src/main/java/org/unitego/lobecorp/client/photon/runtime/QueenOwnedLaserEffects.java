package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredBeamAnimation.Phase;
import net.minecraft.util.Mth;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 自有激光的生产适配；保留旧路径，默认不启用尚未运行验收的组合版本。
@SuppressWarnings("resource") // 世界和命名运行时均为借用，组合统一释放子租约。
public class QueenOwnedLaserEffects {
	private static final boolean ENABLED = Boolean.getBoolean("lobecorp.photon.ownedLaser");
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	private static final Map<UUID, Long> LEGACY = new HashMap<>();
	private static final List<PhotonOwnedLaser> TAILS = new ArrayList<>();

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
				LEGACY.put(snapshot.instanceId(), snapshot.playbackRevision());
				return false;
			}

			LEGACY.put(snapshot.instanceId(), snapshot.playbackRevision());
			playing = new Playing(owner, snapshot, new PhotonOwnedLaser(key, owner.level()));
			LEGACY.remove(snapshot.instanceId());
			PLAYING.put(snapshot.instanceId(), playing);
		}

		try {
			playing.update(snapshot);
			return true;
		} catch (RuntimeException failure) {
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

	private static class Playing {
		private final TheQueenOfHatred owner;
		private final long revision;
		private final PhotonOwnedLaser effect;
		private Phase phase;
		private Vec3 frozenOrigin;
		private Vec3 frozenEnd;

		private Playing(TheQueenOfHatred owner, LcAnimationSnapshot snapshot, PhotonOwnedLaser effect) {
			this.owner = owner;
			revision = snapshot.playbackRevision();
			this.effect = effect;
		}

		private void update(LcAnimationSnapshot snapshot) {
			var phaseValue = parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE);
			var next = phaseValue == Phase.OUTPUT.ordinal() ? Phase.OUTPUT : phaseValue == Phase.RECOVERY.ordinal() ? Phase.RECOVERY : Phase.CHARGE;
			var offset = StarBeamSkill.origin(owner).subtract(owner.getEyePosition())
					.yRot(owner.yBodyRot * Mth.DEG_TO_RAD).xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
					.xRot(owner.getViewXRot(1) * Mth.DEG_TO_RAD).yRot(-owner.yBodyRot * Mth.DEG_TO_RAD);
			var staff = owner.getEyePosition(1).add(offset);
			var origin = frozenOrigin == null ? staff.add(owner.getViewVector(1)) : frozenOrigin;
			var target = staff.add(owner.getViewVector(1).scale(LaserSkill.RANGE));
			var end = frozenEnd != null ? frozenEnd : next == Phase.CHARGE ? target
					: owner.level().clip(new ClipContext(staff, target, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, owner)).getLocation();
			if (frozenEnd == null && end.subtract(origin).dot(owner.getViewVector(1)) <= 0) {
				end = origin;
			}
			var direction = end.subtract(origin);
			if (direction.lengthSqr() == 0) {
				direction = owner.getLookAngle();
			}

			var rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.normalize().toVector3f());
			var position = origin.toVector3f().sub(rotation.transform(effect.space().circleOffset()));
			var beamStart = new Vec3(rotation.transform(effect.space().beamOffset()).add(position));
			var beamEnd = beamStart.add(direction.normalize().scale(Math.max(0, end.subtract(beamStart).dot(direction.normalize()))));
			effect.anchor(position, rotation);
			if (next != phase) {
				phase = next;
				transition(snapshot, origin, end);
			}

			if (phase == Phase.OUTPUT) {
				updateBeam(beamEnd.distanceTo(beamStart));
			}
		}

		private void transition(LcAnimationSnapshot snapshot, Vec3 origin, Vec3 end) {
			if (phase == Phase.CHARGE) {
				for (var name : List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle")) {
					effect.activate(name, "charge_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.WINDUP_TICKS), Map.of());
				}
			} else if (phase == Phase.OUTPUT) {
				effect.stop("accumulate_particle", true);
				effect.activate("light_ball", "laser_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.ENTRY_TICKS), Map.of());
				effect.activate("laser", "laser_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.ENTRY_TICKS), Map.of());
			} else {
				frozenOrigin = origin;
				frozenEnd = end;
				for (var name : PhotonOwnedLaser.COMPONENTS) {
					if (effect.component(name) != null) {
						var animations = ((PhotonNamedAccess) effect.component(name).runtime()).lobecorp$namedRuntime();
						if (animations != null) {
							animations.play(name.equals("laser") ? "laser_end" : "charge_end",
									parameter(snapshot, TheQueenOfHatredBeamAnimation.RECOVERY_TICKS), true, Map.of());
						}
					}
				}
			}
		}

		private void updateBeam(double distance) {
			var part = effect.component("laser");
			if (part == null) {
				return;
			}

			var animations = ((PhotonNamedAccess) part.runtime()).lobecorp$namedRuntime();
			var handle = animations == null ? null : animations.playback("laser_start");
			var inputs = Map.of("length", List.of(Math.max(effect.space().minimumLength(), (float) distance / Math.abs(effect.space().beamScale().z))),
					"width", List.of((float) LaserSkill.WIDTH / Math.abs(effect.space().beamScale().x)),
					"enabled", List.of(distance > 0 ? 1f : 0f));
			if (handle != null) {
				animations.stageParameters(handle, inputs);
			}

			if (part.runtime().findObject("laser") instanceof PhotonChildSpawner spawner) {
				for (var id : spawner.instanceIds()) {
					var child = spawner.animations(id);
					var childHandle = child == null ? null : child.playback("laser_start");
					if (childHandle != null) {
						child.stageParameters(childHandle, inputs);
					}
				}
			}
		}

		private static double parameter(LcAnimationSnapshot snapshot, String key) {
			return snapshot.parameters().getOrDefault(key, 0.0);
		}
	}
}
