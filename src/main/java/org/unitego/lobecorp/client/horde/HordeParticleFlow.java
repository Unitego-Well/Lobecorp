package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/// 粒子专用稀疏流场；固定槽位保存短时横移速度，不保存全部单位状态或读回 GPU。
public class HordeParticleFlow {
	private static final int ROW_SAMPLES = 14;
	private static final int COLUMN_SAMPLES = 16;
	/// 每行含两个外包络代表点，候选与包络代表点合计不超过 252。
	public static final int CANDIDATE_LIMIT = ROW_SAMPLES * (COLUMN_SAMPLES + 2);
	public static final int SAMPLE_LIMIT = 128;
	public static final double RANGE = HordeVisualConfig.MAXIMUM_PARTICLE_RANGE;
	private static final double DRIFT_MARGIN = 1.8;
	private static final double SWAY_MARGIN = 1.5;
	private static final double MAXIMUM_OFFSET = 16;
	private static final double SPEED_RATIO = 0.7;
	private static final double ACCELERATION_TICKS = 4;
	/// 仅代表点的模型半径近似；不读取模型或复制 GPU 逐单位解算。
	private static final double MODEL_RADIUS = 0.6;
	private static final double RESET_DISTANCE = 8;
	private static final double MAXIMUM_DELTA_TICKS = 3;
	/// 新建或重定位的代表点先连续更新 4 tick，再参与粒子；这不是 GPU 历史恢复。
	private static final double WARMUP_TICKS = 4;
	private final Slot[] slots = new Slot[CANDIDATE_LIMIT];
	private double lastTime = Double.NaN;

	public record Field(HordeTide tide, Vec3 origin, List<HordeSplitState.Circle> circles,
	                    double time, double speed, long stop, int columns,
	                    double rowSpacing, double columnSpacing, double entityHeight) {
	}

	public List<HordeParticleSamples.Sample> sample(Field field, Vec3 camera, long phase) {
		double dt = field.time() - lastTime;
		if (!Double.isFinite(dt) || dt < 0 || dt > MAXIMUM_DELTA_TICKS) {
			clear();
			dt = 0;
		}

		lastTime = field.time();
		double head = Math.min(field.tide().length(), HordeTide.head(field.time(), field.speed()));
		double tail = Math.max(0, (field.time() - field.stop()) * field.speed());
		if (head < tail || field.columns() <= 0) {
			return List.of();
		}

		double cameraStation = field.tide().particleStation(camera);
		double progressed = HordeTide.head(field.time(), field.speed());
		long firstRow = Math.max(Math.max(0, (long) Math.floor((progressed - field.tide().length())
				/ field.rowSpacing()) + 1), (long) Math.ceil((progressed
				- Math.min(head, cameraStation + RANGE + DRIFT_MARGIN)) / field.rowSpacing()));
		long lastRow = Math.min((long) Math.floor(Math.min(field.stop(), field.time()) * field.speed()
				/ field.rowSpacing()), (long) Math.floor((progressed
				- Math.max(tail, cameraStation - RANGE - DRIFT_MARGIN)) / field.rowSpacing()));
		var direction = field.tide().forward(cameraStation);
		var center = field.tide().world(cameraStation, 0);
		double cameraLateral = -(camera.x - center.x) * direction.z + (camera.z - center.z) * direction.x;
		double reach = RANGE + MAXIMUM_OFFSET + SWAY_MARGIN;
		int firstColumn = Math.max(0, (int) Math.ceil((cameraLateral - reach) / field.columnSpacing()
				+ (field.columns() - 1) * 0.5));
		int lastColumn = Math.min(field.columns() - 1, (int) Math.floor((cameraLateral + reach)
				/ field.columnSpacing() + (field.columns() - 1) * 0.5));
		if (lastRow < firstRow || lastColumn < firstColumn) {
			return List.of();
		}

		int rows = (int) Math.min(ROW_SAMPLES, lastRow - firstRow + 1);
		int columns = Math.min(COLUMN_SAMPLES, lastColumn - firstColumn + 1);
		var result = new ArrayList<HordeParticleSamples.Sample>(SAMPLE_LIMIT);
		var random = new Random(phase);
		int accepted = 0;
		for (int r = 0; r < rows; r++) {
			long row = select(firstRow, lastRow, r, rows, phase);
			double station = HordeTide.station(HordeTide.head(field.time(), field.speed()), row, field.rowSpacing());
			var forward = field.tide().forward(station);
			var side = new Vec3(-forward.z, 0, forward.x);
			var line = field.tide().world(station, 0);
			int base = r * (COLUMN_SAMPLES + 2);
			var left = position(field, row, 0, base + COLUMN_SAMPLES, dt);
			var right = position(field, row, field.columns() - 1, base + COLUMN_SAMPLES + 1, dt);
			double low = left.subtract(line).dot(side) - field.columnSpacing() * 0.5;
			double high = right.subtract(line).dot(side) + field.columnSpacing() * 0.5;
			for (int c = 0; c < columns; c++) {
				int column = (int) select(firstColumn, lastColumn, c, columns, phase);
				var point = position(field, row, column, base + c, dt);
				double actualStation = station + point.subtract(line).dot(forward);
				double lateral = point.subtract(line).dot(side);
				if (!slots[base + c].warm() || !slots[base + COLUMN_SAMPLES].warm()
						|| !slots[base + COLUMN_SAMPLES + 1].warm()
						|| point.distanceToSqr(camera) > RANGE * RANGE || actualStation < tail || actualStation > head
						|| lateral < low || lateral > high
						|| !clear(field.circles(), field.origin(), point, field.entityHeight())) {
					continue;
				}

				int mask = occupancy(field, point, actualStation, lateral, low, high, tail, head, forward);
				var envelope = new HordeParticleSamples.Envelope(line, forward, station, low, high, tail, head);
				var value = new HordeParticleSamples.Sample(row, column, point, mask, actualStation, envelope);
				int index = random.nextInt(++accepted);
				if (result.size() < SAMPLE_LIMIT) {
					result.add(value);
				} else if (index < SAMPLE_LIMIT) {
					result.set(index, value);
				}
			}
		}

		return result;
	}

