#version 330

#moj_import <lobecorp:horde_data.glsl>

layout(std140) uniform IndexParameters {
    ivec4 IndexMinimum;
    ivec4 IndexDimensions;
    ivec4 IndexLayout;
    ivec4 IndexOrigin;
    vec4 IndexFraction;
    vec4 IndexSettings;
};
uniform sampler2D IndexTargets;
uniform sampler2D IndexState;
uniform sampler2D IndexParent;
out vec4 fragColor;

uint population(uint bits) {
    bits -= (bits >> 1u) & 0x55555555u;
    bits = (bits & 0x33333333u) + ((bits >> 2u) & 0x33333333u);
    return (((bits + (bits >> 4u)) & 0x0f0f0f0fu) * 0x01010101u) >> 24u;
}

bool overlaps(int ordinal, vec3 minimum, vec3 maximum) {
    // state、raw和格子边界统一使用event局部坐标；此处不引入相机坐标。
    int slot = integer(IndexTargets, IndexLayout.z * 7 + ordinal);
    int state = slot * 13;
    vec3 origin = vector3(IndexState, state);
    vec3 halfSize = max(vec3(0.0), vector3(IndexState, state + 3))
    + abs(vector3(IndexState, state + 9)) * 0.1;
    vec3 velocity = abs(vector3(IndexState, state + 6)) * 0.1;
    float horizontal = 4.0 * (halfSize.x + halfSize.z + IndexFraction.w * 2.0) + 3.0;
    vec3 low = origin - vec3(horizontal, halfSize.y + IndexSettings.x + 0.5, horizontal) - velocity;
    vec3 high = origin + vec3(horizontal, halfSize.y + 0.5, horizontal) + velocity;
    // 索引同时覆盖原始目标，避免展开或移动时漏候选；窄相位只读连续圆状态。
    int raw = slot * 7;
    vec3 local = vector3(IndexTargets, raw);
    vec3 rawSize = vector3(IndexTargets, raw + 3);
    low = min(low, local - vec3(IndexFraction.w, IndexSettings.x, IndexFraction.w));
    high = max(high, local + rawSize + vec3(IndexFraction.w, 0.0, IndexFraction.w));
    return all(lessThanEqual(low, maximum)) && all(greaterThanEqual(high, minimum));
}

uint candidateWord(int word, int parent, vec3 minimum, vec3 maximum) {
    uint possible = IndexLayout.w == 0 ? 0xffffffffu : uint(integer(IndexParent, parent + word));
    if (possible == 0u) {
        return 0u;
    }
    uint result = 0u;
    int first = (word - 1) * 32;
    for (int bit = 0; bit < 32 && first + bit < IndexMinimum.w; bit++) {
        uint flag = 1u << uint(bit);
        if ((possible & flag) != 0u && overlaps(first + bit, minimum, maximum)) {
            result |= flag;
        }
    }
    return result;
}

void main() {
    int pixel = int(gl_FragCoord.y) * IndexLayout.y + int(gl_FragCoord.x);
    int cell = pixel / IndexDimensions.w;
    int word = pixel % IndexDimensions.w;
    if (cell >= IndexDimensions.x * IndexDimensions.y * IndexDimensions.z) {
        fragColor = vec4(0.0);
        return;
    }
    ivec3 coordinate = ivec3(cell % IndexDimensions.x,
            (cell / IndexDimensions.x) % IndexDimensions.y,
            cell / (IndexDimensions.x * IndexDimensions.y));
    int parent = 0;
    bool refine = true;
    if (IndexLayout.w != 0) {
        ivec3 dimensions = IndexDimensions.xyz / 2;
        ivec3 previous = coordinate / 2;
        parent = (previous.x + dimensions.x * (previous.y + dimensions.y * previous.z)) * IndexDimensions.w;
        refine = integer(IndexParent, parent) > int(IndexSettings.y);
    }
    uint bits = 0u;
    if (refine) {
        vec3 minimum = vec3(IndexMinimum.xyz - IndexOrigin.xyz + coordinate * IndexLayout.x) - IndexFraction.xyz;
        vec3 maximum = minimum + vec3(IndexLayout.x);
        if (word == 0) {
            for (int at = 1; at < IndexDimensions.w; at++) {
                bits += population(candidateWord(at, parent, minimum, maximum));
            }
        } else {
            bits = candidateWord(word, parent, minimum, maximum);
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
