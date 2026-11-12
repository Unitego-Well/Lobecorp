// 与HordeGpuRenderer.motionValues一致，末字为近处分割边缘标记。
#moj_import <lobecorp:horde_lod_priority.glsl>
const int HORDE_MOTION_VALUES = 9;
const float HORDE_HEADING_STEP = 0.125;
const int HORDE_ROUTE_WORDS = 6;

// 16个vec4，与HordeGpuRenderer.PARAMETER_BYTES一致。
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
    vec4 SplitInfo;
    vec4 SplitShape;
    vec4 DebugInfo;
    vec4 FrameLod;
    ivec4 AvoidanceInfo; // 当前批首行相对状态窗、窗首行低/高32位、活动行数。
};

uniform sampler2D Sampler2;
uniform sampler2D HordeRoute;
uniform sampler2D HordeHeights;
uniform sampler2D HordeAnimation;
uniform sampler2D HordeTerrain;
uniform sampler2D HordeLight;
uniform sampler2D HordeLightIndex;
uniform sampler2D HordeSplit;
uniform sampler2D HordeMotion;
uniform sampler2D HordeAvoidance;
uniform sampler2D HordeLodSelected;
uniform sampler2D HordeLodCompact;

#moj_import <lobecorp:horde_data.glsl>

layout(std140) uniform HordeNearTerrainParameters {
    ivec4 NearTerrainInfo;
};
uniform sampler2D HordeNearTerrain;

vec2 hordeNearGround(vec2 localXZ) {
    ivec2 cell = ivec2(floor(localXZ + WidthFraction.yw)) + ivec2(WorldSection.xz) * 16;
    if (any(lessThan(cell, NearTerrainInfo.xy))
            || any(greaterThanEqual(cell, NearTerrainInfo.xy + ivec2(NearTerrainInfo.z)))) {
        return vec2(0.0, -1.0);
    }
    ivec2 uv = ((cell % NearTerrainInfo.z) + NearTerrainInfo.z) % NearTerrainInfo.z;
    int at = (uv.y * NearTerrainInfo.z + uv.x) * NearTerrainInfo.w;
    uint encoded = rawWord(HordeNearTerrain, at);
    float y = uintBitsToFloat(encoded & 0xfffffffeu);
    uint tag = (uint(cell.x) & 65535u) | ((uint(cell.y) & 65535u) << 16u);
    if (isnan(y) || rawWord(HordeNearTerrain, at + 1) != tag) {
        return vec2(0.0, -1.0);
    }
    return vec2(y - (WorldSection.y * 16.0 + WidthFraction.z), float(encoded & 1u));
}

// 高模只消费每材质128容量的密集表；-1槽在任何动画读取前退出。
#ifdef HORDE_LAYER_MAP
uniform sampler2D HordeVisibleIDs;
uniform sampler2D HordeVisibleFlags;
layout(std140) uniform HordeLayerDraw { ivec4 LayerDrawInfo; };
#endif
int hordeLodInstance(int instance) {
#ifndef HORDE_NO_DETAIL_LOD
    if (DebugInfo.z > 0.5) {
        int selected = integer(HordeLodCompact, (int(DebugInfo.z) - 1) * 128 + MotionInfo.x + instance);
#ifdef HORDE_LAYER_MAP
        if (selected >= 0 && integer(HordeVisibleFlags, selected) == 0) return -1;
#endif
        return selected;
    }
#endif
#ifdef HORDE_LAYER_MAP
    int index = integer(HordeVisibleIDs, LayerDrawInfo.x + instance);
#else
    int index = MotionInfo.x + instance;
#endif
#ifndef HORDE_NO_DETAIL_LOD
    if (DebugInfo.w > 0.5 && integer(HordeLodSelected, index) != 0) return -1;
#endif
    return index;
}

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

vec2 hordeSafeNormalize(vec2 value, vec2 fallback) {
    float squared = dot(value, value);
    return squared > 0.000001 ? value * inversesqrt(squared) : fallback;
}

// 最终 XZ 与朝向采样共用，不读取任何 Y/height 样本。
float motionProjectXZ(float center, float side, float lateral, float flow, float total) {
    return center + side * (lateral + flow + total);
}

float motionDifference(float before, float after) {
    return (after - before) / (2.0 * HORDE_HEADING_STEP);
}

