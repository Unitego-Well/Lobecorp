package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("resource") // 预览由工作区统一关闭。
public class PhotonAnimationParameterEditor {
	public static void build(PhotonAnimationWorkspace workspace, UIElement container) {
		if (!workspace.hasSelection()) {
			return;
		}

		var animation = workspace.selected();
		var parameters = new ArrayConfiguratorGroup<>(PhotonAnimationTextUtil.key("parameters"), false,
				() -> List.copyOf(animation.parameters), (getter, _) -> parameter(workspace, getter.get()), false);
		parameters.setAddDefault(() -> newParameter(workspace));
		parameters.setOnUpdate(values -> workspace.edit(() -> updateParameters(workspace.library(), animation, values, workspace::projectTemplateUsesLibrary)));
		PhotonAnimationUiUtil.tips(parameters, "parameters");
		container.addChild(parameters);
	}

	private static PhotonAnimationParameter newParameter(PhotonAnimationWorkspace workspace) {
		var parameter = new PhotonAnimationParameter();
		var name = PhotonAnimationTextUtil.PARAMETER_NAME;
		int suffix = 1;

		while (!valid(workspace, name)) {
			name = PhotonAnimationTextUtil.PARAMETER_NAME + "_" + suffix++;
		}

		parameter.name = name;
		return parameter;
	}

	private static void updateParameters(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                                     List<PhotonAnimationParameter> parameters, Predicate<Track> currentLibraryTarget) {
		for (var previous : List.copyOf(owner.parameters)) {
			if (!parameters.contains(previous)) {
				remove(library, owner, previous, currentLibraryTarget);
			}
		}

		owner.parameters.clear();
		owner.parameters.addAll(parameters);
	}

	private static ConfiguratorGroup parameter(PhotonAnimationWorkspace workspace, PhotonAnimationParameter parameter) {
		var group = new ConfiguratorGroup(parameter.name, false);
		workspace.bindFormGroup(group, parameter, PhotonAnimationTextUtil.key("parameters"));
		group.lineContainer.setDisplay(false);
		definition(workspace, group, parameter);
		preview(workspace, group, parameter);
		return group;
	}

	private static boolean valid(PhotonAnimationWorkspace workspace, String name) {
		return !name.isBlank() && workspace.selected().parameters.stream().noneMatch(value -> value.name.equals(name.trim()));
	}

	private static void definition(PhotonAnimationWorkspace workspace, ConfiguratorGroup group, PhotonAnimationParameter parameter) {
		var nameField = new StringConfigurator(PhotonAnimationTextUtil.key("name"), () -> parameter.name, _ -> {
		}, "", false);
		nameField.textField.setActive(false);
		PhotonAnimationUiUtil.tips(nameField, "name");
		group.addConfigurator(nameField);
		PhotonAnimationUiUtil.enumeration(group, "type", () -> parameter.type,
				value -> workspace.edit(() -> changeType(workspace.library(), workspace.selected(), parameter, value, workspace::projectTemplateUsesLibrary)),
				PhotonAnimationParameter.Type.values());

		if (parameter.type != PhotonAnimationParameter.Type.BOOLEAN) {
			bound(workspace, group, parameter, true);
			bound(workspace, group, parameter, false);
		}

		PhotonAnimationUiUtil.parameterValues(group, "value", parameter, () -> parameter.defaults,
				value -> workspace.edit(() -> parameter.defaults = parameter.validate(value)));
		group.addChild(PhotonAnimationUiUtil.button("rename", () -> Dialog.stringEditorDialog(
				PhotonAnimationTextUtil.key("rename"), parameter.name,
				name -> name.trim().equals(parameter.name) || valid(workspace, name),
				name -> workspace.edit(() -> parameter.name = name.trim())).show(workspace)));
	}

