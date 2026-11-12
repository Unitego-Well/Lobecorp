
#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_flow.glsl>

layout(std140) uniform AvoidanceWindow {
    ivec4 PreviousWindow; // 当前首行相对旧窗偏移、旧行数、列数、当前行数。
    vec4 AvoidanceClock; // 旧事件时间、实际dt、当前写纹理宽度。
};
uniform sampler2D AvoidancePrevious;
vec4 avoidanceEncode(uint bits) {
    return vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}

// 只改变控制目标；动态圆穿过中线不会直接改写位移或已锁定侧。
vec3 avoidanceSplitTarget(float station, float lateral, float position, float preferred, float natural, float seed, float locked) {
    float heldSide = mix(preferred, locked, abs(locked));
    if (int(SplitInfo.x) <= 0) {
        return vec3(natural, 0.0, heldSide);
    }
    vec2 forward = routeForward(station);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 base = location(station, lateral);
    vec2 weighted = vec2(0.0);
    vec2 mass = vec2(0.0);
    vec2 demands = vec2(0.0);
    float driftBound = motionDriftSpeedBound(seed);
    float phase = motionRandom(seed, HORDE_AVOID_PHASE_SALT) * HORDE_AVOID_TAU;
    float individual = hordeAvoidanceIndividual(motionRandom(seed, HORDE_AVOID_STRENGTH_SALT), phase, OriginTime.w);
    float radiusRandom = motionRandom(seed, HORDE_AVOID_RADIUS_SALT);
    float phaseSin = sin(phase);
    int firstChannel = int(step(0.0, heldSide));
    int lastChannel = firstChannel + 1;
    if (locked == 0.0) {
        firstChannel = 0;
        lastChannel = 2;
    }
    // 单次圆扫描；中心明确分流，个体有效半径与渐变压力保留外缘散度。
    for (int slot = 0; slot < int(SplitInfo.x); slot++) {
        int at = slot * 5;
        float radius = scalar(HordeSplit, at + 2);
        float yMin = scalar(HordeSplit, at + 3);
        float yMax = scalar(HordeSplit, at + 4);
        if (radius <= 0.0 || base.y >= yMax || base.y + LightRoute.w <= yMin) {
            continue;
        }
        vec2 relative = vec2(scalar(HordeSplit, at), scalar(HordeSplit, at + 1)) - base.xz;
        float along = dot(relative, forward);
        float across = dot(relative, side);
        float fade = smoothstep(0.0, HORDE_AVOID_FADE_RADIUS, radius);
        float clearance = hordeAvoidanceClearance(radius, SplitShape.w, fade);
        clearance = hordeAvoidanceRadius(clearance, radiusRandom);
        float band = SplitInfo.z * fade;
        float shift = phaseSin * clearance * HORDE_AVOID_PHASE_SHIFT;
        float farDelta = hordeAvoidanceSoftDelta(abs(across), clearance, 1.0);
        float required = min(farDelta * HORDE_AVOID_MAX_STRENGTH, SplitShape.z) + HORDE_AVOID_SWAY_MARGIN;
        float lead = hordeAvoidanceLead(required, CameraSpeed.w, driftBound, band);
        float pressure = hordeAvoidancePressure(along, across, clearance, lead, band, shift);
        float weight = pressure * fade;
        for (int channel = firstChannel; channel < lastChannel; channel++) {
            float candidateSide = float(channel * 2 - 1);
            float delta = hordeAvoidanceSoftDelta(across, clearance, candidateSide);
            weighted[channel] = hordeAvoidanceUnion(weighted[channel], pressure * fade * delta);
            mass[channel] = max(mass[channel], weight);
            if (pressure > 0.0) {
                demands[channel] = hordeAvoidanceUnion(demands[channel], fade * delta);
            }
        }
    }
    vec2 displacements = vec2(individual * weighted.x, individual * weighted.y);
    int selected = int(step(0.0, heldSide));
    if (locked == 0.0) {
        // 渐入端压力接近零，不能用被缩小的代价把远侧误判成平手并过早锁侧。
        float leftTarget = clamp(natural - individual * demands.x, -SplitShape.z, SplitShape.z);
        float rightTarget = clamp(natural + individual * demands.y, -SplitShape.z, SplitShape.z);
        float selectedSide = hordeAvoidanceChoose(preferred, abs(leftTarget - position), abs(rightTarget - position));
        selected = int(step(0.0, selectedSide));
    }
    float selectedSide = float(selected * 2 - 1);
    if (mass[selected] <= 0.0) {
        return vec3(natural, 0.0, selectedSide);
    }
    // 遭遇标志只取0/1，不能把连续pressure写成分数锁侧。
    float target = natural + selectedSide * displacements[selected];
    return vec3(clamp(target, -SplitShape.z, SplitShape.z), 1.0, selectedSide);
}

// 每格验证途中碰撞与高差，不能只检查走廊端点而漏掉一格障碍。
int terrainCorridor(vec2 point, vec2 direction, int steps, vec2 ground, float reference) {
    int rank = 0;
    for (int cell = 1; cell <= 6; cell++) {
        if (cell > steps) {
            break;
        }
        vec2 next = hordeNearGround(point + direction * float(cell));
        if (ground.y < 0.0 || next.y < 0.5) {
            return 3;
        }
        rank = max(rank, hordeTerrainRank(reference, ground.x, next.x));
        ground = next;
    }
    return rank;
}

