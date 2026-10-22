#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

layout(std140) uniform HordeParameters {
    vec4 OriginTime;
    vec4 CameraSpeed;
    vec4 SpacingLength;
    vec4 RowColumns;
    vec4 SegmentRegions;
    vec4 AnimationInfo;
    vec4 LightRoute;
    vec4 WorldSection;
    vec4 WidthFraction;
    vec4 ProbeInfo;
};

uniform sampler2D Sampler2;
uniform sampler2D HordeRoute;
uniform sampler2D HordeAnimation;
uniform sampler2D HordeRegions;
uniform sampler2D HordeLight;
uniform sampler2D HordeLightIndex;

out vec2 texCoord;
out vec4 lighting;
out float sphericalDistance;
out float cylindricalDistance;
flat out float glowing;

float scalar(sampler2D data, int index) {
    ivec2 size = textureSize(data, 0);
    uvec4 bytes = uvec4(round(texelFetch(data, ivec2(index % size.x, index / size.x), 0) * 255.0));
    return uintBitsToFloat(bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u));
}

vec3 vector3(sampler2D data, int index) {
    return vec3(scalar(data, index), scalar(data, index + 1), scalar(data, index + 2));
}

vec4 vector4(sampler2D data, int index) {
    return vec4(vector3(data, index), scalar(data, index + 3));
}

float heightAt(int node, float lateral) {
    float column = clamp(lateral / WidthFraction.x + 0.5, 0.0, 1.0) * 4.0;
    int low = min(3, int(column));
    return mix(scalar(HordeRoute, node * 16 + 8 + low),
        scalar(HordeRoute, node * 16 + 9 + low), column - float(low));
}

vec3 location(float station, float lateral, float time) {
    float s = clamp(station, 0.0, SpacingLength.w);
    int count = int(LightRoute.y);
    int node = int(scalar(HordeRoute, count * 16 + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * 16 + 3) <= s) {
        node++;
    }
    int a = node * 16;
    int b = a + 16;
    float first = scalar(HordeRoute, a + 3);
    float last = scalar(HordeRoute, b + 3);
    float weight = clamp((s - first) / max(last - first, 0.0001), 0.0, 1.0);
    vec2 forward = mix(vec2(scalar(HordeRoute, a + 4), scalar(HordeRoute, a + 5)),
        vec2(scalar(HordeRoute, b + 4), scalar(HordeRoute, b + 5)), weight);
    forward = length(forward) > 0.0001 ? normalize(forward) : vec2(0.0, 1.0);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 center = mix(vector3(HordeRoute, a), vector3(HordeRoute, b), weight);
    vec2 point = center.xz + side * lateral;
    float signSide = lateral != 0.0 ? sign(lateral) : (int((SpacingLength.z - 1.0) * 0.5) % 2 == 0 ? 1.0 : -1.0);
    float displacement = 0.0;
    for (int i = 0; i < int(SegmentRegions.w); i++) {
        int region = int(scalar(HordeRegions, int(SegmentRegions.z) + i)) * 12;
        float blend = smoothstep(0.0, LightRoute.z, max(0.0, time - scalar(HordeRegions, region + 3)));
        vec2 origin = mix(vector3(HordeRegions, region).xz, vector3(HordeRegions, region + 4).xz, blend);
        vec2 radii = mix(vec2(scalar(HordeRegions, region + 8), scalar(HordeRegions, region + 9)),
            vec2(scalar(HordeRegions, region + 10), scalar(HordeRegions, region + 11)), blend);
        if (min(radii.x, radii.y) <= 0.0001) {
            continue;
        }
        vec2 relative = (point - origin) / radii;
        float amount = 1.0 - dot(relative, relative) * 0.25;
        displacement += smoothstep(0.0, 1.0, amount) * min(max(radii.x, radii.y), LightRoute.w);
    }
    float adjusted = lateral + signSide * min(displacement, LightRoute.w);
    point = center.xz + side * adjusted;
    float y = mix(heightAt(node, adjusted), heightAt(node + 1, adjusted), weight);
    return vec3(point.x, y, point.y);
}

vec3 rotateQuaternion(vec4 q, vec3 value) {
    return value + 2.0 * cross(q.xyz, cross(q.xyz, value) + q.w * value);
}

