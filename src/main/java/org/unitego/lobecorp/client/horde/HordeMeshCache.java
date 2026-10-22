package org.unitego.lobecorp.client.horde;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 同一资源代际只准备一次局部骨骼 TRS；GPU 沿父骨骼链变形，不修改共享模型。
public class HordeMeshCache {
	public static final int SAMPLE_FPS = 20;
	public static final int VERTEX_STRIDE = 9;
	private static final int BONE_STRIDE = 12;
	private static final Map<HordeVisualDefinition, Model> MODELS = new HashMap<>();

	public static Model get(HordeVisualDefinition visual) {
		return MODELS.computeIfAbsent(visual, HordeMeshCache::prepare);
	}

	private static Model prepare(HordeVisualDefinition visual) {
		try {
			String modelJson = read(visual.model(), "geckolib/models/", ".geo.json");
			String animation = read(visual.animation(), "geckolib/animations/", ".animation.json");
			var model = LcClientAnimationResources.model(LcAnimationContent.of(visual.model(), visual.animation(), modelJson, animation));
			var clip = LcAnimationClip.parse(animation, visual.runAnimation());
			if (clip.durationSeconds() <= 0) {
				throw new IllegalArgumentException("奔跑循环时长必须大于零");
			}

			var bones = new ArrayList<Bone>();
			for (var bone : model.topLevelBones()) {
				collect(bone, -1, bones, 0);
			}
			if (bones.isEmpty()) {
				throw new IllegalArgumentException("模型没有骨骼");
			}

			var output = new Capture();
			for (int i = 0; i < bones.size(); i++) {
				output.bone = i;
				if (bones.get(i).bone() instanceof CuboidGeoBone cuboid) {
					for (var cube : cuboid.cubes) {
						cube.render(new PoseStack(), output, 0, 0, -1);
					}
				}
			}
			int frames = Math.max(1, (int) Math.ceil(clip.durationSeconds() * SAMPLE_FPS));
			var data = new float[Math.multiplyExact(Math.multiplyExact(frames, bones.size()), BONE_STRIDE)];
			for (int frame = 0; frame < frames; frame++) {
				LcSkeletalPose pose = clip.sample(clip.durationSeconds() * frame / frames, name -> switch (name) {
					case "query.modified_move_speed", "query.ground_speed", "query.is_moving",
					     "q.modified_move_speed", "q.ground_speed", "q.is_moving" -> 1;
					default -> 0;
				});
				for (int bone = 0; bone < bones.size(); bone++) {
					writePose(data, (frame * bones.size() + bone) * BONE_STRIDE, bones.get(bone), pose);
				}
			}
			return new Model(Arrays.copyOf(output.values, output.size), data, bones.size(), frames, (float) clip.durationSeconds());
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法读取群体模型或动画：" + visual.model(), exception);
		}
	}

	private static void collect(GeoBone bone, int parent, List<Bone> bones, int depth) {
		if (depth >= 64) {
			throw new IllegalArgumentException("群体模型骨骼层级超过 64");
		}
		int index = bones.size();
		bones.add(new Bone(bone, parent));
		for (var child : bone.children()) {
			collect(child, index, bones, depth + 1);
		}
	}

	private static void writePose(float[] data, int offset, Bone entry, LcSkeletalPose pose) {
		var bone = entry.bone();
		var transform = pose.get(bone.name());
		var position = transform.position();
		var angles = pose.rotationAngles(bone.name());
		var scale = transform.scale();
		var stack = new PoseStack();
		stack.translate(position.x, position.y, -position.z);
		bone.translateToPivotPoint(stack);
		stack.mulPose(new Quaternionf().rotationZYX(bone.baseRotZ() - (float) angles.z,
				bone.baseRotY() + (float) angles.y, bone.baseRotX() - (float) angles.x));
		stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
		bone.translateAwayFromPivotPoint(stack);
		var matrix = stack.last().pose();
		var translation = matrix.getTranslation(new Vector3f());
		var rotation = new Quaternionf().rotationZYX(bone.baseRotZ() - (float) angles.z,
				bone.baseRotY() + (float) angles.y, bone.baseRotX() - (float) angles.x);
		data[offset] = translation.x;
		data[offset + 1] = translation.y;
		data[offset + 2] = translation.z;
		data[offset + 3] = entry.parent();
		data[offset + 4] = rotation.x;
		data[offset + 5] = rotation.y;
		data[offset + 6] = rotation.z;
		data[offset + 7] = rotation.w;
		data[offset + 8] = (float) scale.x;
		data[offset + 9] = (float) scale.y;
		data[offset + 10] = (float) scale.z;
	}

	private static String read(Identifier id, String prefix, String suffix) throws IOException {
		Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
		try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()) {
			return com.google.common.io.CharStreams.toString(reader);
		}
	}

	public static void clear() {
		MODELS.clear();
	}

	public record Model(float[] vertices, float[] animation, int bones, int frames, float duration) {
	}

	private record Bone(GeoBone bone, int parent) {
	}

	@NullMarked
	private static class Capture implements VertexConsumer {
		private float[] values = new float[4096];
		private int size;
		private int bone;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (size + VERTEX_STRIDE > values.length) {
				values = Arrays.copyOf(values, values.length * 2);
			}
			size += VERTEX_STRIDE;
			values[size - VERTEX_STRIDE] = x;
			values[size - VERTEX_STRIDE + 1] = y;
			values[size - VERTEX_STRIDE + 2] = z;
			values[size - VERTEX_STRIDE + 8] = bone;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			values[size - VERTEX_STRIDE + 3] = u;
			values[size - VERTEX_STRIDE + 4] = v;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			values[size - VERTEX_STRIDE + 5] = x;
			values[size - VERTEX_STRIDE + 6] = y;
			values[size - VERTEX_STRIDE + 7] = z;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
