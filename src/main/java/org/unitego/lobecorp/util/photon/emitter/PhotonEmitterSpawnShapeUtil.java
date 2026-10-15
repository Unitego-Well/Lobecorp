package org.unitego.lobecorp.util.photon.emitter;

import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Box;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnShape;
import oshi.util.tuples.Pair;

import java.util.List;

/// 采样本地空间的位置；球和圆按体积或面积均匀分布。
public class PhotonEmitterSpawnShapeUtil {
	/// 与 Photon 原生形状范围线一致的每顶点线宽，单位为屏幕像素。
	private static final float GUIDE_LINE_WIDTH = 5;

	public static void drawGuideLines(PhotonEmitterSpawnShape shape, IParticleEmitter emitter, MultiBufferSource bufferSource) {
		if (shape.type == null || !shape.size.isFinite() || !shape.offset.isFinite()
				|| shape.size.x < 0 || shape.size.y < 0 || shape.size.z < 0) {
			return;
		}

		var edges = guideLines(shape, emitter).stream().filter(PhotonEmitterSpawnShapeUtil::isValidEdge).toList();

		if (edges.isEmpty()) {
			return;
		}

		var poseStack = new PoseStack();
		poseStack.mulPose(emitter.transform().localToWorldMatrix());
		RenderBufferUtils.drawEdges(poseStack, bufferSource.getBuffer(RenderTypes.lines()), edges,
				ColorPattern.YELLOW.color, GUIDE_LINE_WIDTH);
	}

	private static List<Pair<Vector3f, Vector3f>> guideLines(PhotonEmitterSpawnShape shape, IParticleEmitter emitter) {
		var rotation = new Vector3f();
		var size = new Vector3f(shape.size);
		var offset = new Vector3f(shape.offset);
		return switch (shape.type) {
			case POINT -> List.of();
			case BOX -> new Box().getGuideLines(emitter, offset, rotation, size.mul(2));
			case SPHERE -> {
				var sphere = new Sphere();
				sphere.setRadius(1);
				yield sphere.getGuideLines(emitter, offset, rotation, size);
			}

			case CIRCLE -> {
				var circle = new Circle();
				circle.setRadius(1);
				yield circle.getGuideLines(emitter, offset, rotation, size);
			}
		};
	}

	private static boolean isValidEdge(Pair<Vector3f, Vector3f> edge) {
		var lengthSquared = edge.getA().distanceSquared(edge.getB());
		return edge.getA().isFinite() && edge.getB().isFinite() && Float.isFinite(lengthSquared) && lengthSquared > 0;
	}

	public static Vector3f sample(PhotonEmitterSpawnShape shape, RandomSource random) {
		if (shape.type == null || !shape.size.isFinite() || !shape.offset.isFinite()
				|| shape.size.x < 0 || shape.size.y < 0 || shape.size.z < 0) {
			throw new IllegalArgumentException();
		}

		var position = switch (shape.type) {
			case POINT -> new Vector3f();
			case BOX ->
					new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
			case SPHERE -> sphere(random);
			case CIRCLE -> circle(random);
		};
		return position.mul(shape.size).add(shape.offset);
	}

	private static Vector3f sphere(RandomSource random) {
		var z = random.nextFloat() * 2 - 1;
		var angle = random.nextFloat() * Mth.TWO_PI;
		var radius = (float) Math.cbrt(random.nextFloat());
		var ring = (float) Math.sqrt(1 - z * z);
		return new Vector3f(Mth.cos(angle) * ring, z, Mth.sin(angle) * ring).mul(radius);
	}

	private static Vector3f circle(RandomSource random) {
		var angle = random.nextFloat() * Mth.TWO_PI;
		var radius = (float) Math.sqrt(random.nextFloat());
		return new Vector3f(Mth.cos(angle) * radius, 0, Mth.sin(angle) * radius);
	}
}
