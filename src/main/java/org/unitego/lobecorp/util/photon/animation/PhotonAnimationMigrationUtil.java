package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.PhotonRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 保留可解析的旧动画，丢弃不支持的旧调用并报告；定义损坏时保留原 NBT。
public class PhotonAnimationMigrationUtil {
	/// 未知版本的阻断报告，不能用已知旧格式解析未来格式。
	private static final String VERSION_PROBLEM = "不支持的动画配置版本：";
	/// 动画结构或引用无效时保留原件的报告前缀。
	private static final String ANIMATION_PROBLEM = "动画定义无法迁移：";
	/// 共享参数定义无法解析时的阻断报告前缀。
	private static final String PARAMETER_PROBLEM = "共享参数无法归属或定义无效：";
	/// 不支持的旧规则已跳过，此报告不阻止保存可用内容。
	private static final String RULE_PROBLEM = "旧触发规则已丢弃：";
	/// 未被保留内容引用的共享参数不进入新动画形参表。
	private static final String UNOWNED_PARAMETER = "未被支持内容引用的共享参数已丢弃：";
	/// 旧操作链仅允许单个本特效播放，不猜测转换子调用或其他操作。
	private static final String UNSUPPORTED_ACTION = "仅支持单个本特效 PLAY；不支持旧子调用或操作链";
	/// 动态共享输入无法转换为动画独立形参时跳过整个调用。
	private static final String DYNAMIC_INPUT = "不支持动态共享参数输入或时长";
	/// 旧动画、参数或列表引用无法唯一解析的报告前缀。
	private static final String INVALID_REFERENCE = "引用不存在或无法唯一确定：";
	/// 原生轨道无法完整解析时阻止覆盖，以保留未识别的动画内容。
	private static final String INVALID_TRACK = "轨道结构无效或类型未注册：";
	/// 用动画和旧参数名称生成稳定身份，多次只读打开不能改变引用。
	private static final String PARAMETER_NAMESPACE = "photon_animation_parameter:";

	private enum LegacyAction {
		PLAY, CONTINUE, STOP, PAUSE, RESUME, SET_PARAMETER, STOP_EMISSION, FINISH, SET_DURATION, TRIGGER
	}

	private enum LegacyScope {
		SELF, CHILD_INSTANCES
	}

	private enum LegacySelection {
		ALL, LATEST
	}

	public static PhotonAnimationLibrary read(HolderLookup.Provider provider, CompoundTag original, int version) {
		var library = new PhotonAnimationLibrary();
		var problems = new ArrayList<String>();
		var warnings = new ArrayList<String>();

		if (version < PhotonAnimationDataUtil.LEGACY_VERSION || version >= PhotonAnimationDataUtil.VERSION) {
			library.blockMigration(original, List.of(VERSION_PROBLEM + version));
			return library;
		}

		try {
			validateLists(original, "animations", "parameters");

			if (original.contains("rules") && !(original.get("rules") instanceof ListTag)) {
				throw new IllegalArgumentException(INVALID_REFERENCE + "rules");
			}
		} catch (IllegalArgumentException exception) {
			library.blockMigration(original, List.of(exception.getMessage()));
			return library;
		}

		library.defaultAnimation = original.getStringOr("defaultAnimation", "");
		var shared = readParameters(original, problems);
		var used = new HashSet<String>();
		readAnimations(provider, original, version, library, shared, used, problems);
		readRules(original, library, shared, used, warnings);

		if (!library.defaultAnimation.isEmpty() && library.find(library.defaultAnimation) == null) {
			problems.add(INVALID_REFERENCE + library.defaultAnimation);
		}

		for (var name : shared.keySet()) {
			if (!used.contains(name)) {
				warnings.add(UNOWNED_PARAMETER + name);
			}
		}

		library.recordMigrationWarnings(warnings);

		if (!problems.isEmpty()) {
			library.blockMigration(original, problems);
		}

		return library;
	}

