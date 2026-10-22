package org.unitego.lobecorp.util.horde;

import com.mojang.blaze3d.platform.NativeImage;
import org.unitego.lobecorp.client.horde.HordeMeshCache;

import java.util.Arrays;
import java.util.List;

/// 将共享三维姿态按顶、前、侧、后正交投影到动画图集；不创建实体或调用其渲染器。
public class HordeImpostorRasterizerUtil implements AutoCloseable {
	public static final int VIEWS = 4, GUTTER = 2;
	private final NativeImage source, emission;
	private NativeImage color, glow;
	private final float[] depth;
	private final int size;
	private final float radius, minimumY, maximumY;
	private final float[][] projected = new float[3][7];

	public HordeImpostorRasterizerUtil(List<HordeMeshCache.Mesh> poses, int size, NativeImage source, NativeImage emission) {
		this.source = source;
		this.emission = emission;
		this.size = size;
		float extent = 0, low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
		for (var pose : poses) {
			float[] values = pose.vertices();
			for (int i = 0; i < values.length; i += 8) {
				extent = Math.max(extent, Math.max(Math.abs(values[i]), Math.abs(values[i + 2])));
				low = Math.min(low, values[i + 1]);
				high = Math.max(high, values[i + 1]);
			}
		}
		radius = Math.max(0.1f, extent + 0.05f);
		minimumY = low - 0.05f;
		maximumY = Math.max(minimumY + 0.1f, high + 0.05f);
		color = new NativeImage(size * VIEWS, size * poses.size(), true);
		glow = new NativeImage(size * VIEWS, size * poses.size(), true);
		depth = new float[size * size];
	}

	public float radius() { return radius; }
	public float minimumY() { return minimumY; }
	public float maximumY() { return maximumY; }

	public void bake(HordeMeshCache.Mesh mesh, int frame) {
		for (int view = 0; view < VIEWS; view++) {
			Arrays.fill(depth, -Float.MAX_VALUE);
			float[] vertices = mesh.vertices();
			for (int quad = 0; quad + 31 < vertices.length; quad += 32) {
				triangle(vertices, quad, quad + 8, quad + 16, view, frame);
				triangle(vertices, quad + 16, quad + 24, quad, view, frame);
			}
		}
	}

	private void project(float[] values, int index, int view, float[] output) {
		float x = values[index], y = values[index + 1], z = values[index + 2];
		float u = switch (view) { case 2 -> -z; case 3 -> -x; default -> x; };
		float v = view == 0 ? z : y;
		output[0] = GUTTER + (u / (radius * 2) + 0.5f) * (size - GUTTER * 2);
		output[1] = GUTTER + (1 - (view == 0 ? v / (radius * 2) + 0.5f
				: (v - minimumY) / (maximumY - minimumY))) * (size - GUTTER * 2);
		output[2] = switch (view) { case 0 -> y; case 2 -> x; case 3 -> -z; default -> z; };
		output[3] = values[index + 3];
		output[4] = values[index + 4];
		output[5] = 0.6f + 0.4f * Math.max(0, values[index + 6]);
	}

	private void triangle(float[] values, int a, int b, int c, int view, int frame) {
		project(values, a, view, projected[0]);
		project(values, b, view, projected[1]);
		project(values, c, view, projected[2]);
		float[] p = projected[0], q = projected[1], r = projected[2];
		float area = edge(p, q, r[0], r[1]);
		if (Math.abs(area) < 0.00001f) {
			return;
		}
		int left = Math.clamp((int) Math.floor(Math.min(p[0], Math.min(q[0], r[0]))), 0, size - 1);
		int right = Math.clamp((int) Math.ceil(Math.max(p[0], Math.max(q[0], r[0]))), 0, size - 1);
		int top = Math.clamp((int) Math.floor(Math.min(p[1], Math.min(q[1], r[1]))), 0, size - 1);
		int bottom = Math.clamp((int) Math.ceil(Math.max(p[1], Math.max(q[1], r[1]))), 0, size - 1);
		for (int y = top; y <= bottom; y++) {
			for (int x = left; x <= right; x++) {
				float wa = edge(q, r, x + 0.5f, y + 0.5f) / area;
				float wb = edge(r, p, x + 0.5f, y + 0.5f) / area;
				float wc = 1 - wa - wb;
				if (wa < -0.00001f || wb < -0.00001f || wc < -0.00001f) {
					continue;
				}
				float z = p[2] * wa + q[2] * wb + r[2] * wc;
				if (z < depth[y * size + x]) {
					continue;
				}
				float u = p[3] * wa + q[3] * wb + r[3] * wc;
				float v = p[4] * wa + q[4] * wb + r[4] * wc;
				int pixel = sample(source, u, v);
				if ((pixel >>> 24) < 26) {
					continue;
				}
				depth[y * size + x] = z;
				float shade = p[5] * wa + q[5] * wb + r[5] * wc;
				color.setPixel(view * size + x, frame * size + y, shaded(pixel, shade));
				glow.setPixel(view * size + x, frame * size + y, sample(emission, u, v));
			}
		}
	}

	private static float edge(float[] a, float[] b, float x, float y) {
		return (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0]);
	}

	private static int sample(NativeImage image, float u, float v) {
		return image.getPixel(Math.clamp((int) (u * image.getWidth()), 0, image.getWidth() - 1),
				Math.clamp((int) (v * image.getHeight()), 0, image.getHeight() - 1));
	}

	private static int shaded(int argb, float shade) {
		int r = Math.clamp(Math.round((argb >>> 16 & 255) * shade), 0, 255);
		int g = Math.clamp(Math.round((argb >>> 8 & 255) * shade), 0, 255);
		int b = Math.clamp(Math.round((argb & 255) * shade), 0, 255);
		return argb & 0xff000000 | r << 16 | g << 8 | b;
	}

	/// 所有权交给DynamicTexture后，close只释放源贴图。
	public NativeImage takeColor() { var result = color; color = null; return result; }
	public NativeImage takeGlow() { var result = glow; glow = null; return result; }

	@Override
	public void close() {
		source.close();
		emission.close();
		if (color != null) { color.close(); }
		if (glow != null) { glow.close(); }
	}
}
