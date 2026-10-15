package org.unitego.lobecorp.util.animation;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil.Sample;
import org.unitego.lobecorp.util.animation.LcRotationTransitionUtil.State;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 仅依赖 JDK 与 JOML 的旋转数学回归入口，不启动或加载 Minecraft。
public class LcRotationTransitionRegression {
	// 四元数使用 float；比较实际方向时允许归一化与三角函数产生的累计误差，单位为弧度。
	private static final double ANGLE_TOLERANCE = 1.0E-4;
	private static final String SUCCESS = "LcRotationTransitionRegression：数学回归通过";
	private static final String UNEXPECTED_STATE_VALUE = "出现未检查的状态字段类型";

	@SuppressWarnings("UnnecessaryModifier")
	public static void main(String[] args) throws ReflectiveOperationException {
		check(args.length == 0, "数学回归不接受启动参数");
		crossHalfTurn();
		multipleTurns();
		dynamicBase();
		endpointsAndSigns();
		cornerCorrection();
		boundedStateAndPurity();
		sampleCopies();
		nearbyEulerBranches();
		normalEulerMultiTurnExample();
		System.out.println(SUCCESS);
	}

	private static void crossHalfTurn() {
		var before = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationY(179.9), 0.5, null);
		var after = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationY(180.1), 0.5, before.state());
		assertRotation("跨180度前的半角", rotationY(89.95), before.rotation());
		assertRotation("跨180度后的半角", rotationY(90.05), after.rotation());
		check(angle(before.rotation(), after.rotation()) < Math.toRadians(0.2), "跨180度连续");
	}

	private static void multipleTurns() {
		State state = null;
		var previous = new Quaternionf();
		for (int degrees = 0; degrees <= 720; degrees += 2) {
			var target = rotationY(degrees);
			if (degrees % 4 == 0) {
				target.mul(-1);
			}

			var sample = LcRotationTransitionUtil.interpolate(new Quaternionf(), target, 0.5, state);
			assertRotation("连续两圈的每个半角：" + degrees, rotationY(degrees * 0.5), sample.rotation());
			check(angle(previous, sample.rotation()) <= Math.toRadians(1.01), "连续两圈的小步变化：" + degrees);
			previous = sample.rotation();
			state = sample.state();
		}
	}

	private static void dynamicBase() {
		var previous = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationY(90), 0.5, null);
		var current = LcRotationTransitionUtil.interpolate(rotationY(60), rotationY(90), 0.5, previous.state());
		assertRotation("动态底层仍参与部分权重", rotationY(75), current.rotation());
	}

	private static void endpointsAndSigns() {
		var source = new Quaternionf().rotationZYX(radians(-35), radians(20), radians(55));
		var target = new Quaternionf().rotationZYX(radians(80), radians(-50), radians(110));
		var previous = cornerSample().state();
		assertRotation("权重0返回来源", source,
				LcRotationTransitionUtil.interpolate(source, target, 0, previous).rotation());
		assertRotation("权重1返回目标", target,
				LcRotationTransitionUtil.interpolate(source, target, 1, previous).rotation());
		assertRotation("负权重钳制到来源", source,
				LcRotationTransitionUtil.interpolate(source, target, -1, previous).rotation());
		assertRotation("超出权重钳制到目标", target,
				LcRotationTransitionUtil.interpolate(source, target, 2, previous).rotation());

		var positive = LcRotationTransitionUtil.interpolate(source, target, 0.35, null);
		var negative = LcRotationTransitionUtil.interpolate(new Quaternionf(source).mul(-1),
				new Quaternionf(target).mul(-1), 0.35, null);
		assertRotation("初次采样不依赖四元数符号", positive.rotation(), negative.rotation());
		var targetNegated = LcRotationTransitionUtil.interpolate(source, new Quaternionf(target).mul(-1), 0.35, null);
		assertRotation("仅目标变号仍保持初次方向", positive.rotation(), targetNegated.rotation());
		var sourceNegated = LcRotationTransitionUtil.interpolate(new Quaternionf(source).mul(-1), target, 0.35, null);
		assertRotation("仅来源变号仍保持初次方向", positive.rotation(), sourceNegated.rotation());
		var continued = LcRotationTransitionUtil.interpolate(source, rotationY(180.1), 0.35, positive.state());
		var signChanged = LcRotationTransitionUtil.interpolate(new Quaternionf(source).mul(-1),
				rotationY(180.1).mul(-1), 0.35, positive.state());
		assertRotation("延续采样不依赖四元数符号", continued.rotation(), signChanged.rotation());
	}

	private static Sample cornerSample() {
		State state = null;
		var previous = new Quaternionf();
		for (int degrees = 0; degrees <= 360; degrees += 2) {
			var sample = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationY(degrees), 0.5, state);
			previous = sample.rotation();
			state = sample.state();
		}

		var corner = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), 0.5, state);
		check(angle(previous, corner.rotation()) < Math.toRadians(0.2), "整圈从Y轴转入X轴连续");
		return corner;
	}

	private static void cornerCorrection() {
		var corner = cornerSample();
		var ordinary = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), 0.5, null);
		check(angle(corner.rotation(), ordinary.rotation()) > Math.toRadians(170), "多轴整圈确实保留连续修正");
		correctionDecays(corner, 0);
		correctionDecays(corner, 1);
	}

	private static void correctionDecays(Sample corner, double endpoint) {
		var state = corner.state();
		double previousDistance = Math.PI + ANGLE_TOLERANCE;
		var previous = corner.rotation();
		for (int step = 0; step <= 10; step++) {
			double weight = 0.5 + (endpoint - 0.5) * step / 10;
			var current = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), weight, state);
			var ordinary = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), weight, null);
			double distance = angle(current.rotation(), ordinary.rotation());
			check(distance <= previousDistance + ANGLE_TOLERANCE, "修正向权重端点递减：" + endpoint);
			check(angle(previous, current.rotation()) <= Math.toRadians(18.1), "修正衰减无突跳：" + endpoint);
			previousDistance = distance;
			previous = current.rotation();
			state = current.state();
		}

		assertRotation("修正衰减至权重端点：" + endpoint,
				endpoint == 0 ? new Quaternionf() : rotationX(0.2), previous);
		double nearEndpoint = endpoint == 0 ? 1.0E-6 : 1 - 1.0E-6;
		var near = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), nearEndpoint, corner.state());
		var ordinary = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationX(0.2), nearEndpoint, null);
		assertRotation("修正在端点邻域趋于零：" + endpoint, ordinary.rotation(), near.rotation());
	}

	private static void boundedStateAndPurity() throws ReflectiveOperationException {
		int count = 0;
		for (var field : State.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			count++;
			var type = field.getType();
			check(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), "状态字段只读");
			check(type != State.class && !type.isArray() && !Collection.class.isAssignableFrom(type)
					&& !Map.class.isAssignableFrom(type) && !Iterable.class.isAssignableFrom(type), "状态不持有历史链");
			check(type == Quaternionf.class || type == Vector3d.class || type == double.class, "状态仅保存固定数学值");
		}
		check(count == 5, "状态字段数量固定");

		var previous = cornerSample().state();
		var stateBefore = stateValues(previous);
		var source = new Quaternionf().rotationZYX(radians(15), radians(-25), radians(45)).mul(2);
		var target = new Quaternionf().rotationZYX(radians(-35), radians(75), radians(120)).mul(3);
		var sourceBefore = new Quaternionf(source);
		var targetBefore = new Quaternionf(target);
		var first = LcRotationTransitionUtil.interpolate(source, target, 0.4, previous);
		var repeated = LcRotationTransitionUtil.interpolate(source, target, 0.4, previous);
		check(source.equals(sourceBefore), "来源四元数保持原值");
		check(target.equals(targetBefore), "目标四元数保持原值");
		check(stateBefore.equals(stateValues(previous)), "上一份状态保持原值");
		assertRotation("同输入重复计算方向一致", first.rotation(), repeated.rotation());
		check(stateValues(first.state()).equals(stateValues(repeated.state())), "同输入重复计算状态一致");
		check(first.state() != previous && first.state() != repeated.state(), "新状态独立分配");

		var firstBefore = stateValues(first.state());
		LcRotationTransitionUtil.interpolate(rotationX(170), rotationY(-170), 0.7, first.state());
		check(firstBefore.equals(stateValues(first.state())), "后续计算不修改直接前态");
		check(stateBefore.equals(stateValues(previous)), "后续计算不修改旧状态");
	}

	private static List<Object> stateValues(State state) throws ReflectiveOperationException {
		var values = new ArrayList<>();
		for (var field : State.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			field.setAccessible(true);
			values.add(switch (field.get(state)) {
				case Quaternionf rotation -> new Quaternionf(rotation);
				case Vector3d vector -> new Vector3d(vector);
				case Double value -> value;
				default -> throw new AssertionError(UNEXPECTED_STATE_VALUE);
			});
		}
		return List.copyOf(values);
	}

	private static void sampleCopies() {
		var sample = LcRotationTransitionUtil.interpolate(new Quaternionf(), rotationY(90), 0.5, null);
		var returned = sample.rotation();
		returned.rotationX(radians(125));
		assertRotation("rotation访问器返回副本", rotationY(45), sample.rotation());

		var input = rotationY(65);
		var copied = new Sample(input, sample.state());
		input.identity();
		assertRotation("Sample构造复制输入", rotationY(65), copied.rotation());
	}

	private static void nearbyEulerBranches() {
		var before = new Quaternionf().rotationZYX(radians(-35), radians(89.9), radians(20));
		var after = new Quaternionf().rotationZYX(radians(-35), radians(90.1), radians(20));
		var previous = LcRotationTransitionUtil.nearbyEuler(before, null);
		var previousCopy = new Vector3f(previous);
		var next = LcRotationTransitionUtil.nearbyEuler(after, previous);
		assertRotation("ZYX族切换前方向等价", before, fromEuler(previous));
		assertRotation("ZYX族切换后方向等价", after, fromEuler(next));
		check(previous.distance(next) < Math.toRadians(1), "ZYX族切换保持Euler连续");
		check(previous.equals(previousCopy), "临近Euler不修改上一角度");

		before = new Quaternionf().rotationZYX(radians(179.9), radians(10), radians(20));
		after = new Quaternionf().rotationZYX(radians(180.1), radians(10), radians(20));
		previous = LcRotationTransitionUtil.nearbyEuler(before, null);
		next = LcRotationTransitionUtil.nearbyEuler(after, previous);
		assertRotation("Euler绕回后方向等价", after, fromEuler(next));
		check(previous.distance(next) < Math.toRadians(0.3), "Euler跨正负180度连续");
	}

	/// 仅示例 NORMAL 调用方保留原始 Euler 多圈；此方法不调用 Minecraft 模型或 GeckoLib 桥接。
	private static void normalEulerMultiTurnExample() {
		var source = new Vector3d();
		var target = new Vector3d(Math.toRadians(1080), Math.toRadians(-1440), Math.toRadians(1800));
		var halfway = source.lerp(target, 0.5, new Vector3d());
		check(Math.abs(halfway.x - Math.toRadians(540)) < ANGLE_TOLERANCE, "Euler示例保留X轴圈数");
		check(Math.abs(halfway.y - Math.toRadians(-720)) < ANGLE_TOLERANCE, "Euler示例保留Y轴圈数");
		check(Math.abs(halfway.z - Math.toRadians(900)) < ANGLE_TOLERANCE, "Euler示例保留Z轴圈数");
	}

	private static Quaternionf fromEuler(Vector3f angles) {
		return new Quaternionf().rotationZYX(angles.z, angles.y, angles.x);
	}

	private static Quaternionf rotationX(double degrees) {
		return new Quaternionf().rotationX(radians(degrees));
	}

	private static Quaternionf rotationY(double degrees) {
		return new Quaternionf().rotationY(radians(degrees));
	}

	private static float radians(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static double angle(Quaternionf expected, Quaternionf actual) {
		var delta = new Quaternionf(expected).normalize().conjugate().mul(new Quaternionf(actual).normalize()).normalize();
		double length = Math.sqrt((double) delta.x * delta.x + (double) delta.y * delta.y + (double) delta.z * delta.z);
		return 2 * Math.atan2(length, Math.abs(delta.w));
	}

	private static void assertRotation(String name, Quaternionf expected, Quaternionf actual) {
		double error = angle(expected, actual);
		check(Double.isFinite(error) && error < ANGLE_TOLERANCE, name + "，弧度误差=" + error);
	}

	private static void check(boolean condition, String name) {
		if (!condition) {
			throw new AssertionError(name);
		}
	}
}
