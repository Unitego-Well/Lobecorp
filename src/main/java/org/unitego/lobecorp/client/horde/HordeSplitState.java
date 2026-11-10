package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 分割割圆表：CPU 平滑后每帧上传一次只读圆表，GPU 只按圆几何做连续让步。
///
/// 不再维护 GPU 阻尼状态机与空间索引：圆的数量很小（玩家、目标、参战成员），
/// 直接线性遍历比重建多层位图更简单，也不会出现索引漏格导致“区域被重新填满”。
public class HordeSplitState implements AutoCloseable {
    /// 每个圆 5 个字：中心 xz、半径、Y 下界、Y 上界（事件局部坐标）。
    public static final int WORDS = 5;
    /// 单事件上限；超出时按半径从大到小保留，避免无界增长。
    public static final int MAXIMUM_CIRCLES = 64;
    /// 退场在40 tick内连续收至精确零，再移除圆。
    private static final double RETIRE_TICKS = 40;
    /// 中心与半径的连续逼近速率（1/秒）。
    private static final double SMOOTHING = 6.0;
    /// 纵向缓冲带：基础格数、速度系数及用户指定的整体倍率。
    private static final double BASE_BAND = 3.2, SPEED_BAND = 8.0, BAND_SCALE = 0.8;
    // 与 horde_split.glsl 同步；最小成员圆达到全强度，零半径时引导带也失去作用。
    private static final double FADE_RADIUS = 0.5;
    /// 两个源圆表面相距不足 2 格时逐渐建立局部软连接。
    private static final double MERGE_GAP = 2.0;
    /// 连接圆半径最多 1.5 格，不用包围巨圆填平链式区域之间的空地。
    private static final double MAXIMUM_BRIDGE_RADIUS = 1.5;
    /// Y 重叠不足半格时收细连接，接触或分离时连续归零。
    private static final double BRIDGE_HEIGHT_FADE = 0.5;
    private static final int INITIAL_CAPACITY = 8;
    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    /// 与上传共用的不可修改快照；连接圆只消耗源圆之后的剩余槽位。
    private List<Circle> circleSnapshot = List.of();
    /// 保留仍有效的连接槽位；新获槽位的连接从零强度渐入，避免预算竞争时跳出整圆。
    private final Map<BridgePair, Double> bridgeWeights = new LinkedHashMap<>();
    private HordeDataTexture texture;
    private int[] words = new int[INITIAL_CAPACITY * WORDS];
    private int capacity = INITIAL_CAPACITY;
    private int count;
    private int uploaded;
    private double lastTime = Double.NaN;
    private double maximumRadius;

    /// 一个已经平滑过的割圆；坐标为事件局部坐标，世界几何在 CPU 侧完成换算。
    public record Circle(double centerX, double centerZ, double radius, double minimumY, double maximumY) {
        public HordeClient.RegionBox bounds(Vec3 origin) {
            return new HordeClient.RegionBox(origin.x + centerX - radius, origin.y + minimumY,
                    origin.z + centerZ - radius, radius * 2, maximumY - minimumY, radius * 2);
        }
    }

    /// 引导带和固定外扩随小圆一起退场，与 GPU 的 smoothstep 保持一致。
    public static double influence(double radius) {
        double weight = Math.clamp(radius / FADE_RADIUS, 0, 1);
        return weight * weight * (3 - 2 * weight);
    }

    /// 视觉纵向缓冲带，单位为格；CPU轮廓和GPU参数共用。
    public static double band(double speed) {
        return (BASE_BAND + speed * SPEED_BAND) * BAND_SCALE;
    }

    /// 每帧刷新：把本帧目标集合平滑成圆表并按需上传。
    public void update(HordeClient.ClientHorde event, double time) {
        updateCircles(event.regions, event.origin, time);
        publish();
    }

