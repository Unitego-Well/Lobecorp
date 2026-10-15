package org.unitego.lobecorp.animation.definition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/// 资源内容的代码包装；定义可共享，回调只修改本次播放的工作数据。
public record LcAnimationDefinition(Identifier modelId, Identifier animationId, String animationName,
                                    List<Consumer<JsonObject>> modelEdits, List<Consumer<JsonObject>> animationEdits,
                                    List<PoseModifier> poseModifiers,
                                    Map<String, ToDoubleFunction<LcAnimationContext>> queries,
                                    List<TimedAction> actions,
                                    List<Consumer<LcAnimationContext>> tickActions,
                                    List<Consumer<LcAnimationInstance>> stopActions,
                                    @Nullable LcRootMotion rootMotion, @Nullable LcPlayerAnimationPolicy playerPolicy) {
	public LcAnimationDefinition {
		modelEdits = List.copyOf(modelEdits);
		animationEdits = List.copyOf(animationEdits);
		poseModifiers = List.copyOf(poseModifiers);
		queries = Map.copyOf(queries);
		actions = actions.stream().sorted(Comparator.comparingDouble(TimedAction::seconds)).toList();
		tickActions = List.copyOf(tickActions);
		stopActions = List.copyOf(stopActions);
	}

	public static Builder wrap(Identifier modelId, Identifier animationId, String animationName) {
		return new Builder(modelId, animationId, animationName);
	}

	public LcAnimationContent derive(LcAnimationContent source) {
		var model = JsonParser.parseString(source.modelJson()).getAsJsonObject();
		var animation = JsonParser.parseString(source.animationJson()).getAsJsonObject();
		modelEdits.forEach(edit -> edit.accept(model));
		animationEdits.forEach(edit -> edit.accept(animation));
		return LcAnimationContent.of(modelId, animationId, model.toString(), animation.toString());
	}

	public record TimedAction(double seconds, Consumer<LcAnimationInstance> action) {
		private static final String INVALID_ACTION_TIME = "Animation action time must be finite and non-negative";

		public TimedAction {
			if (!Double.isFinite(seconds) || seconds < 0) {
				throw new IllegalArgumentException(INVALID_ACTION_TIME);
			}
		}
	}

	/// 未声明范围时，返回姿态的全部骨骼属于绝对结果；显式范围中的缺失骨骼表示单位姿态。
	public record PoseModifier(@Nullable Set<String> ownedBones,
	                           BiFunction<LcAnimationContext, LcSkeletalPose, LcSkeletalPose> operation) {
		public PoseModifier {
			if (ownedBones != null) {
				ownedBones = Set.copyOf(ownedBones);
			}
		}
	}

	public static class Builder {
		private final Identifier modelId;
		private final Identifier animationId;
		private final String animationName;
		private final List<Consumer<JsonObject>> modelEdits = new ArrayList<>();
		private final List<Consumer<JsonObject>> animationEdits = new ArrayList<>();
		private final List<PoseModifier> modifiers = new ArrayList<>();
		private final Map<String, ToDoubleFunction<LcAnimationContext>> queries = new LinkedHashMap<>();
		private final List<TimedAction> actions = new ArrayList<>();
		private final List<Consumer<LcAnimationContext>> tickActions = new ArrayList<>();
		private final List<Consumer<LcAnimationInstance>> stopActions = new ArrayList<>();
		@Nullable
		private LcRootMotion rootMotion;
		@Nullable
		private LcPlayerAnimationPolicy playerPolicy;

		private Builder(Identifier modelId, Identifier animationId, String animationName) {
			this.modelId = modelId;
			this.animationId = animationId;
			this.animationName = animationName;
		}

		public Builder editModel(Consumer<JsonObject> edit) {
			modelEdits.add(edit);
			return this;
		}

		public Builder editAnimation(Consumer<JsonObject> edit) {
			animationEdits.add(edit);
			return this;
		}

		/// 纯服务端计算；返回姿态包含的全部骨骼作为绝对结果覆盖视觉资源，循环边界也会采样。
		public Builder modifyPose(BiFunction<LcAnimationContext, LcSkeletalPose, LcSkeletalPose> modifier) {
			modifiers.add(new PoseModifier(null, modifier));
			return this;
		}

		/// 只声明这些骨骼的绝对全部通道；返回姿态未包含的声明骨骼显式覆盖为单位姿态。
		public Builder modifyPose(Set<String> ownedBones,
		                          BiFunction<LcAnimationContext, LcSkeletalPose, LcSkeletalPose> modifier) {
			modifiers.add(new PoseModifier(Set.copyOf(ownedBones), modifier));
			return this;
		}

		public Builder query(String name, ToDoubleFunction<LcAnimationContext> query) {
			queries.put(name, query);
			return this;
		}

		public Builder at(double seconds, Consumer<LcAnimationInstance> action) {
			actions.add(new TimedAction(seconds, action));
			return this;
		}

		public Builder onTick(Consumer<LcAnimationContext> action) {
			tickActions.add(action);
			return this;
		}

		public Builder onStop(Consumer<LcAnimationInstance> action) {
			stopActions.add(action);
			return this;
		}

		public Builder rootMotion(LcRootMotion mapping) {
			rootMotion = mapping;
			return this;
		}

		/// 只声明玩家第三人称视觉接管，不新增实际移动或第一人称行为。
		public Builder playerAnimation(LcPlayerAnimationPolicy policy) {
			playerPolicy = policy;
			return this;
		}

		public LcAnimationDefinition build() {
			return new LcAnimationDefinition(modelId, animationId, animationName, modelEdits, animationEdits,
					modifiers, queries, actions, tickActions, stopActions, rootMotion, playerPolicy);
		}
	}
}