	/// 先选行列再求位置；保留两端，内部按采样轮次在各自分层内轮换，不排序格点。
	public static long select(long first, long last, int index, int count, long phase) {
		if (count <= 1 || index == 0) {
			return first;
		}
		if (index == count - 1) {
			return last;
		}

		long span = last - first;
		if (span + 1 <= count) {
			return first + index;
		}

		// 在分层内部往返轮换，避免 7→0 回绕使全部代表点同时重新预热。
        long turn = Math.floorMod(phase, 16);
        double fraction = ((turn < 8 ? turn : 15 - turn) + 0.5) / 8.0;
		long low = first + 1 + (index - 1) * (span - 1) / (count - 2);
		long high = first + index * (span - 1) / (count - 2);
		return low + (long) ((high - low + 1) * fraction);
	}

    private Vec3 position(Field field, long row, int column, int index, double dt) {
        double seed = HordeTide.seed(row, column);
        double station = HordeTide.station(HordeTide.head(field.time(), field.speed()), row, field.rowSpacing())
                + HordeTide.drift(field.time(), seed) * Math.min(1, field.speed() / 0.25);
        double lateral = HordeTide.lateral(column, field.columns(), field.columnSpacing());
        var anchor = field.tide().world(station, lateral);
        double natural = HordeTide.sway(field.time(), seed);
        var slot = slots[index];
        if (slot == null || slot.anchor.distanceToSqr(anchor) > RESET_DISTANCE * RESET_DISTANCE) {
            slot = new Slot();
            slot.total = natural;
            slot.previousNatural = natural;
            slots[index] = slot;
            dt = 0;
        }

        slot.anchor = anchor;
        var forward = field.tide().forward(station);
        double target = target(field, anchor, forward, seed, slot);
        slot.advance(natural + target, natural, field.speed() * SPEED_RATIO, dt);
        return field.tide().world(station, lateral + slot.total);
    }

	/// 与 GLSL 相同类型的纵横软压力、柔和正部和并集；省略地形流和个体精确半径。
	private static double target(Field field, Vec3 anchor, Vec3 forward, double seed, Slot slot) {
		double left = 0;
		double right = 0;
		var local = anchor.subtract(field.origin());
		double radiusScale = 0.8 + 0.55 * HordeTide.random(seed, 17);
		double phaseShift = Math.sin(HordeTide.random(seed, 13) * Math.PI * 2) * 0.2;
		double driftBound = 0.102 * Math.min(1, field.speed() / 0.25);
		for (var circle : field.circles()) {
			if (circle.radius() <= 0 || local.y >= circle.maximumY()
					|| local.y + field.entityHeight() <= circle.minimumY()) {
				continue;
			}

			double x = circle.centerX() - local.x;
			double z = circle.centerZ() - local.z;
			double along = x * forward.x + z * forward.z;
			double across = -x * forward.z + z * forward.x;
			double fade = HordeSplitState.influence(circle.radius());
			double clearance = (circle.radius() + (0.45 + MODEL_RADIUS) * fade)
					* radiusScale;
			double band = (3.2 + field.speed() * 8) * fade;
			double required = Math.min(softDelta(Math.abs(across), clearance, 1) * 1.45, MAXIMUM_OFFSET)
					+ SWAY_MARGIN;
			double lead = (field.speed() + driftBound)
					* (required / Math.max(field.speed() * SPEED_RATIO, 0.000001) + ACCELERATION_TICKS)
					* 1.8 + band;
			double pressure = pressure(along - phaseShift * clearance, across, clearance, lead, band);
			left = union(left, pressure * fade * softDelta(across, clearance, -1));
			right = union(right, pressure * fade * softDelta(across, clearance, 1));
		}

		if (left == 0 && right == 0) {
			return 0;
		}

		if (slot.side == 0) {
			slot.side = Math.abs(left - right) > 0.25 ? (left < right ? -1 : 1)
					: (HordeTide.random(seed, 7) < 0.5 ? -1 : 1);
		}

		double strength = 1.2 + 0.25 * HordeTide.random(seed, 11);
		return slot.side * Math.min(MAXIMUM_OFFSET, strength * (slot.side < 0 ? left : right));
	}

