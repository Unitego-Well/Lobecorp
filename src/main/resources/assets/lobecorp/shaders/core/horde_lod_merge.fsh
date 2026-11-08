#version 330
#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D HordeLodSource;
layout(std140) uniform HordeLodParameters { ivec4 LodRange; };
out vec4 fragColor;
void main() {
    int pixel = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    int ordinal = pixel / 2;
    if (ordinal >= LodRange.y) {
        discard;
    }
    int block = ordinal / 128;
    int local = ordinal % 128;
    int a = block * 256 + local;
    int b = block * 256 + 255 - local;
    uint da = rawWord(HordeLodSource, a * 2);
    uint db = rawWord(HordeLodSource, b * 2);
    uint ia = rawWord(HordeLodSource, a * 2 + 1);
    uint ib = rawWord(HordeLodSource, b * 2 + 1);
    int winner = da < db || (da == db && ia < ib) ? a : b;
    uint bits = rawWord(HordeLodSource, winner * 2 + pixel % 2);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
