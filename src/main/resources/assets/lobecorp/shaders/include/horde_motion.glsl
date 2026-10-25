// 与HordeGpuRenderer.MOTION_VALUES一致：位置、角度、光照、sin/cos和帧顶点基址。
const int HORDE_MOTION_VALUES = 8;

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
    ivec4 MotionInfo;
    ivec4 SpatialMinimum;
    ivec4 SpatialDimensions;
    ivec4 SpatialTextures;
    ivec4 UnitIdentity;
    vec4 UnitLimits;
};

uniform sampler2D Sampler2;
uniform sampler2D HordeRoute;
uniform sampler2D HordeHeights;
uniform sampler2D HordeAnimation;
uniform sampler2D HordeRegions;
uniform sampler2D HordeRegionState;
uniform sampler2D HordeTerrain;
uniform sampler2D HordeLight;
uniform sampler2D HordeLightIndex;

uniform sampler2D HordeMotion;
uniform sampler2D HordeUnitCurrent;
uniform sampler2D HordeUnitPrevious;
uniform sampler2D HordeUnitIndex;

#moj_import <lobecorp:horde_data.glsl>
#moj_import <lobecorp:horde_flow.glsl>
#moj_import <lobecorp:horde_spatial.glsl>

float terrainAt(int node, float lateral, int component) {
    float column = clamp(lateral / WidthFraction.x + 0.5, 0.0, 1.0) * (ProbeInfo.y - 1.0);
    int count = int(ProbeInfo.y);
    int low = min(count - 2, int(column));
    return mix(scalar(HordeTerrain, (node * count + low) * 4 + component),
            scalar(HordeTerrain, (node * count + low + 1) * 4 + component), column - float(low));
}

float heightAt(int node, float lateral) {
    return terrainAt(node, lateral, 0);
}

float regionPush(int slot, vec3 point, vec2 forward, vec2 side, float time, float preferredSide) {
    int region = slot * 13;
    float prediction = (time - OriginTime.w) / 20.0;
    vec3 origin = vector3(HordeRegionState, region) + vector3(HordeRegionState, region + 6) * prediction;
    vec3 halfSize = max(vec3(0.0), vector3(HordeRegionState, region + 3)
    + vector3(HordeRegionState, region + 9) * prediction);
    if (min(halfSize.x, halfSize.z) <= 0.0001) {
        return 0.0;
    }
    float separation = max(origin.y - halfSize.y - (point.y + LightRoute.w), point.y - (origin.y + halfSize.y));
    float vertical = 1.0 - smoothstep(0.0, 0.5, separation);
    vec2 delta = point.xz - origin.xz;
    halfSize.xz += vec2(intBitsToFloat(SpatialTextures.w));
    return vertical * hordeGapPush(dot(delta, forward), dot(delta, side),
            dot(abs(side), halfSize.xz), dot(abs(forward), halfSize.xz), preferredSide);
}

vec3 location(float station, float lateral, float time, float preferredSide) {
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
    float terrainFlow = mix(terrainAt(node, lateral, 2), terrainAt(node + 1, lateral, 2), weight);
    point += side * terrainFlow;
    float groundY = mix(heightAt(node, lateral + terrainFlow), heightAt(node + 1, lateral + terrainFlow), weight);
    ivec2 cell = spatialCell(vec3(point.x, groundY, point.y));
    float combined = 0.0;
    int regions = int(SegmentRegions.w);
    int words = (regions + 31) / 32;
    for (int word = 0; word < words; word++) {
        uint possible = spatialWord(cell, word + 1);
        if (possible == 0u) continue;
        for (int bit = 0; bit < 32 && word * 32 + bit < regions; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            float push = regionPush(regionSlot(word * 32 + bit),
                    vec3(point.x, groundY, point.y), forward, side, time, preferredSide);
            combined = hordeMergePush(combined, push);
        }
    }
    float displacement = terrainFlow + preferredSide * combined;
    float adjusted = lateral + clamp(displacement, -ProbeInfo.w, ProbeInfo.w);
    point = center.xz + side * adjusted;
    float y = mix(heightAt(node, adjusted), heightAt(node + 1, adjusted), weight);
    vec3 constrained = regionClamp(vec3(point.x, y, point.y), side * preferredSide);
    float constrainedLateral = dot(constrained.xz - center.xz, side);
    constrained.y = mix(heightAt(node, constrainedLateral), heightAt(node + 1, constrainedLateral), weight);
    return constrained;
}


vec2 routeForward(float station) {
    int count = int(LightRoute.y);
    float s = clamp(station, 0.0, SpacingLength.w);
    int node = int(scalar(HordeRoute, count * 16 + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * 16 + 3) <= s) node++;
    int a = node * 16, b = a + 16;
    float first = scalar(HordeRoute, a + 3), last = scalar(HordeRoute, b + 3);
    float weight = clamp((s - first) / max(last - first, 0.0001), 0.0, 1.0);
    vec2 forward = mix(vec2(scalar(HordeRoute, a + 4), scalar(HordeRoute, a + 5)),
            vec2(scalar(HordeRoute, b + 4), scalar(HordeRoute, b + 5)), weight);
    return length(forward) > 0.0001 ? normalize(forward) : vec2(0.0, 1.0);
}

float motionRandom(float seed, float salt) {
    return fract(sin(seed * 12.9898 + salt * 78.233) * 43758.5453);
}

// 稳定个体种子派生不同频率和幅度；基础速度不变，连续加减速且不反向。
vec3 movingLocation(float station, float lateral, float time, float seed) {
    float a = motionRandom(seed, 1.0);
    float b = motionRandom(seed, 2.0);
    float c = motionRandom(seed, 3.0);
    float phase = a * 6.2831853;
    float drift = (1.2 * sin(time * mix(0.04, 0.07, b) + phase)
    + 0.6 * sin(time * mix(0.015, 0.03, c) + c * 6.2831853))
    * min(1.0, CameraSpeed.w / 0.25);
    // 最大摆幅1.5格，与HordeSamplingUtil的采样覆盖一致。
    float sway = (0.9 * sin(time * mix(0.012, 0.03, c) + phase)
    + 0.6 * sin(time * mix(0.006, 0.016, a) + b * 6.2831853)) * mix(0.5, 1.0, b);
    return location(station + drift, lateral + sway, time, a < 0.5 ? -1.0 : 1.0);
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

#moj_import <lobecorp:horde_unit.glsl>
