"""直接执行生产 GLSL 标量函数；不启动游戏，也不生成 CPU 生产寻路镜像。
入口：python tools/horde_path_contract_test.py
适配器使用双精度算术，验证公式不变量，不代替硬件 GLSL/float 验收。
"""
import math
from pathlib import Path
import re
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHADERS = ROOT / "src/main/resources/assets/lobecorp/shaders/include"


def clamp(value, lower, upper):
    return min(upper, max(lower, value))


def smoothstep(lower, upper, value):
    t = clamp((value - lower) / (upper - lower), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


class SampleXZ:
    def __init__(self, x, y=None):
        self.x = x
        self.y = x if y is None else y

    def __sub__(self, other):
        return SampleXZ(self.x - other.x, self.y - other.y)

    def __getitem__(self, index):
        return (self.x, self.y)[index]

    def __setitem__(self, index, value):
        if index == 0:
            self.x = value
        else:
            self.y = value


def scalar_functions(source, java=False):
    # 仅支持生产标量函数的声明、赋值和 return；不复制路径公式。
    scope = dict(min=min, max=max, abs=abs, clamp=clamp, smoothstep=smoothstep,
                 sin=math.sin, cos=math.cos, sqrt=math.sqrt, sign=lambda x: (x > 0) - (x < 0),
                 fract=lambda x: x - math.floor(x), mix=lambda a, b, t: a + (b - a) * t,
                 math=math, vec2=SampleXZ, OriginTime=SimpleNamespace(w=0.0),
                 CameraSpeed=SimpleNamespace(w=0.75))
    for name, value in re.findall(r"const float (\w+)\s*=\s*([0-9.]+);", source):
        scope[name] = float(value)
    kind = "double" if java else "(?:float|vec2)"
    pattern = rf"\b{kind}\s+(\w+)\(([^()]*)\)\s*\{{([^{{}}]*)\}}"
    for name, parameters, body in re.findall(pattern, source):
        if not (name.startswith("hordeAvoidance") or name in {
                "motionRandom", "motionDrift", "motionSway", "motionDriftSpeedBound",
                "motionDriftVelocity", "motionSwayVelocity", "motionProjectXZ", "motionDifference",
                "motionVelocityXZ", "random", "fract", "mix", "sway", "drift"}):
            continue
        parameters = re.sub(r"\b(?:float|double|vec2)\s+", "", parameters)
        body = re.sub(r"//[^\n]*", "", body)
        body = re.sub(r"\b(?:float|double|vec2)\s+", "", body)
        body = body.replace("Math.PI", "math.pi").replace("Math.sin", "math.sin").replace("Math.floor", "math.floor")
        parameters = re.sub(r"\bfrom\b", "start_value", parameters)
        body = re.sub(r"\bfrom\b", "start_value", body)
        statements = [" ".join(item.split()) for item in body.split(";") if item.strip()]
        code = f"def {name}({parameters}):\n" + "\n".join("    " + item for item in statements)
        exec(compile(code, "<production scalar " + name + ">", "exec"), scope)
    return scope


def actual_target_function(scope):
    # 只转译生产控制函数的循环和分支；输入为固定平面及实际割圆表，不复制控制公式。
    source = (SHADERS.parent / "core/horde_avoidance.fsh").read_text(encoding="utf-8")
    start = source.index("avoidanceTarget(")
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    parameters = re.sub(r"\b(?:float|int|vec2|vec3)\s+", "", source[start + len("avoidanceTarget("):opening]
                        .strip().removesuffix(")").strip())
    body = re.sub(r"//[^\n]*", "", source[opening + 1:end - 1])
    tokens, current, parentheses = [], [], 0
    for char in body:
        parentheses += (char == "(") - (char == ")")
        if parentheses == 0 and char in "{};":
            token = " ".join("".join(current).split())
            if token:
                tokens.append(token)
            if char in "{}":
                tokens.append(char)
            current = []
        else:
            current.append(char)
    lines, indent = [f"def avoidanceTarget({parameters}):"], 1
    for token in tokens:
        if token == "{":
            indent += 1
            continue
        if token == "}":
            indent -= 1
            continue
        token = token.replace("&&", " and ").replace("||", " or ")
        token = re.sub(r"!(?!=)", "not ", token)
        token = re.sub(r"\btrue\b", "True", token)
        token = re.sub(r"\bfalse\b", "False", token)
        token = token.replace("encounter ? 1.0 : 0.0", "(1.0 if encounter else 0.0)")
        token = token.replace("locked != 0.0 ? SplitInfo.z * fade : 0.0",
                              "(SplitInfo.z * fade if locked != 0.0 else 0.0)")
        loop = re.fullmatch(r"for\s*\(int\s+(\w+)\s*=\s*([^;]+);\s*\1\s*<\s*([^;]+);\s*\1\+\+\)", token)
        if loop:
            token = f"for {loop[1]} in range({loop[2]}, {loop[3]}):"
        elif token.startswith("if ("):
            token = "if " + token[4:-1] + ":"
        elif token == "else":
            token = "else:"
        else:
            token = re.sub(r"^(?:float|int|bool|vec2|vec3)\s+", "", token)
        lines.append("    " * indent + token)
    exec(compile("\n".join(lines), "<actual GLSL avoidanceTarget>", "exec"), scope)
    return scope["avoidanceTarget"]


class HordePathContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.control = scalar_functions((SHADERS / "horde_split.glsl").read_text(encoding="utf-8"))
        cls.motion = scalar_functions((SHADERS / "horde_motion.glsl").read_text(encoding="utf-8"))
        cls.cpu = scalar_functions((ROOT / "src/main/java/org/unitego/lobecorp/client/horde/HordeTide.java")
                                   .read_text(encoding="utf-8"), java=True)

    def integrate(self, position, velocity, target, speed, dt):
        limit = 0.7 * speed
        velocity = clamp(velocity, -limit, limit)
        next_velocity = self.control["hordeAvoidanceVelocity"](velocity, target - position, limit, dt)
        delta = self.control["hordeAvoidanceStep"](next_velocity, target - position, limit, dt)
        next_position = clamp(position + delta, -16.0, 16.0)
        return next_position, (next_position - position) / dt if dt > 0 else velocity

    def test_total_side_velocity_and_step_budget_for_dt_and_moving_targets(self):
        for speed in (0.05, 0.25, 0.75, 2.0):
            for dt in (0.0, 1e-4, 0.05, 0.2, 1.0, 5.0, 100.0):
                position, velocity = 0.0, 0.0
                for step in range(300):
                    target = 12.0 if step % 31 < 15 else -12.0
                    new_position, velocity = self.integrate(position, velocity, target, speed, dt)
                    self.assertLessEqual(abs(new_position - position), 0.7 * speed * dt + 1e-12)
                    self.assertLessEqual(abs(velocity), 0.7 * speed + 1e-9)
                    position = new_position

    def test_dynamic_circle_crossing_midline_keeps_locked_side_and_continuity(self):
        for preferred in (-1.0, 1.0):
            locked = self.control["hordeAvoidanceSide"](0.0, preferred, 1.0, 0.0)
            position, velocity = preferred * 4.086, 0.0
            for step in range(1001):
                across = -0.1 + step * 0.0002
                clearance = self.control["hordeAvoidanceClearance"](3.636, 0.0, 1.0)
                signed_target = self.control["hordeAvoidanceGoal"](across, clearance, locked, 0.0, 1.0)
                target = locked * signed_target
                next_position, velocity = self.integrate(position, velocity, target, 0.75, 0.05)
                self.assertLessEqual(abs(next_position - position), 0.525 * 0.05 + 1e-12)
                self.assertEqual(self.control["hordeAvoidanceSide"](locked, -preferred, 1.0, 0.0), preferred)
                self.assertEqual(locked, preferred)
                position = next_position

    def test_sway_compensation_limits_total_motion_and_empty_circle_exit(self):
        speed, dt, time = 0.75, 0.1, 0.0
        seed = 349.0
        offset, velocity, locked = 8.0, 0.0, 1.0
        for step in range(1600):
            now = time + dt
            previous_total = offset + self.motion["motionSway"](time, seed)
            natural = self.motion["motionSway"](now, seed)
            if locked != 0.0:
                total, velocity = self.integrate(previous_total, velocity, natural, speed, dt)
                self.assertLessEqual(abs(total - previous_total), 0.7 * speed * dt + 1e-12)
                arrived = float(total == natural and abs(velocity - self.motion["motionSwayVelocity"](now, seed))
                                <= 0.7 * speed * dt / 4.0)
                locked = self.control["hordeAvoidanceSide"](locked, -1.0, 0.0, arrived)
            else:
                total = natural
            offset = total - natural
            self.assertAlmostEqual(offset + natural, total, delta=1e-12)
            time = now
        self.assertEqual(locked, 0.0)
        self.assertAlmostEqual(offset, 0.0, delta=1e-12)

    def test_required_lead_allows_acceleration_before_static_circle_core(self):
        position, velocity, station = 0.0, 0.0, -60.0
        speed, dt, clearance = 0.75, 0.1, 4.086
        locked = 0.0
        for step in range(1000):
            required = max(abs(clearance - position), clearance) + 1.5
            lead = self.control["hordeAvoidanceLead"](required, speed, 0.102, 9.2)
            lead += 9.2 if locked else 0.0
            encounter = float(-clearance - 9.2 <= -station <= clearance + lead)
            target = clearance if encounter else 0.0
            position, velocity = self.integrate(position, velocity, target, speed, dt)
            locked = self.control["hordeAvoidanceSide"](locked, 1.0, encounter, float(position == 0.0))
            if abs(station) <= 3.636:
                self.assertGreaterEqual(math.hypot(station, position), 3.636 - 1e-9)
            station += speed * dt

    def test_radius_fade_changes_target_without_teleporting_state(self):
        position, velocity, locked = 5.0, 0.0, 1.0
        for step in range(401):
            radius = max(0.0, 3.636 * (1.0 - step / 400.0))
            fade = smoothstep(0.0, 0.5, radius)
            clearance = self.control["hordeAvoidanceClearance"](radius, 0.0, fade)
            target = self.control["hordeAvoidanceGoal"](0.0, clearance, locked, 0.0, fade)
            new_position, velocity = self.integrate(position, velocity, target, 0.75, 0.1)
            self.assertLessEqual(abs(new_position - position), 0.0525 + 1e-12)
            position = new_position
        self.assertEqual(self.control["hordeAvoidanceClearance"](0.0, 1.0, 0.0), 0.0)

    def target_kernel(self, circles):
        scope = dict(self.motion)
        scope.update(self.control)
        data = [value for along, across, radius in circles
                for value in (-across, along, radius, -10.0, 10.0)]
        scope.update(vec2=SampleXZ, vec3=lambda x, y, z: SimpleNamespace(x=x, y=y, z=z),
                     HordeSplit=data, scalar=lambda texture, index: texture[index],
                     routeForward=lambda _: SampleXZ(0.0, 1.0),
                     location=lambda station, lateral: SimpleNamespace(y=0.0, xz=SampleXZ(-lateral, station)),
                     dot=lambda a, b: a.x * b.x + a.y * b.y,
                     step=lambda edge, value: float(value >= edge),
                     SplitInfo=SimpleNamespace(x=len(circles), z=9.2),
                     SplitShape=SimpleNamespace(z=16.0, w=0.75),
                     LightRoute=SimpleNamespace(w=2.0))
        return actual_target_function(scope)

    def test_capacity_precedes_current_position_intersection(self):
        target = self.target_kernel(((0.0, 5.0, 3.636), (0.0, 14.0, 3.636)))
        near = 14.0 - (3.636 + 0.45 + 0.75)
        control = target(-10.0, 0.0, 0.0, 1.0, 0.0, 17.0, 1.0)
        self.assertLessEqual(control.x, near + 1e-12)
        self.assertEqual(control.z, 1.0)
        initial = target(-10.0, 0.0, 0.0, 1.0, 0.0, 17.0, 0.0)
        self.assertEqual(initial.z, -1.0)
        self.assertEqual(initial.x, 0.0)
        position, velocity = 0.0, 0.0
        for frame in range(1000):
            station = -60.0 + frame * 0.075
            control = target(station, 0.0, position, 1.0, 0.0, 17.0, 1.0)
            position, velocity = self.integrate(position, velocity, control.x, 0.75, 0.1)
            self.assertLessEqual(position, near + 1e-10)
        single = self.target_kernel(((0.0, 14.0, 3.636),))
        self.assertEqual(single(-10.0, 0.0, 0.0, 1.0, 0.0, 17.0, 0.0).x, 0.0)

    def test_initial_side_uses_seed_only_for_similar_costs(self):
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        for preferred in (-1.0, 1.0):
            self.assertEqual(target(-10.0, 0.0, 0.0, preferred, 0.0, 17.0, 0.0).z, preferred)
        shorter = self.target_kernel(((0.0, 5.0, 3.636),))
        self.assertEqual(shorter(-10.0, 0.0, 0.0, 1.0, 0.0, 17.0, 0.0).z, -1.0)

    def test_locked_side_does_not_rechoose_when_shorter_side_changes(self):
        for across in (-0.1, 0.0, 0.1, 5.0):
            target = self.target_kernel(((0.0, across, 3.636),))
            self.assertEqual(target(-10.0, 0.0, 0.0, -1.0, 0.0, 17.0, 1.0).z, 1.0)

    def heading_sample(self, sampler, offset, velocity):
        scope = self.motion
        names = ("motionXZ", "motionDrift", "motionDriftVelocity", "motionSway")
        previous = {name: scope.get(name) for name in names}
        try:
            scope["motionXZ"] = sampler
            scope["motionDrift"] = lambda *_: 0.0
            scope["motionDriftVelocity"] = lambda *_: 0.0
            scope["motionSway"] = lambda *_: 0.0
            return scope["motionVelocityXZ"](0.0, 0.0, 17.0, offset, velocity)
        finally:
            for name, value in previous.items():
                if value is None:
                    scope.pop(name, None)
                else:
                    scope[name] = value

    def test_heading_straight_route_includes_spatial_terrain_flow_gradient(self):
        project = self.motion["motionProjectXZ"]

        def sample(station, lateral, total):
            flow = station * 0.2
            return SampleXZ(project(0.0, -1.0, lateral, flow, total),
                            project(station, 0.0, lateral, flow, total))

        velocity = self.heading_sample(sample, 0.0, 0.0)
        self.assertAlmostEqual(velocity.x, -0.15, delta=1e-10)
        self.assertAlmostEqual(velocity.y, 0.75, delta=1e-10)

    def test_heading_curve_includes_rotating_side_basis_with_nonzero_offset(self):
        project = self.motion["motionProjectXZ"]

        def sample(station, lateral, total):
            angle = station / 10.0
            return SampleXZ(project(10.0 * math.cos(angle), -math.cos(angle), lateral, 0.0, total),
                            project(10.0 * math.sin(angle), -math.sin(angle), lateral, 0.0, total))

        velocity = self.heading_sample(sample, 4.0, 0.3)
        self.assertAlmostEqual(velocity.x, -0.3, delta=2e-5)
        self.assertAlmostEqual(velocity.y, 0.75 * (1.0 - 4.0 / 10.0), delta=2e-5)
        old_heading = math.atan2(-0.3, 0.75)
        self.assertGreater(abs(math.atan2(velocity.x, velocity.y) - old_heading), 0.1)

    def test_sway_is_three_times_frequency_without_amplitude_change(self):
        for seed in (0.0, 17.0, 31.0, 349.0):
            a, b, c = (self.cpu["random"](seed, salt) for salt in (1.0, 2.0, 3.0))
            for step in range(200):
                time = step * 1.37
                old_at_triple_time = (
                    0.9 * math.sin(time * 3.0 * (0.012 + 0.018 * c) + a * math.tau)
                    + 0.6 * math.sin(time * 3.0 * (0.006 + 0.010 * a) + b * math.tau)
                ) * (0.5 + 0.5 * b)
                cpu = self.cpu["sway"](time, seed)
                self.assertAlmostEqual(cpu, old_at_triple_time, delta=1e-12)
                self.assertAlmostEqual(cpu, self.motion["motionSway"](time, seed), delta=1e-7)
                self.assertLessEqual(abs(cpu), 1.5)

if __name__ == "__main__":
    unittest.main()
