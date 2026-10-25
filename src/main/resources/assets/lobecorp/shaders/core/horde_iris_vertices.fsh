#version 330

#moj_import <lobecorp:horde_motion.glsl>

uniform sampler2D HordeUVs;
uniform sampler2D HordeIrisAnimation;
out vec4 fragColor;

uint packedDirection(vec4 direction) {
    uvec4 bytes = uvec4(ivec4(round(clamp(direction, -1.0, 1.0) * 127.0))) & uvec4(255u);
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}

// 原版36字节或Iris56字节布局，由渲染期间实际getVertexFormat确定。
void main() {
    int words = MotionInfo.w;
    int pixel = int(gl_FragCoord.y) * int(ProbeInfo.z) + int(gl_FragCoord.x);
    int totalVertices = int(AnimationInfo.x) * MotionInfo.y;
    int ordinalVertex = pixel / words - int(SegmentRegions.z);
    if (ordinalVertex < 0 || ordinalVertex >= totalVertices) {
        discard;
    }
    int vertex = ordinalVertex % int(AnimationInfo.x);
    int instance = ordinalVertex / int(AnimationInfo.x);
    int word = pixel % words;
    int cached = (MotionInfo.x + instance) * HORDE_MOTION_VALUES;
    uint bits;
    if (word == 4 || word == 5) {
        bits = floatBitsToUint(scalar(HordeUVs, vertex * 4 + word - 4));
    } else if (word == 6) {
        bits = 10u << 16u;
    } else if (word == 7) {
        float light = scalar(HordeMotion, cached + 4);
        bits = light < 0.0 ? 0u : (uint(mod(light, 16.0)) << 4u) | (uint(floor(light / 16.0)) << 20u);
    } else if (word == 9 || word == 10) {
        // 虚拟单位没有真实Entity，使用中性实体/方块实体/物品标识。
        bits = 0u;
    } else if (word == 11 || word == 12) {
        bits = floatBitsToUint(scalar(HordeUVs, vertex * 4 + word - 9));
    } else {
        if (word == 3) {
            bits = scalar(HordeMotion, cached + 4) >= 0.0 ? 0xffffffffu : 0u;
        } else {
            int offset = (int(scalar(HordeMotion, cached + 7)) + vertex) * 10;
            vec2 heading = vec2(scalar(HordeMotion, cached + 5), scalar(HordeMotion, cached + 6));
            mat2 yaw = mat2(-heading.y, heading.x, -heading.x, -heading.y);
            if (word < 3) {
                float position;
                if (word == 1) {
                    position = scalar(HordeIrisAnimation, offset + 1);
                } else {
                    vec2 rotated = yaw * vec2(scalar(HordeIrisAnimation, offset), scalar(HordeIrisAnimation, offset + 2));
                    position = word == 0 ? rotated.x : rotated.y;
                }
                bits = floatBitsToUint(position + scalar(HordeMotion, cached + word) + OriginTime[word]);
            } else {
                vec3 normal = vector3(HordeIrisAnimation, offset + 3);
                normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
                if (word == 8) {
                    normal.xz = yaw * normal.xz;
                    bits = packedDirection(vec4(normal, 0.0));
                } else {
                    vec3 tangent = vector3(HordeIrisAnimation, offset + 6);
                    float handedness = scalar(HordeIrisAnimation, offset + 9);
                    tangent.xz = yaw * tangent.xz;
                    bits = packedDirection(vec4(normalize(tangent), handedness));
                }
            }
        }
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
