#version 330

#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_cards.glsl>

uniform sampler2D HordeUVs;
uniform sampler2D HordeIrisAnimation;
out vec4 fragColor;

uint packedDirection(vec4 direction) {
    uvec4 bytes = uvec4(ivec4(round(clamp(direction, -1.0, 1.0) * 127.0))) & uvec4(255u);
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}

void writeWord(uint bits) {
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
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

// 原版36字节或Iris56字节布局，由渲染期间实际getVertexFormat确定。
void main() {
    int words = MotionInfo.w;
    int pixel = int(gl_FragCoord.y) * int(ProbeInfo.z) + int(gl_FragCoord.x);
    int verticesPerInstance = int(FrameLod.w);
    int totalVertices = verticesPerInstance * MotionInfo.y;
    int ordinalVertex = pixel / words - int(SegmentRegions.z);
    if (ordinalVertex < 0 || ordinalVertex >= totalVertices) {
        discard;
    }
    int vertex = ordinalVertex % verticesPerInstance;
    int instance = ordinalVertex / verticesPerInstance;
    int word = pixel % words;
    int lodInstance = hordeLodInstance(instance);
    if (lodInstance < 0) {
        fragColor = vec4(0.0);
        return;
    }
    int cached = lodInstance * HORDE_MOTION_VALUES;
    // 常量和光照字不依赖几何变换，也不需要读取面对缓存。
    if (word == 3) {
        writeWord(scalar(HordeMotion, cached + 4) >= 0.0 ? 0xffffffffu : 0u);
        return;
    }
    if (word == 6 || word == 9 || word == 10) {
        writeWord(word == 6 ? 10u << 16u : 0u);
        return;
    }
    if (word == 7) {
        float light = scalar(HordeMotion, cached + 4);
        writeWord(light < 0.0 ? 0u : (uint(mod(light, 16.0)) << 4u) | (uint(floor(light / 16.0)) << 20u));
        return;
    }
    bool uv = word == 4 || word == 5 || word == 11 || word == 12;
    bool cachedCard = LightRoute.z > 0.5 && FrameLod.z > 1.5;
    if (uv && (FrameLod.z <= 0.5 || cachedCard)) {
        if (cachedCard) {
            vertex = int(scalar(HordeCardFacing, lodInstance * 4 + 3)) * 4 + vertex % 4;
        }
        writeWord(uvWord(word, vertex, cached));
        return;
    }
    float instanceScale = scalar(HordeMotion, cached + 3);
    vec2 heading = vec2(scalar(HordeMotion, cached + 5), scalar(HordeMotion, cached + 6));
    mat2 yaw = mat2(-heading.y, heading.x, -heading.x, -heading.y);
    vec3 point = vector3(HordeMotion, cached);
    mat3 rotation = hordeYawRotation(yaw);
    if (FrameLod.z > 0.5) {
        int frameStart = LightRoute.z > 0.5 ? 0 : int(scalar(HordeMotion, cached + 7)) * int(AnimationInfo.x);
        vec3 center = (vector3(HordeIrisAnimation, frameStart * 10)
                + vector3(HordeIrisAnimation, (frameStart + 2) * 10)) * 0.5;
        vertex = cachedCard
                ? hordeCachedCardVertex(vertex, lodInstance, point, yaw, center * instanceScale, rotation)
                : hordeCardVertex(vertex, point, yaw, center * instanceScale, rotation);
    }
    if (uv) {
        writeWord(uvWord(word, vertex, cached));
        return;
    }
    int geometryFrame = LightRoute.z > 0.5 && FrameLod.z > 0.5
            ? 0 : int(scalar(HordeMotion, cached + 7));
    int offset = (geometryFrame * int(AnimationInfo.x) + vertex) * 10;
    if (word < 3) {
        vec3 position = rotation * vector3(HordeIrisAnimation, offset);
        writeWord(floatBitsToUint(position[word] * instanceScale + point[word]));
    } else if (word == 8) {
        vec3 normal = vector3(HordeIrisAnimation, offset + 3);
        normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
        writeWord(packedDirection(vec4(rotation * normal, 0.0)));
    } else {
        vec3 tangent = vector3(HordeIrisAnimation, offset + 6);
        float handedness = scalar(HordeIrisAnimation, offset + 9);
        writeWord(packedDirection(vec4(normalize(rotation * tangent), handedness)));
    }
}
