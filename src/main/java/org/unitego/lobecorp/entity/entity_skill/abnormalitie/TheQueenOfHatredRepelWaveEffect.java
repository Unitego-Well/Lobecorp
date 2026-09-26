package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.effect.EntitySkillEffectDebugInfo;
import org.unitego.lobecorp.entity.entity_skill.effect.EntitySkillEffect;
import org.unitego.lobecorp.hitbox.RingCylinderSize;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.util.TypedDataKey;

/// 憎恶皇后退散技能释放的脱手环形冲击波。
public class TheQueenOfHatredRepelWaveEffect extends EntitySkillEffect {
	private static final int WAVE_DURATION_TICKS = 20;
	private static final double INITIAL_WAVE_RADIUS = 1.0;
	private static final double MAXIMUM_WAVE_RADIUS = 10.0;
	private static final double WAVE_HEIGHT = MAXIMUM_WAVE_RADIUS * 2.0;
	private static final double WAVE_RING_THICKNESS = 2.0;
	private static final double WAVE_RADIUS_PER_TICK =
			(MAXIMUM_WAVE_RADIUS - INITIAL_WAVE_RADIUS) / (WAVE_DURATION_TICKS - 1);
	private static final TypedDataKey<TheQueenOfHatredRepelWaveEffect> WAVE_EFFECT = TypedDataKey.create();
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new RingCylinderSize(INITIAL_WAVE_RADIUS, WAVE_HEIGHT, WAVE_RING_THICKNESS),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred queen)
						|| !(context.target() instanceof LivingEntity target)) {
					return false;
				}
				TheQueenOfHatredRepelWaveEffect effect = context.instance().getData(WAVE_EFFECT);
				if (effect == null || !queen.isHatedTarget(target) || !effect.intersectsCurrentWave(target)) {
					return false;
				}
				effect.applyHit(queen, context.level(), target);
				return true;
			}
	);
	private final HitboxInstance hitbox;
	private double radius = INITIAL_WAVE_RADIUS;

	public TheQueenOfHatredRepelWaveEffect(TheQueenOfHatred queen, ServerLevel level,
			EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super(level, queen.position(), WAVE_DURATION_TICKS, queen, false, null, false);
		hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, position(), WAVE_DURATION_TICKS);
		hitbox.setSource(queen);
		hitbox.setEntitySkillEffectDebugInfo(new EntitySkillEffectDebugInfo(getClass().getSimpleName(),
				queen.getDisplayName().getString(), runtime.skill().id().toString(), WAVE_DURATION_TICKS));
		hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
		hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
		hitbox.setData(WAVE_EFFECT, this);
	}

	@Override
	protected void tickEffect() {
		radius = Math.min(INITIAL_WAVE_RADIUS + (ageTicks() - 1) * WAVE_RADIUS_PER_TICK,
				MAXIMUM_WAVE_RADIUS);
		hitbox.setPosition(position());
		hitbox.setSize(new RingCylinderSize(radius, WAVE_HEIGHT, WAVE_RING_THICKNESS));
		hitbox.activate();
	}

	@Override
	protected void onRemoved() {
		HitboxManager.remove(level(), hitbox.id());
	}

	private boolean intersectsCurrentWave(LivingEntity target) {
		double distance = target.position().subtract(position()).horizontalDistance();
		double targetRadius = target.getBbWidth() * 0.5;
		double previousRadius = ageTicks() == 1 ? 0.0 : radius - WAVE_RADIUS_PER_TICK;
		return distance + targetRadius >= previousRadius && distance - targetRadius <= radius;
	}

	private void applyHit(TheQueenOfHatred queen, ServerLevel level, LivingEntity target) {
		Vec3 offset = target.position().subtract(position());
		double distance = offset.horizontalDistance();
		double attenuation = Math.max(0.0, 1.0 - distance / MAXIMUM_WAVE_RADIUS);
		float damage = (float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* 0.5 * attenuation);
		if (damage > 0.0F) {
			target.hurtServer(level, queen.damageSources().mobAttack(queen), damage);
		}
		Vec3 horizontalDirection = new Vec3(offset.x, 0.0, offset.z).normalize();
		Vec3 currentMovement = target.getDeltaMovement();
		target.setDeltaMovement(currentMovement.add(
			horizontalDirection.x * 5.0 * attenuation,
			2.0 * attenuation,
			horizontalDirection.z * 5.0 * attenuation));
		target.hurtMarked = true;
	}
}
