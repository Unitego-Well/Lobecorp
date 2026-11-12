#version 330
#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_cards.glsl>
uniform sampler2D HordeUVs;
uniform sampler2D HordeIrisAnimation;
// 使用实际输出纹理尺寸，不能使用源动画图集尺寸。
layout(std140) uniform HordeIrisOutput {
    ivec4 IrisOutputSize;
};
flat out uvec4 RawWords0;
flat out uvec4 RawWords1;
flat out uvec4 RawWords2;
flat out uvec4 RawWords3;
uint packedDirection(vec4 direction) {
    uvec4 bytes = uvec4(ivec4(round(clamp(direction, -1.0, 1.0) * 127.0))) & uvec4(255u);
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}

uint uvWord(int word, int vertex, int cached) {
    if (FrameLod.z <= 0.5) {
        return floatBitsToUint(scalar(HordeUVs, vertex * 4 + (word < 6 ? word - 4 : word - 9)));
    }
    int frame = int(scalar(HordeMotion, cached + 7));
    if (word == 11 || word == 12) {
        return floatBitsToUint(word == 11 ? (float(vertex / 4) + 0.5) / HORDE_CARD_DIRECTIONS
                : (float(frame) + 0.5) / AnimationInfo.y);
    }
    float encoded = scalar(HordeUVs, vertex * 4);
    float index = round(encoded * (HORDE_CARD_DIRECTIONS * 2.0));
    float face = floor(index * 0.5);
    float cornerU = index - face * 2.0;
    float cornerV = scalar(HordeUVs, vertex * 4 + 1);
    return floatBitsToUint(word == 4 ? (face + cornerU) / HORDE_CARD_DIRECTIONS
            : (float(frame) + cornerV) / AnimationInfo.y);
}


void main() {
    int words = MotionInfo.w;
    int globalVertex = gl_VertexID / 2;
    int ordinal = globalVertex - int(SegmentRegions.z);
    int verticesPerInstance = int(FrameLod.w);
    int start = globalVertex * words;
    int width = int(ProbeInfo.z);
    float x = float(start % width) + float((gl_VertexID % 2) * words);
    float y = float(start / width) + 0.5;
    gl_Position = vec4(vec2(x, y) / vec2(IrisOutputSize.xy) * 2.0 - 1.0, 0.0, 1.0);
    RawWords0 = uvec4(0u);
    RawWords1 = uvec4(0u);
    RawWords2 = uvec4(0u);
    RawWords3 = uvec4(0u);
    if (ordinal < 0 || ordinal >= verticesPerInstance * MotionInfo.y) return;
    int instance = hordeLodInstance(ordinal / verticesPerInstance);
    if (instance < 0) return;
    int vertex = ordinal % verticesPerInstance;
    int cached = instance * HORDE_MOTION_VALUES;
    float scale = scalar(HordeMotion, cached + 3);
    vec2 heading = vec2(scalar(HordeMotion, cached + 5), scalar(HordeMotion, cached + 6));
    mat2 yaw = mat2(-heading.y, heading.x, -heading.x, -heading.y);
    vec3 point = vector3(HordeMotion, cached);
    mat3 rotation = hordeYawRotation(yaw);
    if (FrameLod.z > 0.5) {
        int frameStart = LightRoute.z > 0.5 ? 0 : int(scalar(HordeMotion, cached + 7)) * int(AnimationInfo.x);
        vec3 center = (vector3(HordeIrisAnimation, frameStart * 10)
                + vector3(HordeIrisAnimation, (frameStart + 2) * 10)) * 0.5;
#ifdef HORDE_NO_FACING_CACHE
        vertex = hordeCardVertex(vertex, point, yaw, center * scale, rotation);
#else
        if (LightRoute.z > 0.5 && FrameLod.z > 1.5) {
            vertex = hordeCachedCardVertex(vertex, instance, point, yaw, center * scale, rotation);
        } else {
            vertex = hordeCardVertex(vertex, point, yaw, center * scale, rotation);
        }
#endif
    }
    int frame = LightRoute.z > 0.5 && FrameLod.z > 0.5 ? 0 : int(scalar(HordeMotion, cached + 7));
    int offset = (frame * int(AnimationInfo.x) + vertex) * 10;
    vec3 position = rotation * vector3(HordeIrisAnimation, offset);
    position = position * scale + point;
    float light = scalar(HordeMotion, cached + 4);
    uint packedLight = light < 0.0 ? 0u : (uint(mod(light, 16.0)) << 4u) | (uint(floor(light / 16.0)) << 20u);
    vec3 normal = vector3(HordeIrisAnimation, offset + 3);
    normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    RawWords0 = uvec4(floatBitsToUint(position), light >= 0.0 ? 0xffffffffu : 0u);
    RawWords1 = uvec4(uvWord(4, vertex, cached), uvWord(5, vertex, cached), 10u << 16u, packedLight);
    RawWords2.x = packedDirection(vec4(rotation * normal, 0.0));
    if (words == 14) {
        RawWords2.w = uvWord(11, vertex, cached);
        vec3 tangent = vector3(HordeIrisAnimation, offset + 6);
        RawWords3 = uvec4(uvWord(12, vertex, cached),
                packedDirection(vec4(normalize(rotation * tangent), scalar(HordeIrisAnimation, offset + 9))), 0u, 0u);
    }
}
