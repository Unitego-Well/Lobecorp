#version 330

#moj_import <lobecorp:horde_motion.glsl>

layout(std140) uniform AvoidanceWindow {
    ivec4 PreviousWindow; // 当前首行相对旧窗偏移、旧行数、列数、当前行数。
    vec4 AvoidanceClock; // 旧事件时间、实际dt、当前写纹理宽度。
};
uniform sampler2D AvoidancePrevious;
out vec4 fragColor;

vec4 avoidanceEncode(uint bits) {
    return vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}

// 只改变控制目标；动态圆穿过中线不会直接改写位移或已锁定侧。
vec3 avoidanceTarget(float station, float lateral, float position, float preferred, float natural, float seed, float locked) {
    float heldSide = mix(preferred, locked, abs(locked));
    if (int(SplitInfo.x) <= 0) {
        return vec3(natural, 0.0, heldSide);
    }
    vec2 forward = routeForward(station);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 base = location(station, lateral);
    vec2 desired = vec2(-natural, natural);
    vec2 capacities = vec2(SplitShape.z);
    vec2 encounters = vec2(0.0);
    float driftBound = motionDriftSpeedBound(seed);
    int firstChannel = int(step(0.0, heldSide));
    int lastChannel = firstChannel + 1;
    if (locked == 0.0) {
        firstChannel = 0;
        lastChannel = 2;
    }
    // 圆数据只读一遍；初选共享左右候选，已锁定身份只处理原侧。
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
        bool intersects = abs(across) <= clearance + HORDE_AVOID_SWAY_MARGIN
        || abs(across - position) <= clearance;
        for (int channel = firstChannel; channel < lastChannel; channel++) {
            float candidateSide = float(channel * 2 - 1);
            float goal = candidateSide * across + clearance;
            float target = candidateSide * min(goal, SplitShape.z);
            float required = max(abs(target - position), abs(target - natural)) + HORDE_AVOID_SWAY_MARGIN;
            float lead = hordeAvoidanceLead(required, CameraSpeed.w, driftBound, SplitInfo.z * fade);
            lead += locked != 0.0 ? SplitInfo.z * fade : 0.0;
            if (along < -clearance - SplitInfo.z * fade || along > clearance + lead) {
                continue;
            }
            bool blocked = goal > SplitShape.z && candidateSide * across > clearance;
            // 先施加前方不可穿越圆的近侧容量，不能等单位贴圆后才刹车。
            if (blocked) {
                capacities[channel] = min(capacities[channel],
                        mix(SplitShape.z, max(0.0, candidateSide * across - clearance), fade));
            }
            if (!intersects) {
                continue;
            }
            encounters[channel] = 1.0;
            if (!blocked) {
                desired[channel] = max(desired[channel],
                        hordeAvoidanceGoal(across, clearance, candidateSide, natural, fade));
            }
        }
    }
    int selected = int(step(0.0, heldSide));
    if (locked == 0.0) {
        float leftTarget = -clamp(desired.x, -SplitShape.z, capacities.x);
        float rightTarget = clamp(desired.y, -SplitShape.z, capacities.y);
        float selectedSide = hordeAvoidanceChoose(preferred, abs(leftTarget - position), abs(rightTarget - position),
                hordeAvoidanceFits(desired.x, capacities.x), hordeAvoidanceFits(desired.y, capacities.y));
        selected = int(step(0.0, selectedSide));
    }
    float selectedSide = float(selected * 2 - 1);
    if (encounters[selected] <= 0.0) {
        return vec3(natural, 0.0, selectedSide);
    }
    return vec3(selectedSide * clamp(desired[selected], -SplitShape.z, capacities[selected]), 1.0, selectedSide);
}

void main() {
    int pixel = int(gl_FragCoord.y) * int(AvoidanceClock.z) + int(gl_FragCoord.x);
    int unit = pixel / HORDE_AVOID_WORDS;
    int component = pixel % HORDE_AVOID_WORDS;
    int columns = PreviousWindow.z;
    int row = unit / columns;
    int column = unit % columns;
    if (row >= PreviousWindow.w) {
        fragColor = vec4(0.0);
        return;
    }
    // 64位窗映射已经排除不重叠的高位身份；低32位始终作为原始uint保存，不转float。
    uint rowTag = uint(AvoidanceInfo.y) + uint(row);
    if (component == 3) {
        fragColor = avoidanceEncode(rowTag);
        return;
    }
    bool valid = PreviousWindow.x >= -row && PreviousWindow.x < PreviousWindow.y - row;
    int oldRow = valid ? row + PreviousWindow.x : 0;
    int oldAt = (oldRow * columns + column) * HORDE_AVOID_WORDS;
    if (valid) {
        valid = rawWord(AvoidancePrevious, oldAt + 3) == rowTag;
    }
    float dt = AvoidanceClock.y;
    if (valid && dt <= 0.0) {
        fragColor = avoidanceEncode(rawWord(AvoidancePrevious, oldAt + component));
        return;
    }
    float time = OriginTime.w;
    float seed = float(rowTag & 1023u) * 17.0 + float(column) * 31.0;
    float natural = motionSway(time, seed);
    float position = natural;
    float velocity = motionSwayVelocity(time, seed);
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
            float swayVelocity = motionSwayVelocity(time, seed);
            float arrived = position == natural
            && abs(velocity - swayVelocity) <= limit * dt / HORDE_AVOID_ACCEL_TICKS ? 1.0 : 0.0;
            locked = hordeAvoidanceSide(locked, control.z, control.y, arrived);
        } else {
            position = natural;
            velocity = motionSwayVelocity(time, seed);
        }
    }
    float value = component == 0 ? position - natural : component == 1 ? velocity : locked;
    fragColor = avoidanceEncode(floatBitsToUint(value));
}
