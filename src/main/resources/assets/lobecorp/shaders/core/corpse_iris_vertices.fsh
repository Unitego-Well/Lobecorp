#version 330

#moj_import <lobecorp:corpse_data.glsl>
uniform sampler2D CorpseFrames;
uniform sampler2D CorpseUvs;
out vec4 fragColor;

uint packDirection(vec4 value) {
    uvec4 bytes = uvec4(ivec4(round(clamp(value, -1.0, 1.0) * 127.0))) & uvec4(255u);
    return bytes.x | bytes.y << 8u | bytes.z << 16u | bytes.w << 24u;
}

void main() {
    int pixel = int(gl_FragCoord.y) * CorpseInfo.w + int(gl_FragCoord.x);
    int ordinal = pixel / CorpseFormat.y;
    if (ordinal >= CorpseFormat.x * CorpseInfo.z) discard;
    int vertex = ordinal % CorpseFormat.x;
    int instance = CorpseInfo.x + ordinal / CorpseFormat.x;
    int word = pixel % CorpseFormat.y;
    int at = vertex * 10;
    uint bits;
    if (word < 3) {
        vec3 point = corpsePosition(vector3(CorpseFrames, at), instance);
        bits = floatBitsToUint(point[word]);
    } else if (word == 3) {
        uvec4 tint = uvec4(round(clamp(corpseTint(instance), 0.0, 1.0) * 255.0));
        bits = tint.x | tint.y << 8u | tint.z << 16u | tint.w << 24u;
    } else if (word == 4 || word == 5) {
        bits = floatBitsToUint(scalar(CorpseUvs, vertex * 4 + word - 4));
    } else if (word == 6) {
        bits = 10u << 16u;
    } else if (word == 7) {
        bits = rawWord(CorpseInstances, instance * 8 + 4);
    } else if (word == 8) {
        bits = packDirection(vec4(normalize(corpseDirection(vector3(CorpseFrames, at + 3), instance)), 0.0));
    } else if (word == 9) {
        bits = uint(CorpseFormat.z) & 65535u;
    } else if (word == 10) {
        bits = 0u;
    } else if (word == 11 || word == 12) {
        bits = floatBitsToUint(scalar(CorpseUvs, vertex * 4 + word - 9));
    } else {
        vec3 tangent = normalize(corpseDirection(vector3(CorpseFrames, at + 6), instance));
        bits = packDirection(vec4(tangent, scalar(CorpseFrames, at + 9)));
    }
    fragColor = vec4(uvec4(bits & 255u, bits >> 8u & 255u, bits >> 16u & 255u, bits >> 24u)) / 255.0;
}
