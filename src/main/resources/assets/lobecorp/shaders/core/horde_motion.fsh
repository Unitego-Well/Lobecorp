#version 330

#moj_import <lobecorp:horde_motion.glsl>

out vec4 fragColor;

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
    float value;
    if (component < 3) {
        value = unitPosition(row, column, station, lateral, seed)[component];
    } else if (component == 3 || component == 5 || component == 6) {
        vec2 direction = unitDirection(row, column, station);
        value = component == 3 ? atan(direction.x, direction.y) : component == 5 ? direction.x : direction.y;
    } else if (component == 4) {
        vec3 point = unitPosition(row, column, station, lateral, seed);
        vec2 light = lightAt(point);
        bool visible = station >= SegmentRegions.x && station < SegmentRegions.y
        && station >= 0.0 && station < SpacingLength.w;
        value = !visible || light.x < 0.0 ? -1.0 : light.x + light.y * 16.0;
    } else {
        float phase = fract((WorldSection.w + float(row)) * 0.375 + float(column) * 0.625);
        float frame = fract(OriginTime.w / (20.0 * AnimationInfo.z) + phase) * AnimationInfo.y;
        value = float(int(floor(frame)) % int(AnimationInfo.y) * int(AnimationInfo.x));
    }
    uint bits = floatBitsToUint(value);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
        (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