vec2 motionXZ(float station, float lateral, float total) {
    float s = clamp(station, 0.0, SpacingLength.w);
    int count = int(LightRoute.y);
    int node = int(scalar(HordeRoute, count * HORDE_ROUTE_WORDS + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * HORDE_ROUTE_WORDS + 3) <= s) {
        node++;
    }
    int a = node * HORDE_ROUTE_WORDS;
    int b = a + HORDE_ROUTE_WORDS;
    float first = scalar(HordeRoute, a + 3);
    float last = scalar(HordeRoute, b + 3);
    float weight = clamp((s - first) / max(last - first, 0.0001), 0.0, 1.0);
    vec2 forward = mix(vec2(scalar(HordeRoute, a + 4), scalar(HordeRoute, a + 5)),
            vec2(scalar(HordeRoute, b + 4), scalar(HordeRoute, b + 5)), weight);
    forward = length(forward) > 0.0001 ? normalize(forward) : vec2(0.0, 1.0);
    vec2 side = vec2(-forward.y, forward.x);
    vec2 center = mix(vec2(scalar(HordeRoute, a), scalar(HordeRoute, a + 2)),
            vec2(scalar(HordeRoute, b), scalar(HordeRoute, b + 2)), weight);
    float flow = mix(terrainAt(node, lateral, 2), terrainAt(node + 1, lateral, 2), weight);
    return vec2(motionProjectXZ(center.x, side.x, lateral, flow, total),
            motionProjectXZ(center.y, side.y, lateral, flow, total));
}

// 纯路线锚点：只由线路、地形流场和个体摆动决定，不含任何逐单位状态。
vec3 location(float station, float lateral) {
    float s = clamp(station, 0.0, SpacingLength.w);
    int count = int(LightRoute.y);
    int node = int(scalar(HordeRoute, count * HORDE_ROUTE_WORDS + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * HORDE_ROUTE_WORDS + 3) <= s) {
        node++;
    }
    int a = node * HORDE_ROUTE_WORDS;
    int b = a + HORDE_ROUTE_WORDS;
    float first = scalar(HordeRoute, a + 3);
    float last = scalar(HordeRoute, b + 3);
    float weight = clamp((s - first) / max(last - first, 0.0001), 0.0, 1.0);
    vec2 forward = mix(vec2(scalar(HordeRoute, a + 4), scalar(HordeRoute, a + 5)),
        vec2(scalar(HordeRoute, b + 4), scalar(HordeRoute, b + 5)), weight);
    forward = length(forward) > 0.0001 ? normalize(forward) : vec2(0.0, 1.0);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 center = mix(vector3(HordeRoute, a), vector3(HordeRoute, b), weight);
    float terrainFlow = mix(terrainAt(node, lateral, 2), terrainAt(node + 1, lateral, 2), weight);
    float adjusted = lateral + terrainFlow;
    vec2 point = center.xz + side * adjusted;
    float groundY = mix(heightAt(node, adjusted), heightAt(node + 1, adjusted), weight);
    return vec3(point.x, groundY, point.y);
}

