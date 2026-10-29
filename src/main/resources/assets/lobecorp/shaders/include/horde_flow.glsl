// 目标区域与地形使用同一连续排斥函数，方向由稳定的两侧分流决定。
float hordeGapPush(float along, float across, float radius, float forwardHalf, float side) {
    float need = radius - side * across;
    float softness = max(0.1, radius * 0.1);
    float positive = 0.5 * (need + sqrt(need * need + softness * softness));
    float alongWeight = 1.0 - smoothstep(forwardHalf, forwardHalf + max(2.0, radius * 2.0), abs(along));
    float lateralWeight = 1.0 - smoothstep(radius, radius + max(0.5, radius * 0.5), abs(across));
    return positive * alongWeight * lateralWeight;
}

float hordeMergePush(float first, float second) {
    float rounding = 0.5 * first * second / max(first + second, 0.0001);
    return 0.5 * (first + second + sqrt((first - second) * (first - second) + rounding * rounding));
}

// 临界阻尼的解析解；更新中断时继续沿用GPU位置与速度。
vec2 hordeDamped(float value, float velocity, float target, float omega, float dt) {
    float error = value - target;
    float temporary = (velocity + omega * error) * dt;
    float decay = exp(-omega * dt);
    return vec2(target + (error + temporary) * decay, (velocity - omega * temporary) * decay);
}
