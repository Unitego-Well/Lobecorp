// 单位避让：玩家胶囊、分割区域屏障代价梯度、格点邻居分离与硬投影。
// 坐标均为事件局部坐标；位置单位为格，速度单位为格/游戏刻。

const float HORDE_UNIT_RADIUS = 0.45;
const float HORDE_PLAYER_RADIUS = 0.62;
const float HORDE_PLAYER_HEIGHT = 1.8;
const float HORDE_PLAYER_EYE = 1.62;
const float HORDE_NEIGHBOR_GAIN = 0.6;
const float HORDE_BARRIER_BAND = 3.2;

// 提前分流距离：速度越快越早开始侧移，避免贴到屏障才急转。
float hordeBarrierBand() {
    return HORDE_BARRIER_BAND + CameraSpeed.w * 8.0;
}

vec2 hordeSafeNormalize(vec2 value, vec2 fallback) {
    float squared = dot(value, value);
    return squared > 0.000001 ? value * inversesqrt(squared) : fallback;
}

// 玩家胶囊（竖直圆柱，相机位置即第一人称眼睛）：垂直重叠时沿切线绕行，贴得过近时额外向外推。
vec2 hordePlayerSteer(vec3 point, vec2 forward, vec2 side, float preferredSide, out float penetration) {
    penetration = 0.0;
    vec3 eye = -OriginTime.xyz;
    float feet = eye.y - HORDE_PLAYER_EYE;
    if (point.y > feet + HORDE_PLAYER_HEIGHT + 0.4 || point.y + LightRoute.w < feet - 0.4) {
        return vec2(0.0);
    }
    vec2 relative = point.xz - eye.xz;
    float distance = length(relative);
    float avoid = HORDE_PLAYER_RADIUS + HORDE_UNIT_RADIUS + 0.2;
    float band = HORDE_BARRIER_BAND;
    if (distance > avoid + band) {
        return vec2(0.0);
    }
    vec2 radial = distance > 0.001 ? relative / distance : side * preferredSide;
    vec2 tangent = vec2(-radial.y, radial.x);
    float direction = dot(tangent, forward);
    float sideSign = abs(direction) > 0.05 ? sign(direction) : preferredSide;
    float pressure = 1.0 - smoothstep(avoid, avoid + band, distance);
    vec2 result = tangent * sideSign * pressure * 1.35;
    if (distance < avoid) {
        penetration = avoid - distance;
        result += radial * penetration * 0.4;
    }
    return result;
}

// 分割区域：前向走廊把单位分流到左右边缘；区域内给出向外代价梯度，区域外给出软化斥力。
vec2 hordeRegionSteer(vec3 point, vec2 forward, vec2 side, float preferredSide,
        out float penetration, out vec2 escape) {
    vec2 lateral = vec2(0.0);
    escape = vec2(0.0);
    penetration = 0.0;
    // 多个缺口并存时只采用“最近/最强”的一个走廊方向：逐区域累加会让夹在多个缺口之间的单位
    // 每帧在左右之间翻转，表现为突然左右跳、原地抖动。区域外软化斥力仍逐区域累加。
    float bestCorridorWeight = -1.0;
    vec2 bestCorridor = vec2(0.0);
    ivec2 cell = spatialCell(point);
    // 索引域外至少128格没有屏障，直接跳过全部区域；未建立索引时才回退完整候选。
    if ((SpatialDimensions.w & 1) != 0 && cell.x < 0) {
        return lateral;
    }
    int count = int(SegmentRegions.w);
    int words = (count + 31) / 32;
    float band = hordeBarrierBand();
    for (int word = 0; word < words; word++) {
        uint possible = spatialWord(cell, word + 1);
        if (possible == 0u) continue;
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            vec3 minimum;
            vec3 maximum;
            if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)) continue;
            if (point.y >= maximum.y || point.y + LightRoute.w <= minimum.y) continue;
            vec2 center = (minimum.xz + maximum.xz) * 0.5;
            vec2 halfSize = (maximum.xz - minimum.xz) * 0.5;
            vec2 relative = point.xz - center;
            float ahead = dot(relative, forward);
            float across = dot(relative, side);
            float halfAlong = abs(forward.x) * halfSize.x + abs(forward.y) * halfSize.y;
            float halfAcross = abs(side.x) * halfSize.x + abs(side.y) * halfSize.y;
            // 1) 前向走廊：提前分流到左右较近的边缘；侧向速度加大但前向档位不变，不猛转向。
            if (abs(ahead) < halfAlong + band
                    && abs(across) < halfAcross + 1.2 + band) {
                float sideSign = abs(across) > 0.5 ? sign(across) : preferredSide;
                float target = sideSign * (halfAcross + HORDE_UNIT_RADIUS + 0.8);
                float weight = 1.0 - smoothstep(halfAlong, halfAlong + band, abs(ahead));
                if (weight > bestCorridorWeight) {
                    bestCorridorWeight = weight;
                    bestCorridor = side * clamp((target - across) * 1.1, -1.15, 1.15);
                }
            }
            // 2) 屏障代价梯度：区域内沿最近面向外，区域外带宽内软化斥力。
            vec2 inside = min(point.xz - minimum.xz, maximum.xz - point.xz);
            float depth = min(inside.x, inside.y);
            if (depth > 0.0) {
                vec2 normal;
                if (inside.x < inside.y) {
                    normal = vec2(point.x < center.x ? -1.0 : 1.0, 0.0);
                } else {
                    normal = vec2(0.0, point.z < center.y ? -1.0 : 1.0);
                }
                escape += normal * (depth + UnitLimits.z + 0.1);
                penetration = max(penetration, depth);
            } else {
                vec2 closest = clamp(point.xz, minimum.xz, maximum.xz);
                vec2 away = point.xz - closest;
                float distance = length(away);
                if (distance < band) {
                    vec2 direction = hordeSafeNormalize(away, side * preferredSide);
                    float weight = 1.0 - distance / band;
                    escape += direction * weight * 0.25;
                    lateral += side * dot(direction, side) * weight * 0.6;
                }
            }
        }
    }
    return lateral + bestCorridor;
}

