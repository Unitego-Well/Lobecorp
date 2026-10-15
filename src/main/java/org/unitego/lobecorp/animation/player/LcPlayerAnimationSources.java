package org.unitego.lobecorp.animation.player;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

/// 内置来源对应原版求值阶段；其他模组通过自己的标识显式登记来源。
public class LcPlayerAnimationSources {
	public static final Identifier VANILLA_IDLE = Lobecorp.id("vanilla_idle");
	public static final Identifier VANILLA_MOVEMENT = Lobecorp.id("vanilla_movement");
	public static final Identifier VANILLA_LOOK = Lobecorp.id("vanilla_look");
	public static final Identifier VANILLA_ATTACK = Lobecorp.id("vanilla_attack");
	public static final Identifier VANILLA_POSTURE = Lobecorp.id("vanilla_posture");
	public static final Identifier VANILLA_ITEM = Lobecorp.id("vanilla_item");
	public static final Identifier NEO_ARM_POSE = Lobecorp.id("neo_arm_pose");
}
