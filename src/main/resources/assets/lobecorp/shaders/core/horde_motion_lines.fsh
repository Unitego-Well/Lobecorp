#version 330
layout(std140) uniform HordeLineOutput { ivec4 LineOutput; };
flat in uvec4 RawMotion0;
flat in uvec4 RawMotion1;
flat in uint RawMotion2;
out vec4 fragColor;
void main() {
    int word = (int(gl_FragCoord.y) * LineOutput.x + int(gl_FragCoord.x)) % 9;
    uint bits = word < 4 ? RawMotion0[word] : word < 8 ? RawMotion1[word - 4] : RawMotion2;
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u, (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