	private static Map<String, PhotonAnimationParameter> readParameters(CompoundTag original, List<String> problems) {
		var result = new LinkedHashMap<String, PhotonAnimationParameter>();

		for (var entry : original.getListOrEmpty("parameters")) {
			if (!(entry instanceof CompoundTag tag)) {
				problems.add(PARAMETER_PROBLEM + entry);
				continue;
			}

			try {
				var parameter = PhotonAnimationParameter.read(tag);

				if (parameter.name.isBlank() || result.putIfAbsent(parameter.name, parameter) != null) {
					throw new IllegalArgumentException(parameter.name);
				}
			} catch (IllegalArgumentException exception) {
				problems.add(PARAMETER_PROBLEM + tag.getStringOr("name", "") + " / " + exception.getMessage());
			}
		}

		return result;
	}

	private static void readAnimations(HolderLookup.Provider provider, CompoundTag original, int version,
	                                   PhotonAnimationLibrary library, Map<String, PhotonAnimationParameter> shared,
	                                   Set<String> used, List<String> problems) {
		for (var entry : original.getListOrEmpty("animations")) {
			if (!(entry instanceof CompoundTag tag)) {
				problems.add(ANIMATION_PROBLEM + entry);
				continue;
			}

			var previouslyUsed = new HashSet<>(used);

			try {
				validateLists(tag, "bindings", "markers", "parameters");
				validateTracks(tag, "tracks", true);
				var animation = PhotonAnimationDefinition.read(provider, tag, version);

				for (var binding : animation.bindings) {
					if (binding.source == PhotonAnimationBinding.Source.PARAMETER) {
						binding.parameter = localParameter(animation, binding.parameter, shared, used).id.toString();
					}
				}

				if (library.find(animation.id.toString()) != null || library.find(animation.name) != null) {
					throw new IllegalArgumentException(animation.name);
				}

				library.animations.add(animation);
			} catch (IllegalArgumentException exception) {
				used.clear();
				used.addAll(previouslyUsed);
				problems.add(ANIMATION_PROBLEM + tag.getStringOr("name", "") + " / " + exception.getMessage());
			}
		}
	}

	private static void validateLists(CompoundTag owner, String... fields) {
		for (var field : fields) {
			if (owner.contains(field) && !(owner.get(field) instanceof ListTag)) {
				throw new IllegalArgumentException(INVALID_REFERENCE + field);
			}

			for (var entry : owner.getListOrEmpty(field)) {
				if (!(entry instanceof CompoundTag)) {
					throw new IllegalArgumentException(INVALID_REFERENCE + field);
				}
			}
		}
	}

	/// 原生读取器会跳过未知轨道；迁移前递归核对，防止保存时丢失尚未识别的内容。
	private static void validateTracks(CompoundTag owner, String field, boolean legacy) {
		if (!owner.contains(field)) {
			return;
		}

		if (!(owner.get(field) instanceof ListTag tracks)) {
			throw new IllegalArgumentException(INVALID_TRACK + field);
		}

		for (var entry : tracks) {
			if (!(entry instanceof CompoundTag track)) {
				throw new IllegalArgumentException(INVALID_TRACK + entry);
			}

			if (!track.contains("type") && legacy) {
				continue;
			}

			var type = track.getString("type").orElseThrow(() -> new IllegalArgumentException(INVALID_TRACK + field));

			if (PhotonRegistries.TIMELINE_TRACKS.get(type) == null
					|| !(track.get("data") instanceof CompoundTag data)) {
				throw new IllegalArgumentException(INVALID_TRACK + type);
			}

			validateTracks(data, "children", false);
		}
	}

	private static PhotonAnimationParameter localParameter(PhotonAnimationDefinition animation, String name,
	                                                       Map<String, PhotonAnimationParameter> shared, Set<String> used) {
		var parameter = animation.parameter(name);

		if (parameter != null) {
			used.add(parameter.name);
			return parameter;
		}

		var source = shared.get(name);

		if (source == null) {
			throw new IllegalArgumentException(INVALID_REFERENCE + name);
		}

		parameter = PhotonAnimationParameter.read(source.write());
		parameter.id = UUID.nameUUIDFromBytes((PARAMETER_NAMESPACE + animation.id + ":" + name)
				.getBytes(StandardCharsets.UTF_8));
		animation.parameters.add(parameter);
		used.add(name);
		return parameter;
	}