	private static double softDelta(double across, double clearance, double side) {
		double demand = clearance + 0.45 * side * across;
		return 0.5 * (demand + Math.sqrt(demand * demand + 0.36));
	}

	private static double pressure(double along, double across, double clearance, double lead, double band) {
		double axis = along / Math.max(clearance + (along > 0 ? lead : band), 0.001);
		double longitudinal = Math.max(0, 1 - axis * axis);
		double lateral = Math.clamp((Math.abs(across) / Math.max(clearance + SWAY_MARGIN, 0.001) - 0.5) * 2, 0, 1);
		double weight = 1 - lateral * lateral * (3 - 2 * lateral);
		return longitudinal * longitudinal * weight * weight;
	}

	private static double union(double first, double second) {
		double width = 0.25 * Math.min(first, second);
		double overlap = Math.max(0, width - Math.abs(first - second));
		return Math.max(first, second) + overlap * overlap / Math.max(4 * width, 0.000001);
	}

	/// 掩码位表示几何探针占据，和采样列表中是否存在相邻身份无关。
	private static int occupancy(Field field, Vec3 point, double station, double lateral, double low, double high,
	                             double tail, double head, Vec3 forward) {
		int mask = 0;
		int bit = 0;
		for (int r = -1; r <= 1; r++) {
			for (int c = -1; c <= 1; c++) {
				if (r == 0 && c == 0) {
					continue;
				}

				double s = station + r * field.rowSpacing();
				double l = lateral + c * field.columnSpacing();
				var probe = point.add(forward.x * r * field.rowSpacing() - forward.z * c * field.columnSpacing(),
						0, forward.z * r * field.rowSpacing() + forward.x * c * field.columnSpacing());
				if (s >= tail && s <= head && l >= low && l <= high
						&& clear(field.circles(), field.origin(), probe, field.entityHeight())) {
					mask |= 1 << bit;
				}
				bit++;
			}
		}

		return mask;
	}

	/// 圆并集的 XZ 窄相位与真实 Y 重叠；不拿引导胶囊或外包盒当禁入区。
	public static boolean clear(List<HordeSplitState.Circle> circles, Vec3 origin, Vec3 point, double height) {
		double y = point.y - origin.y;
		for (var circle : circles) {
			double x = point.x - origin.x - circle.centerX();
			double z = point.z - origin.z - circle.centerZ();
			boolean overlaps = height > 0 ? y + height > circle.minimumY() : y >= circle.minimumY();
			if (circle.radius() > 0 && y < circle.maximumY() && overlaps
					&& x * x + z * z < circle.radius() * circle.radius()) {
				return false;
			}
		}

		return true;
	}

	public void clear() {
		for (int index = 0; index < slots.length; index++) {
			slots[index] = null;
		}
		lastTime = Double.NaN;
	}

    private static class Slot {
        private Vec3 anchor = Vec3.ZERO;
        private double total;
        private double previousNatural;
        private double velocity;
        private double side;
        private double age;

        private boolean warm() {
            return age >= WARMUP_TICKS;
        }

        /// 避让与退场约束总横移；无避让时直接保留自然 sway。
        private void advance(double target, double natural, double limit, double dt) {
            if (dt <= 0) {
                return;
            }

            age += dt;
            double naturalVelocity = (natural - previousNatural) / dt;
            previousNatural = natural;
            if (side == 0) {
                total = natural;
                velocity = naturalVelocity;
                return;
            }

            double error = target - total;
            double acceleration = limit / ACCELERATION_TICKS;
            double desired = Math.signum(error) * Math.min(limit, Math.sqrt(2 * acceleration * Math.abs(error)));
            double previousVelocity = Math.clamp(velocity, -limit, limit);
            velocity = Math.clamp(desired, Math.max(-limit, previousVelocity - acceleration * dt),
                    Math.min(limit, previousVelocity + acceleration * dt));
            double budget = Math.min(limit * dt, Math.abs(error));
            double step = Math.clamp(velocity * dt, -budget, budget);
            total = Math.clamp(total + step, -MAXIMUM_OFFSET, MAXIMUM_OFFSET);
            if (target == natural && total == natural
                    && Math.abs(velocity - naturalVelocity) <= acceleration * dt) {
                side = 0;
            }
        }
    }
}
