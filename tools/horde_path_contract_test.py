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
            position, velocity = 1.0 * preferred, 0.0
            for step in range(101):
                across = -0.1 + step * 0.002
                target = self.target_kernel(((0.0, across, 3.636),))
                control = target(-5.0, 0.0, position, -preferred, 0.0, 31.0, locked)
                next_position, velocity = self.integrate(position, velocity, control.x, 0.75, 0.05)
                self.assertLessEqual(abs(next_position - position), 0.525 * 0.05 + 1e-12)
                self.assertEqual(control.z, preferred)
                self.assertEqual(control.y, 1.0)
                locked = self.control["hordeAvoidanceSide"](locked, control.z, control.y, 0.0)
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

    def test_pressure_is_gradual_and_has_no_full_strength_platform(self):
        pressure = self.control["hordeAvoidancePressure"]
        centre = pressure(0.0, 0.0, 5.0, 20.0, 9.2, 0.0)
        self.assertEqual(centre, 1.0)
        samples = [pressure(along, 0.0, 5.0, 20.0, 9.2, 0.0)
                   for along in (1.0, 5.0, 10.0, 15.0, 20.0, 25.0)]
        self.assertEqual(samples, sorted(samples, reverse=True))
        self.assertTrue(all(value < centre for value in samples))
        self.assertGreater(samples[2], 0.0)
        self.assertEqual(samples[-1], 0.0)
        for edge in (-14.2, 25.0):
            self.assertLess(abs(pressure(edge - 1e-5, 0.0, 5.0, 20.0, 9.2, 0.0)
                                - pressure(edge + 1e-5, 0.0, 5.0, 20.0, 9.2, 0.0)), 1e-8)
        self.assertEqual(pressure(0.0, 20.0, 5.0, 20.0, 9.2, 0.0), 0.0)

    def test_radius_fade_changes_target_without_teleporting_state(self):
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        data = target.__globals__["HordeSplit"]
        position, velocity, locked = 3.0, 0.0, 1.0
        for step in range(401):
            data[2] = max(0.0, 3.636 * (1.0 - step / 400.0))
            control = target(0.0, 0.0, position, 1.0, 0.0, 31.0, locked)
            new_position, velocity = self.integrate(position, velocity, control.x, 0.75, 0.1)
            self.assertLessEqual(abs(new_position - position), 0.0525 + 1e-12)
            position = new_position
        self.assertEqual(control.x, 0.0)
        self.assertEqual(control.y, 0.0)
        self.assertEqual(control.z, 1.0)

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

    def test_soft_targets_keep_base_columns_distinct_even_with_overlapping_circles(self):
        for count in (1, 4, 16, 64):
            target = self.target_kernel(((0.0, 0.0, 3.636),) * count)
            world = [base + target(0.0, base, 0.0, 1.0, 0.0, 31.0, 1.0).x
                     for base in (-1.0, -0.5, 0.0, 0.5, 1.0)]
            self.assertGreater(world[-1] - world[0], 0.1)
            self.assertGreater(max(world) - min(world), 0.1)
        union = self.control["hordeAvoidanceUnion"]
        for demand in (0.01, 1.0, 10.0, 100.0, 10000.0):
            self.assertEqual(union(demand, 0.0), demand)
            self.assertGreaterEqual(union(demand, demand * 0.5), demand)
            self.assertLessEqual(union(demand, demand), demand * 1.0625 + 1e-12)

    def test_soft_pressure_allows_core_entry_and_individual_target_variation(self):
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        values = [target(0.0, 0.0, 0.0, 1.0, 0.0, float(column * 31), 1.0).x
                  for column in range(64)]
        self.assertGreater(max(values) - min(values), 0.15)
        penetrated = [target(0.0, 0.0, 0.0, 1.0, -1.5, float(column * 31), 1.0).x
                      for column in range(64)]
        self.assertTrue(any(0.0 < value < 3.636 for value in penetrated))
        self.assertGreater(sum(value >= 3.636 for value in values), len(values) * 0.9)
        for column, value in enumerate(values):
            self.assertEqual(value, target(0.0, 0.0, 0.0, 1.0, 0.0, float(column * 31), 1.0).x)

    def test_active_target_preserves_natural_sway_additively(self):
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        low = target(-3.0, 0.0, 0.0, 1.0, -0.75, 31.0, 1.0)
        high = target(-3.0, 0.0, 0.0, 1.0, 0.75, 31.0, 1.0)
        self.assertEqual(low.y, 1.0)
        self.assertEqual(high.y, 1.0)
        self.assertAlmostEqual(high.x - low.x, 1.5, delta=1e-12)

    def test_overlap_is_smooth_and_encounter_flag_stays_binary(self):
        previous = None
        for across in (-0.02, -0.01, 0.0, 0.01, 0.02):
            target = self.target_kernel(((0.0, 0.0, 3.636), (0.0, across, 3.636)))
            value = target(-3.0, 0.0, 0.0, 1.0, 0.0, 31.0, 1.0)
            self.assertIn(value.y, (0.0, 1.0))
            self.assertIn(value.z, (-1.0, 1.0))
            if previous is not None:
                self.assertLess(abs(value.x - previous), 0.05)
            previous = value.x
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        for station in (-200.0, -30.0, -5.0, 0.0, 10.0, 200.0):
            control = target(station, 0.0, 0.0, -1.0, 0.3, 31.0, 1.0)
            self.assertIn(control.y, (0.0, 1.0))
            self.assertEqual(control.z, 1.0)
            for locked in (-1.0, 0.0, 1.0):
                side = self.control["hordeAvoidanceSide"](locked, control.z, control.y, 0.0)
                self.assertIn(side, (-1.0, 0.0, 1.0))
            if control.y == 0.0:
                self.assertEqual(control.x, 0.3)

    def trajectory_density(self, circles, speed):
        target = self.target_kernel(circles)
        scope = target.__globals__
        scope["CameraSpeed"].w = speed
        dt = 0.25
        core = baseline_core = centre = baseline_centre = 0
        crossings, displacements = [], []
        # 所有列都计入实际占据，包括原本在核心外却可能被错误推入的单位。
        for lane in range(-20, 21):
            base = lane * 0.5
            for phase in range(16):
                seed = float(phase * 17 + (lane + 20) * 31)
                now = 1000.0 + phase * 7.0
                scope["OriginTime"].w = now
                position = self.motion["motionSway"](now, seed)
                velocity = self.motion["motionSwayVelocity"](now, seed)
                locked = 0.0
                station = -60.0
                crossed = False
                while station <= 20.0:
                    now += dt
                    station += speed * dt
                    scope["OriginTime"].w = now
                    natural = self.motion["motionSway"](now, seed)
                    along = station + self.motion["motionDrift"](now, seed)
                    control = target(along, base, position, -1.0 if phase % 2 else 1.0,
                                     natural, seed, locked)
                    previous = position
                    if control.y > 0.0 or locked != 0.0:
                        position, velocity = self.integrate(position, velocity, control.x, speed, dt)
                        arrived = float(position == natural and
                                        abs(velocity - self.motion["motionSwayVelocity"](now, seed))
                                        <= 0.7 * speed * dt / 4.0)
                        locked = self.control["hordeAvoidanceSide"](locked, control.z, control.y, arrived)
                        self.assertLessEqual(abs(position - previous), 0.7 * speed * dt + 1e-10)
                    else:
                        position = natural
                        velocity = self.motion["motionSwayVelocity"](now, seed)
                    if abs(along) <= 1.0:
                        def occupied(lateral, scale):
                            return any((along - x) ** 2 + (lateral - y) ** 2 < (radius * scale) ** 2
                                       for x, y, radius in circles)
                        core += occupied(base + position, 1.0)
                        baseline_core += occupied(base + natural, 1.0)
                        centre += occupied(base + position, 0.5)
                        baseline_centre += occupied(base + natural, 0.5)
                    if not crossed and along >= 0.0:
                        crossed = True
                        if abs(base) <= 1.0:
                            crossings.append(abs(base + position))
                            displacements.append(abs(position - natural))
        ordered = sorted(crossings)
        shifts = sorted(displacements)
        return dict(core_ratio=core / baseline_core, centre_ratio=centre / baseline_centre,
                    median_shift=shifts[len(shifts) // 2],
                    edge_spread=ordered[int(len(ordered) * 0.9)] - ordered[int(len(ordered) * 0.1)])

    def test_integrated_paths_open_core_with_distributed_outer_edge(self):
        saved_speed = self.motion["CameraSpeed"].w
        saved_time = self.motion["OriginTime"].w
        try:
            for speed in (0.25, 0.75):
                with self.subTest(speed=speed):
                    result = self.trajectory_density(((0.0, 0.0, 3.636),), speed)
                    print("single-circle integrated density", speed, result)
                    self.assertLess(result["core_ratio"], 0.5)
                    self.assertLess(result["centre_ratio"], 0.2)
                    self.assertGreater(result["median_shift"], 3.0)
                    self.assertGreater(result["edge_spread"], 0.75)
            result = self.trajectory_density(((0.0, -3.473, 3.636), (0.0, 0.0, 3.636),
                                              (0.0, 3.473, 3.636)), 0.75)
            print("three-circle integrated density", result)
            self.assertLess(result["core_ratio"], 0.8)
            self.assertLess(result["centre_ratio"], 0.4)
            self.assertGreater(result["median_shift"], 3.0)
            self.assertGreater(result["edge_spread"], 0.75)
        finally:
            self.motion["CameraSpeed"].w = saved_speed
            self.motion["OriginTime"].w = saved_time

    def test_outer_route_and_no_circle_do_not_create_a_fake_split(self):
        empty = self.target_kernel(())
        outer = self.target_kernel(((0.0, 0.0, 3.636),))
        for base in (-20.0, 20.0):
            self.assertEqual(outer(0.0, base, 0.0, 1.0, 0.4, 31.0, 0.0).x, 0.4)
        self.assertEqual(empty(0.0, 0.0, 0.0, 1.0, 0.4, 31.0, 0.0).x, 0.4)

    def test_initial_side_uses_seed_only_for_similar_costs(self):
        target = self.target_kernel(((0.0, 0.0, 3.636),))
        for preferred in (-1.0, 1.0):
            self.assertEqual(target(-10.0, 0.0, 0.0, preferred, 0.0, 17.0, 0.0).z, preferred)
        shorter = self.target_kernel(((0.0, 5.0, 3.636),))
        self.assertEqual(shorter(-10.0, 0.0, 0.0, 1.0, 0.0, 17.0, 0.0).z, -1.0)

    def test_weak_onset_chooses_geometric_near_side_before_locking(self):
        for across in (-5.0, 5.0):
            target = self.target_kernel(((0.0, across, 3.636),))
            near_side = -math.copysign(1.0, across)
            encounters = 0
            for step in range(3201):
                station = -80.0 + step * 0.025
                value = target(station, 0.0, 0.0, -near_side, 0.0, 17.0, 0.0)
                if value.y > 0.0:
                    encounters += 1
                    self.assertEqual(value.z, near_side)
                    locked = self.control["hordeAvoidanceSide"](0.0, value.z, value.y, 0.0)
                    self.assertEqual(locked, near_side)
            self.assertGreater(encounters, 0)

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
