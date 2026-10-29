package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// 有界 CPU 代表点快照：用同源割圆近似 GPU 分流，不代表 GPU 实际脚点。
public class HordeParticleSamples implements AutoCloseable {
    public static final int CAPACITY = HordeParticleFlow.SAMPLE_LIMIT;
    private static final long CAPTURE_INTERVAL_NANOS = 100_000_000L;
    private static final long MAX_AGE_NANOS = 150_000_000L;
    private static final double MAX_AGE_TICKS = 3;
    private static final ConcurrentHashMap<UUID, HordeParticleSamples> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private volatile boolean closed;
    private volatile @Nullable Snapshot snapshot;
    private HordeClient.ClientEvent owner;
    private long lastCapture;
    private long captureGeneration = -1;
    private long captureSerial;
    private final HordeParticleFlow flow = new HordeParticleFlow();

	/// 位序为纵横偏移(-1,-1)至(1,1)，跳过中心；位值来自变形包络和圆空洞探针。
	/// 不依赖稀疏代表点的身份邻接；出生前仍须核对当前分割与可见性。
	/// 每行的粗略变形包络；最终出生偏移也必须落在此包络中。
	public record Envelope(Vec3 center, Vec3 forward, double station,
	                       double low, double high, double tail, double head) {
		public double stationAt(Vec3 point) {
			return station + point.subtract(center).dot(forward);
		}

		public boolean contains(Vec3 point) {
			var delta = point.subtract(center);
			double lateral = -delta.x * forward.z + delta.z * forward.x;
			double along = stationAt(point);
			return along >= tail && along <= head && lateral >= low && lateral <= high;
		}
	}

	public record Sample(long row, int column, Vec3 ground, int occupancyMask, double station,
	                     @Nullable Envelope envelope) {
		public Sample(long row, int column, Vec3 ground, int occupancyMask, double station) {
			this(row, column, ground, occupancyMask, station, null);
		}

		public boolean contains(Vec3 point) {
			return envelope != null && envelope.contains(point);
		}

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
            return Double.isFinite(age) && age >= 0 && age <= MAX_AGE_TICKS && elapsed >= 0 && elapsed <= MAX_AGE_NANOS;
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
        for (var source : ACTIVE.values()) {
            source.snapshot = null;
            source.flow.clear();
        }
        ACTIVE.clear();
    }

    /// 每事件 10 Hz 采样；批次参数不扩大事件代表点预算。
    public void capture(HordeClient.ClientEvent event, double time, long serial,
                        long firstRow, int firstColumn, int columns) {
        var tide = event.tide;
        long now = System.nanoTime();
	    if (closed || tide == null) {
		    return;
	    }

	    if (owner != event || captureGeneration != GENERATION.get()) {
		    if (owner != null) {
			    ACTIVE.remove(owner.id, this);
		    }
		    flow.clear();
		    snapshot = null;
		    lastCapture = 0;
		    owner = event;
		    captureGeneration = GENERATION.get();
	    }

	    if (now - lastCapture < CAPTURE_INTERVAL_NANOS) {
		    return;
	    }

	    ACTIVE.put(event.id, this);
	    lastCapture = now;
	    var minecraft = Minecraft.getInstance();
	    var player = minecraft.player;
	    var camera = player != null ? player.position() : minecraft.gameRenderer.getMainCamera().position();
	    var field = new HordeParticleFlow.Field(tide, event.origin, event.split.circles(), time, event.speed,
			    event.stop, event.columns, event.rules.rowSpacing(),
			    event.rules.columnSpacing(), event.entityHeight);
	    var samples = flow.sample(field, camera, ++captureSerial);
	    snapshot = new Snapshot(event.id, time, serial, captureGeneration, now, samples);
    }

    @Override
    public void close() {
        closed = true;
        snapshot = null;
        flow.clear();
        if (owner != null) {
            ACTIVE.remove(owner.id, this);
        }
    }
}
