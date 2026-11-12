#version 330
flat in uvec4 rawWords;
out vec4 fragColor;
void main() {
    uint bits = rawWords[int(gl_FragCoord.x) % 4];
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u, (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
