package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;

/// 按进度的潮：单位位置只由波头站程、行列和事件时钟决定，不保存任何逐单位状态。
///
/// 本类与 `horde_motion.glsl` 的 `movingLocation` 使用同一组公式，供粒子候选、调试叠层与无游戏测试复用。
/// 地形让步（GPU 侧横向阻尼位移）不在 CPU 版本内，粒子与调试读数按此差异标注。
public final class HordeTide {
    /// 与 `HordeGpuRenderer` 的路线纹理布局一致：每节点 16 个字。
    private static final int NODE_WORDS = 16;
    /// 路线弧长查找表从节点数据之后开始。
    private static final int LOOKUP_BASE = 6;
    private final float[] route;
    private final float[] heights;
    private final int nodes;
    private final int heightColumns;
    private final int lookupCount;
    private final double length;
    private final double groundWidth;
    private final Vec3 origin;

    public HordeTide(float[] route, float[] heights, int nodes, int heightColumns, double length,
                     double groundWidth, Vec3 origin) {
        this.route = route;
        this.heights = heights;
        this.nodes = nodes;
        this.heightColumns = heightColumns;
        this.length = length;
        this.groundWidth = groundWidth;
        this.origin = origin;
        this.lookupCount = Math.max(1, (int) Math.ceil(length) + 1);
    }

    /// 波头站程：事件已行进弧长。
    public static double head(double time, double speed) {
        return time * speed;
    }

    /// 单位站程 = 波头站程 − 行号 × 行距；行号是相对波头的固定身份。
    public static double station(double head, long row, double rowSpacing) {
        return head - row * rowSpacing;
    }

    /// 列横向偏移（线路中心为 0）。
    public static double lateral(int column, int columns, double columnSpacing) {
        return (column - (columns - 1) * 0.5) * columnSpacing;
    }

    /// 个体种子只依赖全局行号与列号，和帧号、视角、历史无关。
    public static double seed(long row, int column) {
        return Math.floorMod(row, 1024) * 17.0 + column * 31.0;
    }

