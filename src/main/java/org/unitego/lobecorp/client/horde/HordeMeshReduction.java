package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/// 资源准备阶段的刚性隐藏面证明与闭合距离网格；不处理逐单位变换。
public class HordeMeshReduction {
	private static final int STRIDE = HordeMeshCache.VERTEX_STRIDE;
	private static final int FRAME_STRIDE = 6;
	private static final double EPSILON = 0.000001;
	private static final int[][] BOX = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
			{2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
	private static final int BOX_VERTICES = 24;
	// 中景部位分组：腿脚、躯干、双臂（合并）、头、背后的罐子/生物质；远景只合并为上下两个包围盒。
	private static final int PART_LOWER = 0;
	private static final int PART_TORSO = 1;
	private static final int PART_HEAD = 2;
	private static final int PART_RIGHT_ARM = 3;
	private static final int PART_LEFT_ARM = 4;
	private static final int PART_CAN = 5;
	private static final int MID_BOXES = 5;

	public record Cube(int first, int count, int bone) {
	}

	public static HordeMeshCache.Model[] prepare(HordeMeshCache.Model source, List<Cube> cubes,
	                                             List<String> bones, int[] rigidGroups, Identifier texture, boolean reduce) {
		boolean[] visible = new boolean[source.vertexCount() / 4];
		Arrays.fill(visible, true);
		if (reduce) {
			try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();
			     var image = NativeImage.read(input)) {
				filter(source.vertices(), cubes, visible, image);
			} catch (IOException | RuntimeException failure) {
				// 纹理不可证明不透明时只移除零面积面，不猜测镂空材质。
				filter(source.vertices(), cubes, visible, null);
				Lobecorp.LOGGER.warn("群体隐藏面未取得不透明证明，保留可见面：{}", texture, failure);
			}
		}
		var indices = new IntArrayList();
		for (int quad = 0; quad < visible.length; quad++) {
			if (visible[quad]) {
				for (int corner = 0; corner < 4; corner++) {
					indices.add(quad * 4 + corner);
				}
			}
		}
		var full = select(source, indices.toIntArray(), 0);
		return new HordeMeshCache.Model[]{full, proxy(source, full, cubes, bones, rigidGroups, visible, texture, 1),
				proxy(source, full, cubes, bones, rigidGroups, visible, texture, 2)};
	}

	private static void filter(float[] vertices, List<Cube> cubes, boolean[] visible, @Nullable NativeImage image) {
		var bounds = new double[cubes.size()][];
		var opaque = new boolean[cubes.size()];
		for (int i = 0; i < cubes.size(); i++) {
			var cube = cubes.get(i);
			bounds[i] = bounds(vertices, range(cube.first(), cube.count()), STRIDE, 0);
			opaque[i] = image != null && closedAxisAligned(vertices, cube)
					&& opaque(vertices, cube, image);
		}
		for (int owner = 0; owner < cubes.size(); owner++) {
			var cube = cubes.get(owner);
			for (int first = cube.first(); first < cube.first() + cube.count(); first += 4) {
				int quad = first / 4;
				if (areaSquared(vertices, first) <= EPSILON * EPSILON) {
					visible[quad] = false;
					continue;
				}
				for (int other = 0; other < cubes.size(); other++) {
					var cover = cubes.get(other);
					if (other == owner || cover.bone() != cube.bone() || !opaque[other]) {
						continue;
					}
					if (inside(vertices, first, bounds[other])) {
						visible[quad] = false;
						break;
					}
					if (!opaque[owner]) {
						continue;
					}
					for (int face = cover.first(); face < cover.first() + cover.count(); face += 4) {
						if (opposite(vertices, first, face) && sameQuad(vertices, first, face)) {
							visible[quad] = false;
							break;
						}
					}
					if (!visible[quad]) {
						break;
					}
				}
			}
		}
	}

	private static boolean closedAxisAligned(float[] vertices, Cube cube) {
		if (cube.count() != 24) {
			return false;
		}
		for (int vertex = cube.first(); vertex < cube.first() + cube.count(); vertex += 4) {
			if (areaSquared(vertices, vertex) <= EPSILON * EPSILON) {
				return false;
			}
			int axis = 0;
			for (int component = 5; component < 8; component++) {
				double normal = Math.abs(vertices[vertex * STRIDE + component]);
				if (normal > 1 - EPSILON) {
					axis++;
				} else if (normal > EPSILON) {
					return false;
				}
			}
			if (axis != 1) {
				return false;
			}
		}
		return true;
	}

