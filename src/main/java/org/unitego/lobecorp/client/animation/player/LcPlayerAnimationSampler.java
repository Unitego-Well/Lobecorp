package org.unitego.lobecorp.client.animation.player;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.AnimationTimeline;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.Animation;
import com.geckolib.constant.DataTickets;
import com.geckolib.loading.loader.GeckoLibGsonLoader;
import com.geckolib.loading.math.MathParser;
import com.geckolib.loading.math.MathValue;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.loading.math.value.Variable;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.ClientUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.adapter.geckolib.LcMolangParser;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 按服务端快照时间使用 GeckoLib 5.5.2 原生时间线与骨骼处理器求值，不推进独立时钟或回放关键帧事件。
@SuppressWarnings("UnstableApiUsage")
public class LcPlayerAnimationSampler {
	// 公共姿态以格为单位，GeckoLib 骨骼增量使用模型像素。
	private static final double MODEL_UNITS_PER_BLOCK = 16;
	private static final double TICKS_PER_SECOND = 20;
	private static final String ANIMATION_PREFIX = "geckolib/animations/";
	private static final String ANIMATION_SUFFIX = ".animation.json";
	private static final String MISSING_ANIMATION = "玩家动画资源中不存在动画：";
	private static final String INVALID_TIME = "玩家动画采样时间必须是有限的非负秒数";
	private static final String UNKNOWN_QUERY = "玩家动画缺少查询提供器：";
	private static final Map<ClipKey, NativeClip> CLIPS = new HashMap<>();
	private static @Nullable Object resourceGeneration;

	public static LcSkeletalPose sample(LcAnimationSnapshot snapshot, double timeSeconds, Player player,
	                                    AvatarRenderState state, float partialTick) {
		return sample(snapshot, timeSeconds, player, state, partialTick, snapshot.poseOverrides());
	}

	public static LcSkeletalPose sample(LcAnimationSnapshot snapshot, double timeSeconds, Player player,
	                                    AvatarRenderState state, float partialTick, LcSkeletalPose poseOverrides) {
		if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
			throw new IllegalArgumentException(INVALID_TIME);
		}

		var clip = clip(snapshot.content(), snapshot.animationName());
		var animation = clip.animation();
		var time = animation.loopType() == LoopType.LOOP && animation.length() > 0
				? timeSeconds % animation.length() : Math.min(timeSeconds, animation.length());
		var point = clip.timeline().createAnimationPoint(time, null, null);
		var renderState = (GeoRenderState) state;
		renderState.addGeckolibData(DataTickets.PARTIAL_TICK, partialTick);
		renderState.addGeckolibData(DataTickets.IS_MOVING, player.getDeltaMovement().horizontalDistanceSqr() > 0);
		var controller = new SnapshotController(point, time, snapshot.speed());
		var queries = queries(clip.parser(), snapshot, player, renderState, controller, time, partialTick);
		var nativeState = new ControllerState(point, null, -1, 0, false, null, renderState, queries.nativeValues());
		var bones = new LinkedHashMap<String, BoneSnapshot>();
		var model = LcClientAnimationResources.model(snapshot.content());

		for (var boneAnimation : animation.boneAnimations()) {
			model.getBone(boneAnimation.boneName()).ifPresent(bone ->
					bones.put(bone.name(), BoneSnapshot.create(bone)));
		}

