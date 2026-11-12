package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.platform.NativeImage;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.HordePathVariant;

/// 在CPU把完整模型的动画帧从水平八向各渲染一张小图，拼成“8方向×帧数”的片图集。
/// 图集只烘焙一次并随模型缓存；GPU端按方向/帧取tile，避免逐实例CPU处理。
public final class HordeCardBake {
	private static final int TILE = HordeMeshReduction.CardProjection.TILE;
	private static final double[] LIGHT = normalize(-0.35, 1.0, 0.45);

	private HordeCardBake() {
	}

	/// 两张图集由调用方共同拥有；关闭时释放输出，不关闭输入纹理。
	public record Atlas(NativeImage base, @Nullable NativeImage glow) implements AutoCloseable {
		@Override
		public void close() {
			try {
				base.close();
			} finally {
				if (glow != null) {
					glow.close();
				}
			}
		}
	}

	/// 基础材质的 cutout 可见性统一决定两层深度；失败返回 null，输出由 Renderer 持有并关闭。
	public static @Nullable Atlas bakeLayers(HordeMeshCache.Model model, NativeImage baseTexture,
	                                         @Nullable NativeImage glowTexture) {
		return bakeLayers(model, baseTexture, glowTexture, true);
	}

	public static @Nullable NativeImage bake(HordeMeshCache.Model model, NativeImage texture) {
		return bake(model, texture, true);
	}

	/// 发光图集：同一方向/帧布局，但不做方向光照，保留 glowmask 原色与透明度。
	public static @Nullable NativeImage bakeGlow(HordeMeshCache.Model model, NativeImage texture) {
		return bake(model, texture, false);
	}

	private static @Nullable NativeImage bake(HordeMeshCache.Model model, NativeImage texture, boolean shaded) {
		var atlas = bakeLayers(model, texture, null, shaded);
		return atlas == null ? null : atlas.base();
	}

	private static @Nullable Atlas bakeLayers(HordeMeshCache.Model model, NativeImage texture,
	                                          @Nullable NativeImage glowTexture, boolean shaded) {
		NativeImage image = null;
		NativeImage glow = null;
		boolean completed = false;
		try {
			int width = TILE * HordeMeshReduction.CARD_DIRECTIONS;
			int height = TILE * Math.max(1, model.animationFrames());
			image = new NativeImage(width, height, true);
			glow = glowTexture == null ? null : new NativeImage(width, height, true);
			// 仅优化路径合并全帧扫描；baseline 保留逐方向计算。
			var projection = HordePathVariant.optimized() ? HordeMeshReduction.cardProjection(model) : null;
			for (int face = 0; face < HordeMeshReduction.CARD_DIRECTIONS; face++) {
				bakeFace(model, texture, image, glowTexture, glow, face, shaded, projection);
			}
			var result = new Atlas(image, glow);
			completed = true;
			return result;
		} catch (RuntimeException failure) {
			Lobecorp.LOGGER.warn("群体方向片烘焙失败，回退6面盒贴图", failure);
			return null;
		} finally {
			if (!completed) {
				try {
					if (image != null) {
						image.close();
					}
				} finally {
					if (glow != null) {
						glow.close();
					}
				}
			}
		}
	}