vec2 routeForward(float station) {
    int count = int(LightRoute.y);
    float s = clamp(station, 0.0, SpacingLength.w);
    int node = int(scalar(HordeRoute, count * HORDE_ROUTE_WORDS + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * HORDE_ROUTE_WORDS + 3) <= s) node++;
    int a = node * HORDE_ROUTE_WORDS, b = a + HORDE_ROUTE_WORDS;
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
// 固定行列身份决定个体尺寸；普通、Iris和卡片共用，不能使用帧号。
float hordeInstanceScale(int localInstance) {
    int ordinal = localInstance + int(RowColumns.w);
    int row = ordinal / int(RowColumns.z);
    int column = int(RowColumns.y) + ordinal % int(RowColumns.z);
    uint identity = uint(mod(WorldSection.w + float(row), 1024.0)) * 1664525u
        + uint(column) * 1013904223u + 37u;
    identity ^= identity >> 16u;
    identity *= 2246822519u;
    identity ^= identity >> 13u;
    return mix(1.0, 1.2, float(identity & 0x00ffffffu) / 16777215.0);
}

float motionDrift(float time, float seed) {
    float a = motionRandom(seed, 1.0);
    float b = motionRandom(seed, 2.0);
    float phase = a * 6.2831853;
    return (1.2 * sin(time * mix(0.04, 0.07, b) + phase)
    + 0.6 * sin(time * mix(0.015, 0.03, b) + b * 6.2831853))
    * min(1.0, CameraSpeed.w / 0.25);
}

// 最大摆幅1.5格，与HordeSamplingUtil的采样覆盖一致。
float motionSway(float time, float seed) {
    float a = motionRandom(seed, 1.0);
    float b = motionRandom(seed, 2.0);
    float c = motionRandom(seed, 3.0);
    float phase = a * 6.2831853;
    return (0.9 * sin(time * mix(0.036, 0.09, c) + phase)
    + 0.6 * sin(time * mix(0.018, 0.048, a) + b * 6.2831853)) * mix(0.5, 1.0, b);
}

// 与 motionDrift 相同频率、幅度和低速缩放，单位为格/tick。
float motionDriftSpeedBound(float seed) {
    float b = motionRandom(seed, 2.0);
    return (1.2 * mix(0.04, 0.07, b) + 0.6 * mix(0.015, 0.03, b))
    * min(1.0, CameraSpeed.w / 0.25);
}

float motionDriftVelocity(float time, float seed) {
    float a = motionRandom(seed, 1.0);
    float b = motionRandom(seed, 2.0);
    float first = mix(0.04, 0.07, b);
    float second = mix(0.015, 0.03, b);
    return (1.2 * first * cos(time * first + a * 6.2831853)
    + 0.6 * second * cos(time * second + b * 6.2831853))
    * min(1.0, CameraSpeed.w / 0.25);
}

float motionSwayVelocity(float time, float seed) {
    float a = motionRandom(seed, 1.0);
    float b = motionRandom(seed, 2.0);
    float c = motionRandom(seed, 3.0);
    float first = mix(0.036, 0.09, c);
    float second = mix(0.018, 0.048, a);
    return (0.9 * first * cos(time * first + a * 6.2831853)
    + 0.6 * second * cos(time * second + b * 6.2831853)) * mix(0.5, 1.0, b);
}

// 小时间步只在 heading 分量调用；同一当前 terrain 场下的空间导数近似。
vec2 motionVelocityXZ(float station, float lateral, float seed, float offset, float totalVelocity) {
    float actualStation = station + motionDrift(OriginTime.w, seed);
    float total = offset + motionSway(OriginTime.w, seed);
    float forwardVelocity = CameraSpeed.w + motionDriftVelocity(OriginTime.w, seed);
    vec2 before = motionXZ(actualStation - forwardVelocity * HORDE_HEADING_STEP,
            lateral, total - totalVelocity * HORDE_HEADING_STEP);
    vec2 after = motionXZ(actualStation + forwardVelocity * HORDE_HEADING_STEP,
            lateral, total + totalVelocity * HORDE_HEADING_STEP);
    return vec2(motionDifference(before.x, after.x), motionDifference(before.y, after.y));
}

vec3 movingLocation(float station, float lateral, float time, float seed) {
    return location(station + motionDrift(time, seed), lateral + motionSway(time, seed));
}

// 指定横向偏移处的实际地面高度；只在位置分量上调用，让步后脚底仍贴合地形。
float motionGroundHeight(float station, float lateral, float lateralDelta, float seed) {
    float s = clamp(station + motionDrift(OriginTime.w, seed), 0.0, SpacingLength.w);
    int count = int(LightRoute.y);
    int node = int(scalar(HordeRoute, count * HORDE_ROUTE_WORDS + int(floor(s))));
    while (node + 2 < count && scalar(HordeRoute, (node + 1) * HORDE_ROUTE_WORDS + 3) <= s) node++;
    int a = node * HORDE_ROUTE_WORDS, b = a + HORDE_ROUTE_WORDS;
    float first = scalar(HordeRoute, a + 3), last = scalar(HordeRoute, b + 3);
    float weight = clamp((s - first) / max(last - first, 0.0001), 0.0, 1.0);
    float base = lateral;
    float flow = mix(terrainAt(node, base, 2), terrainAt(node + 1, base, 2), weight);
    float adjusted = base + flow + lateralDelta;
    float coarse = mix(heightAt(node, adjusted), heightAt(node + 1, adjusted), weight);
    vec2 finalXZ = motionXZ(s, lateral, lateralDelta);
    vec2 near = hordeNearGround(finalXZ);
    return near.y >= 0.0 ? near.x : coarse;
}

// 光照区段缺失时返回负值，由调用方回退到事件级亮度而不是隐藏单位。
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

#moj_import <lobecorp:horde_split.glsl>
