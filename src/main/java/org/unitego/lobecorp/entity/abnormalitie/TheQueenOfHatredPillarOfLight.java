package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 光柱技能命中后持续显示的同步信标光柱实体。
public class TheQueenOfHatredPillarOfLight extends Entity {
	/// 光柱命中后的可见时间。
	private static final int LIFETIME_TICKS = 2 * TICKS_PER_SECOND;

	/// 创建由注册器或网络数据生成的光柱实体。
	/// @param type 光柱实体类型
	/// @param level 所在世界
	public TheQueenOfHatredPillarOfLight(EntityType<? extends TheQueenOfHatredPillarOfLight> type, Level level) {
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
		if (!level().isClientSide() && tickCount >= LIFETIME_TICKS) {
			discard();
		}
	}
}
