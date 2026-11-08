#version 330

uniform sampler2D HordeDepthSource;
layout(std140) uniform HordeDepthParameters {
    ivec4 DepthSourceInfo; // 源mip、原始深度标志、反转深度标志、保留。
};
out vec4 fragColor;

float depthAt(ivec2 cell, ivec2 size) {
    if (any(greaterThanEqual(cell, size))) {
        return 1.0;
    }
    vec4 encoded = texelFetch(HordeDepthSource, cell, DepthSourceInfo.x);
    if (DepthSourceInfo.y != 0) {
        return DepthSourceInfo.z != 0 ? 1.0 - encoded.r : encoded.r;
    }
    uvec4 bytes = uvec4(round(encoded * 255.0));
    uint bits = bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
    return uintBitsToFloat(bits);
}

void main() {
    ivec2 first = ivec2(gl_FragCoord.xy) * 2;
    ivec2 size = textureSize(HordeDepthSource, DepthSourceInfo.x);
    float farthest = max(max(depthAt(first, size), depthAt(first + ivec2(1, 0), size)),
            max(depthAt(first + ivec2(0, 1), size), depthAt(first + ivec2(1, 1), size)));
    uint bits = floatBitsToUint(farthest);
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
