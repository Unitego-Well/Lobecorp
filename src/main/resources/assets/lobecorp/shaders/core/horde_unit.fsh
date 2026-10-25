#version 330

#moj_import <lobecorp:horde_motion.glsl>

flat in int unitRowDelta;
flat in int unitColumn;
out vec4 fragColor;

void advanceUnit(out vec3 point, out vec2 heading, out float rejected) {
    float station = RowColumns.x - float(unitRowDelta) * SpacingLength.x;
    float lateral = (float(unitColumn) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
    float seed = mod(WorldSection.w + float(unitRowDelta), 1024.0) * 17.0 + float(unitColumn) * 31.0;
    float preferred = motionRandom(seed, 1.0) < 0.5 ? -1.0 : 1.0;
    vec2 forward = routeForward(station);
    vec2 escape = vec2(-forward.y, forward.x) * preferred;
    vec3 desired = movingLocation(station, lateral, OriginTime.w, seed);
    uvec2 row = unitRow(uint(unitRowDelta));
    ivec2 address = unitAddress(row, uint(unitColumn), HordeUnitPrevious);
    int at = unitOffset(address, HordeUnitPrevious);
    bool valid = unitMatches(HordeUnitPrevious, at, row, uint(unitColumn));
    float before = valid ? scalar(HordeUnitPrevious, at + 6) : OriginTime.w;
    valid = valid && !isnan(before) && !isinf(before) && before <= OriginTime.w;
    point = desired;
    heading = forward;
    rejected = 0.0;
    if (!valid) return;
    vec3 previous = vector3(HordeUnitPrevious, at);
    if (any(isnan(previous)) || any(isinf(previous))) return;
    vec2 oldHeading = vec2(scalar(HordeUnitPrevious, at + 7), scalar(HordeUnitPrevious, at + 8));
    if (any(isnan(oldHeading)) || any(isinf(oldHeading)) || length(oldHeading) < 0.0001) oldHeading = forward;
    bool blocked = regionBlocked(previous);
    if (blocked) {
        desired = regionClamp(previous, escape);
    } else if (regionBlocked(desired)) {
        desired = previous;
        rejected = 1.0;
    }
    float elapsed = max(0.0, OriginTime.w - before);
    vec3 delta = desired - previous;
    float distance = length(delta);
    point = previous + delta * min(1.0, UnitLimits.x * elapsed / max(distance, 0.0001));
    if (!blocked && regionBlocked(point)) {
        point = previous;
        rejected = 1.0;
    }
    vec2 movement = point.xz - previous.xz;
    if (length(movement) <= 0.00001) {
        heading = oldHeading;
    } else {
        float oldAngle = atan(oldHeading.x, oldHeading.y);
        float target = atan(movement.x, movement.y);
        float difference = atan(sin(target - oldAngle), cos(target - oldAngle));
        float angle = oldAngle + difference * (1.0 - exp(-UnitLimits.y * elapsed / 20.0));
        heading = vec2(sin(angle), cos(angle));
    }
}

void main() {
    int component = int(gl_FragCoord.x) % HORDE_UNIT_VALUES;
    uvec2 row = unitRow(uint(unitRowDelta));
    uint bits;
    if (component == 3) bits = row.x;
    else if (component == 4) bits = row.y;
    else if (component == 5) bits = uint(unitColumn);
    else if (component == 6) bits = floatBitsToUint(OriginTime.w);
    else if (component == 11) bits = uint(UnitIdentity.w);
    else if ((component == 9 || component == 10) && UnitLimits.w < 0.5) bits = 0u;
    else {
        vec3 point;
        vec2 heading;
        float rejected;
        advanceUnit(point, heading, rejected);
        float value = component < 3 ? point[component] : component == 7 ? heading.x
        : component == 8 ? heading.y : component == 9 ? regionPenetration(point) : rejected;
        bits = floatBitsToUint(value);
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
