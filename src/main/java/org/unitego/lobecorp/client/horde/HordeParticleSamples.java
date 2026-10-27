package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// 按进度解析计算的粒子候选：位置只由波头站程、行列和事件时钟决定，不再读回 GPU 历史。
///
/// 与 GPU 的差异只有地形让步的横向位移（CPU 不计算地形流场），因此这里描述的是
/// “按进度与分割算出的位置”，不是 GPU 最终绘制位置；粒子仍只在该位置通过原版筛选后出生。
public class HordeParticleSamples implements AutoCloseable {
    /// 单帧候选上限；候选来自固定格点，轮换取点不等于穷尽覆盖。
    public static final int CAPACITY = 256;
    /// 候选扫描的格点上限，避免超长线路时整条扫描。
    private static final int SCAN_LIMIT = 16384;
    /// 与配置的粒子生成范围一致。
    private static final double RANGE = 32.0;
    private static final long MAX_AGE_NANOS = 100_000_000L;
    private static final ConcurrentHashMap<UUID, HordeParticleSamples> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private volatile boolean closed;
    private volatile @Nullable Snapshot snapshot;
    private HordeClient.ClientEvent owner;
    private long lastCapture;

    /// 位序为行列偏移(-1,-1)、(-1,0)、(-1,1)、(0,-1)、(0,1)、(1,-1)、(1,0)、(1,1)。
    /// 缺邻仅表示边界候选，也可能是未绘制邻居；出生前仍须核对当前分割和其他浪潮。
    public record Sample(long row, int column, Vec3 ground, int occupancyMask, double station) {
        public boolean inWave(double tail, double head) {
            return Double.isFinite(station) && Double.isFinite(tail) && Double.isFinite(head)
                    && station >= tail && station <= head;
        }

        public boolean interior() {
            return occupancyMask == 255;
        }

        public boolean boundary() {
            return !interior();
        }
    }

    public record Snapshot(UUID eventId, double eventTime, long drawSerial, long generation,
                           long capturedNanos, List<Sample> samples) {
        public Snapshot {
            samples = List.copyOf(samples);
        }

        public boolean fresh(double currentTime, long nowNanos) {
            double age = currentTime - eventTime;
            long elapsed = nowNanos - capturedNanos;
            return Double.isFinite(age) && age >= 0 && age <= 1 && elapsed >= 0 && elapsed <= MAX_AGE_NANOS;
        }
    }

    /// 返回不过期且不晚于调用方事件时钟的快照；无证据、卸载或重载均返回null。
    public static @Nullable Snapshot latest(HordeClient.ClientEvent event, double eventTime) {
        var source = ACTIVE.get(event.id);
        if (source == null || source.closed || source.owner != event) {
            return null;
        }
        var value = source.snapshot;
        return value != null && value.fresh(eventTime, System.nanoTime())
                && value.generation() == GENERATION.get() ? value : null;
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
        ACTIVE.clear();
    }

