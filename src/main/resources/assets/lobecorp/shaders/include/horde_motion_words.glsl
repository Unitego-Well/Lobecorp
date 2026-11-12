#moj_import <lobecorp:horde_motion.glsl>
uniform sampler2D HordePose;
float motionGroundAtXZ(float station, float lateral, float lateralDelta, float seed, vec2 finalXZ) {
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
    vec2 near = hordeNearGround(finalXZ);
    return near.y >= 0.0 ? near.x : coarse;
}

void motionWords(int local, out uvec4 first, out uvec4 second, out uint edgeWord) {
    int ordinal = local + int(RowColumns.w);
    int row = ordinal / int(RowColumns.z);
    int column = int(RowColumns.y) + ordinal % int(RowColumns.z);
    float station = RowColumns.x - float(row) * SpacingLength.x;
    float lateral = (float(column) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
    float seed = mod(WorldSection.w + float(row), 1024.0) * 17.0 + float(column) * 31.0;
    int stateAt = ((AvoidanceInfo.x + row) * int(SpacingLength.z) + column) * HORDE_AVOID_WORDS;
    float offset = scalar(HordeAvoidance, stateAt);
    float total = offset + motionSway(OriginTime.w, seed);
    vec2 horizontal = motionXZ(station + motionDrift(OriginTime.w, seed), lateral, total);
    vec3 point = vec3(horizontal.x, motionGroundAtXZ(station, lateral, total, seed, horizontal), horizontal.y);
    float scale = hordeInstanceScale(local);
    vec2 velocity = motionVelocityXZ(station, lateral, seed, offset, scalar(HordeAvoidance, stateAt + 1));
    float squared = dot(velocity, velocity);
    vec2 heading;
    if (squared > 0.000001) {
        heading = velocity * inversesqrt(squared);
    } else {
        heading = routeForward(station + motionDrift(OriginTime.w, seed));
    }
    vec2 light = lightAt(point);
    float packedLight = light.x < 0.0 ? DebugInfo.x + DebugInfo.y * 16.0 : light.x + light.y * 16.0;
    if (station < SegmentRegions.x || station >= SegmentRegions.y || station < 0.0 || station >= SpacingLength.w) {
        packedLight = -1.0;
    }
    float frame;
    if (FrameLod.y < 0.5) {
        int identityRow = int(mod(WorldSection.w + float(row), 1024.0));
        frame = float(integer(HordePose, identityRow * int(SpacingLength.z) + column));
    } else {
        int frames = int(AnimationInfo.y);
        int raw = int(floor(fract(OriginTime.w / (20.0 * AnimationInfo.z) + motionRandom(seed, 29.0)) * AnimationInfo.y)) % frames;
        int effective = frames;
        float base = FrameLod.x;
        if (base == 0.0) {
            effective = 1;
        } else if (base > 0.0) {
            float distance = length(point + OriginTime.xyz);
            effective = distance < base ? frames : distance < base * 2.0 ? max(1, frames / 2)
                    : distance < base * 3.0 ? max(1, frames / 4) : 1;
        }
        int sampled = effective > 1 ? int(floor(float(raw) * float(effective) / float(frames))) : 0;
        frame = float(effective > 1 ? int(round(float(sampled) * float(frames - 1) / float(effective - 1))) : 0);
    }
    float edge = 0.0;
    vec3 relative = point + OriginTime.xyz;
#ifndef HORDE_NO_DETAIL_LOD
    if (dot(relative, relative) < HORDE_LOD_EDGE_NEAR_SQUARED) {
        for (int slot = 0; slot < int(SplitInfo.x); slot++) {
            int at = slot * 5;
            float radius = scalar(HordeSplit, at + 2);
            float yMin = scalar(HordeSplit, at + 3), yMax = scalar(HordeSplit, at + 4);
            if (radius <= 0.01 || point.y >= yMax || point.y + LightRoute.w * scale <= yMin) {
                continue;
            }
            vec2 center = vec2(scalar(HordeSplit, at), scalar(HordeSplit, at + 1));
            if (abs(length(point.xz - center) - radius) <= max(2.0, SplitShape.w * 2.0 + radius * 0.35)) {
                edge = 1.0;
                break;
            }
        }
    }
#endif
    first = floatBitsToUint(vec4(relative, scale));
    second = floatBitsToUint(vec4(packedLight, heading, frame));
    edgeWord = floatBitsToUint(edge);
}
