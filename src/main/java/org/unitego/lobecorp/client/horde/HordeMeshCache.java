package org.unitego.lobecorp.client.horde;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.animation.LcModelLodUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/// 共享不可变的已变形网格；不写 GeoBone.frameSnapshot，也不运行动画事件。
public class HordeMeshCache {
	private static final int STRIDE = 8;
	private static final Map<HordeVisualDefinition, Resources> RESOURCES = new HashMap<>();
	private static final LinkedHashMap<Key, Mesh> MESHES = new LinkedHashMap<>(16, 0.75f, true);
	private static int built;
	private static long bytes;
	private static String detailSignature = "";

	public static void beginFrame() {
		built = 0;
		String details = org.unitego.lobecorp.config.client.VisualQualityConfig.MODELS.get() + ":"
				+ org.unitego.lobecorp.config.client.VisualQualityConfig.MIN_CUBES.get() + ":"
				+ org.unitego.lobecorp.config.client.VisualQualityConfig.MEDIUM_CUBE_DETAIL.get() + ":"
				+ org.unitego.lobecorp.config.client.VisualQualityConfig.FAR_CUBE_DETAIL.get();
		if (!details.equals(detailSignature)) {
			MESHES.clear();
			bytes = 0;
			detailSignature = details;
		}
	}

	public static Pair meshes(HordeClient.Instance instance) {
		Resources resources = RESOURCES.computeIfAbsent(instance.visual(), HordeMeshCache::load);
		if (resources.clip == null || resources.model == null) {
			return null;
		}
		int fps = instance.band() == 0 ? HordeVisualConfig.NEAR_FPS.get() : HordeVisualConfig.MEDIUM_FPS.get();
		double samplingQuality = Math.max(0.25, Math.ceil(HordeClient.quality() * 4) / 4);
		fps = Math.max(1, (int) Math.round(fps * samplingQuality));
		int frames = Math.max(1, (int) Math.ceil(resources.clip.durationSeconds() * fps));
		double phase = instance.phase() / (double) HordeVisualConfig.POSE_GROUPS.get();
		double seconds = instance.time() / 20 + phase * resources.clip.durationSeconds();
		double frame = (seconds / resources.clip.durationSeconds() % 1 + 1) % 1 * frames;
		int first = instance.band() == 2 ? (int) (phase * 4) : (int) Math.floor(frame);
		if (instance.band() == 2) {
			frames = 4;
		}
		Mesh a = mesh(instance.visual(), resources, instance.band(), first, frames);
		Mesh b = instance.band() == 0 ? mesh(instance.visual(), resources, 0, (first + 1) % frames, frames) : a;
		if (a == null) {
			a = MESHES.entrySet().stream().filter(entry -> entry.getKey().visual.equals(instance.visual())
					&& entry.getKey().band == instance.band()).map(Map.Entry::getValue).findFirst().orElse(null);
		}
		if (a == null) {
			return null;
		}
		if (b == null || b.vertices.length != a.vertices.length) {
			b = a;
		}
		return new Pair(a, b, instance.band() == 0 ? (float) (frame - Math.floor(frame)) : 0);
	}

	public static double duration(HordeVisualDefinition visual) {
		var resources = RESOURCES.computeIfAbsent(visual, HordeMeshCache::load);
		return resources.clip == null ? 0 : resources.clip.durationSeconds();
	}

	public static Mesh bakePose(HordeVisualDefinition visual, int frame, int frames) {
		var resources = RESOURCES.computeIfAbsent(visual, HordeMeshCache::load);
		return resources.clip == null || resources.model == null ? null : bake(resources, 0, frame, frames);
	}

	private static Mesh bake(Resources resources, int band, int frame, int frames) {
		double time = resources.clip.durationSeconds() * frame / frames;
		LcSkeletalPose pose = resources.clip.sample(time, name -> switch (name) {
			case "query.modified_move_speed", "query.ground_speed", "query.is_moving",
			     "q.modified_move_speed", "q.ground_speed", "q.is_moving" -> 1;
			default -> 0;
		});
		var output = new Capture();
		var stack = new PoseStack();
		for (GeoBone bone : resources.model.topLevelBones()) {
			bakeBone(resources.model, bone, pose, stack, output, band);
		}
		return new Mesh(Arrays.copyOf(output.values, output.size));
	}

