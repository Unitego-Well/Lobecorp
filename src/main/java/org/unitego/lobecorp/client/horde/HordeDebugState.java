package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/// 浪潮调试快照：集中保存“按进度的潮 + 分割”所需的观测量，供叠层、Gizmo 与日志读取。
///
/// 只做数据归集，不参与渲染或运动计算；字段为当帧值，跨帧统计另外累加。
public final class HordeDebugState {
    private static int events;
    private static int batches;
    private static long instances;
    private static int groups;
    private static int splitCircles;
    private static double splitMaximumRadius;
    private static int splitWords;
    private static int splitCapacity;
    private static long gapChanges;
    private static double head;
    private static double tail;
    private static long firstRow;
    private static long lastRow;
    private static long drawnTotal;
    private static String lastNotice = "";

    private HordeDebugState() {
    }

    /// 每帧开始前清空当帧值，避免事件结束后叠层仍显示旧数据。
    public static void beginFrame() {
        batches = 0;
        instances = 0;
        groups = 0;
        splitCircles = 0;
        splitWords = 0;
    }

    public static void setEvents(int count) {
        events = count;
    }

    public static void setBatches(int count, long submitted) {
        batches = count;
        instances = submitted;
    }

    public static void setGroups(int count) {
        groups = count;
    }

    public static void setTide(double headStation, double tailStation, long fromRow, long toRow) {
        head = headStation;
        tail = tailStation;
        firstRow = fromRow;
        lastRow = toRow;
    }

    public static void addSplit(HordeSplitState split) {
        if (split == null) {
            return;
        }
        splitCircles = Math.max(splitCircles, split.count());
        splitMaximumRadius = Math.max(splitMaximumRadius, split.maximumRadius());
        splitWords += split.uploadedWords();
        splitCapacity = Math.max(splitCapacity, split.capacityCircles());
    }

    /// 分割目标集合发生变化的次数；持续增长说明“区域在变”，为 0 说明问题在显示侧。
    public static void gapChanged() {
        gapChanges++;
    }

    public static void drawn(long count) {
        drawnTotal += count;
    }

    public static void notice(String text) {
        lastNotice = text == null ? "" : text;
    }

    /// 叠层文本：每行一个观测量，单位与含义写在行内，便于对照实机现象。
    public static List<String> lines() {
        var result = new ArrayList<String>(8);
        result.add("Horde 事件=" + events + " 批次=" + batches + " 绘制组=" + groups + " 实例=" + instances
                + " 累计=" + drawnTotal);
        result.add("潮头站程=" + format(head) + " 潮尾站程=" + format(tail)
                + " 可见行=" + firstRow + ".." + lastRow);
        result.add("分割圆=" + splitCircles + "/" + splitCapacity + " 最大半径=" + format(splitMaximumRadius)
                + " 圆表字数=" + splitWords + " 目标变化=" + gapChanges);
        result.add(HordeClient.statistics());
        result.add(HordeGpuRenderer.statistics());
        if (!lastNotice.isEmpty()) {
            result.add("提示：" + lastNotice);
        }
        return result;
    }

    /// 分割圆的世界几何，Gizmo 直接读取；不参与渲染管线。
    public static List<SplitGizmo> splitGizmos() {
        var result = new ArrayList<SplitGizmo>();
        for (var event : HordeClient.events()) {
            if (!event.ready || event.failed) {
                continue;
            }
            for (var circle : event.split.circles()) {
                if (circle.radius() <= 0.05) {
                    continue;
                }
                var center = event.origin.add(circle.centerX(), circle.minimumY() + 0.05, circle.centerZ());
                result.add(new SplitGizmo(center, circle.radius(), circle.maximumY() - circle.minimumY()));
            }
        }
        return result;
    }

    /// 潮头与潮尾的采样点，用于确认“按进度”推进的位置与可见行范围一致。
    public static List<TideGizmo> tideGizmos() {
        var result = new ArrayList<TideGizmo>();
        for (var event : HordeClient.events()) {
            if (!event.ready || event.failed || event.tide == null || event.nodes == null) {
                continue;
            }
            double now = event.time(0);
            double headStation = HordeTide.head(now, event.speed);
            int middle = event.columns / 2;
            long headRow = 0;
            long tailRow = (long) Math.floor(Math.min(headStation, event.length) / event.rules.rowSpacing());
            result.add(new TideGizmo(position(event, headRow, middle, now), true));
            if (tailRow > 0) {
                result.add(new TideGizmo(position(event, tailRow, middle, now), false));
            }
        }
        return result;
    }

    private static Vec3 position(HordeClient.ClientEvent event, long row, int column, double now) {
        return event.tide.position(row, column, event.columns, now, event.rules.rowSpacing(),
                event.rules.columnSpacing(), event.speed);
    }

    private static String format(double value) {
        return Double.isFinite(value) ? String.format("%.2f", value) : "n/a";
    }

    /// 分割圆的世界几何：中心、半径与高度带，Gizmo 只读快照。
    public record SplitGizmo(Vec3 center, double radius, double height) {
    }

    /// 潮头(true)与潮尾(false)的世界采样点。
    public record TideGizmo(Vec3 point, boolean head) {
    }
}
