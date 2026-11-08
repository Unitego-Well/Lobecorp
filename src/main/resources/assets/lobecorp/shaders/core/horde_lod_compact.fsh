#version 330
#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_lod_priority.glsl>
uniform sampler2D HordeLodSource;
uniform sampler2D HordeLodRanges;
layout(std140) uniform HordeLodParameters { ivec4 LodRange; };
out vec4 fragColor;
void main() {
    int slot = int(gl_FragCoord.x);
    int material = int(gl_FragCoord.y);
    int accepted = 0;
    uint bits = 0xffffffffu;
    for (int rank = 0; rank < 128; rank++) {
        float squared = hordeLodDistance(rawWord(HordeLodSource, rank * 2));
        uint identity = rawWord(HordeLodSource, rank * 2 + 1);
        if (identity == 0xffffffffu || squared >= 16384.0) {
            break;
        }
        int allowance = max(0, 128 - int(floor(sqrt(squared) / 16.0)) * 16);
        if (rank >= allowance) {
            continue;
        }
        int owner = -1;
        for (int range = 0; range < LodRange.z; range++) {
            int first = integer(HordeLodRanges, range * 3);
            int last = integer(HordeLodRanges, range * 3 + 1);
            if (identity >= uint(first) && identity < uint(last)) {
                owner = integer(HordeLodRanges, range * 3 + 2);
                break;
            }
        }
        if (owner == material) {
            if (accepted == slot) {
                bits = identity;
                break;
            }
            accepted++;
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
