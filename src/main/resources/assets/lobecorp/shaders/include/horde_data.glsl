uint rawWord(sampler2D data, int index) {
    ivec2 size = textureSize(data, 0);
    uvec4 bytes = uvec4(round(texelFetch(data, ivec2(index % size.x, index / size.x), 0) * 255.0));
    return bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
}

float scalar(sampler2D data, int index) {
    return uintBitsToFloat(rawWord(data, index));
}

// 与HordeGpuPacking一致：有符号16位相对高度，单位1/8格。
float packedTerrainHeight(sampler2D data, int index) {
    uint word = rawWord(data, index / 2);
    int height = (index & 1) == 0 ? int(word << 16u) >> 16 : int(word) >> 16;
    return float(height) / 8.0;
}

int integer(sampler2D data, int index) {
    return int(rawWord(data, index));
}

vec3 vector3(sampler2D data, int index) {
    return vec3(scalar(data, index), scalar(data, index + 1), scalar(data, index + 2));
}

vec4 vector4(sampler2D data, int index) {
    return vec4(vector3(data, index), scalar(data, index + 3));
}
