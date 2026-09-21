package org.unitego.lobecorp.entity.client.renderer;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.BiConsumer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// 使用六个方向的倍率，以目标骨骼轴承点为中心动态调整方块及其 UV。
public class DynamicCubeGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
	/// 动态方块完全收缩时的比例
	private static final float MINIMUM_SCALE = 0.0F;
	/// 动态方块保持原始尺寸时的比例
	private static final float MAXIMUM_SCALE = 1.0F;
	private final String boneName;
	private final Map<Direction, DataTicket<Float>> scaleDataTickets;
	private final List<RenderPassProvider<R>> additionalRenderPasses;

	public DynamicCubeGeoLayer(GeoRenderer<T, O, R> renderer, String boneName,
			Map<Direction, DataTicket<Float>> scaleDataTickets) {
		this(renderer, boneName, scaleDataTickets, List.of());
	}

	public DynamicCubeGeoLayer(GeoRenderer<T, O, R> renderer, String boneName,
			Map<Direction, DataTicket<Float>> scaleDataTickets,
			List<RenderPassProvider<R>> additionalRenderPasses) {
		super(renderer);
		this.boneName = boneName;
		this.scaleDataTickets = Map.copyOf(scaleDataTickets);
		this.additionalRenderPasses = List.copyOf(additionalRenderPasses);
	}

	@Override
	public void preRender(RenderPassInfo<R> renderPassInfo, @NonNull SubmitNodeCollector renderTasks) {
		if (!renderPassInfo.willRender()) {
			return;
		}
		renderPassInfo.addBoneUpdater((ignoredRenderPass, snapshots) -> snapshots.get(boneName)
				.filter(snapshot -> snapshot.getBone() instanceof CuboidGeoBone)
				.ifPresent(snapshot -> snapshot.skipRender(true)));
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<R> renderPassInfo, @NonNull BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
		if (!renderPassInfo.willRender()) {
			return;
		}
		renderPassInfo.model().getBone(boneName)
				.filter(CuboidGeoBone.class::isInstance)
				.ifPresent(bone -> consumer.accept(bone, this::renderBone));
	}

	private void renderBone(RenderPassInfo<R> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
		Map<Direction, Float> scales = getScales(renderPassInfo);
		if (scales.values().stream().anyMatch(scale -> scale <= MINIMUM_SCALE)) {
			return;
		}
		Identifier texture = renderer.getTextureLocation(renderPassInfo.renderState());
		RenderType renderType = renderer.getRenderType(renderPassInfo.renderState(), texture);
		if (renderType == null) {
			return;
		}
		int packedLight = renderPassInfo.packedLight();
		int packedOverlay = renderPassInfo.packedOverlay();
		int renderColor = renderPassInfo.renderColor();
		submitGeometry(renderPassInfo, bone, scales, renderTasks,
				new DynamicRenderPass(renderType, packedLight, packedOverlay, renderColor));
		for (RenderPassProvider<R> provider : additionalRenderPasses) {
			DynamicRenderPass renderPass = provider.createRenderPass(renderPassInfo);
			if (renderPass != null) {
				submitGeometry(renderPassInfo, bone, scales, renderTasks, renderPass);
			}
		}
	}

	private static <R extends GeoRenderState> void submitGeometry(RenderPassInfo<R> renderPassInfo, GeoBone bone,
			Map<Direction, Float> scales, SubmitNodeCollector renderTasks, DynamicRenderPass renderPass) {
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderPass.renderType(), (pose, vertexConsumer) -> {
			PoseStack poseStack = renderPassInfo.poseStack();
			poseStack.pushPose();
			poseStack.last().set(pose);
			bone.translateAwayFromPivotPoint(poseStack);
			for (GeoCube cube : ((CuboidGeoBone) bone).cubes) {
				poseStack.pushPose();
				renderCube(cube, bone, scales, poseStack, vertexConsumer,
						renderPass.packedLight(), renderPass.packedOverlay(), renderPass.renderColor());
				poseStack.popPose();
			}
			poseStack.popPose();
		});
	}

	private Map<Direction, Float> getScales(RenderPassInfo<R> renderPassInfo) {
		Map<Direction, Float> scales = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) {
			DataTicket<Float> dataTicket = scaleDataTickets.get(direction);
			float scale = dataTicket == null
					? MAXIMUM_SCALE
					: renderPassInfo.getOrDefaultGeckolibData(dataTicket, MAXIMUM_SCALE);
			scales.put(direction, Math.max(MINIMUM_SCALE, scale));
		}
		return scales;
	}

	private static void renderCube(GeoCube cube, GeoBone bone, Map<Direction, Float> scales,
			PoseStack poseStack, VertexConsumer vertexConsumer,
			int packedLight, int packedOverlay, int renderColor) {
		cube.translateToPivotPoint(poseStack);
		cube.rotate(poseStack);
		cube.translateAwayFromPivotPoint(poseStack);
		Matrix3f normalPose = poseStack.last().normal();
		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) {
				continue;
			}
			Vector3f normal = normalPose.transform(quad.normalVec());
			RenderUtil.fixInvertedFlatCube(cube, normal);
			renderVertices(adjustUvs(quad.vertices(), bone, scales), bone, scales, pose, normal,
					vertexConsumer, packedLight, packedOverlay, renderColor);
		}
	}

	private static GeoVertex[] adjustUvs(GeoVertex[] vertices, GeoBone bone, Map<Direction, Float> scales) {
		GeoVertex[] adjustedVertices = new GeoVertex[vertices.length];
		float pivotX = bone.pivotX() / 16.0F;
		float pivotY = bone.pivotY() / 16.0F;
		float pivotZ = bone.pivotZ() / 16.0F;
		float minimumU = Float.POSITIVE_INFINITY;
		float maximumU = Float.NEGATIVE_INFINITY;
		float minimumV = Float.POSITIVE_INFINITY;
		float maximumV = Float.NEGATIVE_INFINITY;
		for (GeoVertex vertex : vertices) {
			minimumU = Math.min(minimumU, vertex.texU());
			maximumU = Math.max(maximumU, vertex.texU());
			minimumV = Math.min(minimumV, vertex.texV());
			maximumV = Math.max(maximumV, vertex.texV());
		}
		float textureCenterU = (minimumU + maximumU) * 0.5F;
		float textureCenterV = (minimumV + maximumV) * 0.5F;
		for (int i = 0; i < vertices.length; i++) {
			GeoVertex vertex = vertices[i];
			float textureU = vertex.texU();
			float textureV = vertex.texV();
			boolean adjustedU = false;
			boolean adjustedV = false;
			GeoVertex xCounterpart = findAxisCounterpart(vertices, vertex, Direction.Axis.X);
			if (xCounterpart != vertex) {
				float scale = vertex.posX() < pivotX ? scales.get(Direction.WEST) : scales.get(Direction.EAST);
				if (vertex.texU() != xCounterpart.texU()) {
					textureU = scaleUvFromCenter(vertex.texU(), textureCenterU, scale);
					adjustedU = true;
				}
				if (vertex.texV() != xCounterpart.texV()) {
					textureV = scaleUvFromCenter(vertex.texV(), textureCenterV, scale);
					adjustedV = true;
				}
			}
			GeoVertex yCounterpart = findAxisCounterpart(vertices, vertex, Direction.Axis.Y);
			if (yCounterpart != vertex) {
				float scale = vertex.posY() < pivotY ? scales.get(Direction.DOWN) : scales.get(Direction.UP);
				if (!adjustedU && vertex.texU() != yCounterpart.texU()) {
					textureU = scaleUvFromCenter(vertex.texU(), textureCenterU, scale);
					adjustedU = true;
				}
				if (!adjustedV && vertex.texV() != yCounterpart.texV()) {
					textureV = scaleUvFromCenter(vertex.texV(), textureCenterV, scale);
					adjustedV = true;
				}
			}
			GeoVertex zCounterpart = findAxisCounterpart(vertices, vertex, Direction.Axis.Z);
			if (zCounterpart != vertex) {
				float scale = vertex.posZ() < pivotZ ? scales.get(Direction.NORTH) : scales.get(Direction.SOUTH);
				if (!adjustedU && vertex.texU() != zCounterpart.texU()) {
					textureU = scaleUvFromCenter(vertex.texU(), textureCenterU, scale);
				}
				if (!adjustedV && vertex.texV() != zCounterpart.texV()) {
					textureV = scaleUvFromCenter(vertex.texV(), textureCenterV, scale);
				}
			}
			adjustedVertices[i] = vertex.withUVs(textureU, textureV);
		}
		return adjustedVertices;
	}

	private static float scaleUvFromCenter(float coordinate, float center, float scale) {
		return center + (coordinate - center) * scale;
	}

	private static GeoVertex findAxisCounterpart(GeoVertex[] vertices, GeoVertex vertex, Direction.Axis axis) {
		for (GeoVertex candidate : vertices) {
			if (candidate != vertex && differsOnlyOnAxis(vertex, candidate, axis)) {
				return candidate;
			}
		}
		return vertex;
	}

	private static boolean differsOnlyOnAxis(GeoVertex first, GeoVertex second, Direction.Axis axis) {
		return switch (axis) {
			case X -> first.posX() != second.posX() && first.posY() == second.posY() && first.posZ() == second.posZ();
			case Y -> first.posY() != second.posY() && first.posX() == second.posX() && first.posZ() == second.posZ();
			case Z -> first.posZ() != second.posZ() && first.posX() == second.posX() && first.posY() == second.posY();
		};
	}

	private static void renderVertices(GeoVertex[] vertices, GeoBone bone, Map<Direction, Float> scales,
			Matrix4f pose, Vector3f normal,
			VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int renderColor) {
		float pivotX = bone.pivotX() / 16.0F;
		float pivotY = bone.pivotY() / 16.0F;
		float pivotZ = bone.pivotZ() / 16.0F;
		for (GeoVertex vertex : vertices) {
			float x = scaleCoordinate(vertex.posX(), pivotX, scales.get(Direction.WEST), scales.get(Direction.EAST));
			float y = scaleCoordinate(vertex.posY(), pivotY, scales.get(Direction.DOWN), scales.get(Direction.UP));
			float z = scaleCoordinate(vertex.posZ(), pivotZ, scales.get(Direction.NORTH), scales.get(Direction.SOUTH));
			vertexConsumer.addVertex(pose, x, y, z)
					.setColor(renderColor)
					.setUv(vertex.texU(), vertex.texV())
					.setOverlay(packedOverlay)
					.setLight(packedLight)
					.setNormal(normal.x(), normal.y(), normal.z());
		}
	}

	private static float scaleCoordinate(float coordinate, float pivot, float negativeScale, float positiveScale) {
		float scale = coordinate < pivot ? negativeScale : positiveScale;
		return pivot + (coordinate - pivot) * scale;
	}

	public interface RenderPassProvider<R extends GeoRenderState> {
		@Nullable DynamicRenderPass createRenderPass(RenderPassInfo<R> renderPassInfo);
	}

	public record DynamicRenderPass(RenderType renderType, int packedLight, int packedOverlay, int renderColor) {
	}
}
