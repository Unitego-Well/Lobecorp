#version 330

#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D StatsSource;
layout(std140) uniform HordeUnitStatsParameters {
    ivec4 StatsInfo;
};
out vec4 fragColor;

void main() {
    int pixel = int(gl_FragCoord.y) * StatsInfo.y + int(gl_FragCoord.x);
    int node = pixel / 2;
    int component = pixel % 2;
    if (node >= (StatsInfo.x + 15) / 16) discard;
    float value = 0.0;
    for (int child = 0; child < 16; child++) {
        int index = node * 16 + child;
        if (index >= StatsInfo.x) break;
        float childValue = 0.0;
        if (StatsInfo.w != 0) {
            int at = index * 12;
            if (rawWord(StatsSource, at + 11) == uint(StatsInfo.z)
                && rawWord(StatsSource, at + 4) != 0xffffffffu) {
                childValue = scalar(StatsSource, at + 9 + component);
            }
        } else {
            childValue = scalar(StatsSource, index * 2 + component);
        }
        if (isnan(childValue) || isinf(childValue) || childValue < 0.0) {
            value = uintBitsToFloat(0x7fc00000u);
            break;
        }
        value = component == 0 ? max(value, childValue) : value + childValue;
    }
    uint bits = floatBitsToUint(value);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
