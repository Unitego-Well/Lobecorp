#version 330
#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D HordeLodSource;
layout(std140) uniform HordeLodParameters { ivec4 LodRange; };
out vec4 fragColor;
// 以无符号距离与身份的词典序比较，保持原排序与打平规则。
bool lessPair(uvec2 a, uvec2 b) {
    return a.x < b.x || (a.x == b.x && a.y < b.y);
}
uvec2 readPair(int base, int index) {
    return uvec2(rawWord(HordeLodSource, (base + index) * 2),
                 rawWord(HordeLodSource, (base + index) * 2 + 1));
}
uvec2 rankedPair(int base, int rank) {
    // 划分前rank项，返回两个右边界中较小的一项。
    int low = 0;
    int high = rank;
    for (int iteration = 0; iteration < 8; ++iteration) {
        int i = (low + high) / 2;
        int j = rank - i;
        // 分层条件保护左边界，禁止负索引取纹素。
        if (i > 0) {
            if (lessPair(readPair(base + 128, j), readPair(base, i - 1))) {
                high = i - 1;
                continue;
            }
        }
        if (j > 0) {
            if (lessPair(readPair(base, i), readPair(base + 128, j - 1))) {
                low = i + 1;
                continue;
            }
        }
        uvec2 a = readPair(base, i);
        uvec2 b = readPair(base + 128, j);
        return lessPair(a, b) ? a : b;
    }
    // 两表已排序且rank在0到127时，最多八步必定命中划分。
    return uvec2(0xffffffffu);
}
void main() {
    int pixel = int(gl_FragCoord.y) * LodRange.x + int(gl_FragCoord.x);
    int ordinal = pixel / 2;
    if (ordinal >= LodRange.y) {
        discard;
    }
    int block = ordinal / 128;
    int local = ordinal % 128;
    if (LodRange.z == 1) {
        uvec2 pair = rankedPair(block * 256, local);
        uint bits = pixel % 2 == 0 ? pair.x : pair.y;
        fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
                (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
        return;
    }
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
