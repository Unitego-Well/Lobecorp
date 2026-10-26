const int HORDE_UNIT_VALUES = 12;
const int HORDE_UNIT_TILE_WIDTH = 192;

uvec2 unitRow(uint delta) {
    uint low = uint(UnitIdentity.y) + delta;
    return uvec2(low, uint(UnitIdentity.z) + (low < uint(UnitIdentity.y) ? 1u : 0u));
}

ivec2 unitAddress(uvec2 row, uint column, sampler2D history) {
    // 行环页：可见行跨度不超过环页容量，所以同一时刻不会有两个可见行共用槽位。
    uint rowTile = (row.x >> 4u) & uint(UnitPage.x);
    uvec3 tile = uvec3(rowTile, 0u, column >> 4u);
    uint mask = uint(UnitIdentity.x);
    uint at = (tile.x * 73856093u ^ tile.y * 19349663u ^ tile.z * 83492791u) & mask;
    for (uint probe = 0u; probe <= mask; probe++) {
        int index = int(at) * 4;
        uint slot = rawWord(HordeUnitIndex, index + 3);
        if (slot == 0u) return ivec2(-1);
        if (uvec3(rawWord(HordeUnitIndex, index), rawWord(HordeUnitIndex, index + 1),
                rawWord(HordeUnitIndex, index + 2)) == tile) {
            int tiles = textureSize(history, 0).x / HORDE_UNIT_TILE_WIDTH;
            int number = int(slot - 1u);
            return ivec2(number % tiles * HORDE_UNIT_TILE_WIDTH + int(column & 15u) * HORDE_UNIT_VALUES,
                    number / tiles * 16 + int(row.x & 15u));
        }
        at = (at + 1u) & mask;
    }
    return ivec2(-1);
}

int unitOffset(ivec2 address, sampler2D history) {
    return address.y * textureSize(history, 0).x + address.x;
}

bool unitMatches(sampler2D history, int at, uvec2 row, uint column) {
    return rawWord(history, at + 3) == row.x && rawWord(history, at + 4) == row.y
    && rawWord(history, at + 5) == column;
}

vec3 unitPosition(int row, int column, float station, float lateral, float seed) {
    uvec2 id = unitRow(uint(row));
    ivec2 address = unitAddress(id, uint(column), HordeUnitCurrent);
    if (address.x >= 0) {
        int at = unitOffset(address, HordeUnitCurrent);
        if (unitMatches(HordeUnitCurrent, at, id, uint(column))) return vector3(HordeUnitCurrent, at);
    }
    return movingLocation(station, lateral, OriginTime.w, seed);
}

// 渲染朝向：保持沿路线的前进朝向，只按横向速度轻微偏转，不做“突然转向”。
vec2 unitDirection(int row, int column, float station) {
    vec2 forward = routeForward(station);
    uvec2 id = unitRow(uint(row));
    ivec2 address = unitAddress(id, uint(column), HordeUnitCurrent);
    if (address.x >= 0) {
        int at = unitOffset(address, HordeUnitCurrent);
        if (unitMatches(HordeUnitCurrent, at, id, uint(column))) {
            vec2 velocity = vec2(scalar(HordeUnitCurrent, at + 7), scalar(HordeUnitCurrent, at + 8));
            float forwardSpeed = dot(velocity, forward);
            vec2 side = vec2(-forward.y, forward.x);
            float lateralSpeed = dot(velocity, side);
            float base = max(abs(forwardSpeed), 0.05);
            vec2 heading = forward * base + side * lateralSpeed * 0.35;
            float squared = dot(heading, heading);
            if (!any(isnan(velocity)) && !any(isinf(velocity)) && squared > 0.000001) {
                return heading * inversesqrt(squared);
            }
        }
    }
    return forward;
}

// 读取相邻格点上一帧的状态；时间过旧说明该格点本帧没有推进（视锥外），不能当作邻居。
bool unitNeighbor(int row, int column, out vec2 position, out vec2 velocity) {
    position = vec2(0.0);
    velocity = vec2(0.0);
    if (row < 0 || column < 0) return false;
    uvec2 identity = uvec2(uint(row), 0u);
    ivec2 address = unitAddress(identity, uint(column), HordeUnitPrevious);
    if (address.x < 0) return false;
    int at = unitOffset(address, HordeUnitPrevious);
    float updated = scalar(HordeUnitPrevious, at + 6);
    if (isnan(updated) || isinf(updated) || abs(OriginTime.w - updated) > 0.03) return false;
    position = vec2(scalar(HordeUnitPrevious, at), scalar(HordeUnitPrevious, at + 2));
    velocity = vec2(scalar(HordeUnitPrevious, at + 7), scalar(HordeUnitPrevious, at + 8));
    return !any(isnan(position)) && !any(isnan(velocity));
}
