#version 330
#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_lod_priority.glsl>
uniform sampler2D HordeLodSource;
uniform sampler2D HordeLodRanges;
layout(std140) uniform HordeLodParameters {
    ivec4 LodRange;
#ifdef HORDE_SELECTED_SCATTER
    ivec4 LodMotion;
#endif
};
out vec4 fragColor;
void writeIdentity(uint bits) {
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
int materialAt(uint identity) {
    int low = 0;
    int high = LodRange.z;
    while (low < high) {
        int middle = low + (high - low) / 2;
        int first = integer(HordeLodRanges, middle * 3);
        int last = integer(HordeLodRanges, middle * 3 + 1);
        if (identity < uint(first)) {
            high = middle;
        } else if (identity >= uint(last)) {
            low = middle + 1;
        } else {
            return integer(HordeLodRanges, middle * 3 + 2);
        }
    }
    return -1;
}
void main() {
    int slot = int(gl_FragCoord.x);
    int material = int(gl_FragCoord.y);
    // 单材质无需查所属范围，但仍逐排名执行原额度判断，保留GPU边界舍入结果。
    int accepted = 0;
    uint bits = 0xffffffffu;
    for (int rank = 0; rank < 128; rank++) {
#ifdef HORDE_SELECTED_SCATTER
        // 实验分支按候选写入的距离档统一额度，原A保留下方原计算。
        uint key = rawWord(HordeLodSource, rank * 2);
        uint identity = rawWord(HordeLodSource, rank * 2 + 1);
        if (!hordeLodAccepted(key, identity, rank, LodMotion.y)) {
            continue;
        }
#else
        float squared = hordeLodDistance(rawWord(HordeLodSource, rank * 2));
        uint identity = rawWord(HordeLodSource, rank * 2 + 1);
        if (identity == 0xffffffffu || squared >= 16384.0) {
            break;
        }
        int allowance = max(0, 128 - int(floor(sqrt(squared) / 16.0)) * 16);
        if (rank >= allowance) {
            continue;
        }
#endif
        int owner = LodRange.w != 0 ? (LodRange.y == 1 ? 0 : materialAt(identity)) : -1;
        for (int range = 0; LodRange.w == 0 && range < LodRange.z; range++) {
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
