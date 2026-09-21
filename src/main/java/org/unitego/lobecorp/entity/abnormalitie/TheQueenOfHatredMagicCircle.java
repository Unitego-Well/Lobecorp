package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后法术前摇期间使用的同步法阵实体。
public class TheQueenOfHatredMagicCircle extends Entity {
	/// 法阵覆盖半径。
	public static final float RADIUS = 5.0F;
	/// 防止技能异常结束后遗留法阵的最大寿命。
	private static final int MAXIMUM_LIFETIME_TICKS = TICKS_PER_SECOND;

	/// 创建由注册器或网络数据生成的法阵实体。
	/// @param type 法阵实体类型
	/// @param level 所在世界
	public TheQueenOfHatredMagicCircle(EntityType<? extends TheQueenOfHatredMagicCircle> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && tickCount >= MAXIMUM_LIFETIME_TICKS) {
			discard();
		}
	}
}
