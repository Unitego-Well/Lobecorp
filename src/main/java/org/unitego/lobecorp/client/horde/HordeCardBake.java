package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.platform.NativeImage;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;

/// 在CPU把完整模型的20个跑步帧从盒子的6个面方向各渲染一张小图，拼成“6方向×帧数”的片图集。
/// 图集只烘焙一次并随模型缓存；GPU端按方向/帧取tile，避免逐实例CPU处理。
final class HordeCardBake {
	static final int TILE = 64;
	private static final int PADDING = 3;
	private static final double[] LIGHT = normalize(-0.35, 1.0, 0.45);

	private HordeCardBake() {
	}

	static @Nullable NativeImage bake(HordeMeshCache.Model model, NativeImage texture) {
		return bake(model, texture, true);
	}

	/// 发光图集：同一方向/帧布局，但不做方向光照，保留 glowmask 原色与透明度。
	static @Nullable NativeImage bakeGlow(HordeMeshCache.Model model, NativeImage texture) {
		return bake(model, texture, false);
	}

	private static @Nullable NativeImage bake(HordeMeshCache.Model model, NativeImage texture, boolean shaded) {
		try {
			int frames = model.frames();
			int width = TILE * 6;
			int height = TILE * Math.max(1, frames);
			var image = new NativeImage(width, height, true);
			for (int face = 0; face < 6; face++) {
				bakeFace(model, texture, image, face, shaded);
			}
			return image;
		} catch (RuntimeException failure) {
			Lobecorp.LOGGER.warn("群体方向片烘焙失败，回退6面盒贴图", failure);
			return null;
		}
	}

