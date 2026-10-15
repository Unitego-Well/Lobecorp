package org.unitego.lobecorp.client.animation.player;

import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy.Channel;

import java.util.Map;
import java.util.Set;

/// 玩家姿态与贡献层的独立回归入口，不启动游戏；需要测试源集的 Minecraft 与模组依赖。
public class LcPlayerRotationRegression {
	// 模型姿态和四元数均使用 float；容许三角函数及往返分解的误差，单位为弧度。
	private static final double ANGLE_TOLERANCE = 1.0E-4;
	private static final String BONE = "right_arm";
	private static final String SUCCESS = "LcPlayerRotationRegression：玩家旋转混合回归通过";

	@SuppressWarnings("UnnecessaryModifier")
	public static void main(String[] args) {
		check(args.length == 0, "玩家回归不接受启动参数");
		directCrossHalfTurn();
		directAndOuterTurns();
		movingVanillaBase();
		normalPreservesTurns();
		transitionPhases();
		masksAndAdditiveWeight();
		freezeIsPure();
		freshHistoryResetsPath();
		System.out.println(SUCCESS);
	}

	private static void directCrossHalfTurn() {
		var base = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE).withWeight(0.5);
		var before = LcPlayerPoseContribution.of(pose(179.9), policy).evaluate(base, base);
		var after = LcPlayerPoseContribution.of(pose(180.1), policy, before.displayed()).evaluate(base, base);
		assertRotation("Direct 跨180度之前", pose(89.95), before.pose());
		assertRotation("Direct 跨180度之后", pose(90.05), after.pose());
		check(angle(before.pose(), after.pose()) < Math.toRadians(0.2), "Direct 半权重不换向");
	}

	private static void directAndOuterTurns() {
		var base = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE).withWeight(0.5);
		LcPlayerPoseContribution previous = null;
		var last = base;
		for (int degrees = 0; degrees <= 720; degrees += 2) {
			var direct = LcPlayerPoseContribution.of(pose(degrees), policy, previous);
			var next = LcPlayerPoseContribution.blend(LcPlayerPoseContribution.identity(), direct, 0.5,
					LcTransitionMode.OVERLAP, LcRotationTransitionMode.SHORTEST_PATH, previous).evaluate(base, base);
			assertRotation("Direct 与外层分别延续两圈：" + degrees, pose(degrees * 0.25), next.pose());
			check(angle(last, next.pose()) < Math.toRadians(0.6), "两级混合逐帧连续：" + degrees);
			previous = next.displayed();
			last = next.pose();
		}
	}

	private static void movingVanillaBase() {
		var reference = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE);
		var direct = LcPlayerPoseContribution.of(pose(90), policy).evaluate(reference, reference).displayed();
		var stopped = LcPlayerPoseContribution.blend(direct, LcPlayerPoseContribution.identity(), 0.5,
				LcTransitionMode.OVERLAP, LcRotationTransitionMode.SHORTEST_PATH);
		var before = stopped.evaluate(reference, reference);
		assertRotation("淡出开始的动态基底", pose(45), before.pose());
		var current = LcPlayerPoseContribution.blend(before.origin(), LcPlayerPoseContribution.identity(), 0.5,
						LcTransitionMode.OVERLAP, LcRotationTransitionMode.SHORTEST_PATH, before.displayed())
				.evaluate(pose(60), reference);
		assertRotation("淡出仍按比例保留原版基底", pose(75), current.pose());

		var partial = policy.withWeight(0.5);
		var first = LcPlayerPoseContribution.of(pose(90), partial).evaluate(reference, reference);
		var next = LcPlayerPoseContribution.of(pose(90), partial, first.displayed()).evaluate(pose(60), reference);
		assertRotation("Direct 部分权重保留原版基底", pose(75), next.pose());
	}

	private static void normalPreservesTurns() {
		var reference = pose(0);
		var targetPart = new PartPose(0, 0, 0, radians(1080), radians(-1440), radians(1800), 1, 1, 1);
		var target = new LcPlayerModelPose(Map.of(BONE, targetPart));
		var policy = policy(LcRotationTransitionMode.NORMAL, LcControllerBlendType.OVERRIDE).withWeight(0.5);
		var direct = LcPlayerPoseContribution.of(target, policy).evaluate(reference, reference);
		assertEuler("NORMAL Direct 保留原始圈数", direct.pose(), 540, -720, 900);
		var outer = LcPlayerPoseContribution.blend(LcPlayerPoseContribution.identity(), direct.displayed(), 0.5,
				LcTransitionMode.OVERLAP, LcRotationTransitionMode.NORMAL).evaluate(reference, reference);
		assertEuler("NORMAL 外层保留原始圈数", outer.pose(), 270, -360, 450);
	}

	private static void transitionPhases() {
		var base = pose(20);
		var reference = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE);
		var origin = LcPlayerPoseContribution.of(pose(90), policy);
		var target = LcPlayerPoseContribution.of(pose(-30), policy);
		assertRotation("OVERLAP 中点", pose(30), transition(origin, target, 0.5, LcTransitionMode.OVERLAP, base, reference));
		assertRotation("SEQUENTIAL 前半段", pose(55), transition(origin, target, 0.25, LcTransitionMode.SEQUENTIAL, base, reference));
		assertRotation("SEQUENTIAL 中点回到底层", base, transition(origin, target, 0.5, LcTransitionMode.SEQUENTIAL, base, reference));
		assertRotation("SEQUENTIAL 后半段", pose(-5), transition(origin, target, 0.75, LcTransitionMode.SEQUENTIAL, base, reference));
		for (var mode : LcTransitionMode.values()) {
			assertRotation(mode + " 起点", pose(90), transition(origin, target, 0, mode, base, reference));
			assertRotation(mode + " 终点", pose(-30), transition(origin, target, 1, mode, base, reference));
		}

		var first = LcPlayerPoseContribution.blend(origin, target, 0.49, LcTransitionMode.SEQUENTIAL,
				LcRotationTransitionMode.SHORTEST_PATH).evaluate(base, reference);
		var second = LcPlayerPoseContribution.blend(first.origin(), target, 0.51, LcTransitionMode.SEQUENTIAL,
				LcRotationTransitionMode.SHORTEST_PATH, first.displayed()).evaluate(base, reference);
		assertRotation("SEQUENTIAL 切换相位后重建分支", pose(19), second.pose());
	}

	private static LcPlayerModelPose transition(LcPlayerPoseContribution origin, LcPlayerPoseContribution target,
	                                            double progress, LcTransitionMode mode,
	                                            LcPlayerModelPose base, LcPlayerModelPose reference) {
		return LcPlayerPoseContribution.blend(origin, target, progress, mode, LcRotationTransitionMode.SHORTEST_PATH)
				.apply(base, reference);
	}

	private static void masksAndAdditiveWeight() {
		var reference = pose(0);
		var basePart = new PartPose(10, 0, 0, 0, radians(60), 0, 2, 1, 1);
		var base = new LcPlayerModelPose(Map.of(BONE, basePart, "head", basePart));
		var targetPart = new PartPose(4, 0, 0, 0, radians(30), 0, 3, 1, 1);
		var target = new LcPlayerModelPose(Map.of(BONE, targetPart, "head", targetPart));
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.ADDITIVE).withWeight(0.5);
		var result = LcPlayerPoseContribution.of(target, policy).apply(base, reference);
		assertRotation("ADDITIVE 旋转权重", pose(75), result);
		check(Math.abs(result.parts().get(BONE).x() - 12) < ANGLE_TOLERANCE, "ADDITIVE 平移权重");
		check(Math.abs(result.parts().get(BONE).xScale() - 4) < ANGLE_TOLERANCE, "ADDITIVE 缩放权重");

		var masked = policy.withBones(Set.of(BONE)).withChannels(Set.of(Channel.ROTATION));
		result = LcPlayerPoseContribution.of(target, masked).apply(base, reference);
		assertRotation("遮罩内旋转权重", pose(75), result);
		check(result.parts().get(BONE).x() == basePart.x(), "关闭平移通道后保留底层");
		check(result.parts().get(BONE).xScale() == basePart.xScale(), "关闭缩放通道后保留底层");
		check(result.parts().get("head").equals(basePart), "遮罩外部位保留底层");
	}

	private static void freezeIsPure() {
		var base = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE);
		var previous = LcPlayerPoseContribution.blend(LcPlayerPoseContribution.identity(),
				LcPlayerPoseContribution.of(pose(179.9), policy), 0.5, LcTransitionMode.OVERLAP,
				LcRotationTransitionMode.SHORTEST_PATH).evaluate(base, base);
		var current = LcPlayerPoseContribution.blend(LcPlayerPoseContribution.identity(),
				LcPlayerPoseContribution.of(pose(180.1), policy, previous.displayed()), 0.5, LcTransitionMode.OVERLAP,
				LcRotationTransitionMode.SHORTEST_PATH, previous.displayed());
		var before = current.evaluate(base, base);
		var frozen = current.freeze(base, base);
		current.freeze(pose(-170), base);
		current.freeze(pose(170), base);
		var repeated = current.evaluate(base, base);
		assertRotation("freeze 不修改旧贡献", before.pose(), repeated.pose());
		check(before.pose().equals(repeated.pose()), "同输入重复求值输出字段一致");
		assertRotation("冻结结果保留当前输出", before.pose(), frozen.apply(base, base));
		assertRotation("冻结贡献仍保留底层比例", pose(120.05), frozen.apply(pose(60), base));

		var continued = LcPlayerPoseContribution.blend(before.origin(),
						LcPlayerPoseContribution.of(pose(180.3), policy, before.displayed()), 0.5,
						LcTransitionMode.OVERLAP, LcRotationTransitionMode.SHORTEST_PATH, before.displayed())
				.evaluate(base, base);
		assertRotation("冻结后继续原分支", pose(90.15), continued.pose());
	}

	private static void freshHistoryResetsPath() {
		var base = pose(0);
		var policy = policy(LcRotationTransitionMode.SHORTEST_PATH, LcControllerBlendType.OVERRIDE).withWeight(0.5);
		var previous = LcPlayerPoseContribution.of(pose(179.9), policy).evaluate(base, base);
		var continued = LcPlayerPoseContribution.of(pose(180.1), policy, previous.displayed()).apply(base, base);
		var restarted = LcPlayerPoseContribution.of(pose(180.1), policy, null).apply(base, base);
		assertRotation("同播放延续分支", pose(90.05), continued);
		assertRotation("新播放清空分支", pose(-89.95), restarted);
	}

	private static LcPlayerAnimationPolicy policy(LcRotationTransitionMode rotation, LcControllerBlendType blend) {
		return LcPlayerAnimationPolicy.fullOverride().withTransitions(new LcAnimationTransitionSettings(20, 20,
				LcTransitionMode.OVERLAP, LcTransitionMode.OVERLAP, rotation, rotation,
				LcTransitionMode.OVERLAP, rotation, blend));
	}

	private static LcPlayerModelPose pose(double degrees) {
		return new LcPlayerModelPose(Map.of(BONE, new PartPose(0, 0, 0, 0, radians(degrees), 0, 1, 1, 1)));
	}

	private static float radians(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static Quaternionf rotation(LcPlayerModelPose pose) {
		var part = pose.parts().get(BONE);
		return new Quaternionf().rotationZYX(part.zRot(), part.yRot(), part.xRot());
	}

	private static double angle(LcPlayerModelPose expected, LcPlayerModelPose actual) {
		var delta = rotation(expected).normalize().conjugate().mul(rotation(actual).normalize()).normalize();
		double length = Math.sqrt((double) delta.x * delta.x + (double) delta.y * delta.y + (double) delta.z * delta.z);
		return 2 * Math.atan2(length, Math.abs(delta.w));
	}

	private static void assertEuler(String name, LcPlayerModelPose actual, double x, double y, double z) {
		var part = actual.parts().get(BONE);
		check(Math.abs(part.xRot() - Math.toRadians(x)) < ANGLE_TOLERANCE
				&& Math.abs(part.yRot() - Math.toRadians(y)) < ANGLE_TOLERANCE
				&& Math.abs(part.zRot() - Math.toRadians(z)) < ANGLE_TOLERANCE, name);
	}

	private static void assertRotation(String name, LcPlayerModelPose expected, LcPlayerModelPose actual) {
		double error = angle(expected, actual);
		check(Double.isFinite(error) && error < ANGLE_TOLERANCE, name + "，弧度误差=" + error);
	}

	private static void check(boolean condition, String name) {
		if (!condition) {
			throw new AssertionError(name);
		}
	}
}

