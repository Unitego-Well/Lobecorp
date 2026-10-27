// 分割让步：结果只是站程延迟与横向偏移，全部由割圆几何和单位行列决定。
// 不保存逐单位速度、历史或左右选择，因此视角变化、丢帧和重载都不会造成“无过渡重填”。
// 割圆数据每槽 5 字：中心 xz（事件局部）、半径、Y 下界、Y 上界。
void hordeSplitOffset(float station, float lateral, float seed, out float delay, out float lateralOffset) {
    delay = 0.0;
    lateralOffset = 0.0;
    int count = int(SplitInfo.x);
    if (count <= 0 || SplitShape.x <= 0.0) {
        return;
    }
    vec2 forward = routeForward(station);
    vec2 side = vec2(-forward.y, forward.x);
    vec3 center = location(station, 0.0);
    float band = SplitInfo.z;
    float strength = SplitShape.x;
    float lead = SplitShape.y;
    float maximum = SplitShape.z;
    float probeHeight = LightRoute.w;
    // 两次迭代让多个重叠割圆的推出结果收敛；迭代次数固定，与帧率和历史无关。
    for (int iteration = 0; iteration < 2; iteration++) {
        for (int slot = 0; slot < count; slot++) {
            int at = slot * 5;
            vec2 circle = vec2(scalar(HordeSplit, at), scalar(HordeSplit, at + 1));
            float radius = scalar(HordeSplit, at + 2);
            float yMin = scalar(HordeSplit, at + 3);
            float yMax = scalar(HordeSplit, at + 4);
            if (radius <= 0.0001 || center.y >= yMax || center.y + probeHeight <= yMin) {
                continue;
            }
            vec2 relative = circle - center.xz;
            float along = dot(relative, forward);
            float across = dot(relative, side) - lateral - lateralOffset;
            float distance = length(vec2(along, across));
            float reach = radius + band + lead;
            if (distance >= reach) {
                continue;
            }
            // 核心内压力满值，核心外到 reach 之间连续衰减，因此迎潮侧提前分流、背潮侧逐渐合流。
            float pressure = 1.0 - smoothstep(radius, reach, distance);
            vec2 away = hordeSafeNormalize(vec2(-along, -across), vec2(0.0, seed >= 0.0 ? -1.0 : 1.0));
            float push = pressure * strength * (reach - radius);
            lateralOffset += away.y * push;
            delay += away.x * push;
        }
    }
    lateralOffset = clamp(lateralOffset, -maximum, maximum);
    delay = clamp(delay, -maximum, maximum);
}
