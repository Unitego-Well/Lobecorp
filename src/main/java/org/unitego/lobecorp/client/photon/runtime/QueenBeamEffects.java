package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
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
	/// 历史法阵起点相对身体中心前移一格，服务端命中原点保持原值。
	private static final double VISUAL_FORWARD = 1;

	public static void tick() {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			clear();
			return;
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
					beam = new Beam(owner, snapshot);
					PLAYING.put(id, beam);
				}
				beam.update(snapshot);
				if (!beam.effect.isAlive()) {
					finish(id);
					RETIRED.put(id, snapshot.playbackRevision());
				}
			} catch (RuntimeException invalid) {
				finish(id);
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
	}

	private static void finish(UUID id) {
		var beam = PLAYING.remove(id);
		if (beam != null) {
			beam.effect.close();
		}
	}

	public static void clear() {
		PLAYING.values().forEach(value -> value.effect.close());
		PLAYING.clear();
		RETIRED.clear();
	}

	private static class Beam {
		private final TheQueenOfHatred owner;
		private final PhotonGeoEffect effect;
		private final PhotonChildSpawner emitter;
		private final boolean star;
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

		private Beam(TheQueenOfHatred owner, LcAnimationSnapshot snapshot) {
			this.owner = owner;
			revision = snapshot.playbackRevision();
			star = TheQueenOfHatredAnimationDefinitions.isStarBeam(snapshot.definitionId());
			var id = LcAnimationDefinitions.resolve(snapshot.definitionId()).effectId();
			var definition = id == null ? null : FXHelper.getFX(id);
			if (definition == null) {
				throw new IllegalArgumentException("特效定义缺失或受保护：" + id);
			}
			effect = new PhotonGeoEffect(definition, owner.level());
			try {
				if (!(effect.runtime().findObject("laser") instanceof PhotonChildSpawner source)) {
					throw new IllegalArgumentException("光束生成器缺失。");
				}
				emitter = source;
				var child = emitter.config.fxLocation == null ? null : FXHelper.getFX(emitter.config.fxLocation);
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
				if (beamScale.x == 0 || beamScale.z == 0) {
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
				anchor();
				effect.start();
				var charge = animations().play("charge_start", parameter(snapshot, TheQueenOfHatredBeamAnimation.WINDUP_TICKS), true, Map.of());
				if (parameter(snapshot, TheQueenOfHatredBeamAnimation.PHASE) != Phase.CHARGE.ordinal()) {
					animations().seek(charge, charge.authoredDuration());
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

		private PhotonNamedRuntime animations() {
			return Objects.requireNonNull(((PhotonNamedAccess) effect.runtime()).lobecorp$namedRuntime());
		}

		private static double parameter(LcAnimationSnapshot snapshot, String key) {
			return snapshot.parameters().getOrDefault(key, 0.0);
		}

		private Vec3 sourceStart() {
			return frozenStart != null ? frozenStart : star ? StarBeamSkill.origin(owner) : LaserSkill.origin(owner).add(owner.getLookAngle().scale(VISUAL_FORWARD));
		}

		private Vec3 sourceEnd(Vec3 origin) {
			if (frozenEnd != null) {
				return frozenEnd;
			}
			var aim = origin.add(owner.getLookAngle());
			var result = star ? StarBeamSkill.endpoint(owner, aim) : LaserSkill.endpoint(owner, aim);
			return result.subtract(origin).dot(owner.getLookAngle()) > 0 ? result : origin;
		}

		private void anchor() {
			var origin = sourceStart();
			end = sourceEnd(origin);
			var direction = end.subtract(origin);
			if (direction.lengthSqr() == 0) {
				direction = owner.getLookAngle();
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
				}
			}
			anchor();
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
			var signature = source.definition().parameters().stream().filter(value -> value.name().equals("length")).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("光束长度参数缺失。"));
			float distance = (float) end.distanceTo(start);
			var values = Map.of("length", List.of(Math.max(signature.minimum(), distance / Math.abs(beamScale.z))),
					"width", List.of((float) (star ? StarBeamSkill.WIDTH : LaserSkill.WIDTH) / Math.abs(beamScale.x)), "enabled", List.of(distance > 0 ? 1f : 0f));
			animations().parameters(source, values);
			for (var id : emitter.instanceIds()) {
				var child = emitter.animations(id);
				var handle = child == null ? null : child.playback("laser_start");
				if (handle != null) {
					child.parameters(handle, values);
				}
			}
		}
	}
}
