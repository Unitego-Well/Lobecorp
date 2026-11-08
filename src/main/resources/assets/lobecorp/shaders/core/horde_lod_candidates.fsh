#version 330

#moj_import <lobecorp:horde_data.glsl>

uniform sampler2D HordeMotion;
layout(std140) uniform HordeLodParameters {
    ivec4 LodRange; // 输出宽度、总容量、全局首编号（0）、本帧总数量。
    ivec4 LodMotion; // 运动首编号（0）、运动每单位字数、保留、保留。
    vec4 LodOrigin;
};
out vec4 fragColor;

void main() {
    int pixel = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    int ordinal = pixel / 2;
    if (ordinal >= LodRange.y) {
        discard;
    }
    uint bits = 0xffffffffu;
    if (ordinal < LodRange.w) {
        int local = ordinal - LodRange.z;
        if (local < 0 || local >= LodRange.w) {
            discard;
        }
        vec3 relative = vector3(HordeMotion, (LodMotion.x + local) * LodMotion.y);
        float distanceSquared = dot(relative, relative);
        if (scalar(HordeMotion, (LodMotion.x + local) * LodMotion.y + 4) >= 0.0) {
            bits = pixel % 2 == 0 ? floatBitsToUint(distanceSquared) : uint(ordinal);
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
