package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

/// 憎恶皇后的基础与移动技能动画定义。
public enum TheQueenOfHatredAnim {
	/// 空闲悬浮动作。
	IDLE(RawAnimation.begin().thenLoop("idle")),
	/// 普通行走动作。
	WALK(RawAnimation.begin().thenLoop("walk")),
	/// 瞬步动作，实际冲刺结束后继续播放至原始动画完成。
	BLINK(RawAnimation.begin().then("blink", LoopType.HOLD_ON_LAST_FRAME)),
	/// 传送准备与完成动作。
	TELEPORT(RawAnimation.begin().then("teleport", LoopType.HOLD_ON_LAST_FRAME)),
	/// 法杖向前横扫动作。
	SWEEP(RawAnimation.begin().then("sweep", LoopType.HOLD_ON_LAST_FRAME)),
	/// 法杖砸地并击退周围敌人的退散动作。
	DISPEL(RawAnimation.begin().then("dispel", LoopType.HOLD_ON_LAST_FRAME)),
	/// 旋转法杖并连续发射追踪星星。
	SPIN(RawAnimation.begin().then("spin", LoopType.HOLD_ON_LAST_FRAME)),
	/// 持续瞄准并发射激光。
	AIM(RawAnimation.begin().then("aim", LoopType.HOLD_ON_LAST_FRAME)),
	/// 激光释放完成后的收势动作。
	AIM_RECOVERY(RawAnimation.begin().then("aim2", LoopType.HOLD_ON_LAST_FRAME)),
	/// 原地持续恢复生命。
	HEAL(RawAnimation.begin().then("heal", LoopType.HOLD_ON_LAST_FRAME)),
	/// 高举法杖的通用施法前摇动作。
	SPELL(RawAnimation.begin().then("spell", LoopType.HOLD_ON_LAST_FRAME)),
	/// 保持施法姿势的通用持续动作。
	SPELL_ACTIVE(RawAnimation.begin().thenLoop("spell2")),
	/// 收起法杖的通用施法后摇动作。
	SPELL_RECOVERY(RawAnimation.begin().then("spell3", LoopType.HOLD_ON_LAST_FRAME));

	private final RawAnimation animation;

	TheQueenOfHatredAnim(RawAnimation animation) {
		this.animation = animation;
	}

	/// @return GeckoLib 使用的动画序列
	public RawAnimation getAnimation() {
		return animation;
	}
}
