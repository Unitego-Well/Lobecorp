package org.unitego.lobecorp.client.horde;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.client.animation.LcAnimationBoneAdapter;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 同一资源代际只准备一次烘焙动画帧；GPU 顶点阶段只选帧并读取静态顶点，不再做骨骼变换。
public class HordeMeshCache {
	public static final int SAMPLE_FPS = 20;
	public static final int VERTEX_STRIDE = 9;
	// 每骨骼每帧布局须与 horde_instanced.vsh 的 6 分量帧顶点布局一致。
	private static final int FRAME_VERTEX_VALUES = 6;
	private static final Map<MeshKey, Model[]> MODELS = new HashMap<>();
	private static final Map<MeshKey, Preparation> PREPARATIONS = new HashMap<>();
	private static final Map<MeshKey, RuntimeException> FAILURES = new HashMap<>();
	private static final ArrayDeque<Preparation> PENDING = new ArrayDeque<>();

	public static Model get(HordeVisualDefinition visual) {
		FAILURES.remove(key(visual));
		request(visual);
		while (true) {
			var model = ready(visual);
			if (model != null) {
				return model;
			}

			var preparation = PREPARATIONS.get(key(visual));
			if (preparation == null) {
				throw new IllegalStateException("群体模型准备任务不存在");
			}

			progress(preparation);
		}
	}

	/// 只登记准备请求，不读取资源或烘焙模型；客户端线程通过 advance 推进。
	private static MeshKey key(HordeVisualDefinition visual) {
		return new MeshKey(visual, HordeVisualConfig.ANIMATION_FRAMES.get(), HordeVisualConfig.REDUCE_HIDDEN_FACES.get());
	}

	public static int settingsKey() {
		return HordeVisualConfig.ANIMATION_FRAMES.get() | (HordeVisualConfig.REDUCE_HIDDEN_FACES.get() ? 256 : 0);
	}

	public static void request(HordeVisualDefinition visual) {
		var key = key(visual);
		if (MODELS.containsKey(key) || PREPARATIONS.containsKey(key) || FAILURES.containsKey(key)) {
			return;
		}
		var preparation = new Preparation(key);
		PREPARATIONS.put(key, preparation);
		PENDING.addLast(preparation);
	}

	/// 预算单位为纳秒；解析、分配与单个 cube 是不可抢占的协作式工作单元。
	public static void advance(long budgetNanos) {
		if (budgetNanos <= 0) {
			return;
		}

		long started = System.nanoTime();
		do {
			var preparation = PENDING.pollFirst();
			if (preparation == null) {
				return;
			}

			progress(preparation);
			if (PREPARATIONS.containsKey(preparation.key)) {
				PENDING.addLast(preparation);
			}
		} while (System.nanoTime() - started < budgetNanos);
	}

	/// 未准备完成时返回 null；失败缓存到资源清理或同步 get 重试，由调用方处理。
	public static @Nullable Model ready(HordeVisualDefinition visual) {
		return ready(visual, 0);
	}

	public static @Nullable Model ready(HordeVisualDefinition visual, int tier) {
		if (tier < 0 || tier > 3) {
			throw new IllegalArgumentException("群体模型精度档无效");
		}
		var key = key(visual);
		var failure = FAILURES.get(key);
		if (failure != null) {
			throw failure;
		}
		var models = MODELS.get(key);
		return models == null ? null : models[tier];
	}

	private static void progress(Preparation preparation) {
		try {
			preparation.step();
			if (preparation.result == null) {
				return;
			}

			MODELS.put(preparation.key, preparation.result);
		} catch (IOException exception) {
			FAILURES.put(preparation.key,
					new IllegalArgumentException("无法读取群体模型或动画：" + preparation.visual.model(), exception));
		} catch (RuntimeException exception) {
			FAILURES.put(preparation.key, exception);
		}

		PREPARATIONS.remove(preparation.key);
		PENDING.remove(preparation);
	}

	private enum PreparationStage {
		MODEL_RESOURCE, ANIMATION_RESOURCE, MODEL_PARSE, ANIMATION_PARSE, BONES, STATIC_GEOMETRY,
		FRAME_STORAGE, FRAME_SAMPLE, FRAME_GEOMETRY, COMPLETE
	}

