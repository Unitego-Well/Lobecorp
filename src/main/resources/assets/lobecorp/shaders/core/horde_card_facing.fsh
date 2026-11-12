#version 330
#moj_import <lobecorp:horde_motion.glsl>
#moj_import <lobecorp:horde_cards.glsl>
out vec4 fragColor;

// 全局motion索引布局；viewport覆盖本批MotionInfo.x开始的连续四字区域。
void main() {
    int pixel = int(gl_FragCoord.y) * textureSize(HordeMotion, 0).x + int(gl_FragCoord.x);
    int instance = pixel / 4;
    if (instance < MotionInfo.x || instance >= MotionInfo.x + MotionInfo.y) discard;
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
    uint bits = floatBitsToUint(values[pixel%4]);
    fragColor = vec4(uvec4(bits & 255u,(bits>>8u)&255u,(bits>>16u)&255u,bits>>24u))/255.0;
}
