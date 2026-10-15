package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock.State;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/// 独立测试目标与实参只属于窗口；启动多个动画不改变制作选择或资源定义。
public class PhotonAnimationTestControls {
	private final PhotonAnimationWorkspace workspace;
	private final Map<UUID, Map<String, float[]>> inputs = new HashMap<>();
	private @Nullable UUID target;

	public PhotonAnimationTestControls(PhotonAnimationWorkspace workspace) {
		this.workspace = workspace;
	}

	public void build(UIElement container) {
		var controls = PhotonAnimationForms.group(container, "runtime_controls");
		var details = PhotonAnimationUiUtil.column();
		PhotonAnimationForms.animationSelector(workspace, controls, "test_target", this::targetReference,
				value -> selectTarget(value, details));
		controls.addConfigurator(new Configurator().addInlineChild(details));
		controls.addConfigurator(new Configurator().addInlineChild(PhotonAnimationUiUtil.row().addChildren(
				PhotonAnimationUiUtil.button("reset", workspace::resetPreview),
				PhotonAnimationUiUtil.button("finish", workspace::finishPreview))));
		liveStatus(controls, "scene_state", () -> Component.translatable(
				PhotonAnimationTextUtil.key("enum." + workspace.sceneState().name())));
		buildDetails(details);
	}

	private String targetReference() {
		return target == null || workspace.library().find(target.toString()) == null ? "" : target.toString();
	}

	private void selectTarget(String value, UIElement details) {
		var animation = workspace.library().find(value);
		var selected = animation == null ? null : animation.id;

		if (Objects.equals(target, selected)) {
			return;
		}

		target = selected;
		buildDetails(details);
	}

	private void buildDetails(UIElement details) {
		details.clearAllChildren();
		var animation = target == null ? null : workspace.library().find(target.toString());

		if (animation == null) {
			return;
		}

		var controls = PhotonAnimationForms.group(details, "status");
		controls.addConfigurator(new Configurator().addInlineChild(PhotonAnimationUiUtil.row().addChildren(
				playbackToggle(animation),
				PhotonAnimationUiUtil.button("play", () -> workspace.testPlay(animation.id, true, invocationInputs(animation))),
				PhotonAnimationUiUtil.button("stop", () -> workspace.testStop(animation.id)))));
		liveStatus(controls, "animation_state", () -> workspace.animationStatus(animation.id));
		PhotonAnimationForms.liveNumber(controls, "runtime_duration", () -> {
			var playback = workspace.testPlayback(animation.id);
			return playback == null ? animation.playbackDuration() : playback.clock.duration();
		}, value -> {
			var playback = workspace.testPlayback(animation.id);

			if (playback != null) {
				playback.setDuration(Math.max(animation.loop ? 1 : 0, value.doubleValue()));
			}
		}, animation.playbackDuration());
		PhotonAnimationForms.readOnlyNumber(controls, "time", () -> {
			var playback = workspace.testPlayback(animation.id);
			return playback == null ? 0 : playback.time(0);
		}, 0);
		buildInputs(controls, animation);
	}

	private Button playbackToggle(PhotonAnimationDefinition animation) {
		var button = new Button() {
			private String action = playbackAction(animation.id);

			@Override
			public void screenTick() {
				super.screenTick();
				var next = playbackAction(animation.id);

				if (action.equals(next)) {
					return;
				}

				action = next;
				setText(PhotonAnimationTextUtil.key(action));
				getStyle().tooltips(PhotonAnimationTextUtil.key(action + ".tooltip"));
			}
		};
		var action = playbackAction(animation.id);
		button.setText(PhotonAnimationTextUtil.key(action));
		button.getStyle().tooltips(PhotonAnimationTextUtil.key(action + ".tooltip"));
		button.getLayout().setFlexShrink(0);
		button.setOnClick(_ -> {
			if (testPlaying(animation.id)) {
				workspace.testPause(animation.id);
				return;
			}

			workspace.testContinue(animation.id, invocationInputs(animation));
		});
		PhotonEditorTooltipUtil.wrap(button, () -> Component.translatable(
				PhotonAnimationTextUtil.key(playbackAction(animation.id) + ".tooltip")));
		return button;
	}

	private String playbackAction(UUID id) {
		return testPlaying(id) ? "pause" : "continue";
	}

	private boolean testPlaying(UUID id) {
		var playback = workspace.testPlayback(id);
		return playback != null && playback.clock.state() == State.PLAYING && workspace.sceneState() == State.PLAYING;
	}

	private void buildInputs(ConfiguratorGroup controls, PhotonAnimationDefinition animation) {
		if (animation.parameters.isEmpty()) {
			return;
		}

		var arguments = new ConfiguratorGroup(PhotonAnimationTextUtil.key("test_inputs"), false);
		PhotonAnimationUiUtil.tips(arguments, "test_inputs");
		controls.addConfigurator(arguments);

		for (var parameter : animation.parameters) {
			var group = new ConfiguratorGroup(parameter.name, false);
			workspace.bindFormGroup(group, parameter, PhotonAnimationTextUtil.key("test_inputs"));
			PhotonAnimationUiUtil.tips(group, "test_inputs");
			arguments.addConfigurator(group);
			PhotonAnimationUiUtil.parameterValues(group, "value", parameter, () -> input(animation, parameter),
					value -> inputs.computeIfAbsent(animation.id, _ -> new HashMap<>())
							.put(parameter.id.toString(), parameter.validate(value)));
		}
	}

	private float[] input(PhotonAnimationDefinition animation, PhotonAnimationParameter parameter) {
		var values = inputs.getOrDefault(animation.id, Map.of()).get(parameter.id.toString());
		return parameter.validate(values == null || values.length != parameter.type.channels() ? parameter.defaults : values);
	}

	private Map<String, float[]> invocationInputs(PhotonAnimationDefinition animation) {
		var values = new HashMap<String, float[]>();

		for (var parameter : animation.parameters) {
			values.put(parameter.id.toString(), input(animation, parameter));
		}

		return values;
	}

	private static void liveStatus(ConfiguratorGroup group, String key, Supplier<Component> supplier) {
		var field = new Configurator(PhotonAnimationTextUtil.key(key));
		var label = new Label().setText(supplier.get());
		TimelineContext.styleLabel(label);
		label.addEventListener(UIEvents.TICK, _ -> label.setText(supplier.get()));
		field.addInlineChild(label);
		PhotonAnimationUiUtil.tips(field, key);
		group.addConfigurator(field);
	}
}