	private static class Preparation {
		private final HordeVisualDefinition visual;
		private final MeshKey key;
		private final List<HordeMeshReduction.Cube> cubes = new ArrayList<>();
		private final List<Bone> bones = new ArrayList<>();
		private final ArrayDeque<PendingBone> remainingBones = new ArrayDeque<>();
		private final Map<String, BoneSnapshot> snapshots = new HashMap<>();
		private final BoneSnapshots accessor = name -> Optional.ofNullable(snapshots.get(name));
		private final Capture output = new Capture();
		private final PoseStack stack = new PoseStack();
		private PreparationStage stage = PreparationStage.MODEL_RESOURCE;
		private String modelJson = "";
		private String animationJson = "";
		private @Nullable LcAnimationClip clip;
		private @Nullable FrameCapture frameCapture;
		private @Nullable Model[] result;
		private float[] frameData = new float[0];
		private int vertexCount;
		private int frames;
		private int frame;
		private int bone;
		private int cube;
		private Set<String> animatedBones = Set.of();

		private Preparation(MeshKey key) {
			this.key = key;
			this.visual = key.visual();
		}

		private void step() throws IOException {
			switch (stage) {
				case MODEL_RESOURCE -> {
					modelJson = read(visual.model(), "geckolib/models/", ".geo.json");
					stage = PreparationStage.ANIMATION_RESOURCE;
				}
				case ANIMATION_RESOURCE -> {
					animationJson = read(visual.animation(), "geckolib/animations/", ".animation.json");
					stage = PreparationStage.MODEL_PARSE;
				}
				case MODEL_PARSE -> parseModel();
				case ANIMATION_PARSE -> parseAnimation();
				case BONES -> collectBone();
				case STATIC_GEOMETRY -> captureStaticCube();
				case FRAME_STORAGE -> allocateFrames();
				case FRAME_SAMPLE -> sampleFrame();
				case FRAME_GEOMETRY -> captureFrameCube();
				case COMPLETE -> {
				}
			}
		}

		private void parseModel() {
			var model = LcClientAnimationResources.model(
					LcAnimationContent.of(visual.model(), visual.animation(), modelJson, animationJson));
			for (var entry : model.topLevelBones()) {
				remainingBones.addLast(new PendingBone(entry, -1, 0));
			}

			stage = PreparationStage.ANIMATION_PARSE;
		}

		private void parseAnimation() {
			var parsed = LcAnimationClip.parse(animationJson, visual.runAnimation());
			if (parsed.durationSeconds() <= 0) {
				throw new IllegalArgumentException("奔跑循环时长必须大于零");
			}

			clip = parsed;
			stage = PreparationStage.BONES;
		}

		private void collectBone() {
			var pending = remainingBones.pollFirst();
			if (pending == null) {
				if (bones.isEmpty()) {
					throw new IllegalArgumentException("模型没有骨骼");
				}

				stage = PreparationStage.STATIC_GEOMETRY;
				return;
			}

			if (pending.depth() >= 64) {
				throw new IllegalArgumentException("群体模型骨骼层级超过 64");
			}

			int index = bones.size();
			bones.add(new Bone(pending.bone(), pending.parent()));
			snapshots.put(pending.bone().name(), BoneSnapshot.create(pending.bone()));
			var children = pending.bone().children();
			for (int child = children.length - 1; child >= 0; child--) {
				remainingBones.addFirst(new PendingBone(children[child], index, pending.depth() + 1));
			}
		}

		private void captureStaticCube() {
			if (bone >= bones.size()) {
				vertexCount = output.size / VERTEX_STRIDE;
				if (vertexCount == 0 || vertexCount % 4 != 0) {
					throw new IllegalArgumentException("群体模型需要完整的可见四边形");
				}
				stage = PreparationStage.FRAME_STORAGE;
				return;
			}

			if (bones.get(bone).bone() instanceof CuboidGeoBone cuboid && cube < cuboid.cubes.length) {
				output.bone = bone;
				int first = output.size / VERTEX_STRIDE;
				cuboid.cubes[cube++].render(new PoseStack(), output, 0, 0, -1);
				cubes.add(new HordeMeshReduction.Cube(first, output.size / VERTEX_STRIDE - first, bone));
				return;
			}

			bone++;
			cube = 0;
		}