	private static Mesh mesh(HordeVisualDefinition visual, Resources resources, int band, int frame, int frames) {
		var key = new Key(visual, band, frame, frames);
		Mesh mesh = MESHES.get(key);
		if (mesh != null || built >= HordeVisualConfig.BUILD_PER_FRAME.get()) {
			return mesh;
		}
		built++;
		mesh = bake(resources, band, frame, frames);
		long budget = HordeVisualConfig.CACHE_MIB.get() * 1024L * 1024;
		long size = mesh.vertices.length * (long) Float.BYTES;
		if (size > budget) {
			return null;
		}
		while (!MESHES.isEmpty() && bytes + size > budget) {
			var iterator = MESHES.entrySet().iterator();
			bytes -= iterator.next().getValue().vertices.length * (long) Float.BYTES;
			iterator.remove();
		}
		bytes += size;
		MESHES.put(key, mesh);
		return mesh;
	}

	private static void bakeBone(BakedGeoModel model, GeoBone bone, LcSkeletalPose pose,
	                             PoseStack stack, Capture output, int band) {
		stack.pushPose();
		var transform = pose.get(bone.name());
		var position = transform.position();
		var angles = pose.rotationAngles(bone.name());
		var scale = transform.scale();
		stack.translate(position.x, position.y, -position.z);
		bone.translateToPivotPoint(stack);
		stack.mulPose(new Quaternionf().rotationZYX(bone.baseRotZ() - (float) angles.z,
				bone.baseRotY() + (float) angles.y, bone.baseRotX() - (float) angles.x));
		stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
		bone.translateAwayFromPivotPoint(stack);
		if (bone instanceof CuboidGeoBone cuboid) {
			for (var cube : cuboid.cubes) {
				if (!LcModelLodUtil.shouldRenderCube(model, cube, band)) {
					continue;
				}
				stack.pushPose();
				cube.render(stack, output, 0, 0, -1);
				stack.popPose();
			}
		}
		for (var child : bone.children()) {
			bakeBone(model, child, pose, stack, output, band);
		}
		stack.popPose();
	}

	private static Resources load(HordeVisualDefinition visual) {
		try {
			String model = read(visual.model(), "geckolib/models/", ".geo.json");
			String animation = read(visual.animation(), "geckolib/animations/", ".animation.json");
			var content = LcAnimationContent.of(visual.model(), visual.animation(), model, animation);
			return new Resources(LcClientAnimationResources.model(content), LcAnimationClip.parse(animation, visual.runAnimation()));
		} catch (IOException | RuntimeException exception) {
			Lobecorp.LOGGER.warn("无法准备群体奔跑网格 {}", visual.model(), exception);
			return new Resources(null, null);
		}
	}

	private static String read(Identifier id, String prefix, String suffix) throws IOException {
		Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
		try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()) {
			return com.google.common.io.CharStreams.toString(reader);
		}
	}

	public static void clear() {
		MESHES.clear();
		RESOURCES.clear();
		bytes = 0;
		built = 0;
	}

	public static String statistics() {
		return MESHES.size() + "/" + bytes / 1024 + "KiB";
	}

	public record Mesh(float[] vertices) {
	}

	public record Pair(Mesh first, Mesh second, float weight) {
	}

	private record Key(HordeVisualDefinition visual, int band, int frame, int frames) {
	}

	private record Resources(BakedGeoModel model, LcAnimationClip clip) {
	}

	private static class Capture implements VertexConsumer {
		private float[] values = new float[4096];
		private int size;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (size + STRIDE > values.length) {
				values = Arrays.copyOf(values, values.length * 2);
			}
			size += STRIDE;
			values[size - STRIDE] = x;
			values[size - STRIDE + 1] = y;
			values[size - STRIDE + 2] = z;
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
		public VertexConsumer setUv(float u, float v) {
			values[size - STRIDE + 3] = u;
			values[size - STRIDE + 4] = v;
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
		public VertexConsumer setNormal(float x, float y, float z) {
			values[size - STRIDE + 5] = x;
			values[size - STRIDE + 6] = y;
			values[size - STRIDE + 7] = z;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
