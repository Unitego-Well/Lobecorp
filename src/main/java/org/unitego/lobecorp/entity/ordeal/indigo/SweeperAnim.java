package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;

/// 清道夫动画定义。
/// 基础动画由客户端移动状态选择，动作动画由服务端通过 GeoEntity#triggerAnim 触发。
public enum SweeperAnim {
	/// 待机
	IDLE("idle", RawAnimation.begin().thenLoop("idle")),
	/// 移动
	MOVE("move", RawAnimation.begin().thenLoop("move")),
	/// 奔跑
	RUN("run", RawAnimation.begin().thenLoop("run")),
	/// 攻击第 1 段
	ATTACK1("attack", RawAnimation.begin().then("attack", LoopType.PLAY_ONCE)),
	/// 攻击第 2 段
	ATTACK2("attack2", RawAnimation.begin().then("attack2", LoopType.PLAY_ONCE)),
	/// 攻击第 3 段
	ATTACK3("attack3", RawAnimation.begin().then("attack3", LoopType.PLAY_ONCE)),
	/// 飞扑起跳
	LEAP("leap", RawAnimation.begin().then("leap", LoopType.HOLD_ON_LAST_FRAME)),
	/// 飞扑落地
	LEAP2("leap2", RawAnimation.begin().then("leap2", LoopType.PLAY_ONCE)),
	/// 清理开始
	CLEAR1("clear", RawAnimation.begin().then("clear", LoopType.HOLD_ON_LAST_FRAME)),
	/// 清理处理中
	CLEAR2("clear2", RawAnimation.begin().thenLoop("clear2")),
	/// 清理结束
	CLEAR3("clear3", RawAnimation.begin().then("clear3", LoopType.PLAY_ONCE));

	private final String id;
	private final RawAnimation animation;

	SweeperAnim(String id, RawAnimation animation) {
		this.id = id;
		this.animation = animation;
	}

	public RawAnimation getAnimation() {
		return animation;
	}

	public String getId() {
		return id;
	}
}
