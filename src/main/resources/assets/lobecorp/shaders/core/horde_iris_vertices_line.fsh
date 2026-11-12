#version 330
#moj_import <lobecorp:horde_motion.glsl>
flat in uvec4 RawWords0;
flat in uvec4 RawWords1;
flat in uvec4 RawWords2;
flat in uvec4 RawWords3;
out vec4 fragColor;
void main() {
    int word = int(gl_FragCoord.x) % MotionInfo.w;
    uvec4 groups[4] = uvec4[4](RawWords0, RawWords1, RawWords2, RawWords3);
    uint bits = groups[word / 4][word % 4];
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
