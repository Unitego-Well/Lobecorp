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

vec2 unitDirection(int row, int column, float station) {
    uvec2 id = unitRow(uint(row));
    ivec2 address = unitAddress(id, uint(column), HordeUnitCurrent);
    if (address.x >= 0) {
        int at = unitOffset(address, HordeUnitCurrent);
        if (unitMatches(HordeUnitCurrent, at, id, uint(column))) {
            vec2 direction = vec2(scalar(HordeUnitCurrent, at + 7), scalar(HordeUnitCurrent, at + 8));
            if (!any(isnan(direction)) && !any(isinf(direction)) && length(direction) > 0.0001) return direction;
        }
    }
    return routeForward(station);
}
