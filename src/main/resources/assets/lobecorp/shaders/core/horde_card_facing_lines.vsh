#version 330
#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_cards.glsl>
flat out uvec4 rawWords;
void main() {
    int unit = gl_VertexID / 2;
    int instance = unit;
    int cached = instance * HORDE_MOTION_VALUES;
    vec3 point = vector3(HordeMotion, cached);
    vec2 heading = vec2(scalar(HordeMotion,cached+5),scalar(HordeMotion,cached+6));
    mat2 yaw = mat2(-heading.y,heading.x,-heading.x,-heading.y);
    float scale = scalar(HordeMotion,cached+3);
    vec3 center = (vector3(HordeAnimation,0)+vector3(HordeAnimation,12))*0.5*scale;
    vec3 observer = -point - hordeYawRotation(yaw)*center;
    float horizontal = length(observer.xz);
    vec2 direction = horizontal > 0.000001 ? observer.xz/horizontal : yaw*vec2(0,1);
    vec2 localDirection = transpose(yaw)*direction;
    float face = mod(floor(atan(localDirection.x,localDirection.y)/HORDE_CARD_ANGLE_STEP+0.5),8.0);
    float slope = clamp(observer.y/max(horizontal,0.000001),-HORDE_CARD_MAX_SLOPE,HORDE_CARD_MAX_SLOPE);
    vec4 values = vec4(direction,slope*inversesqrt(1.0+slope*slope),face);
    rawWords = floatBitsToUint(values);
    int width = textureSize(HordeMotion, 0).x;
    int pixel = unit * 4;
    vec2 rasterPoint = vec2(float(pixel % width + (gl_VertexID % 2) * 4), float(pixel / width) + 0.5);
    gl_Position = vec4(rasterPoint / vec2(width, textureSize(HordeMotion, 0).y) * 2.0 - 1.0, 0.0, 1.0);
}
