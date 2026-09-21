package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

/// 清道夫动画定义。
/// 基础动画由客户端移动状态选择，动作动画由同步到客户端的技能生命周期回调触发。
public enum SweeperAnim {
	/// 待机
	IDLE(RawAnimation.begin().thenLoop("idle")),
	/// 移动
	MOVE(RawAnimation.begin().thenLoop("move")),
	/// 奔跑
	RUN(RawAnimation.begin().thenLoop("run")),
	/// 攻击第 1 段
	ATTACK1(RawAnimation.begin().then("attack", LoopType.PLAY_ONCE)),
	/// 攻击第 2 段
	ATTACK2(RawAnimation.begin().then("attack2", LoopType.PLAY_ONCE)),
	/// 攻击第 3 段
	ATTACK3(RawAnimation.begin().then("attack3", LoopType.PLAY_ONCE)),
	/// 飞扑起跳
	LEAP(RawAnimation.begin().then("leap", LoopType.HOLD_ON_LAST_FRAME)),
	/// 飞扑落地
	LEAP2(RawAnimation.begin().then("leap2", LoopType.PLAY_ONCE)),
	/// 清理开始
	CLEAR1(RawAnimation.begin().then("clear", LoopType.HOLD_ON_LAST_FRAME)),
	/// 清理处理中
	CLEAR2(RawAnimation.begin().thenLoop("clear2")),
	/// 清理结束
	CLEAR3(RawAnimation.begin().then("clear3", LoopType.PLAY_ONCE));

	private final RawAnimation animation;

	SweeperAnim(RawAnimation animation) {
		this.animation = animation;
	}

	public RawAnimation getAnimation() {
		return animation;
	}
}
