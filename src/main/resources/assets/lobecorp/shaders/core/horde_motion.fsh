#version 330

#moj_import <lobecorp:horde_motion.glsl>

out vec4 fragColor;

// 按进度的潮：每个实例的运动缓存只由波头站程、行列和事件时钟决定。
// 这里不再读取任何单位历史，所以不存在“历史失效后回落到解析锚点”的重填路径。
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
    float delay;
    float lateralOffset;
    hordeSplitOffset(station, lateral, seed, delay, lateralOffset);
    float finalStation = station + delay;
    float finalLateral = lateral + lateralOffset;
    float value;
    if (component < 3) {
        value = movingLocation(finalStation, finalLateral, OriginTime.w, seed)[component];
    } else if (component == 3 || component == 5 || component == 6) {
        vec2 direction = routeForward(finalStation + motionDrift(OriginTime.w, seed));
        value = component == 3 ? atan(direction.x, direction.y) : component == 5 ? direction.x : direction.y;
    } else if (component == 4) {
        vec3 point = movingLocation(finalStation, finalLateral, OriginTime.w, seed);
        vec2 light = lightAt(point);
        // 光照区段缺失时回退到事件级亮度：宁可亮度不准，也不把单位整批隐藏。
        value = light.x < 0.0 ? DebugInfo.x + DebugInfo.y * 16.0 : light.x + light.y * 16.0;
    } else {
        float phase = fract((WorldSection.w + float(row)) * 0.375 + float(column) * 0.625);
        float frame = fract(OriginTime.w / (20.0 * AnimationInfo.z) + phase) * AnimationInfo.y;
        int frames = int(AnimationInfo.y);
        int raw = int(floor(frame)) % frames;
        // 距离降帧在GPU完成：<base 全帧，base..2base 半数，2base..3base 四分之一，更远静止；-1不降，0静止。
        int effective = frames;
        float base = FrameLod.x;
        if (base == 0.0) {
            effective = 1;
        } else if (base > 0.0) {
            vec3 point = movingLocation(finalStation, finalLateral, OriginTime.w, seed) + OriginTime.xyz;
            float distance = length(point);
            effective = distance < base ? frames
                    : distance < base * 2.0 ? max(1, frames / 2)
                    : distance < base * 3.0 ? max(1, frames / 4) : 1;
        }
        int sampled = effective > 1 ? int(floor(float(raw) * float(effective) / float(frames))) : 0;
        int mapped = effective > 1 ? int(round(float(sampled) * float(frames - 1) / float(effective - 1))) : 0;
        value = float(mapped * int(AnimationInfo.x));
    }
    uint bits = floatBitsToUint(value);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
        (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