    /// 每帧按当前批次的行列范围取有界样本；位置由 [HordeTide] 纯函数给出。
    public void capture(HordeClient.ClientEvent event, double time, long serial,
                        long firstRow, int firstColumn, int columns) {
        var tide = event.tide;
        long now = System.nanoTime();
        if (closed || tide == null || now - lastCapture < 25_000_000L) {
            return;
        }
        owner = event;
        ACTIVE.put(event.id, this);
        lastCapture = now;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        // 用玩家位置作距离基准：粒子最终也只在玩家附近出生，且该位置在 tick 与渲染线程都稳定。
        var camera = player != null ? player.position() : minecraft.gameRenderer.getMainCamera().position();
        double head = HordeTide.head(time, event.speed);
        double rowSpacing = event.rules.rowSpacing();
        double columnSpacing = event.rules.columnSpacing();
        double rangeSquared = RANGE * RANGE;
        var candidates = new ArrayList<Candidate>();
        int rows = Math.max(1, (int) Math.ceil(head / rowSpacing));
        // 以相机所在站程为中心取行窗口：只扫潮尾会让靠近相机的一段没有候选。
        long centerRow = Math.round((head - tide.stationOf(camera)) / rowSpacing);
        // 行窗口按事件总列数换算，保证行跨度乘以列数不超过扫描上限。
        long span = Math.max(1, SCAN_LIMIT / Math.max(1, event.columns));
        long from = Math.max(0, centerRow - span / 2);
        long to = Math.min(rows, from + span);
        to = Math.max(from, Math.min(to, rows));
        for (long row = from; row <= to; row++) {
            double station = HordeTide.station(head, row, rowSpacing);
            if (station < 0 || station > event.length) {
                continue;
            }
            for (int column = 0; column < event.columns; column++) {
                var ground = tide.position(row, column, event.columns, time, rowSpacing, columnSpacing, event.speed);
                if (ground.distanceToSqr(camera) > rangeSquared) {
                    continue;
                }
                int mask = occupancy(tide, event, time, head, row, column, rowSpacing, columnSpacing, ground);
                long rank = rank(serial, row, column);
                candidates.add(new Candidate(rank, new Sample(row, column, ground, mask, station)));
            }
        }
        candidates.sort((first, second) -> Long.compare(first.rank, second.rank));
        int size = Math.min(CAPACITY, candidates.size());
        var samples = new ArrayList<Sample>(size);
        for (int index = 0; index < size; index++) {
            samples.add(candidates.get(index).sample);
        }
        snapshot = new Snapshot(event.id, time, serial, GENERATION.get(), now, samples);
        if (serial % 40 == 0) {
            var probe = tide.position(from, firstColumn, event.columns, time, rowSpacing, columnSpacing, event.speed);
            org.unitego.lobecorp.Lobecorp.LOGGER.info(
                    "浪潮粒子采样 rows={}..{} center={} head={} cameraStation={} 候选={} 采样={} camera={} probe={} columns={}",
                    from, to, centerRow, head, tide.stationOf(camera), candidates.size(), samples.size(),
                    camera, probe, event.columns);
        }
    }

    /// 8 邻居占据掩码：同层、在波带内、在列范围内即视为占据，不依赖是否本帧被绘制。
    private static int occupancy(HordeTide tide, HordeClient.ClientEvent event, double time, double head,
                                 long row, int column, double rowSpacing, double columnSpacing, Vec3 ground) {
        int mask = 0;
        int bit = 0;
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                if (rowOffset == 0 && columnOffset == 0) {
                    continue;
                }
                long neighborRow = row + rowOffset;
                int neighborColumn = column + columnOffset;
                boolean inside = neighborRow >= 0 && neighborColumn >= 0 && neighborColumn < event.columns
                        && HordeTide.station(head, neighborRow, rowSpacing) <= event.length
                        && HordeTide.station(head, neighborRow, rowSpacing) >= 0;
                if (inside) {
                    var neighbor = tide.position(neighborRow, neighborColumn, event.columns, time, rowSpacing,
                            columnSpacing, event.speed);
                    inside = Math.abs(neighbor.y - ground.y) <= 1.5
                            && neighbor.distanceToSqr(ground) <= RANGE * RANGE;
                }
                if (inside) {
                    mask |= 1 << bit;
                }
                bit++;
            }
        }
        return mask;
    }

    /// 每帧轮换的稳定排名：同一格点在不同帧有不同排名，因此候选集合会缓慢轮转。
    private static long rank(long serial, long row, int column) {
        long value = serial * 0x9E3779B97F4A7C15L ^ row * 0xC2B2AE3D27D4EB4FL ^ column * 0x165667B19E3779F9L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        return value ^ (value >>> 29);
    }

    private record Candidate(long rank, Sample sample) {
    }

    @Override
    public void close() {
        closed = true;
        snapshot = null;
        if (owner != null) {
            ACTIVE.remove(owner.id, this);
        }
    }
}
