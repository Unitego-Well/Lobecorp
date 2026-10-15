package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("resource") // 预览由工作区统一关闭。
public class PhotonAnimationRuleEditor {
	public static void build(PhotonAnimationWorkspace workspace, UIElement container) {
		var rules = new ArrayConfiguratorGroup<>(PhotonAnimationTextUtil.key("rules"), false,
				() -> List.copyOf(workspace.library().rules), (getter, _) -> rule(workspace, getter.get()), false);
		rules.setAddDefault(PhotonAnimationRule::new);
		rules.setOnUpdate(values -> workspace.edit(() -> {
			workspace.library().rules.clear();
			workspace.library().rules.addAll(values);
		}));
		PhotonAnimationUiUtil.tips(rules, "rules");
		container.addChild(rules);

		var trigger = PhotonAnimationForms.group(container, "trigger");
		var name = new String[]{""};
		PhotonAnimationUiUtil.text(trigger, "name", () -> name[0], value -> name[0] = value);
		trigger.addChild(PhotonAnimationUiUtil.button("trigger", () -> {
			if (!workspace.preview().active()) {
				workspace.preview().reset();
			}

			workspace.preview().runtime().trigger(name[0], new CompoundTag());
			workspace.preview().scene().particleManager.play();
		}));

		if (workspace.hasSelection()) {
			markers(workspace, container);
		}
	}

	private static ConfiguratorGroup rule(PhotonAnimationWorkspace workspace, PhotonAnimationRule rule) {
		var group = new ConfiguratorGroup(PhotonAnimationTextUtil.key("rules"), false);
		workspace.bindFormGroup(group, rule);
		group.lineContainer.setDisplay(false);
		PhotonAnimationUiUtil.enumeration(group, "event", () -> rule.event,
				value -> workspace.edit(() -> rule.event = value), PhotonAnimationRule.Event.values());
		PhotonAnimationForms.animationSelector(workspace, group, "title", () -> rule.animation,
				value -> workspace.edit(() -> rule.animation = value));
		PhotonAnimationUiUtil.text(group, "channel", () -> rule.channel, value -> workspace.edit(() -> rule.channel = value));
		PhotonAnimationUiUtil.text(group, "name", () -> rule.name, value -> workspace.edit(() -> rule.name = value));
		PhotonAnimationForms.animationSelector(workspace, group, "rule_target", () -> rule.target,
				value -> workspace.edit(() -> {
					rule.target = value;
					rule.inputs.clear();
				}));
		PhotonAnimationUiUtil.number(group, "action_duration", () -> rule.duration,
				value -> workspace.edit(() -> {
					var duration = value.doubleValue();

					if (!Double.isFinite(duration) || (duration != -1 && duration < 0)) {
						throw new IllegalArgumentException();
					}

					rule.duration = duration;
				}), rule.duration);
		PhotonAnimationInputEditor.build(group, workspace.library().find(rule.target),
				workspace.library().find(rule.animation), rule.inputs, workspace::edit, workspace);
		return group;
	}

	private static void markers(PhotonAnimationWorkspace workspace, UIElement container) {
		var animation = workspace.selected();
		var markers = new ArrayConfiguratorGroup<>(PhotonAnimationTextUtil.key("markers"), false,
				() -> List.copyOf(animation.markers),
				(getter, setter) -> marker(workspace, animation, getter, setter), false);
		markers.setAddDefault(() -> new PhotonAnimationDefinition.Marker(0, "", new CompoundTag()));
		markers.setOnUpdate(values -> workspace.edit(() -> {
			animation.markers.clear();
			animation.markers.addAll(values);
		}));
		PhotonAnimationUiUtil.tips(markers, "markers");
		PhotonAnimationUiUtil.tooltip(markers.addButton, "new_marker");
		PhotonAnimationUiUtil.tooltip(markers.removeButton, "delete");
		container.addChild(markers);
	}

	private static ConfiguratorGroup marker(PhotonAnimationWorkspace workspace, PhotonAnimationDefinition animation,
	                                        Supplier<PhotonAnimationDefinition.Marker> getter,
	                                        Consumer<PhotonAnimationDefinition.Marker> setter) {
		var group = new ConfiguratorGroup(PhotonAnimationTextUtil.key("markers"), false);
		workspace.bindFormGroup(group, getter.get());
		group.lineContainer.setDisplay(false);
		PhotonAnimationUiUtil.number(group, "time", () -> getter.get().time(), value -> {
			var marker = getter.get();
			var replacement = new PhotonAnimationDefinition.Marker(
					Math.clamp(value.doubleValue(), 0, animation.duration), marker.name(), marker.data());
			workspace.replaceFormIdentity(marker, replacement);
			setter.accept(replacement);
		}, 0);
		PhotonAnimationUiUtil.text(group, "name", () -> getter.get().name(), value -> {
			var marker = getter.get();
			var replacement = new PhotonAnimationDefinition.Marker(marker.time(), value, marker.data());
			workspace.replaceFormIdentity(marker, replacement);
			setter.accept(replacement);
		});
		return group;
	}
}
