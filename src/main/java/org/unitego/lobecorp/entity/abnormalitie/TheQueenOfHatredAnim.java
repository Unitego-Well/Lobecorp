package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import org.unitego.lobecorp.animation.LcBoneMask;

/// 憎恶皇后的待机与姿态动画定义。
public enum TheQueenOfHatredAnim {
	/// 空闲悬浮动作。
	IDLE(RawAnimation.begin().thenLoop("idle")),
	/// 普通行走动作。
	WALK(RawAnimation.begin().thenLoop("walk")),
	/// 面向玩家的姿态。
	POSE(RawAnimation.begin().thenLoop("pose")),
	/// 面向玩家的姿态二。
	POSE2(RawAnimation.begin().thenLoop("pose2")),
	/// 面向玩家的姿态三。
	POSE3(RawAnimation.begin().thenLoop("pose3")),
	/// 面向玩家的循环动作。
	POSE4(RawAnimation.begin().thenLoop("pose4")),
	/// 退散动作。
	DISPEL(RawAnimation.begin().then("dispel", LoopType.HOLD_ON_LAST_FRAME));

	static final String LOCOMOTION_ANIMATION_LAYER = "locomotion";
	static final String IDLE_POSE_ANIMATION_LAYER = "idle_pose";
	static final String ACTION_ANIMATION_LAYER = "action";
	/// 动画状态切换时长，单位为游戏刻。
	static final int ANIMATION_TRANSITION_TICKS = 3;
	/// 行走动画速度判定阈值。
	/// 行走动画速度阈值，单位为动画速度。
	static final float WALK_ANIMATION_SPEED_THRESHOLD = 0.1F;
	/// 已进入行走动画后的退出速度阈值，单位为动画速度。
	static final float WALK_ANIMATION_EXIT_SPEED_THRESHOLD = 0.05F;
	static final LcBoneMask FULL_BODY_ANIMATION_MASK = LcBoneMask.builder().includeRoot("root").build();

	private final RawAnimation animation;

	TheQueenOfHatredAnim(RawAnimation animation) {
		this.animation = animation;
	}

	/// @return GeckoLib 使用的动画序列
	public RawAnimation getAnimation() {
		return animation;
	}
}
