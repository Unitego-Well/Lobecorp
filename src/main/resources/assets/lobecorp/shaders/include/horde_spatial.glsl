uniform sampler2D HordeSpatial32;
uniform sampler2D HordeSpatial16;
uniform sampler2D HordeSpatial8;

int spatialInteger(int level, int at) {
    if (level == 0) return integer(HordeSpatial32, at);
    if (level == 1) return integer(HordeSpatial16, at);
    return integer(HordeSpatial8, at);
}

// 格外或容量回退时返回完整候选；不能把索引范围当成浪潮显示范围。
ivec2 spatialCell(vec3 point) {
    if ((SpatialDimensions.w & 1) == 0) {
        return ivec2(-1, 0);
    }
    vec3 local = point + WidthFraction.yzw
    + vec3(ivec3(WorldSection.xyz) * 16 - SpatialMinimum.xyz);
    ivec3 coarse = ivec3(floor(local / 32.0));
    if (any(lessThan(coarse, ivec3(0))) || any(greaterThanEqual(coarse, SpatialDimensions.xyz))) {
        return ivec2(-1, 0);
    }
    ivec2 result = ivec2(0, (coarse.x + SpatialDimensions.x
    * (coarse.y + SpatialDimensions.y * coarse.z)) * SpatialMinimum.w);
    for (int level = 1; level < 3; level++) {
        if ((SpatialDimensions.w & (1 << level)) == 0 || spatialInteger(result.x, result.y) <= 8) {
            break;
        }
        int factor = 1 << level;
        ivec3 dimensions = SpatialDimensions.xyz * factor;
        ivec3 cell = ivec3(floor(local / (32.0 / float(factor))));
        result = ivec2(level, (cell.x + dimensions.x * (cell.y + dimensions.y * cell.z)) * SpatialMinimum.w);
    }
    return result;
}

uint spatialWord(ivec2 cell, int word) {
    return cell.x < 0 ? 0xffffffffu : uint(spatialInteger(cell.x, cell.y + word));
}

int regionSlot(int ordinal) {
    return integer(HordeRegions, int(LightRoute.z) * 7 + ordinal);
}

bool regionClear(vec3 point) {
    ivec2 cell = spatialCell(point);
    int count = int(SegmentRegions.w);
    int words = (count + 31) / 32;
    float radius = intBitsToFloat(SpatialTextures.w);
    ivec3 originBlock = ivec3(WorldSection.xyz) * 16;
    for (int word = 0; word < words; word++) {
        uint possible = spatialWord(cell, word + 1);
        if (possible == 0u) continue;
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            int slot = regionSlot(word * 32 + bit);
            int raw = slot * 7;
            if (integer(HordeRegions, raw + 6) <= 0) continue;
            ivec3 worldMinimum = ivec3(integer(HordeRegions, raw),
                    integer(HordeRegions, raw + 1), integer(HordeRegions, raw + 2));
            vec3 minimum = vec3(worldMinimum - originBlock) - WidthFraction.yzw;
            vec3 dimensions = vec3(integer(HordeRegions, raw + 3),
                    integer(HordeRegions, raw + 4), integer(HordeRegions, raw + 5));
            if (point.y < minimum.y + dimensions.y && point.y + LightRoute.w > minimum.y
                && all(greaterThan(point.xz, minimum.xz - vec2(radius)))
                && all(lessThan(point.xz, minimum.xz + dimensions.xz + vec2(radius)))) {
                return false;
            }
        }
    }
    return true;
}

bool regionBounds(int slot, out vec3 minimum, out vec3 maximum) {
    int at = slot * 7;
    minimum = vec3(0.0);
    maximum = vec3(0.0);
    if (integer(HordeRegions, at + 6) <= 0) return false;
    ivec3 world = ivec3(integer(HordeRegions, at), integer(HordeRegions, at + 1), integer(HordeRegions, at + 2));
    minimum = vec3(world - ivec3(WorldSection.xyz) * 16) - WidthFraction.yzw;
    maximum = minimum + vec3(integer(HordeRegions, at + 3),
            integer(HordeRegions, at + 4), integer(HordeRegions, at + 5));
    float radius = intBitsToFloat(SpatialTextures.w);
    minimum.xz -= vec2(radius);
    maximum.xz += vec2(radius);
    return true;
}

bool regionContains(vec3 point, vec3 minimum, vec3 maximum) {
    return point.y < maximum.y && point.y + LightRoute.w > minimum.y
    && all(greaterThan(point.xz, minimum.xz)) && all(lessThan(point.xz, maximum.xz));
}

bool regionBlocked(vec3 point) {
    return !regionClear(point);
}

// 固定逃逸方向保持相交区域中的纠正单调；最多两轮几何求解，时间收敛由单位历史限速。
vec3 regionClamp(vec3 point, vec2 direction) {
    direction = length(direction) > 0.0001 ? normalize(direction) : vec2(1.0, 0.0);
    int count = int(SegmentRegions.w);
    for (int iteration = 0; iteration < 2; iteration++) {
        bool changed = false;
        ivec2 cell = spatialCell(point);
        for (int word = 0; word < (count + 31) / 32; word++) {
            uint possible = spatialWord(cell, word + 1);
            for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
                if ((possible & (1u << uint(bit))) == 0u) continue;
                vec3 minimum, maximum;
                if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)
                    || !regionContains(point, minimum, maximum)) continue;
                vec2 distance = vec2(1e20);
                if (abs(direction.x) > 0.0001) distance.x =
                ((direction.x > 0.0 ? maximum.x : minimum.x) - point.x) / direction.x;
                if (abs(direction.y) > 0.0001) distance.y =
                ((direction.y > 0.0 ? maximum.z : minimum.z) - point.z) / direction.y;
                point.xz += direction * (max(0.0, min(distance.x, distance.y)) + UnitLimits.z);
                changed = true;
            }
        }
        if (!changed) break;
    }
    return point;
}

float regionPenetration(vec3 point) {
    float depth = 0.0;
    int count = int(SegmentRegions.w);
    ivec2 cell = spatialCell(point);
    for (int word = 0; word < (count + 31) / 32; word++) {
        uint possible = spatialWord(cell, word + 1);
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            vec3 minimum, maximum;
            if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)
                || !regionContains(point, minimum, maximum)) continue;
            vec2 edge = min(point.xz - minimum.xz, maximum.xz - point.xz);
            depth = max(depth, min(edge.x, edge.y));
        }
    }
    return depth;
}
