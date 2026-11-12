#version 330
#moj_import <lobecorp:horde_avoidance_solve.glsl>
out vec4 fragColor;
void main() {
    int pixel = int(gl_FragCoord.y) * int(AvoidanceClock.z) + int(gl_FragCoord.x);
    int unit = pixel / 4;
    int row = unit / PreviousWindow.z;
    if (row >= PreviousWindow.w) { fragColor = vec4(0); return; }
    if (pixel % 4 == 3) {
        fragColor = avoidanceEncode(uint(AvoidanceInfo.y) + uint(row));
        return;
    }
    uvec4 words = avoidanceSolve(unit);
    fragColor = avoidanceEncode(words[pixel % 4]);
}