	private static void bakeFace(HordeMeshCache.Model model, NativeImage texture, NativeImage atlas,
	                             @Nullable NativeImage glowTexture, @Nullable NativeImage glowAtlas, int face, boolean shaded,
	                             HordeMeshReduction.@Nullable CardProjection cachedProjection) {
		var projection = cachedProjection != null ? cachedProjection : HordeMeshReduction.cardProjection(model);
		var axes = HordeMeshReduction.cardAxes(face);
		double[] right = axes[0];
		double[] down = axes[1];
		double[] forward = axes[2];
		double[] center = projection.center();
		double scale = TILE / projection.side();
		float[] projected = new float[model.vertexCount() * 3];
		float[] zbuffer = new float[TILE * TILE];
		java.util.Arrays.fill(zbuffer, -Float.MAX_VALUE);
		float[] shade = new float[TILE * TILE];
		int[] pixels = new int[TILE * TILE];
		int[] glowPixels = glowTexture == null ? null : new int[TILE * TILE];
		float[] staticVertices = model.vertices();
		float[] frameVertices = model.frameVertices();
		for (int frame = 0; frame < model.animationFrames(); frame++) {
			int geometryFrame = frame % model.geometryFrames();
			java.util.Arrays.fill(zbuffer, -Float.MAX_VALUE);
			java.util.Arrays.fill(pixels, 0);
			if (glowPixels != null) {
				java.util.Arrays.fill(glowPixels, 0);
			}
			java.util.Arrays.fill(shade, 0);
			for (int vertex = 0; vertex < model.vertexCount(); vertex++) {
				int at = (geometryFrame * model.vertexCount() + vertex) * 6;
				double x = frameVertices[at] - center[0];
				double y = frameVertices[at + 1] - center[1];
				double z = frameVertices[at + 2] - center[2];
				projected[vertex * 3] = (float) ((x * right[0] + y * right[1] + z * right[2]) * scale + TILE * 0.5);
				projected[vertex * 3 + 1] = (float) ((x * down[0] + y * down[1] + z * down[2]) * scale + TILE * 0.5);
				projected[vertex * 3 + 2] = (float) (x * forward[0] + y * forward[1] + z * forward[2]);
			}
			for (int at = 0; at + 3 < model.vertexCount(); at += 4) {
				int a = at;
				int b = at + 1;
				int c = at + 2;
				int d = at + 3;
				double light = shaded ? faceLight(frameVertices, geometryFrame, model.vertexCount(), a, b, c) : 1.0;
				textureTriangle(texture, glowTexture, glowPixels, staticVertices, projected, a, b, c, zbuffer, pixels, shade, light);
				textureTriangle(texture, glowTexture, glowPixels, staticVertices, projected, a, c, d, zbuffer, pixels, shade, light);
			}
			// 片盒走透明裁剪（cutout）渲染：透明区域保持透明，由渲染管线丢弃，不填实心底。
			// 注意：NativeImage.getPixel() 返回 ARGB，写入必须用 setPixel(ARGB)，不能用 setPixelABGR。
			for (int y = 0; y < TILE; y++) {
				for (int x = 0; x < TILE; x++) {
					int pixel = pixels[y * TILE + x];
					if (pixel == 0) {
						continue;
					}
					if (glowAtlas != null && glowPixels != null) {
						glowAtlas.setPixel(face * TILE + x, frame * TILE + y, glowPixels[y * TILE + x]);
					}
					float factor = shade[y * TILE + x];
					int r = (int) (((pixel >> 16) & 0xff) * factor);
					int g = (int) (((pixel >> 8) & 0xff) * factor);
					int b = (int) ((pixel & 0xff) * factor);
					atlas.setPixel(face * TILE + x, frame * TILE + y,
							(pixel & 0xff000000) | (r << 16) | (g << 8) | b);
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

	private static void textureTriangle(NativeImage texture, @Nullable NativeImage glowTexture,
	                                    int @Nullable [] glowPixels, float[] vertices, float[] projected,
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
				int rgba = sample(texture, u, v);
				if ((rgba >>> 24) / 255.0f < 0.1f) {
					continue;
				}
				// 即使当前表面不发光，也必须覆盖后景发光像素。
				if (glowTexture != null && glowPixels != null) {
					glowPixels[at] = sample(glowTexture, u, v);
				}
				zbuffer[at] = depth;
				pixels[at] = rgba;
				shade[at] = (float) light;
			}
		}
	}

	private static int sample(NativeImage texture, float u, float v) {
		int x = Math.clamp((int) Math.floor(u * texture.getWidth()), 0, texture.getWidth() - 1);
		int y = Math.clamp((int) Math.floor(v * texture.getHeight()), 0, texture.getHeight() - 1);
		return texture.getPixel(x, y);
	}

	private static double[] normalize(double x, double y, double z) {
		double length = Math.sqrt(x * x + y * y + z * z);
		return length < 0.0001 ? new double[]{0, 0, 0} : new double[]{x / length, y / length, z / length};
	}

}
