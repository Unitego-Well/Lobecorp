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
const int HORDE_AVOID_WORDS = 4;

float hordeAvoidanceLead(float distance, float speed, float driftBound, float band) {
    float limit = HORDE_AVOID_SPEED_RATIO * speed;
    return (speed + driftBound) * (distance / max(limit, 0.000001) + HORDE_AVOID_ACCEL_TICKS) + band;
}

float hordeAvoidanceClearance(float radius, float modelRadius, float fade) {
    return radius + (HORDE_AVOID_CLEARANCE + modelRadius) * fade;
}

float hordeAvoidanceGoal(float across, float clearance, float choice, float natural, float fade) {
    return mix(choice * natural, choice * across + clearance, fade);
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

// 比较完整目标能否容纳；初选只在同等可行性下比较横移成本。
float hordeAvoidanceFits(float goal, float capacity) {
    return 1.0 - max(0.0, sign(goal - capacity));
}

float hordeAvoidanceChoose(float preferred, float leftCost, float rightCost, float leftFits, float rightFits) {
    float distinct = max(0.0, sign(abs(leftCost - rightCost) - HORDE_AVOID_SIDE_TIE));
    float byCost = mix(preferred, sign(leftCost - rightCost), distinct);
    float byFit = sign(rightFits - leftFits);
    return mix(byCost, byFit, abs(byFit));
}

// 遭遇中不更换已锁定的符号；只有无遭遇并返回自然摆动后才释放。
float hordeAvoidanceSide(float locked, float preferred, float encounter, float arrived) {
    float acquired = mix(locked, preferred, encounter * (1.0 - abs(locked)));
    return acquired * (1.0 - (1.0 - encounter) * arrived);
}