	private static void readRules(CompoundTag original, PhotonAnimationLibrary library,
	                              Map<String, PhotonAnimationParameter> shared, Set<String> used, List<String> warnings) {
		int index = 0;

		for (var entry : original.getListOrEmpty("rules")) {
			index++;

			if (!(entry instanceof CompoundTag tag)) {
				warnings.add(RULE_PROBLEM + index);
				continue;
			}

			var parameterCounts = library.animations.stream().mapToInt(animation -> animation.parameters.size()).toArray();
			var referenced = new HashSet<>(used);

			try {
				library.rules.add(convertRule(tag, library, shared, referenced));
				used.addAll(referenced);
			} catch (IllegalArgumentException exception) {
				restoreRuleParameters(library, parameterCounts);
				warnings.add(RULE_PROBLEM + index + " / " + exception.getMessage());
			}
		}
	}

	/// 失败调用只撤销本次追加的形参，保留已有绑定及先前成功调用的形参身份。
	private static void restoreRuleParameters(PhotonAnimationLibrary library, int[] parameterCounts) {
		for (int index = 0; index < parameterCounts.length; index++) {
			var parameters = library.animations.get(index).parameters;
			parameters.subList(parameterCounts[index], parameters.size()).clear();
		}
	}

	private static PhotonAnimationRule convertRule(CompoundTag tag, PhotonAnimationLibrary library,
	                                               Map<String, PhotonAnimationParameter> shared, Set<String> used) {
		var rule = new PhotonAnimationRule();

		if (tag.getString("event").isEmpty()) {
			throw new IllegalArgumentException(UNSUPPORTED_ACTION);
		}

		rule.event = PhotonAnimationDataUtil.strictEnumeration(tag, "event", PhotonAnimationRule.Event.TRIGGER);
		rule.animation = tag.getStringOr("animation", "");
		rule.channel = tag.getStringOr("channel", "");
		rule.name = tag.getStringOr("name", "");

		if (!rule.animation.isEmpty()) {
			rule.animation = requireAnimation(library, rule.animation).id.toString();
		}

		var actions = tag.getListOrEmpty("actions");

		for (var entry : actions) {
			if (!(entry instanceof CompoundTag action)) {
				throw new IllegalArgumentException(UNSUPPORTED_ACTION);
			}

			if (action.getString("type").isEmpty()) {
				throw new IllegalArgumentException(UNSUPPORTED_ACTION);
			}

			validateLists(action, "inputs");
			PhotonAnimationDataUtil.strictEnumeration(action, "type", LegacyAction.PLAY);
			PhotonAnimationDataUtil.strictEnumeration(action, "scope", LegacyScope.SELF);
			PhotonAnimationDataUtil.strictEnumeration(action, "instances", LegacySelection.ALL);
		}

		if (actions.size() != 1 || !(actions.getFirst() instanceof CompoundTag action)
				|| PhotonAnimationDataUtil.strictEnumeration(action, "type", LegacyAction.PLAY) != LegacyAction.PLAY
				|| PhotonAnimationDataUtil.strictEnumeration(action, "scope", LegacyScope.SELF) != LegacyScope.SELF) {
			throw new IllegalArgumentException(UNSUPPORTED_ACTION);
		}

		if (!action.getStringOr("durationParameter", "").isEmpty()) {
			throw new IllegalArgumentException(DYNAMIC_INPUT);
		}

		var target = requireAnimation(library, action.getStringOr("target", ""));
		rule.target = target.id.toString();
		rule.duration = action.getDoubleOr("duration", -1);

		if (!Double.isFinite(rule.duration) || rule.duration < 0 && rule.duration != -1) {
			throw new IllegalArgumentException();
		}

		for (var entry : action.getListOrEmpty("inputs")) {
			if (!(entry instanceof CompoundTag inputTag) || !inputTag.getStringOr("source", "").isEmpty()) {
				throw new IllegalArgumentException(DYNAMIC_INPUT);
			}

			var input = PhotonAnimationInput.read(inputTag);
			var parameter = localParameter(target, input.target, shared, used);
			input.target = parameter.id.toString();
			input.value = parameter.validate(input.value);
			rule.inputs.add(input);
		}

		return rule;
	}

	private static PhotonAnimationDefinition requireAnimation(PhotonAnimationLibrary library, String reference) {
		var animation = library.find(reference);

		if (animation == null) {
			throw new IllegalArgumentException(INVALID_REFERENCE + reference);
		}

		return animation;
	}
}
