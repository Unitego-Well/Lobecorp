#version 330

#moj_import <lobecorp:horde_data.glsl>

uniform sampler2D HordeLodSource;
layout(std140) uniform HordeLodParameters {
    ivec4 LodRange; // 纹理宽度、候选容量、双调阶段、比较步长。
};
out vec4 fragColor;

void main() {
    int pixel = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    int ordinal = pixel / 2;
    if (ordinal >= LodRange.y) {
        discard;
    }
    int partner = ordinal ^ LodRange.w;
    uint distanceA = rawWord(HordeLodSource, ordinal * 2);
    uint distanceB = rawWord(HordeLodSource, partner * 2);
    uint identityA = rawWord(HordeLodSource, ordinal * 2 + 1);
    uint identityB = rawWord(HordeLodSource, partner * 2 + 1);
    bool lower = distanceA < distanceB || (distanceA == distanceB && identityA < identityB);
    bool ascending = (ordinal & LodRange.z) == 0;
    bool keepLower = ((ordinal & LodRange.w) == 0) == ascending;
    int selected = lower == keepLower ? ordinal : partner;
    uint bits = rawWord(HordeLodSource, selected * 2 + pixel % 2);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
