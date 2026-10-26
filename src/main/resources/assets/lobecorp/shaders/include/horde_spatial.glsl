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

// 此路径与unitPosition统一使用event局部坐标，不能在这里加相机偏移OriginTime。
// 外包围只用于索引与传递圆参数；活动与退场都读取同一份GPU连续状态。
bool regionBounds(int slot, out vec3 minimum, out vec3 maximum) {
    minimum = vec3(0.0);
    maximum = vec3(0.0);
    int generation = abs(integer(HordeRegions, slot * 7 + 6));
    int at = slot * 13;
    if (generation == 0 || integer(HordeRegionState, at + 12) != generation) return false;
    vec3 center = vector3(HordeRegionState, at);
    vec3 halfSize = max(vec3(0.0), vector3(HordeRegionState, at + 3));
    float core = max(halfSize.x, halfSize.z);
    if (core <= 0.0001) return false;
    float radius = core + intBitsToFloat(SpatialTextures.w) * smoothstep(0.0, 0.45, core);
    minimum = center - vec3(radius, halfSize.y, radius);
    maximum = center + vec3(radius, halfSize.y, radius);
    return true;
}

bool regionContains(vec3 point, vec3 minimum, vec3 maximum) {
    vec2 relative = point.xz - (minimum.xz + maximum.xz) * 0.5;
    float radius = (maximum.x - minimum.x) * 0.5;
    return point.y < maximum.y && point.y + LightRoute.w > minimum.y
    && dot(relative, relative) < radius * radius;
}

bool regionClear(vec3 point) {
    ivec2 cell = spatialCell(point);
    int count = int(SegmentRegions.w);
    for (int word = 0; word < (count + 31) / 32; word++) {
        uint possible = spatialWord(cell, word + 1);
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            vec3 minimum, maximum;
            if (regionBounds(regionSlot(word * 32 + bit), minimum, maximum)
                && regionContains(point, minimum, maximum)) return false;
        }
    }
    return true;
}

bool regionBlocked(vec3 point) {
    return !regionClear(point);
}

// 沿固定方向求圆的远端交点；相交核心不会用相反法线互相抵消。
vec3 regionClamp(vec3 point, vec2 direction) {
    direction = length(direction) > 0.0001 ? normalize(direction) : vec2(1.0, 0.0);
    int count = int(SegmentRegions.w);
    for (int iteration = 0; iteration < 2; iteration++) {
        float reach = 0.0;
        ivec2 cell = spatialCell(point);
        for (int word = 0; word < (count + 31) / 32; word++) {
            uint possible = spatialWord(cell, word + 1);
            for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
                if ((possible & (1u << uint(bit))) == 0u) continue;
                vec3 minimum, maximum;
                if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)
                    || !regionContains(point, minimum, maximum)) continue;
                vec2 relative = point.xz - (minimum.xz + maximum.xz) * 0.5;
                float radius = (maximum.x - minimum.x) * 0.5;
                float projection = dot(relative, direction);
                reach = max(reach, -projection + sqrt(max(0.0,
                        projection * projection + radius * radius - dot(relative, relative))));
            }
        }
        if (reach <= 0.0) break;
        point.xz += direction * (reach + UnitLimits.z);
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
            float radius = (maximum.x - minimum.x) * 0.5;
            depth = max(depth, radius - length(point.xz - (minimum.xz + maximum.xz) * 0.5));
        }
    }
    return depth;
}
