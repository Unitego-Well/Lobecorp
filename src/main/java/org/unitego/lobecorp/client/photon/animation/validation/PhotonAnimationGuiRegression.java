package org.unitego.lobecorp.client.photon.animation.validation;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProperty;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationBindingEditor;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationForms;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationFormState;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationFormState.GroupIdentity;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationParameterEditor;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock.State;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

/// 参数迁移及隔离原生配置器检查，不借用当前工作区、不生成或修改资源。
public class PhotonAnimationGuiRegression {
	private int assertions;

	public static int runIndependent() {
		var checks = new PhotonAnimationGuiRegression();
		checks.parameterMigration();
		checks.formalReferences();
		checks.formalRemoval();
		checks.formalChildScopes();
		checks.inputRoundTrip();
		return checks.assertions;
	}

	/// 需要 NeoForge 完成模组加载；计数包含独立参数迁移检查。
	public static int runLoaded() {
		var checks = new PhotonAnimationGuiRegression();
		checks.parameterMigration();
		checks.formalReferences();
		checks.formalRemoval();
		checks.formalChildScopes();
		checks.inputRoundTrip();
		checks.inputGroups();
		checks.formState();
		checks.arraySelection();
		checks.parameterWidgets();
		checks.inputFocus();
		checks.bindingLabels();
		checks.statusFormatting();
		return checks.assertions + PhotonAnimationPreviewRegression.runLoaded();
	}

	private void inputRoundTrip() {
		var input = new PhotonAnimationInput();
		input.target = UUID.randomUUID().toString();
		input.source = UUID.randomUUID().toString();
		input.follow = true;
		input.value = new float[]{2, 3, 4};
		var loaded = PhotonAnimationInput.read(input.write());
		check(loaded.write().equals(input.write()), "formal input round trip");
		loaded.value[0] = 9;
		equal(2, input.value[0], "input values are copied");
	}