		return clip.parser().evaluate(queries, () -> {
			AnimationProcessor.createBoneSnapshots(nativeState, name -> Optional.ofNullable(bones.get(name)));
			return finish(snapshot, bones, poseOverrides);
		});
	}

	private static NativeClip clip(LcAnimationContent content, String name) {
		var generation = GeckoLibResources.getBakedAnimations();
		if (resourceGeneration != generation) {
			resourceGeneration = generation;
			CLIPS.clear();
		}

		return CLIPS.computeIfAbsent(new ClipKey(content.version(), name), _ -> {
			var parser = new PlayerParser();
			var json = JsonParser.parseString(animationJson(content)).getAsJsonObject();
			var selected = json.getAsJsonObject("animations").get(name);
			if (selected == null) {
				throw new IllegalArgumentException(MISSING_ANIMATION + name);
			}

			var definitions = new JsonObject();
			definitions.add(name, selected);
			json.add("animations", definitions);
			var animations = new GeckoLibGsonLoader().bakeGeckoLibAnimationsFile(content.animationId(),
					json, parser);
			var animation = animations.getAnimation(name);

			if (animation == null) {
				throw new IllegalArgumentException(MISSING_ANIMATION + name);
			}

			var stage = new AnimationTimeline.Stage(0, animation.length(), false, animation, animation.loopType());
			return new NativeClip(animation, new AnimationTimeline(new AnimationTimeline.Stage[]{stage}), parser);
		});
	}

	private static String animationJson(LcAnimationContent content) {
		var file = content.animationId().withPath(ANIMATION_PREFIX + content.animationId().getPath() + ANIMATION_SUFFIX);
		var resource = Minecraft.getInstance().getResourceManager().getResource(file);

		if (resource.isEmpty()) {
			return content.animationJson();
		}

		try (var reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).toString();
		} catch (IOException exception) {
			throw new IllegalStateException(file.toString(), exception);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static PlayerQueries queries(PlayerParser parser, LcAnimationSnapshot snapshot, Player player,
	                                     GeoRenderState state, SnapshotController controller,
	                                     double time, float partialTick) {
		var values = new HashMap<String, Double>();
		snapshot.parameters().forEach((name, value) -> values.put(normalize(name), value));
		// 查询以权威快照为准；原生采样时间只为缺少快照值的查询提供默认值。
		values.putIfAbsent(MolangQueries.ANIM_TIME, time);
		values.putIfAbsent(MolangQueries.CONTROLLER_SPEED, snapshot.speed());
		var nativeValues = new Reference2DoubleOpenHashMap<Variable>();
		var nativeQueries = new LinkedHashSet<Variable>();

		for (var name : parser.queryNames) {
			if (!MolangQueries.isExistingVariable(name)) {
				continue;
			}

			var variable = MathParser.getVariableFor(name);
			nativeQueries.add(variable);
			var value = values.get(name);

			if (value != null) {
				nativeValues.put(variable, value.doubleValue());
			}
		}

		var viewer = ClientUtil.getClientPlayer();
		var actor = new MolangQueries.Actor<>(player, state, controller, time * TICKS_PER_SECOND, partialTick,
				player.level(), viewer == null ? player : viewer, ClientUtil.getCameraPos());
		// Actor 的类型参数本身不要求 GeoAnimatable；保留真实 Player 供原生 Entity/LivingEntity 查询强转。
		MolangQueries.buildActorVariables((MolangQueries.Actor) actor, nativeQueries, nativeValues);
		return new PlayerQueries(values, nativeValues);
	}

	private static String normalize(String name) {
		if (name.startsWith("q.")) {
			return "query." + name.substring(2);
		}

		if (name.startsWith("v.")) {
			return "variable." + name.substring(2);
		}

		if (name.startsWith("t.")) {
			return "temp." + name.substring(2);
		}

		return name;
	}

	private static LcSkeletalPose finish(LcAnimationSnapshot snapshot, Map<String, BoneSnapshot> samples,
	                                     LcSkeletalPose poseOverrides) {
		var bones = new LinkedHashMap<String, LcTransform>();
		var angles = new LinkedHashMap<String, Vec3>();

		samples.forEach((name, bone) -> {
			var position = new Vec3(bone.getTranslateX() / MODEL_UNITS_PER_BLOCK,
					bone.getTranslateY() / MODEL_UNITS_PER_BLOCK, -bone.getTranslateZ() / MODEL_UNITS_PER_BLOCK);
			var rotation = new Vec3(-bone.getRotX(), bone.getRotY(), -bone.getRotZ());
			var scale = new Vec3(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
			bones.put(name, new LcTransform(position,
					new Quaternionf().rotationZYX((float) rotation.z, (float) rotation.y, (float) rotation.x), scale));
			angles.put(name, rotation);
		});

		// 同步代码声明拥有的骨骼绝对姿态，不在客户端执行回调。
		poseOverrides.bones().forEach((name, transform) -> {
			bones.put(name, transform);
			angles.put(name, poseOverrides.rotationAngles(name));
		});

		var rootBone = snapshot.rootBone();
		if (rootBone != null && bones.containsKey(rootBone)) {
			var transform = bones.get(rootBone);
			var position = transform.position();
			var axes = snapshot.rootAxes();
			bones.put(rootBone, new LcTransform(new Vec3(axes.contains(LcRootMotion.Axis.X) ? 0 : position.x,
					axes.contains(LcRootMotion.Axis.Y) ? 0 : position.y,
					axes.contains(LcRootMotion.Axis.Z) ? 0 : position.z), transform.rotation(), transform.scale()));
		}

		return new LcSkeletalPose(bones, angles);
	}

	public static void clear() {
		CLIPS.clear();
		resourceGeneration = null;
	}

	private record ClipKey(String contentVersion, String animationName) {
	}

	private record NativeClip(Animation animation, AnimationTimeline timeline, PlayerParser parser) {
	}

	private static class SnapshotController extends AnimationController<GeoAnimatable> {
		private SnapshotController(AnimationPoint point, double time, double speed) {
			super(_ -> PlayState.CONTINUE);
			animationPoint = point;
			timelineTime = time;
			animationSpeed = speed;
		}
	}

	@NullMarked
	private static class PlayerParser extends LcMolangParser {
		private final Set<String> queryNames = new LinkedHashSet<>();

		@Override
		protected Optional<MathValue> compileSingleValue(Either<String, List<MathValue>> symbol) {
			var name = symbol.left().orElse(null);

			if (name != null && !name.startsWith("!") && !isNumeric(name) && !isFunctionRegistered(name)) {
				var variable = normalize(name.startsWith("-") ? name.substring(1) : name);

				if (variable.startsWith("query.")) {
					queryNames.add(variable);
				}
			}

			return super.compileSingleValue(symbol);
		}
	}

	private record PlayerQueries(Map<String, Double> values,
	                             Reference2DoubleOpenHashMap<Variable> nativeValues) implements ILcAnimationQueries {
		@Override
		public double get(String name) {
			var value = values.get(name);
			if (value != null) {
				return value;
			}

			if (name.startsWith("query.")) {
				if (MolangQueries.isExistingVariable(name)) {
					var variable = MathParser.getVariableFor(name);
					if (nativeValues.containsKey(variable)) {
						return nativeValues.getDouble(variable);
					}
				}

				throw new IllegalArgumentException(UNKNOWN_QUERY + name);
			}

			return 0;
		}

		@Override
		public void set(String name, double value) {
			if (name.startsWith("query.")) {
				ILcAnimationQueries.super.set(name, value);
				return;
			}

			values.put(name, value);
		}
	}
}
