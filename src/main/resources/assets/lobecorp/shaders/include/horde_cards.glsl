// 水平八向选最近图，围绕片中心朝向相机；俯仰限制为正负20度。
const float HORDE_CARD_ANGLE_STEP = 0.78539816339;
const float HORDE_CARD_DIRECTIONS = 8.0;
const float HORDE_CARD_MAX_SLOPE = 0.36397023427; // tan(20度)。

mat3 hordeYawRotation(mat2 yaw) {
    return mat3(vec3(yaw[0].x, 0.0, yaw[0].y), vec3(0.0, 1.0, 0.0),
        vec3(yaw[1].x, 0.0, yaw[1].y));
}

int hordeCardVertex(int vertex, inout vec3 point, mat2 yaw, vec3 center, out mat3 rotation) {
    vec3 originalCenter = hordeYawRotation(yaw) * center;
    vec3 observer = -point - originalCenter;
    float horizontal = length(observer.xz);
    vec2 direction = horizontal > 0.000001 ? observer.xz / horizontal : yaw * vec2(0.0, 1.0);
    vec2 localDirection = transpose(yaw) * direction;
    float angle = atan(localDirection.x, localDirection.y);
    int face = int(mod(floor(angle / HORDE_CARD_ANGLE_STEP + 0.5), HORDE_CARD_DIRECTIONS));
    float selected = float(face) * HORDE_CARD_ANGLE_STEP;
    float s = sin(selected);
    float c = cos(selected);
    // 斜率截断避免额外的atan/sin/cos俯仰计算，正上/正下也保持角度上限。
    float slope = clamp(observer.y / max(horizontal, 0.000001), -HORDE_CARD_MAX_SLOPE, HORDE_CARD_MAX_SLOPE);
    float pitchCos = inversesqrt(1.0 + slope * slope);
    float pitchSin = slope * pitchCos;
    vec3 right = vec3(direction.y, 0.0, -direction.x);
    vec3 up = vec3(-direction.x * pitchSin, pitchCos, -direction.y * pitchSin);
    vec3 forward = vec3(direction.x * pitchCos, pitchSin, direction.y * pitchCos);
    rotation = mat3(right, up, forward)
        * transpose(mat3(vec3(c, 0.0, -s), vec3(0.0, 1.0, 0.0), vec3(s, 0.0, c)));
    point += originalCenter - rotation * center;
    return face * 4 + vertex % 4;
}

// 四个RGBA8原始字：水平单位向量、俯仰正弦、face；位置始终来自当前motion。
uniform sampler2D HordeCardFacing;

int hordeCachedCardVertex(int vertex, int instance, inout vec3 point,
        mat2 yaw, vec3 center, out mat3 rotation) {
    int at = instance * 4;
    vec2 direction = vec2(scalar(HordeCardFacing, at), scalar(HordeCardFacing, at + 1));
    float pitchSin = scalar(HordeCardFacing, at + 2);
    float pitchCos = sqrt(max(0.0, 1.0 - pitchSin * pitchSin));
    int face = int(scalar(HordeCardFacing, at + 3));
    // 八向表避免在每个消费者重新计算选向三角函数。
    const vec2 selected[8] = vec2[8](vec2(0,1), vec2(0.70710678119), vec2(1,0),
        vec2(0.70710678119,-0.70710678119), vec2(0,-1), vec2(-0.70710678119),
        vec2(-1,0), vec2(-0.70710678119,0.70710678119));
    float s = selected[face].x;
    float c = selected[face].y;
    rotation = mat3(vec3(direction.y,0,-direction.x),
        vec3(-direction.x*pitchSin,pitchCos,-direction.y*pitchSin),
        vec3(direction.x*pitchCos,pitchSin,direction.y*pitchCos))
        * transpose(mat3(vec3(c,0,-s),vec3(0,1,0),vec3(s,0,c)));
    point += hordeYawRotation(yaw) * center - rotation * center;
    return face * 4 + vertex % 4;
}
