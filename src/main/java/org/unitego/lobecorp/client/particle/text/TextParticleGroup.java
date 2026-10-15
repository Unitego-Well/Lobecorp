package org.unitego.lobecorp.client.particle.text;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/// 按字体材质合并所有可见文本，避免每字创建 TextSubmit 和重新排版。
@NullMarked
public class TextParticleGroup extends ParticleGroup<TextParticle> {

	public TextParticleGroup(ParticleEngine engine) {
		super(engine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
		var draws = new ArrayList<Draw>();
		for (var particle : particles) {
			if (!particle.isAlive()) {
				continue;
			}

			particle.ensureLayout();
			if (frustum.isVisible(particle.getBoundingBox())) {
				particle.extract(camera.position(), partialTickTime, draws);
			}
		}

		var batches = new LinkedHashMap<RenderType, List<Draw>>();
		for (var draw : draws) {
			batches.computeIfAbsent(draw.mesh().renderType(), _ -> new ArrayList<>()).add(draw);
		}

		var frozen = new ArrayList<Batch>();
		batches.forEach((type, entries) -> frozen.add(new Batch(type, List.copyOf(entries))));
		return new State(List.copyOf(frozen));
	}

	/// 每帧矩阵是提取阶段创建的快照，提交回调不再访问粒子或客户端 tick 状态。
	public record Draw(TextGlyphMesh mesh, Matrix4f pose, int light) {
	}

	private record Batch(RenderType type, List<Draw> draws) {

		private void render(PoseStack.Pose base, VertexConsumer buffer) {
			var pose = new Matrix4f();
			for (var draw : draws) {
				base.pose().mul(draw.pose(), pose);
				draw.mesh().render(pose, buffer, draw.light());
			}
		}
	}

	private record State(List<Batch> batches) implements ParticleGroupRenderState {

		@Override
		public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
			var pose = new PoseStack();
			for (var batch : batches) {
				collector.submitCustomGeometry(pose, batch.type(), batch::render);
			}
		}
	}
}