	private static boolean opaque(float[] vertices, Cube cube, NativeImage image) {
		for (int first = cube.first(); first < cube.first() + cube.count(); first += 4) {
			double minU = 1, minV = 1, maxU = 0, maxV = 0;
			for (int corner = 0; corner < 4; corner++) {
				int at = (first + corner) * STRIDE;
				minU = Math.min(minU, vertices[at + 3]);
				maxU = Math.max(maxU, vertices[at + 3]);
				minV = Math.min(minV, vertices[at + 4]);
				maxV = Math.max(maxV, vertices[at + 4]);
			}
			if (minU < 0 || minV < 0 || maxU > 1 || maxV > 1) {
				return false;
			}
			int x0 = Math.clamp((int) Math.floor(minU * image.getWidth()) - 1, 0, image.getWidth() - 1);
			int x1 = Math.clamp((int) Math.ceil(maxU * image.getWidth()), x0, image.getWidth() - 1);
			int y0 = Math.clamp((int) Math.floor(minV * image.getHeight()) - 1, 0, image.getHeight() - 1);
			int y1 = Math.clamp((int) Math.ceil(maxV * image.getHeight()), y0, image.getHeight() - 1);
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					if ((image.getPixel(x, y) >>> 24) != 255) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean inside(float[] vertices, int first, double[] bounds) {
		for (int axis = 0; axis < 3; axis++) {
			double center = 0;
			for (int corner = 0; corner < 4; corner++) {
				double value = vertices[(first + corner) * STRIDE + axis];
				if (value < bounds[axis] - EPSILON || value > bounds[axis + 3] + EPSILON) {
					return false;
				}
				center += value / 4;
			}
			if (center <= bounds[axis] + EPSILON || center >= bounds[axis + 3] - EPSILON) {
				return false;
			}
		}
		return true;
	}

	private static boolean opposite(float[] vertices, int a, int b) {
		double dot = 0;
		for (int axis = 5; axis < 8; axis++) {
			dot += vertices[a * STRIDE + axis] * vertices[b * STRIDE + axis];
		}
		return dot < -1 + EPSILON;
	}

	private static boolean sameQuad(float[] vertices, int a, int b) {
		for (int corner = 0; corner < 4; corner++) {
			boolean found = false;
			for (int other = 0; other < 4; other++) {
				boolean same = true;
				for (int axis = 0; axis < 3; axis++) {
					same &= Math.abs(vertices[(a + corner) * STRIDE + axis]
							- vertices[(b + other) * STRIDE + axis]) <= EPSILON;
				}
				found |= same;
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static double areaSquared(float[] vertices, int first) {
		double[] a = point(vertices, first, STRIDE, 0);
		double[] b = point(vertices, first + 1, STRIDE, 0);
		double[] c = point(vertices, first + 2, STRIDE, 0);
		double[] d = point(vertices, first + 3, STRIDE, 0);
		return lengthSquared(cross(a, b, c)) + lengthSquared(cross(a, c, d));
	}

	private static HordeMeshCache.Model select(HordeMeshCache.Model source, int[] indices, int tier) {
		var vertices = new float[Math.multiplyExact(indices.length, STRIDE)];
		var frames = new float[Math.multiplyExact(Math.multiplyExact(source.frames(), indices.length), FRAME_STRIDE)];
		for (int vertex = 0; vertex < indices.length; vertex++) {
			System.arraycopy(source.vertices(), indices[vertex] * STRIDE, vertices, vertex * STRIDE, STRIDE);
			for (int frame = 0; frame < source.frames(); frame++) {
				System.arraycopy(source.frameVertices(), (frame * source.vertexCount() + indices[vertex]) * FRAME_STRIDE,
						frames, (frame * indices.length + vertex) * FRAME_STRIDE, FRAME_STRIDE);
			}
		}
		return new HordeMeshCache.Model(vertices, frames, indices.length, source.frames(),
				source.duration(), source.sourceVertexCount(), tier);
	}


	private static int partOf(String name) {
		var lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("can") || lower.contains("jar") || lower.contains("pot") || lower.contains("biomass")) {
			return PART_CAN;
		}
		if (lower.contains("leg") || lower.contains("foot") || lower.contains("feet") || lower.contains("knee")) {
			return PART_LOWER;
		}
		if (lower.contains("head") || lower.contains("neck")) {
			return PART_HEAD;
		}
		if (lower.contains("arm") || lower.contains("hand")) {
			return lower.contains("left") ? PART_LEFT_ARM : PART_RIGHT_ARM;
		}
		return PART_TORSO;
	}

	/// 中景按腿脚/躯干/双臂/头/罐子合并为5个包围盒（薄片保留真实几何），远景合并为上下2个包围盒；近景完整几何不受影响。
    private static HordeMeshCache.Model proxy(HordeMeshCache.Model source, HordeMeshCache.Model full,
                                              List<Cube> cubes, List<String> names, int[] rigidGroups, boolean[] visible,
                                              Identifier texture, int tier) {
		int boxes = tier == 1 ? MID_BOXES : 2;
        var clusters = new IntArrayList[boxes];
        var bones = new int[boxes];
        for (int index = 0; index < boxes; index++) {
            clusters[index] = new IntArrayList();
            bones[index] = -1;
        }
        var pieces = new ArrayList<Piece>();
        for (var cube : cubes) {
            var indices = range(cube.first(), cube.count());
            var box = bounds(source.vertices(), indices, STRIDE, 0);
            boolean flat = box[3] - box[0] <= EPSILON || box[4] - box[1] <= EPSILON || box[5] - box[2] <= EPSILON;
            if (tier == 1 && flat) {
                // 刀面等薄片保留真实几何，避免中景丢失轮廓。
                pieces.add(new Piece(visible(indices, visible), true, cube.bone()));
                continue;
            }
            int part = partOf(names.get(cube.bone()));
            int cluster;
            if (tier == 1) {
                cluster = switch (part) {
                    case PART_LOWER -> 0;
                    case PART_TORSO -> 1;
                    case PART_RIGHT_ARM, PART_LEFT_ARM -> 2;
                    case PART_HEAD -> 3;
                    default -> 4;
                };
            } else {
                cluster = part == PART_LOWER ? 0 : 1;
            }
            clusters[cluster].addElements(clusters[cluster].size(), indices);
            if (bones[cluster] < 0) {
                bones[cluster] = cube.bone();
            }
        }
        var image = loadTexture(texture);
        for (int index = 0; index < boxes; index++) {
            if (clusters[index].isEmpty()) {
                continue;
            }
            int[] kept = visible(clusters[index].toIntArray(), visible);
            if (kept.length == 0) {
                continue;
            }
            pieces.add(new Piece(kept, false, bones[index] < 0 ? cubes.getFirst().bone() : bones[index]));
        }
        int count = pieces.stream().mapToInt(piece -> piece.direct() ? piece.indices().length : BOX_VERTICES).sum();
        if (count >= full.vertexCount() || count == 0) {
            return new HordeMeshCache.Model(full.vertices(), full.frameVertices(), full.vertexCount(),
                    full.frames(), full.duration(), full.sourceVertexCount(), tier);
        }
        var vertices = new float[Math.multiplyExact(count, STRIDE)];
        var frames = new float[Math.multiplyExact(Math.multiplyExact(source.frames(), count), FRAME_STRIDE)];
        int at = 0;
        for (var piece : pieces) {
            if (piece.direct()) {
                for (int index : piece.indices()) {
                    System.arraycopy(source.vertices(), index * STRIDE, vertices, at * STRIDE, STRIDE);
                    for (int frame = 0; frame < source.frames(); frame++) {
                        System.arraycopy(source.frameVertices(), (frame * source.vertexCount() + index) * FRAME_STRIDE,
                                frames, (frame * count + at) * FRAME_STRIDE, FRAME_STRIDE);
                    }
                    at++;
                }
                continue;
            }
            double[] box = bounds(source.vertices(), piece.indices(), STRIDE, 0);
            for (int[] face : BOX) {
                double[] normal = faceNormal(box, face);
                int uv = bestFace(source.vertices(), piece.indices(), normal, image);
                for (int corner = 0; corner < 4; corner++) {
                    double[] point = corner(box, face[corner]);
                    int target = (at + corner) * STRIDE;
                    for (int axis = 0; axis < 3; axis++) {
                        vertices[target + axis] = (float) point[axis];
                        vertices[target + 5 + axis] = (float) normal[axis];
                    }
                    vertices[target + 3] = source.vertices()[(uv + corner) * STRIDE + 3];
                    vertices[target + 4] = source.vertices()[(uv + corner) * STRIDE + 4];
                    vertices[target + 8] = piece.bone();
                }
                for (int frame = 0; frame < source.frames(); frame++) {
                    double[] posed = bounds(source.frameVertices(), piece.indices(), FRAME_STRIDE,
                            frame * source.vertexCount() * FRAME_STRIDE);
                    double[] posedNormal = faceNormal(posed, face);
                    for (int vertex = 0; vertex < 4; vertex++) {
                        double[] point = corner(posed, face[vertex]);
                        int target = (frame * count + at + vertex) * FRAME_STRIDE;
                        for (int axis = 0; axis < 3; axis++) {
                            frames[target + axis] = (float) point[axis];
                            frames[target + 3 + axis] = (float) posedNormal[axis];
                        }
                    }
                }
                at += 4;
            }
        }
        if (image != null) {
            image.close();
        }
        return new HordeMeshCache.Model(vertices, frames, count, source.frames(), source.duration(),
                source.sourceVertexCount(), tier);
    }

	private static int[] visible(int[] indices, boolean[] visible) {
		return Arrays.stream(indices).filter(index -> visible[index / 4]).toArray();
	}

	private static int bestFace(float[] vertices, int[] indices, double[] normal, @Nullable NativeImage image) {
		int selected = -1;
		double bestRed = -Double.MAX_VALUE;
		for (int i = 0; i < indices.length; i += 4) {
			int vertex = indices[i];
			double score = 0;
			for (int axis = 0; axis < 3; axis++) {
				score += vertices[vertex * STRIDE + 5 + axis] * normal[axis];
			}
			if (score < 0.9) {
				continue;
			}
			double red = redness(vertices, vertex, image);
			if (red > bestRed) {
				bestRed = red;
				selected = vertex;
			}
		}
		if (selected >= 0) {
			return selected;
		}
		double best = -Double.MAX_VALUE;
		selected = indices[0];
		for (int i = 0; i < indices.length; i += 4) {
			int vertex = indices[i];
			double score = 0;
			for (int axis = 0; axis < 3; axis++) {
				score += vertices[vertex * STRIDE + 5 + axis] * normal[axis];
			}
			if (score > best) {
				best = score;
				selected = vertex;
			}
		}
		return selected;
	}

	/// 以UV中心像素的红色优势选择面；罐子/生物质和红色刀面因此能在代理盒上保留红色，而不是被灰色躯干面覆盖。
	private static double redness(float[] vertices, int vertex, @Nullable NativeImage image) {
		if (image == null) {
			return 0;
		}
		double u = 0;
		double v = 0;
		for (int corner = 0; corner < 4; corner++) {
			u += vertices[(vertex + corner) * STRIDE + 3];
			v += vertices[(vertex + corner) * STRIDE + 4];
		}
		int x = Math.clamp((int) Math.floor(u / 4 * image.getWidth()), 0, image.getWidth() - 1);
		int y = Math.clamp((int) Math.floor(v / 4 * image.getHeight()), 0, image.getHeight() - 1);
		int rgb = image.getPixel(x, y);
		int r = (rgb >> 16) & 0xff;
		int g = (rgb >> 8) & 0xff;
		int b = rgb & 0xff;
		return r - Math.max(g, b);
	}

	private static @Nullable NativeImage loadTexture(Identifier texture) {
		try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open()) {
			return NativeImage.read(input);
		} catch (IOException | RuntimeException failure) {
			return null;
		}
	}

	private static int[] range(int first, int count) {
		int[] result = new int[count];
		Arrays.setAll(result, index -> first + index);
		return result;
	}

	private static double[] bounds(float[] vertices, int[] indices, int stride, int offset) {
		double[] result = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int index : indices) {
			for (int axis = 0; axis < 3; axis++) {
				double value = vertices[offset + index * stride + axis];
				result[axis] = Math.min(result[axis], value);
				result[axis + 3] = Math.max(result[axis + 3], value);
			}
		}
		return result;
	}

	private static double[] point(float[] vertices, int index, int stride, int offset) {
		return new double[]{vertices[offset + index * stride], vertices[offset + index * stride + 1],
				vertices[offset + index * stride + 2]};
	}

	private static double[] corner(double[] bounds, int corner) {
		return new double[]{bounds[(corner & 1) != 0 ? 3 : 0], bounds[(corner & 2) != 0 ? 4 : 1],
				bounds[(corner & 4) != 0 ? 5 : 2]};
	}

	private static double[] faceNormal(double[] bounds, int[] face) {
		double[] result = cross(corner(bounds, face[0]), corner(bounds, face[1]), corner(bounds, face[2]));
		double length = Math.sqrt(lengthSquared(result));
		if (length <= EPSILON) {
			return new double[]{0, 1, 0};
		}
		for (int axis = 0; axis < 3; axis++) {
			result[axis] /= length;
		}
		return result;
	}

	private static double[] cross(double[] a, double[] b, double[] c) {
		double x = b[0] - a[0], y = b[1] - a[1], z = b[2] - a[2];
		double u = c[0] - a[0], v = c[1] - a[1], w = c[2] - a[2];
		return new double[]{y * w - z * v, z * u - x * w, x * v - y * u};
	}

	private static double lengthSquared(double[] vector) {
		return vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2];
	}

	private record Piece(int[] indices, boolean direct, int bone) {
	}
}
