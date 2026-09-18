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
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.BiConsumer;

/// 使用目标骨骼现有方块的尺寸、位置和 UV，按渲染数据比例动态重绘方块。
public class DynamicCubeGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
	/// 动态方块完全收缩时的比例
	private static final float MINIMUM_SCALE = 0.0F;
	/// 动态方块保持原始尺寸时的比例
	private static final float MAXIMUM_SCALE = 1.0F;

	private final String boneName;
	private final DataTicket<Float> scaleDataTicket;

	public DynamicCubeGeoLayer(GeoRenderer<T, O, R> renderer, String boneName, DataTicket<Float> scaleDataTicket) {
		super(renderer);
		this.boneName = boneName;
		this.scaleDataTicket = scaleDataTicket;
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
		float scale = Math.clamp(renderPassInfo.getOrDefaultGeckolibData(scaleDataTicket, MINIMUM_SCALE),
				MINIMUM_SCALE, MAXIMUM_SCALE);
		if (scale <= MINIMUM_SCALE) {
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
		renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
			PoseStack poseStack = renderPassInfo.poseStack();
			poseStack.pushPose();
			poseStack.last().set(pose);
			bone.translateAwayFromPivotPoint(poseStack);
			for (GeoCube cube : ((CuboidGeoBone) bone).cubes) {
				poseStack.pushPose();
				renderCube(cube, scale, poseStack, vertexConsumer, packedLight, packedOverlay, renderColor);
				poseStack.popPose();
			}
			poseStack.popPose();
		});
	}

	private static void renderCube(GeoCube cube, float scale, PoseStack poseStack, VertexConsumer vertexConsumer,
			int packedLight, int packedOverlay, int renderColor) {
		cube.translateToPivotPoint(poseStack);
		cube.rotate(poseStack);
		poseStack.scale(scale, scale, scale);
		cube.translateAwayFromPivotPoint(poseStack);
		Matrix3f normalPose = poseStack.last().normal();
		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) {
				continue;
			}
			Vector3f normal = normalPose.transform(quad.normalVec());
			RenderUtil.fixInvertedFlatCube(cube, normal);
			for (GeoVertex vertex : quad.vertices()) {
				vertexConsumer.addVertex(pose, vertex.posX(), vertex.posY(), vertex.posZ())
						.setColor(renderColor)
						.setUv(vertex.texU(), vertex.texV())
						.setOverlay(packedOverlay)
						.setLight(packedLight)
						.setNormal(normal.x(), normal.y(), normal.z());
			}
		}
	}
}
