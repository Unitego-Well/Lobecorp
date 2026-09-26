package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

/// 憎恶皇后的待机与姿态动画定义。
public enum TheQueenOfHatredAnim {
	IDLE_2(RawAnimation.begin().thenLoop("idle2")),
	STAND(RawAnimation.begin().thenLoop("站姿")),
	EYES(RawAnimation.begin().thenPlay("eyes")),
	IDLE(RawAnimation.begin().thenLoop("idle")),
	WALK(RawAnimation.begin().thenLoop("walk")),
	HOVER(RawAnimation.begin().thenLoop("hover")),
	FLY(RawAnimation.begin().thenLoop("fly")),
	SIT_SEQUENCE(RawAnimation.begin().then("sitdown", LoopType.HOLD_ON_LAST_FRAME).thenLoop("sit")),
	SIT_SEQUENCE_2(RawAnimation.begin().then("sitdown2", LoopType.HOLD_ON_LAST_FRAME).thenLoop("sit2")),
	SIT_DOWN(RawAnimation.begin().then("sitdown", LoopType.HOLD_ON_LAST_FRAME)),
	SIT(RawAnimation.begin().thenLoop("sit")),
	SIT_DOWN_2(RawAnimation.begin().then("sitdown2", LoopType.HOLD_ON_LAST_FRAME)),
	SIT_2(RawAnimation.begin().thenLoop("sit2")),
	SWEEP(RawAnimation.begin().then("sweep", LoopType.HOLD_ON_LAST_FRAME)),
	SPIN(RawAnimation.begin().then("spin", LoopType.HOLD_ON_LAST_FRAME)),
	KISS(RawAnimation.begin().then("kiss", LoopType.HOLD_ON_LAST_FRAME)),
	DISPEL(RawAnimation.begin().then("dispel", LoopType.HOLD_ON_LAST_FRAME)),
	BLINK(RawAnimation.begin().then("blink", LoopType.HOLD_ON_LAST_FRAME)),
	TELEPORT(RawAnimation.begin().then("teleport", LoopType.HOLD_ON_LAST_FRAME)),
	AIM(RawAnimation.begin().then("aim", LoopType.HOLD_ON_LAST_FRAME)),
	AIM_2(RawAnimation.begin().then("aim2", LoopType.HOLD_ON_LAST_FRAME)),
	SPELL(RawAnimation.begin().then("spell", LoopType.HOLD_ON_LAST_FRAME));

	private final RawAnimation animation;

	TheQueenOfHatredAnim(RawAnimation animation) {
		this.animation = animation;
	}

	/// @return GeckoLib 使用的动画序列
	public RawAnimation getAnimation() {
		return animation;
	}
}
