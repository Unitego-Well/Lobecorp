package org.unitego.lobecorp.conductor.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/// 指挥家技能目标规则、范围和预览几何的契约。
public interface ConductorTargeting {
	TargetKind targetKind();

	default boolean directional() {
		return false;
	}

	default Vec3 facingPosition(Mob mob) {
		return mob.position().add(Vec3.directionFromRotation(0.0F, mob.getYRot()));
	}

	default PreviewShape previewShape() {
		return switch (targetKind()) {
			case SELF -> PreviewShape.NONE;
			case ENTITY -> PreviewShape.ENTITY;
			case POSITION, EITHER -> PreviewShape.POSITION;
		};
	}

	default RangeMetric rangeMetric() {
		return RangeMetric.NONE;
	}

	default double maximumRange(Mob mob) {
		return 0.0D;
	}

	default boolean hasRange(Mob mob) {
		return rangeMetric() != RangeMetric.NONE && maximumRange(mob) > 0.0D;
	}

	default double previewRadius(Mob mob) {
		return hasRange(mob) ? maximumRange(mob) : 0.0D;
	}

	default PreviewGeometry previewGeometry(Mob mob, Vec3 requestedPosition, Vec3 effectivePosition) {
		return PreviewGeometry.EMPTY;
	}

	default boolean isAvailable(Mob mob) {
		return true;
	}

	default boolean canTarget(Mob mob, LivingEntity target) {
		return target != mob && target.isAlive();
	}

	@SuppressWarnings("unused")
	default boolean canTargetPosition(Mob mob, Vec3 position) {
		return true;
	}

	@SuppressWarnings("unused")
	default Vec3 targetPosition(Mob mob, LivingEntity target, Vec3 position) {
		return target == null ? position : target.getBoundingBox().getCenter();
	}

	default boolean isWithinRange(Mob mob, Vec3 position) {
		if (!hasRange(mob)) {
			return true;
		}
		double range = maximumRange(mob);
		return rangeMetric() == RangeMetric.HORIZONTAL
				? mob.position().subtract(position).horizontalDistanceSqr() <= range * range
				: mob.position().distanceToSqr(position) <= range * range;
	}

	default Vec3 correctPosition(Mob mob, Vec3 position) {
		if (!hasRange(mob) || isWithinRange(mob, position)) {
			return position;
		}
		Vec3 offset = position.subtract(mob.position());
		if (rangeMetric() == RangeMetric.HORIZONTAL) {
			Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z).normalize().scale(maximumRange(mob));
			return new Vec3(mob.getX() + horizontal.x, position.y, mob.getZ() + horizontal.z);
		}
		return mob.position().add(offset.normalize().scale(maximumRange(mob)));
	}

	enum TargetKind {
		SELF,
		ENTITY,
		POSITION,
		EITHER
	}

	enum PreviewShape {
		NONE,
		AREA,
		ENTITY,
		POSITION
	}

	enum RangeMetric {
		NONE,
		HORIZONTAL,
		DISTANCE
	}

	record PreviewCircle(Vec3 center, double radius) {
	}

	record PreviewBeam(Vec3 start, Vec3 end, double halfWidth) {
	}

	record PreviewSector(Vec3 origin, Vec3 forward, double radius, double angle) {
	}

	record PreviewGeometry(List<PreviewCircle> circles, List<PreviewBeam> beams, List<PreviewSector> sectors) {
		public static final PreviewGeometry EMPTY = new PreviewGeometry(List.of(), List.of());

		public PreviewGeometry(List<PreviewCircle> circles, List<PreviewBeam> beams) {
			this(circles, beams, List.of());
		}

		public PreviewGeometry {
			circles = List.copyOf(circles);
			beams = List.copyOf(beams);
			sectors = List.copyOf(sectors);
		}
	}
}
