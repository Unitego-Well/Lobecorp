#version 330

#moj_import <lobecorp:horde_motion.glsl>

flat in int unitRowDelta;
flat in int unitColumn;
out vec4 fragColor;

// 每单位由GPU推进；圆核心只改变目标速度，不投影位置或隐藏被突然包围的单位。
void advanceUnit(out vec3 point, out vec2 velocity, out float penetration, out float rejected, bool resolveHeight) {
    float station = RowColumns.x - float(unitRowDelta) * SpacingLength.x;
    float lateral = (float(unitColumn) - (SpacingLength.z - 1.0) * 0.5) * SpacingLength.y;
    float seed = mod(WorldSection.w + float(unitRowDelta), 1024.0) * 17.0 + float(unitColumn) * 31.0;
    float preferredSide = motionRandom(seed, 1.0) < 0.5 ? -1.0 : 1.0;
    vec2 forward = routeForward(station);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 anchor = movingLocation(station, lateral, OriginTime.w, seed);

    uvec2 row = unitRow(uint(unitRowDelta));
    ivec2 address = unitAddress(row, uint(unitColumn), HordeUnitPrevious);
    int at = address.x >= 0 ? unitOffset(address, HordeUnitPrevious) : 0;
    bool matches = address.x >= 0 && unitMatches(HordeUnitPrevious, at, row, uint(unitColumn));
    float before = matches ? scalar(HordeUnitPrevious, at + 6) : OriginTime.w;
    bool finiteTime = !isnan(before) && !isinf(before) && before <= OriginTime.w;
    // 尖峰或离开视锥后仍保留有效位置和速度；单帧积分时间单独限幅，不整批重播种。
    float age = finiteTime ? OriginTime.w - before : 0.0;
    vec3 previous = matches && finiteTime ? vector3(HordeUnitPrevious, at) : anchor;
    vec2 previousVelocity = matches && finiteTime
    ? vec2(scalar(HordeUnitPrevious, at + 7), scalar(HordeUnitPrevious, at + 8))
    : forward * max(0.05, CameraSpeed.w);
    if (any(isnan(previous)) || any(isinf(previous))) previous = anchor;
    if (any(isnan(previousVelocity)) || any(isinf(previousVelocity))) previousVelocity = forward * max(0.05, CameraSpeed.w);

    float elapsed = matches && finiteTime ? clamp(age, 0.0, 5.0) : 0.0;
    float baseSpeed = max(0.05, CameraSpeed.w);

    // 历史侧向偏移优先于速度和随机种子，避让期间不随目标中心线左右翻转。
    float historicalSide = dot(previous.xz - anchor.xz, side);
    float lateralVelocity = dot(previousVelocity, side);
    if (abs(historicalSide) > 0.18) preferredSide = sign(historicalSide);
    else if (abs(lateralVelocity) > 0.04) preferredSide = sign(lateralVelocity);

    // 个体连续速度差异独立于锚点追赶，受阻后逐渐减速、离开后逐渐恢复。
    vec2 anchorDelta = anchor.xz - previous.xz;
    float alongGap = dot(anchorDelta, forward);
    float acrossGap = dot(anchorDelta, side);
    float variation = 1.0 + 0.12 * sin(OriginTime.w * mix(0.025, 0.055, motionRandom(seed, 4.0))
    + motionRandom(seed, 5.0) * 6.2831853);
    float speed = clamp(baseSpeed * variation + alongGap * 0.08, baseSpeed * 0.55, baseSpeed * 1.5);
    vec2 steering = side * clamp(acrossGap * 0.32, -0.45, 0.45);
    // 横向阻尼：绕行结束后速度回正，和主体会合，不长期分叉。
    steering -= side * dot(previousVelocity, side) * 0.30;

    float regionDepth;
    float regionInfluence;
    vec2 regionSteer = hordeRegionSteer(previous, forward, side, preferredSide, regionDepth, regionInfluence);
    // 绕行时抑制锚点回拉和邻居挤压，尾部影响衰减后再连续合流，避免两股力把单位拉回圆内。
    steering *= 1.0 - regionInfluence;
    steering += regionSteer;
    steering += hordeNeighborSteer(int(row.x), unitColumn, previous, side, preferredSide)
    * (1.0 - regionInfluence * 0.85);

    vec2 desired = forward * speed + steering;
    // 横向绕行共享每tick位移预算，不通过额外位置推出突破速度上限。
    float limit = max(UnitLimits.x, baseSpeed);
    float exitDepth;
    vec2 exitDirection = hordeSafeNormalize(forward * 0.45 + side * preferredSide, forward);
    vec2 exit = hordeBarrierPush(previous, exitDirection, exitDepth);
    if (exitDepth > 0.0001) {
        // 同一个方向穿过组合核心；浅穿透与深包围共用连续退出，不额外瞬移。
        desired = hordeSafeNormalize(exit, exitDirection) * limit;
    }
    float desiredSpeed = length(desired);
    if (desiredSpeed > limit) {
        desired *= limit / desiredSpeed;
    }
    // 只要没有被屏障压住，就保留最低前进速度：不允许原地停住。
    float forwardSpeed = dot(desired, forward);
    if (forwardSpeed < baseSpeed * 0.35 && exitDepth <= 0.0) {
        desired += forward * (baseSpeed * 0.35 - forwardSpeed);
    }

    // 指数响应之后再限制加速度，低帧率和受阻解除时也不一帧切换速度。
    vec2 change = (desired - previousVelocity) * (1.0 - exp(-0.65 * elapsed));
    float acceleration = mix(0.055, 0.085, motionRandom(seed, 6.0));
    float changeLength = length(change);
    if (changeLength > acceleration * elapsed) change *= acceleration * elapsed / changeLength;
    velocity = previousVelocity + change;
    float exitSpeed = length(velocity);
    if (exitSpeed > limit) velocity *= limit / exitSpeed;
    vec2 position = previous.xz + velocity * elapsed;
    penetration = regionPenetration(vec3(position.x, previous.y, position.y));
    rejected = penetration > 0.0001 ? 1.0 : 0.0;
    // 统计仍在圆核心内或几乎失去运动速度的单位，不将连续退出误记为已完成净空。
    if (length(velocity) < baseSpeed * 0.08) {
        rejected = 1.0;
    }

    if (resolveHeight) {
        float lateralDelta = dot(position - anchor.xz, side);
        point = vec3(position.x, motionGroundHeight(station, lateral, lateralDelta, seed), position.y);
    } else {
        point = vec3(position.x, anchor.y, position.y);
    }
}

void main() {
    int component = int(gl_FragCoord.x) % HORDE_UNIT_VALUES;
    uvec2 row = unitRow(uint(unitRowDelta));
    uint bits;
    if (component == 3) {
        bits = row.x;
    } else if (component == 4) {
        bits = row.y;
    } else if (component == 5) {
        bits = uint(unitColumn);
    } else if (component == 6) {
        bits = floatBitsToUint(OriginTime.w);
    } else if (component == 11) {
        bits = uint(UnitIdentity.w);
    } else if ((component == 9 || component == 10) && UnitLimits.w < 0.5) {
        bits = 0u;
    } else {
        vec3 point;
        vec2 velocity;
        float penetration;
        float rejected;
        advanceUnit(point, velocity, penetration, rejected, component == 1);
        float value = component < 3 ? point[component]
                : component == 7 ? velocity.x
                : component == 8 ? velocity.y
                : component == 9 ? penetration : rejected;
        bits = floatBitsToUint(value);
    }
    fragColor = vec4(uvec4(bits & 255u, (bits >> 8u) & 255u,
            (bits >> 16u) & 255u, bits >> 24u)) / 255.0;
}