    /// 只更新 CPU 几何，不分配或上传 GPU 资源，供无游戏回归复用。
    public void updateCircles(Map<String, HordeClient.RegionBox> regions, Vec3 origin, double time) {
        double delta = Double.isNaN(lastTime) ? 0 : Math.max(0, time - lastTime);
        lastTime = time;
        double alpha = 1.0 - Math.exp(-SMOOTHING * delta / 20.0);
        for (var entry : shapes.entrySet()) {
            entry.getValue().seen = false;
        }
        for (var entry : regions.entrySet()) {
            var box = entry.getValue();
            double radius = Math.max(box.sizeX(), box.sizeZ()) * 0.5;
            double centerX = box.x() + box.sizeX() * 0.5 - origin.x;
            double centerZ = box.z() + box.sizeZ() * 0.5 - origin.z;
            double minimumY = box.y() - origin.y;
            double maximumY = box.y() + box.sizeY() - origin.y;
            var shape = shapes.get(entry.getKey());
            if (shape == null) {
                shape = new Shape(new Circle(centerX, centerZ, 0, minimumY, maximumY));
                shapes.put(entry.getKey(), shape);
            } else {
                shape.current = new Circle(
                        approach(shape.current.centerX, centerX, alpha),
                        approach(shape.current.centerZ, centerZ, alpha),
                        approach(shape.current.radius, radius, alpha),
                        approach(shape.current.minimumY, minimumY, alpha),
                        approach(shape.current.maximumY, maximumY, alpha));
            }
            shape.seen = true;
            shape.retireAt = Double.POSITIVE_INFINITY;
        }
        var iterator = shapes.entrySet().iterator();
        while (iterator.hasNext()) {
            var shape = iterator.next().getValue();
            if (shape.seen) {
                continue;
            }
            if (shape.retireAt == Double.POSITIVE_INFINITY) {
                shape.retireAt = time + RETIRE_TICKS;
                shape.retireRadius = shape.current.radius;
            }
            double remaining = Math.clamp((shape.retireAt - time) / RETIRE_TICKS, 0, 1);
            double radius = shape.retireRadius * remaining * remaining * (3 - 2 * remaining);
            shape.current = new Circle(shape.current.centerX, shape.current.centerZ,
                    radius, shape.current.minimumY, shape.current.maximumY);
            if (time >= shape.retireAt) {
                iterator.remove();
            }
        }
        refreshCircleSnapshot(alpha);
    }

    private void refreshCircleSnapshot(double alpha) {
        var sources = new ArrayList<>(shapes.values());
        if (sources.size() > MAXIMUM_CIRCLES) {
            sources.sort((first, second) -> Double.compare(second.current.radius(), first.current.radius()));
            sources.subList(MAXIMUM_CIRCLES, sources.size()).clear();
        }

        var result = new ArrayList<Circle>(MAXIMUM_CIRCLES);
        for (var source : sources) {
            result.add(source.current);
        }

        // 先保留仍有效的连接；配对身份使用源 Shape，不受每帧新 Circle 实例影响。
        var iterator = bridgeWeights.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pair = entry.getKey();
            var circle = sources.contains(pair.first()) && sources.contains(pair.second())
                    ? bridge(pair.first().current, pair.second().current) : null;
            if (circle == null || result.size() >= MAXIMUM_CIRCLES) {
                iterator.remove();
                continue;
            }

            double weight = approach(entry.getValue(), 1, alpha);
            entry.setValue(weight);
            result.add(weightedBridge(circle, weight));
        }

        // 只配对源圆；最多检查 64×63/2 对，已有连接的复查也不超过 64 对。
        for (int first = 0; first < sources.size() && result.size() < MAXIMUM_CIRCLES; first++) {
            for (int second = first + 1; second < sources.size() && result.size() < MAXIMUM_CIRCLES; second++) {
                var circle = bridge(sources.get(first).current, sources.get(second).current);
                if (circle == null) {
                    continue;
                }

                var pair = new BridgePair(sources.get(first), sources.get(second));
                if (!bridgeWeights.containsKey(pair)) {
                    bridgeWeights.put(pair, alpha);
                    result.add(weightedBridge(circle, alpha));
                }
            }
        }