		private void allocateFrames() {
			var animation = Objects.requireNonNull(clip);
			frames = key.frames();
			frameData = new float[Math.multiplyExact(Math.multiplyExact(frames, vertexCount), FRAME_VERTEX_VALUES)];
			frameCapture = new FrameCapture(frameData, vertexCount);
			stage = PreparationStage.FRAME_SAMPLE;
		}

		private void sampleFrame() {
			var animation = Objects.requireNonNull(clip);
			if (frame >= frames) {
				var original = new Model(Arrays.copyOf(output.values, output.size), frameData, vertexCount, frames,
						(float) animation.durationSeconds());
				int[] rigidGroups = new int[bones.size()];
				for (int index = 0; index < bones.size(); index++) {
					var bone = bones.get(index);
					rigidGroups[index] = !animatedBones.contains(bone.bone().name()) && bone.parent() >= 0
							? bone.parent() : index;
				}
				result = HordeMeshReduction.prepare(original, cubes,
						bones.stream().map(entry -> entry.bone().name()).toList(), rigidGroups, visual.texture(), key.hidden());
				stage = PreparationStage.COMPLETE;
				return;
			}

			LcSkeletalPose pose = animation.sample(animation.durationSeconds() * frame / frames, name -> switch (name) {
				case "query.modified_move_speed", "query.ground_speed", "query.is_moving",
				     "q.modified_move_speed", "q.ground_speed", "q.is_moving" -> 1;
				default -> 0;
			});
			snapshots.values().forEach(snapshot -> snapshot.setTranslation(0, 0, 0)
					.setRotation(0, 0, 0).setScale(1, 1, 1));
			if (frame == 0) {
				animatedBones = pose.bones().keySet();
			}
			LcAnimationBoneAdapter.apply(pose, accessor);
			Objects.requireNonNull(frameCapture).start(frame * vertexCount * FRAME_VERTEX_VALUES);
			bone = 0;
			cube = 0;
			stage = PreparationStage.FRAME_GEOMETRY;
		}

		private void captureFrameCube() {
			var capture = Objects.requireNonNull(frameCapture);
			if (bone >= bones.size()) {
				if (capture.vertex != vertexCount) {
					throw new IllegalStateException("烘焙帧顶点数量与静态几何不一致");
				}

				frame++;
				stage = PreparationStage.FRAME_SAMPLE;
				return;
			}

			if (!(bones.get(bone).bone() instanceof CuboidGeoBone cuboid) || cube >= cuboid.cubes.length) {
				if (cube > 0) {
					stack.popPose();
				}

				bone++;
				cube = 0;
				return;
			}

			try {
				if (cube == 0) {
					snapshots.values().forEach(BoneSnapshot::apply);
					stack.pushPose();
					prepBoneChain(stack, bone, bones);
				}

				cuboid.cubes[cube++].render(stack, capture, 0, 0, -1);
			} finally {
				// 私有矩阵栈可跨预算保留，共享骨骼快照必须在每次返回前解除。
				snapshots.values().forEach(BoneSnapshot::cleanup);
			}
		}
	}

	private record PendingBone(GeoBone bone, int parent, int depth) {
	}

	private static void prepBoneChain(PoseStack stack, int index, List<Bone> bones) {
		int parent = bones.get(index).parent();
		if (parent >= 0) {
			prepBoneChain(stack, parent, bones);
		}
		RenderUtil.prepMatrixForBone(stack, bones.get(index).bone());
	}