    /// 与 GLSL `motionRandom` 等价的确定性伪随机。
    static double random(double seed, double salt) {
        return fract(Math.sin(seed * 12.9898 + salt * 78.233) * 43758.5453);
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    /// 纵向个体差异，最大约 ±1.8 格，连续且不反向。
    static double drift(double time, double seed) {
        double a = random(seed, 1.0);
        double b = random(seed, 2.0);
        double phase = a * Math.PI * 2.0;
        return 1.2 * Math.sin(time * mix(0.04, 0.07, b) + phase)
                + 0.6 * Math.sin(time * mix(0.015, 0.03, b) + b * Math.PI * 2.0);
    }

    /// 横向摆动，最大约 ±1.5 格。
    static double sway(double time, double seed) {
        double a = random(seed, 1.0);
        double b = random(seed, 2.0);
        double c = random(seed, 3.0);
        double phase = a * Math.PI * 2.0;
        return (0.9 * Math.sin(time * mix(0.036, 0.09, c) + phase)
                + 0.6 * Math.sin(time * mix(0.018, 0.048, a) + b * Math.PI * 2.0)) * mix(0.5, 1.0, b);
    }

    private static double mix(double from, double to, double weight) {
        return from + (to - from) * weight;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return value < minimum ? minimum : Math.min(value, maximum);
    }

    /// 单位在线路上的解析位置（世界坐标），不含分割让步。
    public Vec3 position(long row, int column, int columns, double time, double rowSpacing,
                         double columnSpacing, double speed) {
        double head = head(time, speed);
        double station = station(head, row, rowSpacing) + drift(time, seed(row, column));
        double lateral = lateral(column, columns, columnSpacing) + sway(time, seed(row, column));
        return origin.add(local(station, lateral));
    }

    /// 事件局部坐标下的线路位置；横向地形流为 0，与 GPU 全量让步存在小幅差异。
    public Vec3 local(double station, double lateral) {
        double s = clamp(station, 0.0, length);
        int node = nodeAt(s);
        int next = Math.min(node + 1, nodes - 1);
        int first = node * NODE_WORDS;
        int second = next * NODE_WORDS;
        double from = route[first + 3];
        double to = route[second + 3];
        double weight = clamp((s - from) / Math.max(to - from, 0.0001), 0.0, 1.0);
        double forwardX = mix(route[first + 4], route[second + 4], weight);
        double forwardZ = mix(route[first + 5], route[second + 5], weight);
        double forwardLength = Math.hypot(forwardX, forwardZ);
        if (forwardLength <= 0.0001) {
            forwardX = 0.0;
            forwardZ = 1.0;
            forwardLength = 1.0;
        }
        double sideX = -forwardZ / forwardLength;
        double sideZ = forwardX / forwardLength;
        double centerX = mix(route[first], route[second], weight);
        double centerZ = mix(route[first + 2], route[second + 2], weight);
        double y = mix(terrain(node, lateral, 0), terrain(next, lateral, 0), weight);
        return new Vec3(centerX + sideX * lateral, y, centerZ + sideZ * lateral);
    }


	/// 粒子近似使用的世界坐标入口，不改变原始单位位置公式。
	public Vec3 world(double station, double lateral) {
		return origin.add(local(station, lateral));
	}

	/// 当前站程的插值方向，与线路位置使用相同的节点方向。
	public Vec3 forward(double station) {
		double s = clamp(station, 0, length);
		int node = nodeAt(s);
		int next = Math.min(node + 1, nodes - 1);
		double weight = clamp((s - route[node * NODE_WORDS + 3])
				/ Math.max(route[next * NODE_WORDS + 3] - route[node * NODE_WORDS + 3], 0.0001), 0, 1);
		double x = mix(route[node * NODE_WORDS + 4], route[next * NODE_WORDS + 4], weight);
		double z = mix(route[node * NODE_WORDS + 5], route[next * NODE_WORDS + 5], weight);
		double magnitude = Math.hypot(x, z);
		return magnitude > 0.0001 ? new Vec3(x / magnitude, 0, z / magnitude) : new Vec3(0, 0, 1);
	}

	/// 每次粒子采样仅做一次线段投影，避免相机落在长路段中间时错过附近行。
	public double particleStation(Vec3 world) {
		var point = world.subtract(origin);
		double best = Double.POSITIVE_INFINITY;
		double station = 0;
		for (int node = 0; node + 1 < nodes; node++) {
			int a = node * NODE_WORDS;
			int b = a + NODE_WORDS;
			double dx = route[b] - route[a];
			double dz = route[b + 2] - route[a + 2];
			double weight = clamp(((point.x - route[a]) * dx + (point.z - route[a + 2]) * dz)
					/ Math.max(dx * dx + dz * dz, 0.0001), 0, 1);
			double x = point.x - mix(route[a], route[b], weight);
			double z = point.z - mix(route[a + 2], route[b + 2], weight);
			double distance = x * x + z * z;
			if (distance < best) {
				best = distance;
				station = mix(route[a + 3], route[b + 3], weight);
			}
		}

		return station;
	}

    /// 指定站程的地面高度（事件局部 Y）。
    public double groundHeight(double station, double lateral) {
        double s = clamp(station, 0.0, length);
        int node = nodeAt(s);
        int next = Math.min(node + 1, nodes - 1);
        double from = route[node * NODE_WORDS + 3];
        double to = route[next * NODE_WORDS + 3];
        double weight = clamp((s - from) / Math.max(to - from, 0.0001), 0.0, 1.0);
        return mix(terrain(node, lateral, 0), terrain(next, lateral, 0), weight);
    }

    /// 原始路线节点数，供调试与粒子边界判定。
    public int nodes() {
        return nodes;
    }

    public double length() {
        return length;
    }

    /// 按站程定位原始路段；与 GLSL 的查找表加前向扫描一致。
    public int nodeAt(double station) {
        int index = (int) Math.floor(clamp(station, 0.0, length));
        int node = index < lookupCount ? (int) route[nodes * NODE_WORDS + index] : 0;
        while (node + 2 < nodes && route[(node + 1) * NODE_WORDS + 3] <= station) {
            node++;
        }
        return Math.clamp(node, 0, nodes - 1);
    }

	/// 与服务端路线投影使用相同的三维线段距离，避免计数器按最近节点跳变。
	public double projectedStation(Vec3 world) {
		var point = world.subtract(origin);
		double nearest = Double.POSITIVE_INFINITY;
		double station = 0;
		for (int node = 0; node + 1 < nodes; node++) {
			int first = node * NODE_WORDS, second = first + NODE_WORDS;
			var from = new Vec3(route[first], route[first + 1], route[first + 2]);
			var to = new Vec3(route[second], route[second + 1], route[second + 2]);
			var delta = to.subtract(from);
			double weight = delta.lengthSqr() == 0 ? 0 : Math.clamp(point.subtract(from).dot(delta) / delta.lengthSqr(), 0, 1);
			double distance = from.add(delta.scale(weight)).distanceToSqr(point);
			if (distance < nearest) {
				nearest = distance;
				station = route[first + 3] + (route[second + 3] - route[first + 3]) * weight;
			}
		}
		return station;
	}

    /// 世界坐标在路线上的最近站程；供粒子与调试把相机位置换算成行号。
    ///
    /// 先按节点粗扫再在相邻节点内细化，代价与节点数成正比，只在采样时调用。
    public double stationOf(Vec3 world) {
        double localX = world.x - origin.x;
        double localZ = world.z - origin.z;
        double best = Double.POSITIVE_INFINITY;
        int nearest = 0;
        for (int node = 0; node < nodes; node++) {
            int at = node * NODE_WORDS;
            double dx = route[at] - localX;
            double dz = route[at + 2] - localZ;
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                nearest = node;
            }
        }
        return route[nearest * NODE_WORDS + 3];
    }

    /// 与 GLSL `heightAt` 等价的地面高度采样；CPU 侧只有地面高度，不含 GPU 地形流场位移。
    private double terrain(int node, double lateral, int component) {
        double column = clamp(lateral / Math.max(groundWidth, 0.0001) + 0.5, 0.0, 1.0) * (heightColumns - 1.0);
        int low = Math.max(0, Math.min(Math.max(0, heightColumns - 2), (int) column));
        int base = Math.clamp(node, 0, nodes - 1) * heightColumns;
        double from = heights[base + low];
        double to = heights[base + Math.min(low + 1, heightColumns - 1)];
        return mix(from, to, column - low);
    }
}
