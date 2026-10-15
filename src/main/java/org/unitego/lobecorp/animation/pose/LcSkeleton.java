package org.unitego.lobecorp.animation.pose;

import com.geckolib.loading.definition.geometry.Geometry;
import com.geckolib.loading.definition.geometry.GeometryBone;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 骨架只持有不可变的绑定姿态与挂点；坐标统一为实体前方为 +Z、位移单位为格。
public class LcSkeleton {
	// 对应 Bedrock GeoJSON 的像素单位，与 GeckoLib 骨骼矩阵的换算一致。
	private static final double MODEL_UNITS_PER_BLOCK = 16;
	private static final String MISSING_GEOMETRY = "模型资源中不存在 geometry 定义";
	private static final String DUPLICATE_BONE = "模型中存在重复骨骼：";
	private static final String MISSING_LOCATOR = "骨骼中不存在挂点：";
	private static final String MISSING_BONE = "模型中不存在骨骼：";
	private static final String CYCLIC_HIERARCHY = "模型骨骼父子关系存在循环：";

	private final Map<String, Bone> bones;

	private LcSkeleton(Map<String, Bone> bones) {
		this.bones = Collections.unmodifiableMap(new LinkedHashMap<>(bones));
		validateHierarchy();
	}

	@SuppressWarnings("UnstableApiUsage")
	public static LcSkeleton parse(String modelJson) {
		// 版本适配点：复用 GeckoLib 5.5.2 的完整 GeoJSON 解析，不创建渲染骨骼或模型。
		var geometry = Geometry.GSON.fromJson(modelJson, Geometry.class);
		var definitions = geometry.definitions();

		if (definitions.length == 0) {
			throw new IllegalArgumentException(MISSING_GEOMETRY);
		}

		var result = new LinkedHashMap<String, Bone>();
		var sourceBones = definitions[0].bones();

		if (sourceBones != null) {
			for (var source : sourceBones) {
				var bone = parseBone(source);

				if (result.putIfAbsent(bone.name(), bone) != null) {
					throw new IllegalArgumentException(DUPLICATE_BONE + bone.name());
				}
			}
		}

		return new LcSkeleton(result);
	}

	public Map<String, Bone> bones() {
		return bones;
	}

	/// 返回骨骼枢轴处的世界变换，与 GeckoLib 的骨骼监听阶段一致。
	public LcTransform boneTransform(LcSkeletalPose pose, String bone, LcTransform worldRoot) {
		return LcTransform.fromMatrix(boneMatrix(pose, requireBone(bone), worldRoot));
	}

	public LcTransform locatorTransform(LcSkeletalPose pose, String bone, String locator, LcTransform worldRoot) {
		var definition = requireBone(bone);
		var local = Objects.requireNonNull(definition.locators().get(locator), MISSING_LOCATOR + bone + "/" + locator);
		var pivot = definition.pivot();
		var matrix = boneMatrix(pose, definition, worldRoot)
				.translate(-pivot.x, -pivot.y, -pivot.z)
				.mul(local.matrix());
		return LcTransform.fromMatrix(matrix);
	}

	@SuppressWarnings("UnstableApiUsage")
	private static Bone parseBone(GeometryBone source) {
		var locators = new LinkedHashMap<String, LcTransform>();

		if (source.locators() != null) {
			for (var entry : source.locators().entrySet()) {
				var locator = entry.getValue();
				var angles = sourceRotation(locator.rotation());
				locators.put(entry.getKey(), new LcTransform(sourcePosition(locator.offset()),
						new Quaternionf().rotationZYX((float) angles.z, (float) angles.y, (float) angles.x),
						new Vec3(1, 1, 1)));
			}
		}

		return new Bone(source.name(), source.parent(), sourcePosition(source.pivot()),
				sourceRotation(source.rotation()), locators);
	}

	private Matrix4d boneMatrix(LcSkeletalPose pose, Bone target, LcTransform worldRoot) {
		var chain = new ArrayList<Bone>();

		for (var current = target; current != null; current = current.parent() == null ? null : requireBone(current.parent())) {
			chain.add(current);
		}

		var matrix = worldRoot.matrix();

		for (var index = chain.size() - 1; index >= 0; index--) {
			var bone = chain.get(index);
			var delta = pose.get(bone.name());
			var position = delta.position();
			var pivot = bone.pivot();
			var rotation = bone.rotation().add(pose.rotationAngles(bone.name()));
			var scale = delta.scale();
			matrix.translate(position.x, position.y, position.z)
					.translate(pivot.x, pivot.y, pivot.z)
					.rotateZYX(rotation.z, rotation.y, rotation.x)
					.scale(scale.x, scale.y, scale.z);

			if (index > 0) {
				matrix.translate(-pivot.x, -pivot.y, -pivot.z);
			}
		}

		return matrix;
	}

	private Bone requireBone(String name) {
		return Objects.requireNonNull(bones.get(name), MISSING_BONE + name);
	}

	private void validateHierarchy() {
		for (var bone : bones.values()) {
			var visited = new HashSet<String>();

			for (var current = bone; current != null; current = current.parent() == null ? null : requireBone(current.parent())) {
				if (!visited.add(current.name())) {
					throw new IllegalArgumentException(CYCLIC_HIERARCHY + current.name());
				}
			}
		}
	}

	private static Vec3 sourcePosition(@Nullable Vec3 source) {
		return source == null ? Vec3.ZERO : new Vec3(source.x, source.y, -source.z).scale(1 / MODEL_UNITS_PER_BLOCK);
	}

	private static Vec3 sourceRotation(@Nullable Vec3 source) {
		return source == null ? Vec3.ZERO : new Vec3(Math.toRadians(source.x), -Math.toRadians(source.y), -Math.toRadians(source.z));
	}

	/// pivot 是模型绝对枢轴坐标，rotation 是转换后以弧度表示的完整 ZYX Euler 通道。
	public record Bone(String name, @Nullable String parent, Vec3 pivot, Vec3 rotation,
	                   Map<String, LcTransform> locators) {
		public Bone {
			locators = Map.copyOf(locators);
		}
	}
}