	private static String read(Identifier id, String prefix, String suffix) throws IOException {
		Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
		try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()) {
			return com.google.common.io.CharStreams.toString(reader);
		}
	}

	/// 不执行动画控制器；从资源的静态骨骼变换冻结一次可共享几何。
	public static Model freeze(HordeVisualDefinition visual) {
		try {
			String modelJson = read(visual.model(), "geckolib/models/", ".geo.json");
			String animationJson = read(visual.animation(), "geckolib/animations/", ".animation.json");
			var baked = LcClientAnimationResources.model(
					LcAnimationContent.of(visual.model(), visual.animation(), modelJson, animationJson));
			var bones = new ArrayList<Bone>();
			var pending = new ArrayDeque<PendingBone>();
			for (var root : baked.topLevelBones()) {
				pending.addLast(new PendingBone(root, -1, 0));
			}
			while (!pending.isEmpty()) {
				var next = pending.removeFirst();
				if (next.depth() >= 64) {
					throw new IllegalArgumentException("尸体模型骨骼层级过深");
				}
				int index = bones.size();
				bones.add(new Bone(next.bone(), next.parent()));
				var children = next.bone().children();
				for (int child = children.length - 1; child >= 0; child--) {
					pending.addFirst(new PendingBone(children[child], index, next.depth() + 1));
				}
			}
			var snapshots = bones.stream().map(entry -> BoneSnapshot.create(entry.bone())
					.setTranslation(0, 0, 0).setRotation(0, 0, 0).setScale(1, 1, 1)).toList();
			var output = new Capture();
			var pose = new PoseStack();
			try {
				snapshots.forEach(BoneSnapshot::apply);
				for (int index = 0; index < bones.size(); index++) {
					pose.pushPose();
					try {
						prepBoneChain(pose, index, bones);
						output.bone = index;
						if (bones.get(index).bone() instanceof CuboidGeoBone cuboid) {
							for (var cube : cuboid.cubes) {
								cube.render(pose, output, 0, 0, -1);
							}
						}
					} finally {
						pose.popPose();
					}
				}
			} finally {
				snapshots.forEach(BoneSnapshot::cleanup);
			}
			int count = output.size / VERTEX_STRIDE;
			if (count == 0 || count % 4 != 0) {
				throw new IllegalArgumentException("尸体模型缺少完整四边形");
			}
			float[] vertices = Arrays.copyOf(output.values, output.size);
			float[] frame = new float[count * FRAME_VERTEX_VALUES];
			for (int vertex = 0; vertex < count; vertex++) {
				System.arraycopy(vertices, vertex * VERTEX_STRIDE, frame, vertex * FRAME_VERTEX_VALUES, 3);
				System.arraycopy(vertices, vertex * VERTEX_STRIDE + 5, frame, vertex * FRAME_VERTEX_VALUES + 3, 3);
			}
			return new Model(vertices, frame, count, 1, 1);
		} catch (IOException failure) {
			throw new IllegalArgumentException("无法冻结尸体模型：" + visual.model(), failure);
		}
	}

	public static void clear() {
		MODELS.clear();
		PREPARATIONS.clear();
		FAILURES.clear();
		PENDING.clear();
	}

	public record Model(float[] vertices, float[] frameVertices, int vertexCount, int frames, float duration,
	                    int sourceVertexCount, int tier) {
		public Model(float[] vertices, float[] frameVertices, int vertexCount, int frames, float duration) {
			this(vertices, frameVertices, vertexCount, frames, duration, vertexCount, 0);
		}
	}

	private record MeshKey(HordeVisualDefinition visual, int frames, boolean hidden) {
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

	@NullMarked
	private static class FrameCapture implements VertexConsumer {
		private final float[] data;
		private final int count;
		private int offset;
		private int vertex;

		private FrameCapture(float[] data, int count) {
			this.data = data;
			this.count = count;
		}

		private void start(int offset) {
			this.offset = offset;
			this.vertex = 0;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (vertex >= count) {
				throw new IllegalStateException("烘焙帧顶点数量超过静态几何");
			}
			int at = offset + vertex * FRAME_VERTEX_VALUES;
			data[at] = x;
			data[at + 1] = y;
			data[at + 2] = z;
			vertex++;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			int at = offset + (vertex - 1) * FRAME_VERTEX_VALUES + 3;
			float length = (float) Math.sqrt(x * x + y * y + z * z);
			if (length > 0.0001f) {
				x /= length;
				y /= length;
				z /= length;
			} else {
				x = 0;
				y = 1;
				z = 0;
			}
			data[at] = x;
			data[at + 1] = y;
			data[at + 2] = z;
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