	private static void bound(PhotonAnimationWorkspace workspace, ConfiguratorGroup group,
	                          PhotonAnimationParameter parameter, boolean minimum) {
		var key = minimum ? "minimum" : "maximum";
		var field = new NumberConfigurator(PhotonAnimationTextUtil.key(key),
				() -> minimum ? parameter.effectiveMinimum() : parameter.effectiveMaximum(),
				value -> workspace.edit(() -> changeBounds(workspace.library(), workspace.selected(), parameter,
						minimum ? value.floatValue() : parameter.effectiveMinimum(),
						minimum ? parameter.effectiveMaximum() : value.floatValue(), workspace::projectTemplateUsesLibrary)),
				minimum ? parameter.effectiveMinimum() : parameter.effectiveMaximum(), false);
		field.setType(parameter.type.isInteger() ? ConfigNumber.Type.INTEGER : ConfigNumber.Type.FLOAT);
		var lower = parameter.type.isInteger() ? -PhotonAnimationParameter.MAX_EXACT_INTEGER
				: parameter.type == PhotonAnimationParameter.Type.COLOR ? 0 : -Float.MAX_VALUE;
		var upper = parameter.type.isInteger() ? PhotonAnimationParameter.MAX_EXACT_INTEGER
				: parameter.type == PhotonAnimationParameter.Type.COLOR ? 1 : Float.MAX_VALUE;
		field.setRange(minimum ? lower : parameter.effectiveMinimum(), minimum ? parameter.effectiveMaximum() : upper);
		PhotonAnimationUiUtil.tips(field, key);
		group.addConfigurator(field);
	}

	public static void remove(PhotonAnimationLibrary library, PhotonAnimationDefinition owner, PhotonAnimationParameter parameter) {
		remove(library, owner, parameter, _ -> false);
	}

	public static void remove(PhotonAnimationLibrary library, PhotonAnimationDefinition owner, PhotonAnimationParameter parameter,
	                          Predicate<Track> currentLibraryTarget) {
		if (!owner.parameters.remove(parameter)) {
			return;
		}

		if (PhotonAnimationInputEditor.matches(owner.durationParameter, parameter)) {
			owner.durationParameter = "";
		}

		for (var binding : owner.bindings) {
			if (PhotonAnimationInputEditor.matches(binding.parameter, parameter)) {
				binding.parameter = "";
			}

			if (PhotonAnimationInputEditor.matches(binding.scaleParameter, parameter)) {
				binding.scaleParameter = "";
			}

			if (PhotonAnimationInputEditor.matches(binding.offsetParameter, parameter)) {
				binding.offsetParameter = "";
			}
		}

		for (var rule : library.rules) {
			if (library.find(rule.target) == owner) {
				rule.inputs.removeIf(input -> PhotonAnimationInputEditor.matches(input.target, parameter));
			}

			if (library.find(rule.animation) == owner) {
				clearSources(rule.inputs, parameter);
			}
		}

		for (var animation : library.animations) {
			for (var track : animation.leafTracks(true)) {
				for (var clip : track.clips()) {
					if (!(clip instanceof PhotonChildAnimationClip child)) {
						continue;
					}

					if (currentLibraryTarget.test(track) && library.find(child.animation) == owner) {
						child.inputs.removeIf(input -> input.target.equals(parameter.id.toString()));
					}

					if (animation == owner) {
						clearSources(child.inputs, parameter);
					}
				}
			}
		}
	}

	private static void clearSources(List<PhotonAnimationInput> inputs, PhotonAnimationParameter parameter) {
		for (var input : inputs) {
			if (PhotonAnimationInputEditor.matches(input.source, parameter)) {
				input.source = "";
				input.follow = false;
			}
		}
	}

	public static void changeType(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                              PhotonAnimationParameter parameter, PhotonAnimationParameter.Type type) {
		changeType(library, owner, parameter, type, _ -> false);
	}

