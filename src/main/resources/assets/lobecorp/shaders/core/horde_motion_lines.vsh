#version 330
#moj_import <lobecorp:horde_motion_words.glsl>
layout(std140) uniform HordeLineOutput { ivec4 LineOutput; };
flat out uvec4 RawMotion0;
flat out uvec4 RawMotion1;
flat out uint RawMotion2;
void main() {
    int instance = gl_VertexID / 2;
    motionWords(instance - MotionInfo.x, RawMotion0, RawMotion1, RawMotion2);
    int pixel = instance * HORDE_MOTION_VALUES;
    vec2 point = vec2(float(pixel % LineOutput.x + (gl_VertexID % 2) * HORDE_MOTION_VALUES), float(pixel / LineOutput.x) + 0.5);
    gl_Position = vec4(point / vec2(LineOutput.xy) * 2.0 - 1.0, 0.0, 1.0);
}
