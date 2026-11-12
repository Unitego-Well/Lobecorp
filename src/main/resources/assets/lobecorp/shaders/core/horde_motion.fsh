#version 330

#moj_import <lobecorp:horde_motion.glsl>

uniform sampler2D HordePose;
out vec4 fragColor;

// 前进站程保持纯函数；总横向位移、速度读取每事件独立 GPU 行窗。
// 运动缓存不承担稳定身份或运动历史，末字只描述本帧LOD优先级。
void main() {
    int pixel = int(gl_FragCoord.y) * MotionInfo.z + int(gl_FragCoord.x);
    int local = pixel / HORDE_MOTION_VALUES - MotionInfo.x;
    if (local < 0 || local >= MotionInfo.y) {
        discard;
    }
    int component = pixel % HORDE_MOTION_VALUES;
    int ordinal = local + int(RowColumns.w);
    int row = ordinal / int(RowColumns.z);
    int column = int(RowColumns.y) + ordinal % int(RowColumns.z);
    float station = RowColumns.x - float(row) * SpacingLength.x;
    float lateral = (float(column) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
    float seed = mod(WorldSection.w + float(row), 1024.0) * 17.0 + float(column) * 31.0;
    int stateRow = AvoidanceInfo.x + row;
    int stateAt = (stateRow * int(SpacingLength.z) + column) * HORDE_AVOID_WORDS;
    if (component == 7 && LightRoute.z > 0.5 && FrameLod.y < 0.5) {
        int identityRow = int(mod(WorldSection.w + float(row), 1024.0));
        uint bits = floatBitsToUint(float(integer(HordePose, identityRow * int(SpacingLength.z) + column)));
        fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u, (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
        return;
    }
    float finalStation = station;
    // 总侧移已经包含自然摆动；这里不能再调用 movingLocation 叠加第二次 sway。
    vec3 point = vec3(0.0);
    bool needsPoint = component < 3 || component == 4 || component == 8 || (component == 7 && FrameLod.x > 0.0);
    if (needsPoint) {
        float totalLateral = scalar(HordeAvoidance, stateAt) + motionSway(OriginTime.w, seed);
        vec2 horizontal = motionXZ(finalStation + motionDrift(OriginTime.w, seed), lateral, totalLateral);
        point = vec3(horizontal.x, 0.0, horizontal.y);
        if (component == 1 || component == 4 || component == 7 || component == 8) {
            point.y = motionGroundHeight(finalStation, lateral, totalLateral, seed);
        }
    }
    float value;
    if (component < 3) {
        value = point[component] + OriginTime[component];
    } else if (component == 3) {
        value = hordeInstanceScale(local);
    } else if (component == 5 || component == 6) {
        float lateralSpeed = scalar(HordeAvoidance, stateAt + 1);
        vec2 velocity = motionVelocityXZ(finalStation, lateral, seed, scalar(HordeAvoidance, stateAt), lateralSpeed);
        vec2 direction;
        if (dot(velocity, velocity) > 0.000001) {
            direction = velocity * inversesqrt(dot(velocity, velocity));
        } else {
            direction = routeForward(finalStation + motionDrift(OriginTime.w, seed));
        }
        value = component == 5 ? direction.x : direction.y;
    } else if (component == 4) {
        vec2 light = lightAt(point);
        // 光照区段缺失时回退到事件级亮度：宁可亮度不准，也不把单位整批隐藏。
        value = light.x < 0.0 ? DebugInfo.x + DebugInfo.y * 16.0 : light.x + light.y * 16.0;
        if (station < SegmentRegions.x || station >= SegmentRegions.y || station < 0.0 || station >= SpacingLength.w) {
            value = -1.0;
        }
    } else if (component == 8) {
        value = 0.0;
        vec3 relative = point + OriginTime.xyz;
        // 每个距离档内优先同事件分割边缘，不跨档抢占更近实例的额度。
        if (dot(relative, relative) < HORDE_LOD_EDGE_NEAR_SQUARED) {
            float modelScale = hordeInstanceScale(local);
            for (int slot = 0; slot < int(SplitInfo.x); slot++) {
                int at = slot * 5;
                float radius = scalar(HordeSplit, at + 2);
                float yMin = scalar(HordeSplit, at + 3);
                float yMax = scalar(HordeSplit, at + 4);
                if (radius <= 0.01 || point.y >= yMax || point.y + LightRoute.w * modelScale <= yMin) {
                    continue;
                }
                vec2 center = vec2(scalar(HordeSplit, at), scalar(HordeSplit, at + 1));
                float edgeDistance = abs(length(point.xz - center) - radius);
                if (edgeDistance <= max(2.0, SplitShape.w * 2.0 + radius * 0.35)) {
                    value = 1.0;
                    break;
                }
            }
        }
    } else {
        float phase = motionRandom(seed, 29.0);
        float time = FrameLod.y > 0.5 ? OriginTime.w / (20.0 * AnimationInfo.z) : 0.0;
        float frame = fract(time + phase) * AnimationInfo.y;
        int frames = int(AnimationInfo.y);
        int raw = int(floor(frame)) % frames;
        // 距离降帧在GPU完成：<base 全帧，base..2base 半数，2base..3base 四分之一，更远静止；-1不降，0静止。
        int effective = frames;
        float base = FrameLod.x;
        if (FrameLod.y < 0.5) {
            effective = frames;
        } else if (base == 0.0) {
            effective = 1;
        } else if (base > 0.0) {
            float distance = length(point + OriginTime.xyz);
            effective = distance < base ? frames
                    : distance < base * 2.0 ? max(1, frames / 2)
                    : distance < base * 3.0 ? max(1, frames / 4) : 1;
        }
        int sampled = effective > 1 ? int(floor(float(raw) * float(effective) / float(frames))) : 0;
        int mapped = effective > 1 ? int(round(float(sampled) * float(frames - 1) / float(effective - 1))) : 0;
        value = float(mapped);
    }
    uint bits = floatBitsToUint(value);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
        (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
