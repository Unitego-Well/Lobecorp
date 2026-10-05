package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.entity.skill.effect.EntitySkillEffect;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarfallSkill;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

import java.util.List;

/// 固定区域内独立持续生成星星；来源死亡、卸载或离开当前世界时清除。
public class QueenStarfallEffect extends EntitySkillEffect {
	/// 星星生成高度上下界，单位为格。
	private static final double MINIMUM_HEIGHT = 12.0;
	private static final double MAXIMUM_HEIGHT = 18.0;
	/// 允许最高出生点的星星到达地面的飞行寿命，单位为 tick。
	private static final int STAR_LIFETIME_TICKS = 40;
	/// 星陨每枚星星相对女皇攻击属性的伤害倍率。
	private static final double DAMAGE_MULTIPLIER = 1.0;
	/// 星陨爆裂星星的爆炸半径，单位为格。
	private static final double BURST_RADIUS = 2.5;
	private final TheQueenOfHatred queen;
	private final int intervalTicks;

	public QueenStarfallEffect(ServerLevel level, Vec3 position, TheQueenOfHatred queen, boolean phaseTwo) {
		super(level, position, StarfallSkill.EFFECT_TICKS, queen, true, null, false);
		this.queen = queen;
		this.intervalTicks = phaseTwo ? StarfallSkill.SPAWN_INTERVAL_TICKS / 2 : StarfallSkill.SPAWN_INTERVAL_TICKS;
	}

	@Override
	protected void tickEffect() {
		if ((ageTicks() - 1) % intervalTicks != 0)
			return;
		var random = queen.getRandom();
		double angle = random.nextDouble() * Math.TAU;
		double radius = Math.sqrt(random.nextDouble()) * StarfallSkill.RADIUS;
		Vec3 destination = position().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
		double height = MINIMUM_HEIGHT + random.nextDouble() * (MAXIMUM_HEIGHT - MINIMUM_HEIGHT);
		List<EntityType<MagicStarProjectile>> types = List.of(AbnormalitieEntityTypes.MAGIC_NORMAL_STAR.get(),
				AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get(), AbnormalitieEntityTypes.MAGIC_BURST_STAR.get());
		MagicStarProjectile star = new MagicStarProjectile(types.get(random.nextInt(types.size())), level());
		star.configure(queen, MagicStarProjectile.StarSize.MEDIUM, MagicStarProjectile.StarVariant.A,
				destination.add(0.0, height, 0.0), new Vec3(0.0, -1.0, 0.0));
		star.setFlightParameters(STAR_LIFETIME_TICKS, DAMAGE_MULTIPLIER, BURST_RADIUS);
		level().addFreshEntity(star);
	}
}

