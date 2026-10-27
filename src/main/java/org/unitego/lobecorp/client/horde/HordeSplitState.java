package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.textures.GpuTextureView;

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
    /// 目标消失后仍保留的 tick 数，让割圆收缩而不是突然消失。
    private static final double RETIRE_TICKS = 40;
    /// 中心与半径的连续逼近速率（1/秒）。
    private static final double SMOOTHING = 6.0;
    private static final int INITIAL_CAPACITY = 8;
    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    private HordeDataTexture texture;
    private int[] words = new int[INITIAL_CAPACITY * WORDS];
    private int capacity = INITIAL_CAPACITY;
    private int count;
    private int uploaded;
    private double lastTime = Double.NaN;
    private double maximumRadius;

    /// 一个已经平滑过的割圆；坐标为事件局部坐标，世界几何在 CPU 侧完成换算。
    public record Circle(double centerX, double centerZ, double radius, double minimumY, double maximumY) {
    }

    /// 每帧刷新：把本帧目标集合平滑成圆表并按需上传。
    public void update(HordeClient.ClientEvent event, double time) {
        double delta = Double.isNaN(lastTime) ? 0 : Math.max(0, time - lastTime);
        lastTime = time;
        double alpha = 1.0 - Math.exp(-SMOOTHING * delta / 20.0);
        var origin = event.origin;
        for (var entry : shapes.entrySet()) {
            entry.getValue().seen = false;
        }
        for (var entry : event.regions.entrySet()) {
            var box = entry.getValue();
            double radius = Math.max(box.sizeX(), box.sizeZ()) * 0.5;
            double centerX = box.x() + box.sizeX() * 0.5 - origin.x;
            double centerZ = box.z() + box.sizeZ() * 0.5 - origin.z;
            double minimumY = box.y() - origin.y;
            double maximumY = box.y() + box.sizeY() - origin.y;
            var shape = shapes.get(entry.getKey());
            if (shape == null) {
                shape = new Shape(new Circle(centerX, centerZ, radius, minimumY, maximumY));
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
            }
            // 目标消失后半径连续收缩，避免整片浪潮在下一帧直接穿过原本的禁区。
            shape.current = new Circle(shape.current.centerX, shape.current.centerZ,
                    approach(shape.current.radius, 0, alpha), shape.current.minimumY, shape.current.maximumY);
            if (time >= shape.retireAt || shape.current.radius <= 0.01) {
                iterator.remove();
            }
        }
        publish();
    }

    private static double approach(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    /// 按稳定顺序写成圆表；顺序就是容器插入顺序，不按半径重排，避免每帧换槽位。
    private void publish() {
        var active = new ArrayList<Shape>(shapes.values());
        active.sort((first, second) -> Double.compare(second.current.radius, first.current.radius));
        if (active.size() > MAXIMUM_CIRCLES) {
            active = new ArrayList<>(active.subList(0, MAXIMUM_CIRCLES));
        }
        count = active.size();
        maximumRadius = 0;
        if (count == 0) {
            uploaded = 0;
            return;
        }
        int required = count * WORDS;
        if (texture == null || required > capacity * WORDS) {
            resize(Math.max(INITIAL_CAPACITY, Integer.highestOneBit(Math.max(1, count - 1)) * 2));
        }
        for (int index = 0; index < count; index++) {
            var circle = active.get(index).current;
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

    /// 当前平滑后的圆，供调试叠层与基准报告读取。
    public List<Circle> circles() {
        var result = new ArrayList<Circle>(shapes.size());
        for (var shape : shapes.values()) {
            result.add(shape.current);
        }
        return result;
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
        count = 0;
        uploaded = 0;
        capacity = INITIAL_CAPACITY;
        words = new int[capacity * WORDS];
        lastTime = Double.NaN;
    }

    private static class Shape {
        private Circle current;
        private boolean seen;
        private double retireAt = Double.POSITIVE_INFINITY;

        private Shape(Circle current) {
            this.current = current;
        }
    }
}
