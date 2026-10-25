#moj_import <lobecorp:horde_data.glsl>
uniform sampler2D CorpseInstances;
layout(std140) uniform CorpseParameters {
    ivec4 CorpseInfo;
    ivec4 CorpseFormat;
};

vec3 corpsePosition(vec3 local, int instance) {
    int at = instance * 8;
    float yaw = -scalar(CorpseInstances, at + 3);
    float scale = scalar(CorpseInstances, at + 7);
    local *= scale;
    local.xz = mat2(-cos(yaw), sin(yaw), -sin(yaw), -cos(yaw)) * local.xz;
    local.y += 0.01 * scale;
    return local + vector3(CorpseInstances, at);
}

vec3 corpseDirection(vec3 local, int instance) {
    float yaw = -scalar(CorpseInstances, instance * 8 + 3);
    local.xz = mat2(-cos(yaw), sin(yaw), -sin(yaw), -cos(yaw)) * local.xz;
    return local;
}

vec4 corpseTint(int instance) {
    int at = instance * 8;
    uint argb = rawWord(CorpseInstances, at + 5);
    vec4 tint = vec4(float((argb >> 16u) & 255u), float((argb >> 8u) & 255u),
            float(argb & 255u), float(argb >> 24u)) / 255.0;
    float range = scalar(CorpseInstances, at + 6);
    float distance = length(vector3(CorpseInstances, at));
    float fade = 1.0 - smoothstep(max(0.0, range - 8.0), range, distance);
    tint.rgb *= fade;
    tint.a *= fade;
    return tint;
}
