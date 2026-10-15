package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// 按被调用动画的形参生成实参表；未编辑的默认值只显示，不写入覆盖。
public class PhotonAnimationInputEditor {
	public static void build(ConfiguratorGroup parent, @Nullable PhotonAnimationDefinition target,
	                         @Nullable PhotonAnimationDefinition source, List<PhotonAnimationInput> inputs,
	                         Consumer<Runnable> edit, @Nullable PhotonAnimationWorkspace workspace) {
		if (target == null) {
			return;
		}

		var group = new ConfiguratorGroup(PhotonAnimationTextUtil.key("action_inputs"), false);
		PhotonAnimationUiUtil.tips(group, "action_inputs");
		parent.addConfigurator(group);

		for (var parameter : target.parameters) {
			var row = new ConfiguratorGroup(parameter.name, false);
			row.lineContainer.setDisplay(false);

			if (workspace != null) {
				workspace.bindFormGroup(row, parameter, "input:" + target.id);
			}

			group.addConfigurator(row);
			input(row, parameter, source, inputs, edit);
		}
	}

	private static void input(ConfiguratorGroup group, PhotonAnimationParameter target,
	                          @Nullable PhotonAnimationDefinition source, List<PhotonAnimationInput> inputs,
	                          Consumer<Runnable> edit) {
		var values = new ConfiguratorGroup(PhotonAnimationTextUtil.key("fixed_value"), false).hideTitle();
		var override = new BooleanConfigurator(PhotonAnimationTextUtil.key("input_override"),
				() -> find(inputs, target) != null, value -> {
			edit.accept(() -> {
				if (value) {
					ensure(inputs, target);
					return;
				}

				inputs.removeIf(input -> matches(input.target, target));
			});
			valueFields(values, target, inputs, edit);
		}, false, true);
		PhotonAnimationUiUtil.tips(override, "input_override");
		group.addConfigurator(override);
		parameterSelector(group, "input_source", () -> {
			var input = find(inputs, target);
			return input == null ? "" : input.source;
		}, value -> {
			edit.accept(() -> {
				var input = ensure(inputs, target);
				input.source = value;

				if (value.isEmpty()) {
					input.follow = false;
				}
			});
			valueFields(values, target, inputs, edit);
		}, source, parameter -> compatible(target, parameter));
		group.addConfigurator(values);
		valueFields(values, target, inputs, edit);
	}

	private static void valueFields(ConfiguratorGroup group, PhotonAnimationParameter target,
	                                List<PhotonAnimationInput> inputs, Consumer<Runnable> edit) {
		group.removeAllConfigurators();
		var input = find(inputs, target);

		if (input == null || input.source.isEmpty()) {
			PhotonAnimationUiUtil.parameterValues(group, "fixed_value", target,
					() -> values(inputs, target), value -> edit.accept(() -> {
						var edited = ensure(inputs, target);
						edited.source = "";
						edited.follow = false;
						edited.value = target.validate(value);
					}));
			return;
		}

		PhotonAnimationUiUtil.bool(group, "input_follow", () -> {
			var current = find(inputs, target);
			return current != null && current.follow;
		}, value -> edit.accept(() -> ensure(inputs, target).follow = value));
	}

	private static boolean compatible(PhotonAnimationParameter target, PhotonAnimationParameter source) {
		return target.type == source.type || target.type.isNumeric() && source.type.isNumeric()
				&& target.type.channels() == source.type.channels();
	}

	private static float[] values(List<PhotonAnimationInput> inputs, PhotonAnimationParameter target) {
		var input = find(inputs, target);

		if (input == null || input.value.length != target.type.channels()) {
			return target.defaults.clone();
		}

		return target.validate(input.value);
	}

	public static @Nullable PhotonAnimationInput find(List<PhotonAnimationInput> inputs, PhotonAnimationParameter target) {
		return inputs.stream().filter(input -> matches(input.target, target)).findFirst().orElse(null);
	}

	private static PhotonAnimationInput ensure(List<PhotonAnimationInput> inputs, PhotonAnimationParameter target) {
		var existing = find(inputs, target);

		if (existing != null) {
			existing.target = target.id.toString();
			return existing;
		}

		var input = new PhotonAnimationInput();
		input.target = target.id.toString();
		input.value = target.defaults.clone();
		inputs.add(input);
		return input;
	}

	public static boolean matches(String reference, PhotonAnimationParameter parameter) {
		return reference.equals(parameter.id.toString()) || reference.equals(parameter.name);
	}

	public static void parameterSelector(ConfiguratorGroup group, String key, Supplier<String> getter,
	                                     Consumer<String> setter, @Nullable PhotonAnimationDefinition source,
	                                     Predicate<PhotonAnimationParameter> filter) {
		var candidates = new ArrayList<String>();
		candidates.add("");

		if (source != null) {
			source.parameters.stream().filter(filter).map(parameter -> parameter.id.toString()).forEach(candidates::add);
		}

		var selected = source == null ? null : source.parameter(getter.get());
		var selection = selected == null ? getter.get() : selected.id.toString();

		if (!candidates.contains(selection)) {
			candidates.add(selection);
		}

		var field = new SelectorConfigurator<>(PhotonAnimationTextUtil.key(key), () -> {
			var current = source == null ? null : source.parameter(getter.get());
			return current == null ? getter.get() : current.id.toString();
		}, setter, "", true, candidates, value -> {
			if (value.isEmpty()) {
				return Component.translatable(PhotonAnimationTextUtil.key("fixed_source")).getString();
			}

			var parameter = source == null ? null : source.parameter(value);
			return parameter == null ? value : parameter.name;
		});
		PhotonAnimationUiUtil.tips(field, key);
		group.addConfigurator(field);
	}

	public static boolean scalar(PhotonAnimationParameter parameter) {
		return parameter.type.isNumeric() && parameter.type.channels() == 1;
	}
}