	private void formalReferences() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{2}, 0, 4);
		var owner = library.animations.getFirst();
		var externalAnimation = UUID.randomUUID().toString();
		var original = UUID.randomUUID();
		var remapped = UUID.randomUUID();
		var track = new PhotonChildAnimationTrack();
		track.targetId(original);
		var clip = new PhotonChildAnimationClip(2, 10, 1);
		clip.animation = externalAnimation;
		var child = new PhotonAnimationInput();
		child.target = UUID.randomUUID().toString();
		child.source = parameter.id.toString();
		child.value = new float[]{3};
		clip.inputs.add(child);
		track.clips().add(clip);
		owner.tracks.add(track);
		var own = input(library, parameter.id.toString(), new float[]{3});
		library.remapTargets(Map.of(original.toString(), remapped.toString()));
		check(Objects.equals(track.targetId(), remapped), "parent spawner target remapped");
		check(clip.animation.equals(externalAnimation), "external animation reference is unchanged");
		check(child.source.equals(parameter.id.toString()), "formal identity is not a scene object identity");
		var id = parameter.id;
		parameter.name = "renamed";
		var renamed = PhotonAnimationParameter.read(parameter.write());
		check(renamed.id.equals(id) && child.source.equals(id.toString()), "rename retains formal references");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.VECTOR);
		values(new float[]{3}, child.value, "child target signature is not replaced by parent source signature");
		values(new float[]{3, 1, 1}, own.value, "own formal input type migration");
		own.value[1] = 3;
		equal(1, parameter.defaults[1], "input defaults isolation");
	}

	private void parameterMigration() {
		growingParameter();
		colorParameter();
		discreteParameter();
		unchangedAndInvalidRange();
		expandedParameterMigration();
		emptyIntegerMigration();
	}

	private void formalRemoval() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{2}, 0, 4);
		var owner = library.animations.getFirst();
		var other = new PhotonAnimationDefinition();
		other.name = "other";
		var otherParameter = new PhotonAnimationParameter();
		otherParameter.name = parameter.name;
		other.parameters.add(otherParameter);
		library.animations.add(other);
		var binding = new PhotonAnimationBinding();
		binding.source = PhotonAnimationBinding.Source.PARAMETER;
		binding.parameter = parameter.id.toString();
		binding.scaleParameter = parameter.id.toString();
		binding.offsetParameter = parameter.id.toString();
		owner.bindings.add(binding);
		owner.durationParameter = parameter.id.toString();
		var own = input(library, parameter.id.toString(), new float[]{3});
		var rule = new PhotonAnimationRule();
		rule.animation = owner.id.toString();
		rule.target = other.id.toString();
		var forwarded = new PhotonAnimationInput();
		forwarded.target = otherParameter.id.toString();
		forwarded.source = parameter.id.toString();
		forwarded.follow = true;
		forwarded.value = new float[]{4};
		rule.inputs.add(forwarded);
		library.rules.add(rule);
		var track = new PhotonChildAnimationTrack();
		var clip = new PhotonChildAnimationClip();
		clip.animation = UUID.randomUUID().toString();
		var child = PhotonAnimationInput.read(forwarded.write());
		clip.inputs.add(child);
		track.clips().add(clip);
		owner.tracks.add(track);
		PhotonAnimationParameterEditor.remove(library, owner, parameter);
		check(owner.parameters.isEmpty(), "animation formal removed");
		check(other.parameters.contains(otherParameter), "same name in other animation preserved");
		check(binding.parameter.isEmpty() && binding.scaleParameter.isEmpty() && binding.offsetParameter.isEmpty(),
				"binding formal references cleared");
		check(owner.durationParameter.isEmpty(), "duration formal cleared");
		check(library.rules.stream().noneMatch(value -> value.inputs.contains(own)), "removed target input pruned");
		check(forwarded.source.isEmpty() && !forwarded.follow, "removed source falls back to fixed value");
		values(new float[]{4}, forwarded.value, "source removal retains fixed fallback");
		check(child.target.equals(otherParameter.id.toString()), "external child target remains unchanged");
		check(child.source.isEmpty() && !child.follow, "child source reference cleared by owner identity");
	}

	private void formalChildScopes() {
		formalChildScope(true, false);
		formalChildScope(false, false);
		formalChildScope(false, true);
	}

	private void formalChildScope(boolean currentLibrary, boolean inferred) {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{2}, 0, 4);
		var owner = library.animations.getFirst();
		var track = new PhotonChildAnimationTrack();
		var clip = new PhotonChildAnimationClip();
		clip.animation = owner.id.toString();
		var child = new PhotonAnimationInput();
		child.target = parameter.id.toString();
		child.source = parameter.id.toString();
		child.follow = true;
		child.value = new float[]{3};
		clip.inputs.add(child);
		track.clips().add(clip);
		owner.tracks.add(track);

		if (inferred) {
			PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.VECTOR);
			PhotonAnimationParameterEditor.changeBounds(library, owner, parameter, 1, 2);
		} else {
			PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.VECTOR, _ -> currentLibrary);
			PhotonAnimationParameterEditor.changeBounds(library, owner, parameter, 1, 2, _ -> currentLibrary);
		}

		values(currentLibrary ? new float[]{2, 1, 1} : new float[]{3}, child.value,
				"child target migration follows actual library ownership, inferred=" + inferred);

		if (inferred) {
			PhotonAnimationParameterEditor.remove(library, owner, parameter);
		} else {
			PhotonAnimationParameterEditor.remove(library, owner, parameter, _ -> currentLibrary);
		}

		check(clip.inputs.isEmpty() == currentLibrary, "same UUID child target removal follows resource ownership");

		if (!currentLibrary) {
			check(child.target.equals(parameter.id.toString()), "external same UUID target is retained");
			check(child.source.isEmpty() && !child.follow, "parent source is cleared independently of child target scope");
		}
	}

	private void formState() {
		var oldRoot = new UIElement();
		var removed = new ConfiguratorGroup("old", false);
		var original = new ConfiguratorGroup("old", true);
		var definition = new ConfiguratorGroup("radius", false);
		var preview = new ConfiguratorGroup("radius", true);
		oldRoot.addChild(removed);
		oldRoot.addChild(original);
		oldRoot.addChild(definition);
		oldRoot.addChild(preview);
		var identity = UUID.randomUUID();
		var parameter = UUID.randomUUID();
		var definitionRole = PhotonAnimationTextUtil.key("parameters");
		var previewRole = PhotonAnimationTextUtil.key("preview_parameters");
		var state = PhotonAnimationFormState.capture(oldRoot, new ScrollerView(), Map.of(
				removed, new GroupIdentity(UUID.randomUUID(), ""), original, new GroupIdentity(identity, ""),
				definition, new GroupIdentity(parameter, definitionRole), preview, new GroupIdentity(parameter, previewRole)));
		var rebuilt = new UIElement();
		var renamed = new ConfiguratorGroup("renamed", false);
		var nextDefinition = new ConfiguratorGroup("new_radius", true);
		var nextPreview = new ConfiguratorGroup("new_radius", false);
		var inserted = new ConfiguratorGroup("old", true);
		rebuilt.addChild(nextPreview);
		rebuilt.addChild(renamed);
		rebuilt.addChild(inserted);
		rebuilt.addChild(nextDefinition);
		state.restore(rebuilt, new ScrollerView(), Map.of(
				renamed, new GroupIdentity(identity, ""), nextDefinition, new GroupIdentity(parameter, definitionRole),
				nextPreview, new GroupIdentity(parameter, previewRole), inserted, new GroupIdentity(UUID.randomUUID(), "")));
		check(renamed.isCollapse(), "form rename/delete/reorder preserves model state");
		check(!nextDefinition.isCollapse(), "definition state remains distinct from preview");
		check(nextPreview.isCollapse(), "preview state remains distinct from definition");
		check(inserted.isCollapse(), "new item keeps native initial state");
	}

	private void arraySelection() {
		var first = UUID.randomUUID();
		var selected = UUID.randomUUID();
		var oldRoot = new UIElement();
		var identities = new HashMap<ConfiguratorGroup, GroupIdentity>();
		var original = formArray(oldRoot, List.of(first, selected), identities, "rules");
		original.setSelected(arrayItem(original, 1));
		var state = PhotonAnimationFormState.capture(oldRoot, new ScrollerView(), identities);
		var reorderedRoot = new UIElement();
		var reorderedIdentities = new HashMap<ConfiguratorGroup, GroupIdentity>();
		var reordered = formArray(reorderedRoot, List.of(selected, UUID.randomUUID(), first), reorderedIdentities, "rules");
		state.restore(reorderedRoot, new ScrollerView(), reorderedIdentities);
		check(reordered.getSelected() == arrayItem(reordered, 0), "array selection follows model after reorder");
		check(Objects.requireNonNull(reordered.getSelected()).object.equals(selected), "array selected object retains identity");
		var removedRoot = new UIElement();
		var removedIdentities = new HashMap<ConfiguratorGroup, GroupIdentity>();
		var removed = formArray(removedRoot, List.of(first, UUID.randomUUID()), removedIdentities, "rules");
		removed.setSelected(arrayItem(removed, 1));
		state.restore(removedRoot, new ScrollerView(), removedIdentities);
		check(removed.getSelected() == null, "deleted selected item does not select its old index");
		var otherRoot = new UIElement();
		var otherIdentities = new HashMap<ConfiguratorGroup, GroupIdentity>();
		var other = formArray(otherRoot, List.of(selected, first), otherIdentities, "other");
		state.restore(otherRoot, new ScrollerView(), otherIdentities);
		check(other.getSelected() == null, "same UUID with another role does not receive selection");
	}

	private static ArrayConfiguratorGroup<UUID> formArray(UIElement root, List<UUID> values,
	                                                      Map<ConfiguratorGroup, GroupIdentity> identities, String role) {
		var array = new ArrayConfiguratorGroup<>("array", false, () -> values, (getter, _) -> {
			var group = new ConfiguratorGroup("item", false);
			identities.put(group, new GroupIdentity(getter.get(), role));
			return group;
		}, false);
		root.addChild(array);
		return array;
	}

	@SuppressWarnings("unchecked") // 隔离原生数组的配置器列表仅含该数组自己的 ItemConfigurator。
	private static ArrayConfiguratorGroup<UUID>.ItemConfigurator arrayItem(ArrayConfiguratorGroup<UUID> array, int index) {
		return (ArrayConfiguratorGroup<UUID>.ItemConfigurator) array.getConfigurators().get(index);
	}

	private void parameterWidgets() {
		for (var type : PhotonAnimationParameter.Type.values()) {
			var parameter = new PhotonAnimationParameter();
			parameter.type = type;
			parameter.minimum = -2;
			parameter.maximum = 4;
			parameter.defaults = new float[type.channels()];

			for (var axis = 0; axis < parameter.defaults.length; axis++) {
				parameter.defaults[axis] = type.isInteger() ? axis + 1 : 0.12345f + axis * 0.2f;
			}

			parameter.defaults = parameter.validate(parameter.defaults);
			var original = parameter.defaults;
			var writes = new int[]{0};
			var group = new ConfiguratorGroup();
			PhotonAnimationUiUtil.parameterValues(group, "value", parameter, () -> parameter.defaults, values -> {
				writes[0]++;
				parameter.defaults = values;
			});
			check(writes[0] == 0 && parameter.defaults == original, "building parameter controls does not quantize " + type);
			var fields = group.getConfigurators();

			if (type == PhotonAnimationParameter.Type.BOOLEAN) {
				check(fields.size() == 1 && fields.getFirst() instanceof BooleanConfigurator, "boolean uses native checkbox");
				continue;
			}

			var offset = type == PhotonAnimationParameter.Type.COLOR ? 1 : 0;
			check(fields.size() == type.channels() + offset, "parameter control channel count " + type);

			if (offset > 0) {
				check(fields.getFirst() instanceof ColorConfigurator, "color uses native picker");
			}

			for (var axis = 0; axis < type.channels(); axis++) {
				check(fields.get(axis + offset) instanceof NumberConfigurator, "numeric channel control " + type);
				var number = (NumberConfigurator) fields.get(axis + offset);
				check(number.getNumberType() == (type.isInteger() ? ConfigNumber.Type.INTEGER : ConfigNumber.Type.FLOAT),
						"numeric channel type " + type);
				equal(parameter.effectiveMinimum(), Objects.requireNonNull(number.getMin()).floatValue(), "numeric minimum " + type);
				equal(parameter.effectiveMaximum(), Objects.requireNonNull(number.getMax()).floatValue(), "numeric maximum " + type);
			}
		}
	}

	@SuppressWarnings("ConstantValue") // 回归空焦点的约定以及原生输入控件的快捷键隔离。
	private void inputFocus() {
		check(!PhotonAnimationUiUtil.editingInput(null), "missing input focus does not block shortcuts");
		check(!PhotonAnimationUiUtil.editingInput(new UIElement()), "ordinary UI does not block shortcuts");
		var text = new TextField();
		check(PhotonAnimationUiUtil.editingInput(text), "text input owns keyboard focus");
		var color = new ColorSelector();
		check(PhotonAnimationUiUtil.editingInput(color), "color picker owns keyboard focus");
		var child = new UIElement();
		color.addChild(child);
		check(PhotonAnimationUiUtil.editingInput(child), "color picker descendant owns keyboard focus");
	}

	private void growingParameter() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{2}, 0, 4);
		var action = input(library, parameter.name, new float[]{-3});
		var unrelated = input(library, "other", new float[]{7, 8});
		var unrelatedValues = unrelated.value;
		PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.VECTOR);
		check(parameter.type == PhotonAnimationParameter.Type.VECTOR, "growing type");
		values(new float[]{2, 1, 1}, parameter.defaults, "growing defaults");
		values(new float[]{0, 1, 1}, action.value, "growing action");
		check(unrelated.value == unrelatedValues, "unrelated parameter action");
		action.value[1] = 3;
		equal(1, parameter.defaults[1], "action defaults isolation");
		var loaded = PhotonAnimationParameter.read(parameter.write());
		values(parameter.defaults, loaded.defaults, "migrated parameter round trip");
	}

	private void colorParameter() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.VECTOR, new float[]{0.2f, 0.5f, 2}, -1, 3);
		var action = input(library, parameter.name, new float[]{-5, 0.25f, Float.NaN});
		PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.COLOR);
		values(new float[]{0.2f, 0.5f, 1, 1}, parameter.defaults, "color defaults");
		values(new float[]{0, 0.25f, 1, 1}, action.value, "color action");
	}

	private void discreteParameter() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.VECTOR, new float[]{1.6f, 7, 8}, 0, 8);
		var action = input(library, parameter.name, new float[]{7.6f, 9, 10});
		PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.INTEGER);
		values(new float[]{2}, parameter.defaults, "integer defaults");
		values(new float[]{8}, action.value, "integer action");
		parameter.defaults[0] = 0.2f;
		action.value[0] = 0.7f;
		PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.BOOLEAN);
		values(new float[]{0}, parameter.defaults, "boolean defaults");
		values(new float[]{1}, action.value, "boolean action");
	}

	private void unchangedAndInvalidRange() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{4}, 0, 8);
		var action = input(library, parameter.name, new float[]{3});
		var defaults = parameter.defaults;
		var actionValues = action.value;
		PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, parameter.type);
		check(parameter.defaults == defaults && action.value == actionValues, "same type preserves values");
		parameter.minimum = 9;
		var rejected = false;

		try {
			PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.VECTOR);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}

		check(rejected, "invalid range rejected");
		check(parameter.type == PhotonAnimationParameter.Type.NUMBER && parameter.defaults == defaults
				&& action.value == actionValues, "invalid range leaves definitions intact");
	}

	private void expandedParameterMigration() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{2.6f}, -2.2f, 4.8f);
		var first = input(library, parameter.id.toString(), new float[]{5.8f});
		var second = input(library, parameter.id.toString(), new float[]{-8});
		var other = new PhotonAnimationDefinition();
		other.name = "other";
		var otherParameter = new PhotonAnimationParameter();
		otherParameter.name = parameter.name;
		otherParameter.type = PhotonAnimationParameter.Type.FLOAT_VEC2;
		other.parameters.add(otherParameter);
		library.animations.add(other);
		var unrelated = new PhotonAnimationInput();
		unrelated.target = otherParameter.id.toString();
		unrelated.value = new float[]{7.6f, 8};
		var rule = new PhotonAnimationRule();
		rule.target = other.id.toString();
		rule.inputs.add(unrelated);
		library.rules.add(rule);
		var unrelatedValues = unrelated.value;
		var owner = library.animations.getFirst();
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.FLOAT_VEC2);
		values(new float[]{2.6f, 1}, parameter.defaults, "float vec2 defaults");
		values(new float[]{4.8f, 1}, first.value, "float vec2 input");
		values(new float[]{-2.2f, 1}, second.value, "float vec2 other input");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.FLOAT_VEC4);
		values(new float[]{2.6f, 1, 1, 1}, parameter.defaults, "float vec4 defaults");
		values(new float[]{4.8f, 1, 1, 1}, first.value, "float vec4 input");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.INTEGER_VEC4);
		values(new float[]{3, 1, 1, 1}, parameter.defaults, "integer vec4 defaults");
		values(new float[]{4, 1, 1, 1}, first.value, "integer vec4 input");
		values(new float[]{-2, 1, 1, 1}, second.value, "integer vec4 other input");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.INTEGER_VEC3);
		values(new float[]{3, 1, 1}, parameter.defaults, "integer vec3 defaults");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.INTEGER_VEC2);
		values(new float[]{3, 1}, parameter.defaults, "integer vec2 defaults");
		PhotonAnimationParameterEditor.changeType(library, owner, parameter, PhotonAnimationParameter.Type.INTEGER);
		values(new float[]{3}, parameter.defaults, "integer scalar defaults");
		values(new float[]{4}, first.value, "integer scalar input");
		values(new float[]{-2}, second.value, "integer scalar other input");
		check(unrelated.value == unrelatedValues, "other animation same-name inputs do not migrate");
	}

	private void emptyIntegerMigration() {
		var library = new PhotonAnimationLibrary();
		var parameter = parameter(library, PhotonAnimationParameter.Type.NUMBER, new float[]{0.25f}, 0.2f, 0.3f);
		var action = input(library, parameter.name, new float[]{0.26f});
		var original = parameter.defaults;
		var actionValue = action.value;
		var rejected = false;

		try {
			PhotonAnimationParameterEditor.changeType(library, library.animations.getFirst(), parameter, PhotonAnimationParameter.Type.INTEGER_VEC2);
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}

		check(rejected, "empty integer interval rejected during type change");
		check(parameter.type == PhotonAnimationParameter.Type.NUMBER && parameter.defaults == original
				&& action.value == actionValue, "empty interval preserves definitions");
		equal(0.2f, parameter.minimum, "empty interval preserves minimum");
		equal(0.3f, parameter.maximum, "empty interval preserves maximum");
	}

	private static PhotonAnimationParameter parameter(PhotonAnimationLibrary library, PhotonAnimationParameter.Type type,
	                                                  float[] defaults, float minimum, float maximum) {
		var parameter = new PhotonAnimationParameter();
		parameter.name = "parameter";
		parameter.type = type;
		parameter.defaults = defaults;
		parameter.minimum = minimum;
		parameter.maximum = maximum;

		if (library.animations.isEmpty()) {
			var owner = new PhotonAnimationDefinition();
			owner.name = "owner";
			library.animations.add(owner);
		}

		library.animations.getFirst().parameters.add(parameter);
		return parameter;
	}

	private static PhotonAnimationInput input(PhotonAnimationLibrary library, String target, float[] values) {
		var owner = library.animations.getFirst();
		var rule = new PhotonAnimationRule();
		rule.target = owner.id.toString();
		var input = new PhotonAnimationInput();
		var parameter = owner.parameter(target);
		input.target = parameter == null ? target : parameter.id.toString();
		input.value = values;
		rule.inputs.add(input);
		library.rules.add(rule);
		return input;
	}

	private void inputGroups() {
		var root = new UIElement();
		var rule = PhotonAnimationForms.group(root, "rules");
		var values = List.of(new PhotonAnimationInput(), new PhotonAnimationInput());
		var inputs = new ArrayConfiguratorGroup<>("inputs", false, () -> values,
				(_, _) -> new ConfiguratorGroup("input", false), false);
		rule.addConfigurator(inputs);
		check(inputs.getConfigurators().size() == 2, "inputs use native array rows");
		check(inputs.getParent() == rule.configuratorContainer, "input array belongs to trigger group");
		inputs.setCollapse(true);
		rule.setCollapse(true);
		check(!immediateDisplay(rule.configuratorContainer), "collapsed trigger hides inputs");
		rule.setCollapse(false);
		check(immediateDisplay(rule.configuratorContainer), "expanded trigger restores inputs");
		check(inputs.isCollapse() && !immediateDisplay(inputs.configuratorContainer), "input array keeps own collapse");
	}

	private static boolean immediateDisplay(UIElement element) {
		// 隔离控件未挂接 ModularUI，setter 后需读取即时样式，不能读取布局引擎尚未刷新的显示缓存。
		return element.getLayout().getValueImmediate(LayoutProperties.DISPLAY) != TaffyDisplay.NONE;
	}

	private void bindingLabels() {
		var property = new ColorPropertyType("startColor", "photon.emitter.config.startColor");
		var title = PhotonAnimationBindingEditor.bindingTitle("circle", property);
		check(title.getContents() instanceof TranslatableContents, "binding title translatable");
		var contents = (TranslatableContents) title.getContents();
		check(contents.getKey().equals(PhotonAnimationTextUtil.key("binding_title")), "binding title format key");
		check(contents.getArgs().length == 2 && contents.getArgs()[1] instanceof Component, "binding title component args");
		var label = (Component) contents.getArgs()[1];
		check(label.getContents() instanceof TranslatableContents nativeLabel
				&& nativeLabel.getKey().equals(property.displayNameKey()), "native property remains translatable");
		var group = new ConfiguratorGroup();
		group.setLabel(title);
		check(group.getLabel().equals(title), "native group retains translated title");
		var channels = PhotonAnimationProperty.valueChannelCount(property);
		check(channels == PhotonAnimationParameter.Type.COLOR.channels(), "color binding uses RGBA channels");
		check(PhotonAnimationBindingEditor.componentCandidates(channels, 0).equals(List.of(-1, 0, 1, 2, 3)), "color component candidates");
		check(PhotonAnimationBindingEditor.componentLabel(channels, 3).equals(PhotonAnimationTextUtil.key("component.a")), "alpha component label");
		check(PhotonAnimationBindingEditor.componentCandidates(3, 99).equals(List.of(-1, 0, 1, 2, 99)), "invalid component retained for repair");
		check(!PhotonAnimationBindingEditor.componentLabel(3, 99).isBlank(), "invalid component has safe label");
		check(PhotonAnimationTextUtil.componentSuffix(3, 0).equals("component.x"), "vector X label");
		check(PhotonAnimationTextUtil.componentSuffix(3, 1).equals("component.y"), "vector Y label");
		check(PhotonAnimationTextUtil.componentSuffix(3, 2).equals("component.z"), "vector Z label");
		check(PhotonAnimationTextUtil.componentSuffix(4, 0).equals("component.r"), "color R label");
		check(PhotonAnimationTextUtil.componentSuffix(4, 1).equals("component.g"), "color G label");
		check(PhotonAnimationTextUtil.componentSuffix(4, 2).equals("component.b"), "color B label");
		componentRepair();
	}

	private void statusFormatting() {
		var problemSets = List.of(List.<String>of(), List.of("first problem", "second problem"));

		for (var state : State.values()) {
			for (var problems : problemSets) {
				statusFormatting(state, problems);
			}
		}
	}

	private void statusFormatting(State state, List<String> problems) {
		var status = PhotonAnimationUiUtil.animationStatus(state, 12.5, 0.375, 3L, problems);
		var name = "animation status " + state + " problems " + problems.size();
		check(status.getContents() instanceof TranslatableContents, name + " translated format");
		var contents = (TranslatableContents) status.getContents();
		check(contents.getKey().equals(PhotonAnimationTextUtil.key("status_format")), name + " format key");
		var args = contents.getArgs();
		check(args.length == 5, name + " argument count");

		for (var index = 0; index < args.length; index++) {
			check(args[index] instanceof Component || TranslatableContents.isAllowedPrimitiveArgument(args[index]),
					name + " legal argument " + index);
		}

		check(args[0] instanceof Component, name + " state component");
		var label = (Component) args[0];
		check(label.getContents() instanceof TranslatableContents translated
				&& translated.getKey().equals(PhotonAnimationTextUtil.key("enum." + state.name())), name + " translated state");
		check(Objects.equals(12.5, args[1]), name + " time");
		check(Objects.equals(0.375, args[2]), name + " progress");
		check(Objects.equals(3L, args[3]), name + " loops");
		check(args[4] instanceof String, name + " problem string");
		check(Objects.equals(problems.toString(), args[4]), name + " problem contents");
	}

	private void componentRepair() {
		var group = new ConfiguratorGroup();
		var selected = new int[]{99};
		PhotonAnimationForms.select(group, "component", () -> selected[0], value -> selected[0] = value,
				PhotonAnimationBindingEditor.componentCandidates(3, selected[0]), axis -> PhotonAnimationBindingEditor.componentLabel(3, axis));
		check(selected[0] == 99, "building selector does not change definition");
		var field = (SelectorConfigurator<?>) group.getConfigurators().getFirst();
		check(Objects.equals(99, field.selector.getValue()), "invalid component displays as selected candidate");
		@SuppressWarnings("unchecked")
		var selector = (SelectorConfigurator<Integer>) field;
		selector.selector.setSelected(2, true);
		check(selected[0] == 2, "valid component selection repairs definition");
	}

	private void values(float[] expected, float[] actual, String name) {
		check(expected.length == actual.length, name + " dimension");

		for (var axis = 0; axis < expected.length; axis++) {
			equal(expected[axis], actual[axis], name + " component " + axis);
		}
	}

	private void equal(double expected, double actual, String name) {
		check(Math.abs(expected - actual) < 0.00001, name);
	}

	private void check(boolean condition, String name) {
		assertions++;

		if (!condition) {
			throw new AssertionError(name);
		}
	}
}