	private static void bakeFace(HordeMeshCache.Model model, NativeImage texture, NativeImage atlas, int face, boolean shaded) {
		double[] box = bounds(model);
		int[] corners = HordeMeshReduction.boxFaces()[face];
		double[] p0 = HordeMeshReduction.corner(box, corners[0]);
		double[] p1 = HordeMeshReduction.corner(box, corners[1]);
		double[] p3 = HordeMeshReduction.corner(box, corners[3]);
		double[] right = normalize(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]);
		double[] down = normalize(p3[0] - p0[0], p3[1] - p0[1], p3[2] - p0[2]);
		double[] forward = cross(right, down);
		double[] center = {(box[0] + box[3]) * 0.5, (box[1] + box[4]) * 0.5, (box[2] + box[5]) * 0.5};
		double extent = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
		double scale = (TILE - PADDING * 2) / Math.max(0.0001, extent);
		float[] projected = new float[model.vertexCount() * 3];
		float[] zbuffer = new float[TILE * TILE];
		java.util.Arrays.fill(zbuffer, -Float.MAX_VALUE);
		float[] shade = new float[TILE * TILE];
		int[] pixels = new int[TILE * TILE];
		float[] staticVertices = model.vertices();
		float[] frameVertices = model.frameVertices();
		for (int frame = 0; frame < model.frames(); frame++) {
			java.util.Arrays.fill(zbuffer, -Float.MAX_VALUE);
			java.util.Arrays.fill(pixels, 0);
			java.util.Arrays.fill(shade, 0);
			for (int vertex = 0; vertex < model.vertexCount(); vertex++) {
				int at = (frame * model.vertexCount() + vertex) * 6;
				double x = frameVertices[at] - center[0];
				double y = frameVertices[at + 1] - center[1];
				double z = frameVertices[at + 2] - center[2];
				projected[vertex * 3] = (float) ((x * right[0] + y * right[1] + z * right[2]) * scale + TILE * 0.5);
				projected[vertex * 3 + 1] = (float) ((x * down[0] + y * down[1] + z * down[2]) * scale + TILE * 0.5);
				projected[vertex * 3 + 2] = (float) (-(x * forward[0] + y * forward[1] + z * forward[2]));
			}
			for (int at = 0; at + 3 < model.vertexCount(); at += 4) {
				int a = at;
				int b = at + 1;
				int c = at + 2;
				int d = at + 3;
				double light = shaded ? faceLight(frameVertices, frame, model.vertexCount(), a, b, c) : 1.0;
				textureTriangle(texture, staticVertices, projected, a, b, c, zbuffer, pixels, shade, light);
				textureTriangle(texture, staticVertices, projected, a, c, d, zbuffer, pixels, shade, light);
			}
			// 片盒走透明裁剪（cutout）渲染：透明区域保持透明，由渲染管线丢弃，不填实心底。
			// 注意：NativeImage.getPixel() 返回 ARGB，写入必须用 setPixel(ARGB)，不能用 setPixelABGR。
			for (int y = 0; y < TILE; y++) {
				for (int x = 0; x < TILE; x++) {
					int pixel = pixels[y * TILE + x];
					if (pixel == 0) {
						continue;
					}
					float factor = shade[y * TILE + x];
					int r = (int) (((pixel >> 16) & 0xff) * factor);
					int g = (int) (((pixel >> 8) & 0xff) * factor);
					int b = (int) ((pixel & 0xff) * factor);
					atlas.setPixel(face * TILE + x, frame * TILE + y,
							(255 << 24) | (r << 16) | (g << 8) | b);
				}
			}
		}
	}

	private static double faceLight(float[] frameVertices, int frame, int vertexCount, int a, int b, int c) {
		double nx = 0;
		double ny = 0;
		double nz = 0;
		for (int vertex : new int[]{a, b, c}) {
			int at = (frame * vertexCount + vertex) * 6 + 3;
			nx += frameVertices[at];
			ny += frameVertices[at + 1];
			nz += frameVertices[at + 2];
		}
		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length < 0.0001) {
			return 1;
		}
		double dot = (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / length;
		return Math.clamp(0.45 + 0.55 * Math.abs(dot), 0.45, 1.0);
	}

	private static void textureTriangle(NativeImage texture, float[] vertices, float[] projected,
	                                    int a, int b, int c, float[] zbuffer, int[] pixels, float[] shade,
	                                    double light) {
		float ax = projected[a * 3];
		float ay = projected[a * 3 + 1];
		float az = projected[a * 3 + 2];
		float bx = projected[b * 3];
		float by = projected[b * 3 + 1];
		float bz = projected[b * 3 + 2];
		float cx = projected[c * 3];
		float cy = projected[c * 3 + 1];
		float cz = projected[c * 3 + 2];
		float au = vertices[a * 9 + 3];
		float av = vertices[a * 9 + 4];
		float bu = vertices[b * 9 + 3];
		float bv = vertices[b * 9 + 4];
		float cu = vertices[c * 9 + 3];
		float cv = vertices[c * 9 + 4];
		float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
		if (Math.abs(area) < 0.0001) {
			return;
		}
		if (area < 0) {
			// 保证正绕序并同步交换顶点/UV，否则整片会被重心测试跳过。
			float tx = bx;
			float ty = by;
			float tz = bz;
			bx = cx;
			by = cy;
			bz = cz;
			cx = tx;
			cy = ty;
			cz = tz;
			float tu = bu;
			float tv = bv;
			bu = cu;
			bv = cv;
			cu = tu;
			cv = tv;
			area = -area;
		}
		int minX = (int) Math.floor(Math.max(0, Math.min(ax, Math.min(bx, cx))));
		int maxX = (int) Math.ceil(Math.min(TILE - 1, Math.max(ax, Math.max(bx, cx))));
		int minY = (int) Math.floor(Math.max(0, Math.min(ay, Math.min(by, cy))));
		int maxY = (int) Math.ceil(Math.min(TILE - 1, Math.max(ay, Math.max(by, cy))));
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				float w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
				float w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
				float w2 = 1 - w0 - w1;
				if (w0 < 0 || w1 < 0 || w2 < 0) {
					continue;
				}
				float depth = w0 * az + w1 * bz + w2 * cz;
				int at = y * TILE + x;
				if (depth <= zbuffer[at]) {
					continue;
				}
				float u = w0 * au + w1 * bu + w2 * cu;
				float v = w0 * av + w1 * bv + w2 * cv;
				int tx = Math.clamp((int) (u * (texture.getWidth() - 1)), 0, texture.getWidth() - 1);
				int ty = Math.clamp((int) (v * (texture.getHeight() - 1)), 0, texture.getHeight() - 1);
				int rgba = texture.getPixel(tx, ty);
				if (((rgba >>> 24) & 0xff) < 128) {
					continue;
				}
				zbuffer[at] = depth;
				pixels[at] = rgba;
				shade[at] = (float) light;
			}
		}
	}

	private static double[] bounds(HordeMeshCache.Model model) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		float[] frames = model.frameVertices();
		for (int at = 0; at + 2 < frames.length; at += 6) {
			minX = Math.min(minX, frames[at]);
			minY = Math.min(minY, frames[at + 1]);
			minZ = Math.min(minZ, frames[at + 2]);
			maxX = Math.max(maxX, frames[at]);
			maxY = Math.max(maxY, frames[at + 1]);
			maxZ = Math.max(maxZ, frames[at + 2]);
		}
		return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
	}

	private static double[] normalize(double x, double y, double z) {
		double length = Math.sqrt(x * x + y * y + z * z);
		return length < 0.0001 ? new double[]{0, 0, 0} : new double[]{x / length, y / length, z / length};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}
}
