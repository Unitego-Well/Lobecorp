package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.util.TypedDataKey;

/// 二阶段冲刺复用瞬步的受限移动，每个实际移动 tick 留下一枚中型星星。
public class DashSkill extends BlinkSkill {
	/// 冲刺与瞬步共用最大水平距离，单位为格。
	public static final double MAXIMUM_DISTANCE = BlinkSkill.MAXIMUM_DISTANCE;
	/// 每枚路径星星独立停留的时间，单位为游戏刻。
	private static final int TRAIL_STAR_LIFETIME_TICKS = 40;
	/// 星星最早生成的游戏时间，确保晚于前摇结束的游戏刻。
	private static final TypedDataKey<Long> TRAIL_START_GAME_TIME = TypedDataKey.create();
	/// 前摇结束到开始生成星星的间隔，单位为游戏刻。
	private static final int TRAIL_START_DELAY_TICKS = 1;

	public DashSkill(Properties properties) {
		super(properties);
	}

	@Override
	public double maximumDistance() {
		return MAXIMUM_DISTANCE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return queen.isPhaseTwo() && super.canUse(queen, runtime);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(queen, runtime);
		runtime.setData(TRAIL_START_GAME_TIME, queen.level().getGameTime() + TRAIL_START_DELAY_TICKS);
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Vec3 before = queen.position();
		super.onTick(queen, runtime);
		if (!(queen.level() instanceof ServerLevel level) || before.subtract(queen.position()).horizontalDistanceSqr() == 0.0) return;
		Long trailStart = runtime.getData(TRAIL_START_GAME_TIME);
		if (trailStart == null || level.getGameTime() < trailStart) return;
		Vec3 origin = before.lerp(queen.position(), 0.5).add(0.0, queen.getBbHeight() / 2.0, 0.0);
		MagicStarProjectile star = new MagicStarProjectile(AbnormalitieEntityTypes.MAGIC_NORMAL_STAR.get(), level);
		star.configure(queen, MagicStarProjectile.StarSize.MEDIUM, MagicStarProjectile.StarVariant.A, origin, Vec3.ZERO);
		star.setStationary(TRAIL_STAR_LIFETIME_TICKS);
		level.addFreshEntity(star);
	}
}
