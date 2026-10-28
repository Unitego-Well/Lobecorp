// 分割控制的纯标量函数；位置、速度与选侧历史由独立 GPU 行窗保存。
// Motion8 只消费状态，批次重排与模型重载不会重建单位运动。
// 割圆数据每槽 5 字：中心 xz（事件局部）、半径、Y 下界、Y 上界。
// 侧向速度以格/tick计；活动避让和退场约束总横向运动，而非给前进速度乘系数。
const float HORDE_AVOID_SPEED_RATIO = 0.7;
const float HORDE_AVOID_ACCEL_TICKS = 4.0;
const float HORDE_AVOID_SIDE_SALT = 7.0;
const float HORDE_AVOID_SIDE_TIE = 0.25;
const float HORDE_AVOID_SWAY_MARGIN = 1.5;
const float HORDE_AVOID_CLEARANCE = 0.45;
const float HORDE_AVOID_FADE_RADIUS = 0.5;
const float HORDE_AVOID_SOFT_GAIN = 4.0;
const float HORDE_AVOID_FREE_PRIOR = 1.0;
const float HORDE_AVOID_MAX_BLEND = 0.8;
const float HORDE_AVOID_MIN_STRENGTH = 0.55;
const float HORDE_AVOID_MIN_PULSE = 0.85;
const float HORDE_AVOID_PULSE_RATE = 0.07;
const float HORDE_AVOID_PHASE_SHIFT = 0.2;
const float HORDE_AVOID_DELTA_WIDTH = 0.6;
const float HORDE_AVOID_STRENGTH_SALT = 11.0;
const float HORDE_AVOID_PHASE_SALT = 13.0;
const float HORDE_AVOID_TAU = 6.2831853;
const int HORDE_AVOID_WORDS = 4;

float hordeAvoidanceLead(float distance, float speed, float driftBound, float band) {
    float limit = HORDE_AVOID_SPEED_RATIO * speed;
    return (speed + driftBound) * (distance / max(limit, 0.000001) + HORDE_AVOID_ACCEL_TICKS) + band;
}

float hordeAvoidanceClearance(float radius, float modelRadius, float fade) {
    return radius + (HORDE_AVOID_CLEARANCE + modelRadius) * fade;
}

// 椭圆紧支撑压力：只有中心点达到峰值，进入、离开和侧缘均连续归零。
float hordeAvoidancePressure(float along, float across, float clearance, float lead, float band, float shift) {
    float axis = along - shift;
    float reach = clearance + mix(band, lead, max(0.0, sign(axis)));
    float longitudinal = axis / max(reach, 0.001);
    float lateral = across / max(clearance + HORDE_AVOID_SWAY_MARGIN, 0.001);
    float remaining = max(0.0, 1.0 - longitudinal * longitudinal - lateral * lateral);
    return remaining * remaining;
}

float hordeAvoidanceSoftDelta(float across, float clearance, float choice) {
    float demand = clearance + choice * across;
    return 0.5 * (demand + sqrt(demand * demand + HORDE_AVOID_DELTA_WIDTH * HORDE_AVOID_DELTA_WIDTH));
}

// 稳定个体强度放在混合之后，重叠时也不会被权重归一化抹平。
float hordeAvoidanceIndividual(float strengthSeed, float phase, float time) {
    float strength = mix(HORDE_AVOID_MIN_STRENGTH, 1.0, strengthSeed);
    float pulse = mix(HORDE_AVOID_MIN_PULSE, 1.0, 0.5 + 0.5 * sin(time * HORDE_AVOID_PULSE_RATE + phase));
    return strength * pulse;
}

// 自由先验始终保留；再多圆也不达到100%圈界投影，natural在调用方独立相加。
float hordeAvoidanceBlend(float weightedDelta, float mass, float individual) {
    return individual * weightedDelta / (HORDE_AVOID_FREE_PRIOR + mass / HORDE_AVOID_MAX_BLEND);
}

float hordeAvoidanceVelocity(float velocity, float error, float limit, float dt) {
    float acceleration = limit / HORDE_AVOID_ACCEL_TICKS;
    float target = sign(error) * min(limit, sqrt(max(0.0, 2.0 * acceleration * abs(error))));
    return clamp(target, max(-limit, velocity - acceleration * dt), min(limit, velocity + acceleration * dt));
}

// 不跨过当前目标，且单步位移不超过实际 dt 对应的侧向速度预算。
float hordeAvoidanceStep(float nextVelocity, float error, float limit, float dt) {
    float budget = min(limit * dt, abs(error));
    return clamp(nextVelocity * dt, -budget, budget);
}

// 初选比较柔和目标的横移距离，代价相近时才使用稳定种子打平。
float hordeAvoidanceChoose(float preferred, float leftCost, float rightCost) {
    float distinct = max(0.0, sign(abs(leftCost - rightCost) - HORDE_AVOID_SIDE_TIE));
    return mix(preferred, sign(leftCost - rightCost), distinct);
}

// 遭遇中不更换已锁定的符号；只有无遭遇并返回自然摆动后才释放。
float hordeAvoidanceSide(float locked, float preferred, float encounter, float arrived) {
    float acquired = mix(locked, preferred, encounter * (1.0 - abs(locked)));
    return acquired * (1.0 - (1.0 - encounter) * arrived);
}