// 地形只改变控制目标，沿用既有侧向速度、加速度与稳定行身份。
vec3 avoidanceTarget(float station, float lateral, float position, float preferred, float natural, float seed, float locked) {
    vec3 control = avoidanceSplitTarget(station, lateral, position, preferred, natural, seed, locked);
    vec2 forward = routeForward(station);
    vec2 side = vec2(-forward.y, forward.x);
    vec2 point = motionXZ(station, lateral, position);
    vec2 ground = hordeNearGround(point);
    int lead = int(ceil(clamp(CameraSpeed.w * HORDE_AVOID_ACCEL_TICKS + 3.0, 3.0, 6.0)));
    if (ground.y < 0.0) {
        return control;
    }
    int bestRank = terrainCorridor(point, forward, lead, ground, ground.x);
    float selected = control.z;
    float offset = 0.0;
    // 保留原两格局部侧路范围，依次评估一格、两格；同等级保留更近的路线。
    for (int distance = 1; distance <= 2 && bestRank > 0; distance++) {
        for (int choice = 0; choice < 2; choice++) {
            float candidateSide = choice == 0 ? control.z : -control.z;
            float delta = candidateSide * float(distance);
            if (abs(position + delta) > SplitShape.z) {
                continue;
            }
            vec2 endpoint = point + side * delta;
            int rank = terrainCorridor(point, side * candidateSide, distance, ground, ground.x);
            if (rank < 3) {
                rank = max(rank, terrainCorridor(endpoint, forward, lead, hordeNearGround(endpoint), ground.x));
            }
            if (rank < bestRank) {
                bestRank = rank;
                selected = candidateSide;
                offset = delta;
            }
        }
    }
    return offset == 0.0 ? control : vec3(position + offset, 1.0, selected);
}

uvec4 avoidanceSolve(int unit) {
    int columns = PreviousWindow.z;
    int row = unit / columns;
    int column = unit % columns;
    uint rowTag = uint(AvoidanceInfo.y) + uint(row);
    bool valid = PreviousWindow.x >= -row && PreviousWindow.x < PreviousWindow.y - row;
    int oldRow = valid ? row + PreviousWindow.x : 0;
    int oldAt = (oldRow * columns + column) * HORDE_AVOID_WORDS;
    if (valid) {
        valid = rawWord(AvoidancePrevious, oldAt + 3) == rowTag;
    }
    float dt = AvoidanceClock.y;
    if (valid && dt <= 0.0) {
        return uvec4(rawWord(AvoidancePrevious, oldAt), rawWord(AvoidancePrevious, oldAt + 1), rawWord(AvoidancePrevious, oldAt + 2), rowTag);
    }
    float time = OriginTime.w;
    float seed = float(rowTag & 1023u) * 17.0 + float(column) * 31.0;
    float natural = motionSway(time, seed);
    float position = natural;
    float swayVelocity = motionSwayVelocity(time, seed);
    float velocity = swayVelocity;
    float locked = 0.0;
    if (valid) {
        position = scalar(AvoidancePrevious, oldAt) + motionSway(AvoidanceClock.x, seed);
        velocity = scalar(AvoidancePrevious, oldAt + 1);
        locked = scalar(AvoidancePrevious, oldAt + 2);
        float station = RowColumns.x + float(AvoidanceInfo.x - row) * SpacingLength.x;
        station += motionDrift(time, seed);
        float lateral = (float(column) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
        float preferred = motionRandom(seed, HORDE_AVOID_SIDE_SALT) < 0.5 ? -1.0 : 1.0;
        vec3 control = avoidanceTarget(station, lateral, position, preferred, natural, seed, locked);
        if (control.y > 0.0 || locked != 0.0) {
            float limit = HORDE_AVOID_SPEED_RATIO * CameraSpeed.w;
            float oldVelocity = clamp(velocity, -limit, limit);
            float error = control.x - position;
            float nextVelocity = hordeAvoidanceVelocity(oldVelocity, error, limit, dt);
            float delta = hordeAvoidanceStep(nextVelocity, error, limit, dt);
            float next = clamp(position + delta, -SplitShape.z, SplitShape.z);
            velocity = (next - position) / dt;
            position = next;
            float arrived = position == natural
            && abs(velocity - swayVelocity) <= limit * dt / HORDE_AVOID_ACCEL_TICKS ? 1.0 : 0.0;
            if (LightRoute.z > 0.5 && control.y > 0.0 && control.z != locked) {
                // 地形明确选到可通行的另一侧时允许释放旧侧；原区域分流仍维持稳定选侧。
                locked = control.z;
            } else {
                locked = hordeAvoidanceSide(locked, control.z, control.y, arrived);
            }
        } else {
            position = natural;
            velocity = swayVelocity;
        }
    }
    return uvec4(floatBitsToUint(position - natural), floatBitsToUint(velocity), floatBitsToUint(locked), rowTag);
}
