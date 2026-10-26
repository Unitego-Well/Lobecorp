package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/// 显式基准的相机位姿门禁与跨轮指纹；只在渲染线程读取主相机，不修改相机、投影或窗口。
public final class PhotonBenchCameraAudit {
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;
	private final Matrix4f matrix = new Matrix4f();
	private final float[] values = new float[16];
	private long fingerprint = Long.MIN_VALUE;
	private long samples;
	private long changes;
	private long unavailable;
	private JsonObject first;
	private JsonObject last;

	/// 位姿门禁只比较位置与朝向；用于证明本轮相机与请求值一致。
	public static boolean poseMatches(BenchClientPose expected, BenchClientPose observed) {
		return expected != null && observed != null
				&& Math.abs(expected.x() - observed.x()) < 1e-4
				&& Math.abs(expected.y() - observed.y()) < 1e-4
				&& Math.abs(expected.z() - observed.z()) < 1e-4
				&& Math.abs(expected.yaw() - observed.yaw()) < 1e-3
				&& Math.abs(expected.pitch() - observed.pitch()) < 1e-3;
	}

	/// 每帧只计算一个数值指纹；只在首帧与变化时构造 JSON，避免审计本身制造大量对象。
	public void sample() {
		var camera = Minecraft.getInstance().gameRenderer.getMainCamera();

		if (!camera.isInitialized()) {
			unavailable++;
			return;
		}

		camera.getViewRotationProjectionMatrix(matrix).get(values);

		var position = camera.position();
		double x = position.x;
		double y = position.y;
		double z = position.z;
		float pitch = camera.xRot();
		float yaw = camera.yRot();
		float roll = camera.getRoll();
		float fov = camera.getFov();
		long candidate = fingerprint(x, y, z, pitch, yaw, roll, fov);

		if (samples++ == 0) {
			fingerprint = candidate;
			first = snapshot(x, y, z, pitch, yaw, roll, fov, candidate);
			last = first;
			return;
		}

		if (candidate != fingerprint) {
			changes++;
			fingerprint = candidate;
			last = snapshot(x, y, z, pitch, yaw, roll, fov, candidate);
		}
	}

	public long samples() {
		return samples;
	}

	public long changes() {
		return changes;
	}

	public long unavailableFrames() {
		return unavailable;
	}

	public JsonObject first() {
		return first;
	}

	public JsonObject last() {
		return last;
	}

	private JsonObject snapshot(double x, double y, double z, float pitch, float yaw, float roll, float fov, long value) {
		var result = new JsonObject();
		result.addProperty("x", x);
		result.addProperty("y", y);
		result.addProperty("z", z);
		result.addProperty("pitch", pitch);
		result.addProperty("yaw", yaw);
		result.addProperty("roll", roll);
		result.addProperty("fov", fov);
		var matrixValues = new JsonArray();

		for (float element : values) {
			matrixValues.add(element);
		}

		result.add("view_rotation_projection_matrix", matrixValues);
		result.addProperty("fingerprint", Long.toUnsignedString(value, 16));
		return result;
	}

	private long fingerprint(double x, double y, double z, float pitch, float yaw, float roll, float fov) {
		long value = FNV_OFFSET;
		value = mix(value, Double.doubleToRawLongBits(x));
		value = mix(value, Double.doubleToRawLongBits(y));
		value = mix(value, Double.doubleToRawLongBits(z));
		value = mix(value, Float.floatToRawIntBits(pitch));
		value = mix(value, Float.floatToRawIntBits(yaw));
		value = mix(value, Float.floatToRawIntBits(roll));
		value = mix(value, Float.floatToRawIntBits(fov));

		for (float element : values) {
			value = mix(value, Float.floatToRawIntBits(element));
		}

		return value;
	}

	private static long mix(long value, long bits) {
		return (value ^ bits) * FNV_PRIME;
	}

}