// 硬约束：返回把点推出玩家胶囊和所有已进入区域的总推力，并给出最大穿透深度。
vec2 hordeBarrierPush(vec3 point, out float depth) {
    depth = 0.0;
    vec2 push = vec2(0.0);
    vec3 eye = -OriginTime.xyz;
    float feet = eye.y - HORDE_PLAYER_EYE;
    if (point.y < feet + HORDE_PLAYER_HEIGHT + 0.4 && point.y + LightRoute.w > feet - 0.4) {
        vec2 relative = point.xz - eye.xz;
        float distance = length(relative);
        float avoid = HORDE_PLAYER_RADIUS + HORDE_UNIT_RADIUS;
        if (distance < avoid) {
            vec2 normal = distance > 0.001 ? relative / distance : vec2(0.0, 1.0);
            push += normal * (avoid - distance + UnitLimits.z);
            depth = max(depth, avoid - distance);
        }
    }
    ivec2 cell = spatialCell(point);
    if ((SpatialDimensions.w & 1) != 0 && cell.x < 0) {
        return push;
    }
    int count = int(SegmentRegions.w);
    int words = (count + 31) / 32;
    float band = hordeBarrierBand();
    // 多个重叠缺口只取穿透最深的一个作为硬推力：逐区域求和会让夹在中间的单位的推力互相抵消/来回翻转。
    vec2 bestPush = vec2(0.0);
    float bestPenetration = 0.0;
    for (int word = 0; word < words; word++) {
        uint possible = spatialWord(cell, word + 1);
        if (possible == 0u) continue;
        for (int bit = 0; bit < 32 && word * 32 + bit < count; bit++) {
            if ((possible & (1u << uint(bit))) == 0u) continue;
            vec3 minimum;
            vec3 maximum;
            if (!regionBounds(regionSlot(word * 32 + bit), minimum, maximum)) continue;
            if (point.y >= maximum.y || point.y + LightRoute.w <= minimum.y) continue;
            vec2 inside = min(point.xz - minimum.xz, maximum.xz - point.xz);
            float penetration = min(inside.x, inside.y);
            if (penetration <= bestPenetration) continue;
            vec2 center = (minimum.xz + maximum.xz) * 0.5;
            vec2 normal;
            if (inside.x < inside.y) {
                normal = vec2(point.x < center.x ? -1.0 : 1.0, 0.0);
            } else {
                normal = vec2(0.0, point.z < center.y ? -1.0 : 1.0);
            }
            bestPenetration = penetration;
            bestPush = normal * (penetration + UnitLimits.z);
        }
    }
    push += bestPush;
    depth = max(depth, bestPenetration);
    return push;
}

// 格点邻居分离：所有单位共享前向速度，相对运动近似只有横向，因此只施加横向分离。
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
