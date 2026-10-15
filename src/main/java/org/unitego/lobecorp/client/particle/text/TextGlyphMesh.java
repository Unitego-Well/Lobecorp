package org.unitego.lobecorp.client.particle.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/// 缓存字形的局部顶点；逐帧只变换位置，保留字体提供的 UV 和样式颜色。
public record TextGlyphMesh(Supplier<RenderType> material, float[] vertices, int[] colors, double radius) {

	private static final int VERTEX_STRIDE = 5;
	/// 与原版八方向描边一致，偏移以字体像素为单位。
	private static final int OUTLINE_RADIUS = 1;

	public RenderType renderType() {
		return material.get();
	}

	/// 描边与正文共用一次选出的字形，避免混淆样式重新随机选字后轮廓错位。
	public TextGlyphMesh createOutline(Supplier<RenderType> type, int color) {
		int diameter = OUTLINE_RADIUS * 2 + 1;
		int directionCount = diameter * diameter - 1;
		var coordinates = new float[vertices.length * directionCount];
		var packedColors = new int[colors.length * directionCount];
		Arrays.fill(packedColors, color);
		int targetOffset = 0;
		double outlineRadius = 0;

		for (int x = -OUTLINE_RADIUS; x <= OUTLINE_RADIUS; x++) {
			for (int y = -OUTLINE_RADIUS; y <= OUTLINE_RADIUS; y++) {
				if (x == 0 && y == 0) {
					continue;
				}

				for (int sourceOffset = 0; sourceOffset < vertices.length; sourceOffset += VERTEX_STRIDE) {
					System.arraycopy(vertices, sourceOffset, coordinates, targetOffset, VERTEX_STRIDE);
					float shiftedX = vertices[sourceOffset] + x;
					float shiftedY = vertices[sourceOffset + 1] + y;
					float z = vertices[sourceOffset + 2];
					coordinates[targetOffset] = shiftedX;
					coordinates[targetOffset + 1] = shiftedY;
					outlineRadius = Math.max(outlineRadius, Math.sqrt(shiftedX * shiftedX + shiftedY * shiftedY + z * z));
					targetOffset += VERTEX_STRIDE;
				}
			}
		}

		return new TextGlyphMesh(type, coordinates, packedColors, outlineRadius);
	}

	public void render(Matrix4fc pose, VertexConsumer buffer, int light) {
		for (int index = 0; index < colors.length; index++) {
			int offset = index * VERTEX_STRIDE;
			float x = vertices[offset];
			float y = vertices[offset + 1];
			float z = vertices[offset + 2];
			float transformedX = pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30();
			float transformedY = pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31();
			float transformedZ = pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32();
			buffer.addVertex(transformedX, transformedY, transformedZ).setColor(colors[index])
					.setUv(vertices[offset + 3], vertices[offset + 4]).setLight(light);
		}
	}

	public static class Capture implements VertexConsumer {

		private final List<float[]> vertices = new ArrayList<>();
		private final List<Integer> colors = new ArrayList<>();
		private float[] current;

		public TextGlyphMesh finish(Supplier<RenderType> type, float centerX, float centerY) {
			var coordinates = new float[vertices.size() * VERTEX_STRIDE];
			var packedColors = new int[vertices.size()];
			double radius = 0;

			for (int index = 0; index < vertices.size(); index++) {
				var vertex = vertices.get(index);
				vertex[0] -= centerX;
				vertex[1] -= centerY;
				System.arraycopy(vertex, 0, coordinates, index * VERTEX_STRIDE, VERTEX_STRIDE);
				packedColors[index] = colors.get(index);
				radius = Math.max(radius, Math.sqrt(vertex[0] * vertex[0] + vertex[1] * vertex[1] + vertex[2] * vertex[2]));
			}

			return new TextGlyphMesh(type, coordinates, packedColors, radius);
		}

		@Override
		public @NotNull VertexConsumer addVertex(float x, float y, float z) {
			current = new float[]{x, y, z, 0, 0};
			vertices.add(current);
			colors.add(0);
			return this;
		}

		@Override
		public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return setColor(alpha << 24 | red << 16 | green << 8 | blue);
		}

		@Override
		public @NotNull VertexConsumer setColor(int color) {
			colors.set(colors.size() - 1, color);
			return this;
		}

		@Override
		public @NotNull VertexConsumer setUv(float u, float v) {
			current[3] = u;
			current[4] = v;
			return this;
		}

		@Override
		public @NotNull VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public @NotNull VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public @NotNull VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		@Override
		public @NotNull VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