        circleSnapshot = List.copyOf(result);
    }

    private static Circle weightedBridge(Circle circle, double weight) {
        return new Circle(circle.centerX(), circle.centerZ(), circle.radius() * weight,
                circle.minimumY(), circle.maximumY());
    }

    private static Circle bridge(Circle first, Circle second) {
        double minimumY = Math.max(first.minimumY(), second.minimumY());
        double maximumY = Math.min(first.maximumY(), second.maximumY());
        double smallerRadius = Math.min(first.radius(), second.radius());
        if (maximumY <= minimumY || smallerRadius <= 0) {
            return null;
        }

        double dx = second.centerX() - first.centerX();
        double dz = second.centerZ() - first.centerZ();
        double distance = Math.hypot(dx, dz);
        double gap = distance - first.radius() - second.radius();
        if (gap >= MERGE_GAP || gap <= -smallerRadius) {
            return null;
        }

        // 已深度重叠时连接也归零，避免重合小圆产生多余的软并集压力。
        double proximity = influence(FADE_RADIUS * (1 - Math.max(0, gap) / MERGE_GAP));
        double overlap = influence(FADE_RADIUS * (1 + Math.min(0, gap) / smallerRadius));
        double height = influence(FADE_RADIUS * (maximumY - minimumY) / BRIDGE_HEIGHT_FADE);
        double radius = Math.min(smallerRadius, MAXIMUM_BRIDGE_RADIUS)
                * influence(smallerRadius) * proximity * overlap * height;
        if (radius <= 0) {
            return null;
        }

        // 连接中心取两侧相向表面的中点，不受大圆中心偏置，也不扩大 Y 区间。
        double along = (distance + first.radius() - second.radius()) * 0.5 / distance;
        return new Circle(first.centerX() + dx * along, first.centerZ() + dz * along,
                radius, minimumY, maximumY);
    }

    private static double approach(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    /// 上传与 circles() 相同的快照；源圆在前，局部连接在后。
    private void publish() {
        count = circleSnapshot.size();
        maximumRadius = 0;
        if (count == 0) {
            if (texture == null) {
                resize(INITIAL_CAPACITY);
            }
            uploaded = 0;
            return;
        }
        int required = count * WORDS;
        if (texture == null || required > capacity * WORDS) {
            resize(Math.max(INITIAL_CAPACITY, Integer.highestOneBit(Math.max(1, count - 1)) * 2));
        }
        for (int index = 0; index < count; index++) {
            var circle = circleSnapshot.get(index);
            int at = index * WORDS;
            words[at] = Float.floatToRawIntBits((float) circle.centerX());
            words[at + 1] = Float.floatToRawIntBits((float) circle.centerZ());
            words[at + 2] = Float.floatToRawIntBits((float) Math.max(0, circle.radius()));
            words[at + 3] = Float.floatToRawIntBits((float) circle.minimumY());
            words[at + 4] = Float.floatToRawIntBits((float) circle.maximumY());
            maximumRadius = Math.max(maximumRadius, circle.radius());
        }
        texture.update(0, words, 0, required);
        uploaded = required;
    }

    private void resize(int circles) {
        var replacement = new HordeDataTexture("Horde split circles", new int[circles * WORDS]);
        if (texture != null) {
            texture.close();
        }
        texture = replacement;
        capacity = circles;
        words = new int[capacity * WORDS];
    }

    /// 圆表纹理；没有割圆时保留一张全零纹理，管线绑定无需分支。
    public HordeDataTexture texture() {
        if (texture == null) {
            resize(INITIAL_CAPACITY);
        }
        return texture;
    }

    public int count() {
        return count;
    }

    /// 供管线绑定使用的纹理视图；没有割圆时也返回合法视图。
    public GpuTextureView view() {
        return texture().texture().getTextureView();
    }

    public int uploadedWords() {
        return uploaded;
    }

    public double maximumRadius() {
        return maximumRadius;
    }

    /// 与最终上传共用的只读几何快照，包含平滑源圆及其局部连接；后续更新不改变旧快照。
    public List<Circle> circles() {
        return circleSnapshot;
    }

    /// 距上次上传的圆表字数，调试叠层用它区分“圆在变”与“只是重传”。
    public int capacityCircles() {
        return capacity;
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        shapes.clear();
        circleSnapshot = List.of();
        bridgeWeights.clear();
        count = 0;
        uploaded = 0;
        capacity = INITIAL_CAPACITY;
        words = new int[capacity * WORDS];
        lastTime = Double.NaN;
    }

    private record BridgePair(Shape first, Shape second) {
    }

    private static class Shape {
        private Circle current;
        private boolean seen;
        private double retireAt = Double.POSITIVE_INFINITY;
	    private double retireRadius;

        private Shape(Circle current) {
            this.current = current;
        }
    }
}