	public static void changeType(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                              PhotonAnimationParameter parameter, PhotonAnimationParameter.Type type,
	                              Predicate<Track> currentLibraryTarget) {
		if (parameter.type == type) {
			return;
		}

		var converted = converted(type, parameter.minimum, parameter.maximum);
		var fallback = new float[type.channels()];
		Arrays.fill(fallback, converted.defaults[0]);
		var defaults = migrate(converted, parameter.defaults, converted.validate(fallback));
		applyValues(library, owner, parameter, converted, defaults, true, currentLibraryTarget);
	}

	public static void changeBounds(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                                PhotonAnimationParameter parameter, float minimum, float maximum) {
		changeBounds(library, owner, parameter, minimum, maximum, _ -> false);
	}

	public static void changeBounds(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                                PhotonAnimationParameter parameter, float minimum, float maximum,
	                                Predicate<Track> currentLibraryTarget) {
		var converted = converted(parameter.type, minimum, maximum);
		applyValues(library, owner, parameter, converted, converted.validate(parameter.defaults), false, currentLibraryTarget);
	}

	private static PhotonAnimationParameter converted(PhotonAnimationParameter.Type type, float minimum, float maximum) {
		var converted = new PhotonAnimationParameter();
		converted.type = type;
		converted.minimum = minimum;
		converted.maximum = maximum;
		converted.minimum = converted.effectiveMinimum();
		converted.maximum = converted.effectiveMaximum();
		return converted;
	}

	private static void applyValues(PhotonAnimationLibrary library, PhotonAnimationDefinition owner,
	                                PhotonAnimationParameter parameter, PhotonAnimationParameter converted,
	                                float[] defaults, boolean resize, Predicate<Track> currentLibraryTarget) {
		Consumer<PhotonAnimationInput> update = input -> input.value = resize
				? migrate(converted, input.value, defaults) : converted.validate(input.value);

		for (var rule : library.rules) {
			if (library.find(rule.target) == owner) {
				rule.inputs.stream().filter(input -> PhotonAnimationInputEditor.matches(input.target, parameter)).forEach(update);
			}
		}

		for (var animation : library.animations) {
			for (var track : animation.leafTracks(true)) {
				for (var clip : track.clips()) {
					if (clip instanceof PhotonChildAnimationClip child && currentLibraryTarget.test(track) && library.find(child.animation) == owner) {
						child.inputs.stream().filter(input -> input.target.equals(parameter.id.toString())).forEach(update);
					}
				}
			}
		}

		parameter.type = converted.type;
		parameter.minimum = converted.minimum;
		parameter.maximum = converted.maximum;
		parameter.defaults = defaults;
	}

	private static float[] migrate(PhotonAnimationParameter parameter, float[] previous, float[] fallback) {
		var values = fallback.clone();

		for (var axis = 0; axis < Math.min(previous.length, values.length); axis++) {
			if (Float.isFinite(previous[axis])) {
				values[axis] = previous[axis];
			}
		}

		return parameter.validate(values);
	}

	private static @Nullable PhotonAnimationPlayback playback(PhotonAnimationWorkspace workspace) {
		var runtime = workspace.preview().runtimeOrNull();
		return runtime == null || !workspace.hasSelection() ? null : runtime.playback(workspace.selected().id.toString());
	}

	private static void preview(PhotonAnimationWorkspace workspace, ConfiguratorGroup parent, PhotonAnimationParameter parameter) {
		var group = new ConfiguratorGroup(PhotonAnimationTextUtil.key("preview_parameters"), false);
		workspace.bindFormGroup(group, parameter, PhotonAnimationTextUtil.key("preview_parameters"));
		PhotonAnimationUiUtil.tips(group, "preview_parameters");
		parent.addConfigurator(group);
		PhotonAnimationUiUtil.parameterValues(group, "value", parameter, () -> {
			var current = playback(workspace);
			return current == null ? parameter.defaults : current.parameter(parameter.id.toString());
		}, value -> {
			var current = playback(workspace);

			if (current != null) {
				current.setParameter(parameter.id.toString(), parameter.validate(value));
			}
		});
	}
}
