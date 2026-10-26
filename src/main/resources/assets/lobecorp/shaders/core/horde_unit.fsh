#version 330

#moj_import <lobecorp:horde_motion.glsl>

flat in int unitRowDelta;
flat in int unitColumn;
out vec4 fragColor;

// 每单位由GPU推进：位置与速度是持久状态；玩家胶囊、分割区域和邻居只修改速度，前向档位持续保留。
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
    // 帧率/服务端尖峰会让时间差变大：最多容忍1秒，避免整批单位被瞬间重播种（视觉上的“突然清空再填充”）。
    float age = finiteTime ? OriginTime.w - before : 0.0;
    vec3 previous = matches && finiteTime ? vector3(HordeUnitPrevious, at) : anchor;
    vec2 previousVelocity = matches && finiteTime && age <= 20.0
            ? vec2(scalar(HordeUnitPrevious, at + 7), scalar(HordeUnitPrevious, at + 8))
            : forward * max(0.05, CameraSpeed.w);
    if (any(isnan(previous)) || any(isinf(previous))) previous = anchor;
    if (any(isnan(previousVelocity)) || any(isinf(previousVelocity))) previousVelocity = forward * max(0.05, CameraSpeed.w);

    float elapsed = matches && finiteTime ? clamp(age, 0.02, 5.0) : 1.0;
    float baseSpeed = max(0.05, CameraSpeed.w);

    // 期望速度 = 线路前向基速 + 锚点拉回 + 玩家胶囊/分割区域/邻居分离的避让速度。
    vec2 anchorDelta = anchor.xz - previous.xz;
    float alongGap = dot(anchorDelta, forward);
    float acrossGap = dot(anchorDelta, side);
    float speed = clamp(baseSpeed + alongGap * 0.12, baseSpeed * 0.55, baseSpeed * 2.0);
    vec2 steering = side * clamp(acrossGap * 0.32, -0.45, 0.45);
    // 横向阻尼：绕行结束后速度回正，和主体会合，不长期分叉。
    steering -= side * dot(previousVelocity, side) * 0.30;

    float playerDepth;
    steering += hordePlayerSteer(previous, forward, side, preferredSide, playerDepth);
    float regionDepth;
    vec2 regionEscape;
    steering += hordeRegionSteer(previous, forward, side, preferredSide, regionDepth, regionEscape);
    steering += hordeNeighborSteer(int(row.x), unitColumn, previous, side, preferredSide);

    vec2 desired = forward * speed + steering;
    // 高速浪潮（0.75格/刻）仍要留出横向避让余量：上限不压死基速与侧移。
    float limit = max(UnitLimits.x, baseSpeed * 2.4);
    float desiredSpeed = length(desired);
    if (desiredSpeed > limit) {
        desired *= limit / desiredSpeed;
    }
    // 只要没有被屏障压住，就保留最低前进速度：不允许原地停住。
    float forwardSpeed = dot(desired, forward);
    if (forwardSpeed < baseSpeed * 0.35 && regionDepth <= 0.0 && playerDepth <= 0.0) {
        desired += forward * (baseSpeed * 0.35 - forwardSpeed);
    }

    vec2 position = previous.xz + desired * elapsed;
    velocity = desired;
    penetration = 0.0;
    rejected = 0.0;

    // 分割区域与玩家胶囊：沿代价梯度平滑推出（每帧位移受限），并给向外的主动离开速度；
    // 新出现的生物/屏障不会再让单位瞬移或整批清空，而是被推着离开后再和主体会合。
    float exitBudget = limit * elapsed;
    for (int iteration = 0; iteration < 2 && exitBudget > 0.0001; iteration++) {
        float depth;
        vec2 push = hordeBarrierPush(vec3(position.x, anchor.y, position.y), depth);
        if (depth <= 0.0001) break;
        vec2 direction = hordeSafeNormalize(push, forward);
        float step = min(depth + UnitLimits.z, exitBudget);
        position += direction * step;
        exitBudget -= step;
        float inward = dot(velocity, direction);
        if (inward < 0.0) velocity -= direction * inward;
        // 只去掉向内的速度，不叠加径向速度：朝向保持稳定，避免突然转向。
        penetration = max(penetration, depth);
        rejected = 1.0;
    }
    float exitSpeed = length(velocity);
    if (exitSpeed > limit) velocity *= limit / exitSpeed;
    // 约束异常统计：被投影纠正或几乎失去前进速度（原地停）都计入 rejected，供基准判定“不停顿”。
    if (length(velocity) < baseSpeed * 0.08) {
        rejected = 1.0;
    }

    // 横向不得越出浪潮宽度；越界只收横向速度，不反向。
    float drift = dot(position - anchor.xz, side);
    if (abs(drift) > ProbeInfo.w) {
        position -= side * (drift - sign(drift) * ProbeInfo.w);
        velocity -= side * dot(velocity, side);
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
