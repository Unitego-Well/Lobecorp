#version 330
#moj_import <lobecorp:horde_avoidance_solve.glsl>
flat out uvec4 rawWords;
void main() {
    int unit = gl_VertexID / 2;
    rawWords = avoidanceSolve(unit);
    int width = int(AvoidanceClock.z);
    int pixel = unit * 4;
    vec2 point = vec2(float(pixel % width + (gl_VertexID % 2) * 4), float(pixel / width) + 0.5);
    gl_Position = vec4(point / vec2(width, AvoidanceClock.w) * 2.0 - 1.0, 0.0, 1.0);
}
