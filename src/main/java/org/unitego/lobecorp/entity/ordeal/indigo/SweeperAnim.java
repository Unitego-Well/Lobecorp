package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

/// 清道夫动画定义。
/// 基础动画由客户端移动状态选择，动作动画由同步到客户端的技能生命周期回调触发。
public enum SweeperAnim {
	IDLE(RawAnimation.begin().thenLoop("idle")),
	MOVE(RawAnimation.begin().thenLoop("move")),
	RUN(RawAnimation.begin().thenLoop("run")),
	ATTACK1(RawAnimation.begin().then("attack", LoopType.PLAY_ONCE)),
	ATTACK2(RawAnimation.begin().then("attack2", LoopType.PLAY_ONCE)),
	ATTACK3(RawAnimation.begin().then("attack3", LoopType.PLAY_ONCE)),
	LEAP(RawAnimation.begin().then("leap", LoopType.HOLD_ON_LAST_FRAME)),
	LEAP2(RawAnimation.begin().then("leap2", LoopType.PLAY_ONCE)),
	CLEAR1(RawAnimation.begin().then("clear", LoopType.HOLD_ON_LAST_FRAME)),
	CLEAR2(RawAnimation.begin().thenLoop("clear2")),
	CLEAR3(RawAnimation.begin().then("clear3", LoopType.PLAY_ONCE));

	private final RawAnimation animation;

	SweeperAnim(RawAnimation animation) {
		this.animation = animation;
	}

	public RawAnimation getAnimation() {
		return animation;
	}
}
