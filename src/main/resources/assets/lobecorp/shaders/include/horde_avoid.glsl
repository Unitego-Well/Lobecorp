// 每个生物只有水平圆形禁入核心；沿潮方向延长的是引导轮廓，不是禁区。
// 坐标为事件局部坐标；位置单位为格，速度单位为格/游戏刻。
const float HORDE_UNIT_RADIUS = 0.45;
const float HORDE_NEIGHBOR_GAIN = 0.6;
const float HORDE_BARRIER_BAND = 3.2;

float hordeBarrierBand() {
    return HORDE_BARRIER_BAND + CameraSpeed.w * 8.0;
}

vec2 hordeSafeNormalize(vec2 value, vec2 fallback) {
    float squared = dot(value, value);
    return squared > 0.000001 ? value * inversesqrt(squared) : fallback;
}

// 只取最强引导，避免多个缺口的相反侧移相消；圆外保留平滑的提前分流和尾部合流。
vec2 hordeRegionSteer(vec3 point, vec2 forward, vec2 side, float preferredSide,
        out float penetration, out float influence) {
    penetration = 0.0;
    influence = 0.0;
    float bestWeight = 0.0;
    vec2 bestSteer = vec2(0.0);
    ivec2 cell = spatialCell(point);
    int count = int(SegmentRegions.w);
    float band = hordeBarrierBand();
    float baseSpeed = max(0.05, CameraSpeed.w);
    for (int word = 0; word < (count + 31) / 32; word++) {
        uint possible = spatialWord(cell, word + 1);
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            vec3 minimum, maximum;
            if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)) continue;
            if (point.y >= maximum.y || point.y + LightRoute.w <= minimum.y) continue;
            vec2 relative = point.xz - (minimum.xz + maximum.xz) * 0.5;
            float radius = (maximum.x - minimum.x) * 0.5;
            float distance = length(relative);
            penetration = max(penetration, radius - distance);
            float along = dot(relative, forward);
            float across = dot(relative, side);
            float lead = band + radius * 0.6;
            float tail = band * 0.6 + radius * 0.5;
            float beyond = along - clamp(along, -lead, tail);
            float capsuleDistance = length(vec2(beyond, across));
            float pressure = 1.0 - smoothstep(radius, radius + band * 0.6, capsuleDistance);
            float approach = 1.0 - smoothstep(tail, tail + radius + band * 0.6, along);
            float weight = pressure * approach;
            if (weight <= bestWeight) continue;
            // 进入中心带后沿用个体历史侧，移动目标穿过中心线也不逐帧翻边。
            float chosen = abs(across) > radius * 0.65 ? sign(across) : preferredSide;
            float outerRadius = radius + 0.65;
            float rounded = sqrt(max(0.0, outerRadius * outerRadius - beyond * beyond));
            float targetAcross = chosen * rounded;
            float lateral = clamp((targetAcross - across) * 0.32, -baseSpeed * 1.5, baseSpeed * 1.5);
            float entering = 1.0 - smoothstep(-radius, radius * 0.5, along);
            bestSteer = side * lateral * weight - forward * baseSpeed * 0.55 * weight * entering;
            bestWeight = weight;
        }
    }
    influence = bestWeight;
    return bestSteer;
}

// 沿一个持久方向求所有覆盖圆的最远出口；重叠核心不会把退出方向互相抵消。
vec2 hordeBarrierPush(vec3 point, vec2 direction, out float depth) {
    direction = hordeSafeNormalize(direction, vec2(0.0, 1.0));
    depth = 0.0;
    float reach = 0.0;
    ivec2 cell = spatialCell(point);
    int count = int(SegmentRegions.w);
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
            float exitDistance = -projection + sqrt(max(0.0,
                    projection * projection + radius * radius - dot(relative, relative)));
            reach = max(reach, exitDistance);
            depth = max(depth, radius - length(relative));
        }
    }
    return direction * reach;
}

// 格点邻居分离仅施加横向速度，不让整齐的前向队列互相制动。
vec2 hordeNeighborSteer(int row, int column, vec3 point, vec2 side, float preferredSide) {
    vec2 result = vec2(0.0);
    float spacing = min(SpacingLength.x, SpacingLength.y);
    float radius = max(0.9, spacing * 0.78);
    for (int rowOffset = -1; rowOffset <= 0; rowOffset++) {
        for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
            if (rowOffset == 0 && columnOffset == 0) continue;
            vec2 neighborPosition;
            vec2 neighborVelocity;
            if (!unitNeighbor(row + rowOffset, column + columnOffset, neighborPosition, neighborVelocity)) continue;
            vec2 delta = point.xz - neighborPosition;
            float distance = length(delta);
            if (distance < 0.0001 || distance >= radius) continue;
            float effect = (radius - distance) / radius;
            float lateral = dot(delta, side);
            float sideSign = abs(lateral) > 0.02 ? sign(lateral) : preferredSide;
            result += side * sideSign * effect * HORDE_NEIGHBOR_GAIN * (rowOffset < 0 ? 1.0 : 0.75);
        }
    }
    return result;
}