void animate(int bone, float frame, inout vec3 position, inout vec3 normal) {
    int bones = int(AnimationInfo.x);
    int frames = int(AnimationInfo.y);
    int first = int(floor(frame)) % frames;
    int next = (first + 1) % frames;
    float weight = fract(frame);
    for (int depth = 0; depth < 64 && bone >= 0; depth++) {
        int a = (first * bones + bone) * 12;
        int b = (next * bones + bone) * 12;
        vec3 translation = mix(vector3(HordeAnimation, a), vector3(HordeAnimation, b), weight);
        vec4 qa = vector4(HordeAnimation, a + 4);
        vec4 qb = vector4(HordeAnimation, b + 4);
        if (dot(qa, qb) < 0.0) {
            qb = -qb;
        }
        vec4 q = normalize(mix(qa, qb, weight));
        vec3 scale = mix(vector3(HordeAnimation, a + 8), vector3(HordeAnimation, b + 8), weight);
        position = rotateQuaternion(q, position * scale) + translation;
        vec3 inverseScale = vec3(
            abs(scale.x) > 0.0001 ? 1.0 / scale.x : 0.0,
            abs(scale.y) > 0.0001 ? 1.0 / scale.y : 0.0,
            abs(scale.z) > 0.0001 ? 1.0 / scale.z : 0.0);
        normal = rotateQuaternion(q, normal * inverseScale);
        bone = int(scalar(HordeAnimation, a + 3));
    }
}

vec2 lightAt(vec3 position) {
    vec3 local = floor(position + WidthFraction.yzw + vec3(0.0, ProbeInfo.x, 0.0));
    ivec3 section = ivec3(floor(local / 16.0)) + ivec3(WorldSection.xyz);
    ivec3 block = ivec3(mod(local, 16.0));
    uint mask = uint(LightRoute.x);
    uint hash = (uint(section.x) * 73856093u ^ uint(section.y) * 19349663u ^ uint(section.z) * 83492791u) & mask;
    for (uint probe = 0u; probe <= mask; probe++) {
        int index = int(hash) * 4;
        int slot = int(scalar(HordeLightIndex, index + 3)) - 1;
        if (slot < 0) {
            return vec2(-1.0);
        }
        if (ivec3(vector3(HordeLightIndex, index)) == section) {
            int ordinal = block.y * 256 + block.z * 16 + block.x;
            int pixel = ordinal / 4;
            ivec2 uv = ivec2((slot % 4) * 64 + pixel % 64, (slot / 4) * 16 + pixel / 64);
            uvec4 lightBytes = uvec4(round(texelFetch(HordeLight, uv, 0) * 255.0));
            uint value = lightBytes[ordinal % 4];
            return vec2(float(value & 15u), float(value >> 4u));
        }
        hash = (hash + 1u) & mask;
    }
    return vec2(0.0);
}

void main() {
    int ordinal = gl_InstanceID + int(RowColumns.w);
    int row = ordinal / int(RowColumns.z);
    int column = int(RowColumns.y) + ordinal % int(RowColumns.z);
    float station = RowColumns.x - float(row) * SpacingLength.x;
    if (station < SegmentRegions.x || station >= SegmentRegions.y || station < 0.0 || station >= SpacingLength.w) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        texCoord = UV0;
        lighting = vec4(0.0);
        sphericalDistance = 0.0;
        cylindricalDistance = 0.0;
        glowing = 0.0;
        return;
    }
    float lateral = (float(column) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
    vec3 point = location(station, lateral, OriginTime.w);
    vec3 next = location(station + CameraSpeed.w * 0.25, lateral, OriginTime.w + 0.25);
    vec2 heading = next.xz - point.xz;
    if (length(heading) < 0.0001) {
        heading = point.xz - location(station - CameraSpeed.w * 0.25, lateral, OriginTime.w - 0.25).xz;
    }
    heading = length(heading) > 0.0001 ? normalize(heading) : vec2(0.0, 1.0);
    mat2 yaw = mat2(-heading.y, heading.x, -heading.x, -heading.y);
    vec3 position = Position;
    vec3 normal = Normal;
    int bone = int(round(Color.r * 255.0)) + int(round(Color.g * 255.0)) * 256 + int(round(Color.b * 255.0)) * 65536;
    float phase = fract((WorldSection.w + float(row)) * 0.375 + float(column) * 0.625);
    float frame = fract(OriginTime.w / (20.0 * AnimationInfo.z) + phase) * AnimationInfo.y;
    animate(bone, frame, position, normal);
    position.xz = yaw * position.xz;
    normal.xz = yaw * normal.xz;
    normal = length(normal) > 0.0001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    vec3 relative = position + point + OriginTime.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
    sphericalDistance = length(relative);
    cylindricalDistance = max(length(relative.xz), abs(relative.y));
    vec2 light = lightAt(point);
    lighting = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(1.0))
        * sample_lightmap(Sampler2, ivec2(light * 16.0));
    if (light.x < 0.0) {
        lighting.a = 0.0;
    }
    glowing = AnimationInfo.w;
    texCoord = UV0;
}
