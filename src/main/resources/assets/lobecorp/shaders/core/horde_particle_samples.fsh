#version 330

#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D SampleUnits;
uniform sampler2D SampleSelection;
layout(std140) uniform HordeParticleSampleParameters {
    ivec4 SampleInfo;
    vec4 SampleCamera;
    ivec4 SamplePage;
    ivec4 SampleRow;
};
out vec4 fragColor;

// 与HordeUnitState的16列、每单位12字环页布局一致，不是世界坐标网格。
const int SAMPLE_UNIT_VALUES = 12;
const int SAMPLE_TILE_WIDTH = 16 * SAMPLE_UNIT_VALUES;

uint sampleHash(uint value) {
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    return value * 0x846ca68bu;
}

bool sampleValid(int at) {
    return rawWord(SampleUnits, at + 11) == uint(SampleInfo.y)
    && rawWord(SampleUnits, at + 4) != 0xffffffffu;
}

// 相邻身份必须确实存在于本帧GPU历史，并保持同一地面层及有限空间距离。
bool sampleNeighbor(uvec2 row, int column, int dr, int dc, vec3 point) {
    if (column + dc < 0 || (dr < 0 && row == uvec2(0u))) return false;
    uvec2 other = row;
    if (dr < 0) {
        other.x--;
        if (row.x == 0u) other.y--;
    } else if (dr > 0) {
        other.x++;
        if (other.x == 0u) other.y++;
    }
    uint col = uint(column + dc);
    if (int(col >> 4u) >= SamplePage.y) return false;
    int tile = int((other.x >> 4u) & uint(SamplePage.x)) * SamplePage.y + int(col >> 4u);
    ivec2 address = ivec2(tile % SamplePage.z * SAMPLE_TILE_WIDTH + int(col & 15u) * SAMPLE_UNIT_VALUES,
            tile / SamplePage.z * 16 + int(other.x & 15u));
    int at = address.y * textureSize(SampleUnits, 0).x + address.x;
    if (!sampleValid(at) || rawWord(SampleUnits, at + 3) != other.x
        || rawWord(SampleUnits, at + 4) != other.y || rawWord(SampleUnits, at + 5) != col) return false;
    vec3 delta = vector3(SampleUnits, at) - point;
    return !any(isnan(delta)) && !any(isinf(delta))
    && abs(delta.y) <= 1.5 && dot(delta.xz, delta.xz) <= SampleCamera.w * SampleCamera.w;
}

// 大全局行号先以整数相减；仅局部有符号行差参与浮点站程运算。
float sampleStation(int at) {
    uvec2 row = uvec2(rawWord(SampleUnits, at + 3), rawWord(SampleUnits, at + 4));
    uvec2 reference = uvec2(SampleRow.xy);
    uvec2 difference = uvec2(row.x - reference.x,
            row.y - reference.y - (row.x < reference.x ? 1u : 0u));
    int delta = int(difference.x);
    if (difference.y != (delta < 0 ? 0xffffffffu : 0u)) return uintBitsToFloat(0x7fc00000u);
    return uintBitsToFloat(uint(SampleRow.z)) - float(delta) * uintBitsToFloat(uint(SampleRow.w));
}

void main() {
    int pixel = int(gl_FragCoord.x);
    uint bits = 0u;
    if (SampleInfo.z == 0) {
        // 每桶遍历不相交的页内编号，不在CPU猜测运动位置；每次轮换hash选择，稀疏区域同样被遍历。
        uint best = 0xffffffffu;
        for (int index = pixel; index < SampleInfo.x; index += SampleInfo.w) {
            int at = index * SAMPLE_UNIT_VALUES;
            if (!sampleValid(at)) continue;
            vec3 point = vector3(SampleUnits, at);
            vec3 delta = point - SampleCamera.xyz;
            if (any(isnan(point)) || any(isinf(point)) || dot(delta, delta) > 1024.0) continue;
            float station = sampleStation(at);
            if (isnan(station) || isinf(station) || station < 0.0
                    || station > uintBitsToFloat(uint(SamplePage.w))) continue;
            uint rank = sampleHash(uint(index) ^ sampleHash(uint(SampleInfo.y)));
            if (bits == 0u || rank < best) {
                best = rank;
                bits = uint(index + 1);
            }
        }
    } else {
        int component = pixel % 9;
        uint selected = rawWord(SampleSelection, pixel / 9);
        if (selected != 0u) {
            int at = int(selected - 1u) * SAMPLE_UNIT_VALUES;
            if (component < 6) {
                bits = rawWord(SampleUnits, at + component);
            } else if (component == 7) {
                bits = 1u;
            } else if (component == 8) {
                bits = floatBitsToUint(sampleStation(at));
            } else {
                uvec2 row = uvec2(rawWord(SampleUnits, at + 3), rawWord(SampleUnits, at + 4));
                int column = int(rawWord(SampleUnits, at + 5));
                vec3 point = vector3(SampleUnits, at);
                int bit = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        if (sampleNeighbor(row, column, dr, dc, point)) bits |= 1u << uint(bit);
                        bit++;
                    }
                }
            }
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
