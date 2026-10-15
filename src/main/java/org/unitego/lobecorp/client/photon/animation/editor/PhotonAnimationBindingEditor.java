package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProperty;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class PhotonAnimationBindingEditor {
	public static void build(PhotonAnimationWorkspace workspace, UIElement container) {
		if (!workspace.hasSelection()) {
			return;
		}

		for (var track : workspace.selected().leafTracks(true)) {
			if (!(track instanceof AnimationTrack animation) || track instanceof SpeedTrack) {
				continue;
			}

			var target = track.targetId();

			if (target == null) {
				continue;
			}

			for (var property : animation.properties()) {
				var path = property.type().path();
				var binding = workspace.selected().bindings.stream().filter(value -> value.matches(target, path))
						.findFirst().orElseGet(PhotonAnimationBinding::new);
				var group = PhotonAnimationForms.group(container, "bindings");
				workspace.bindFormGroup(group, property);
				group.setLabel(bindingTitle(track.displayName(), property.type()));
				PhotonAnimationUiUtil.enumeration(group, "blend", () -> binding.blend,
						value -> update(workspace, binding, target, path, entry -> entry.blend = value),
						property.type().stepped() && (!(property.type() instanceof ConfigPropertyType config)
								|| config.valueType() != ConfigValueType.INT) ? new PhotonAnimationBinding.Blend[]{PhotonAnimationBinding.Blend.OVERRIDE}
								: PhotonAnimationBinding.Blend.values());
				PhotonAnimationUiUtil.enumeration(group, "source", () -> binding.source,
						value -> update(workspace, binding, target, path, entry -> entry.source = value),
						PhotonAnimationBinding.Source.values());
				PhotonAnimationInputEditor.parameterSelector(group, "parameter", () -> binding.parameter,
						value -> update(workspace, binding, target, path, entry -> entry.parameter = value),
						workspace.selected(), _ -> true);

				var channels = PhotonAnimationProperty.valueChannelCount(property.type());
				PhotonAnimationForms.select(group, "component", () -> binding.component,
						value -> update(workspace, binding, target, path, entry -> entry.component = value),
						componentCandidates(channels, binding.component), axis -> componentLabel(channels, axis));
				PhotonAnimationUiUtil.number(group, "scale", () -> binding.scale,
						value -> update(workspace, binding, target, path, entry -> entry.scale = value.floatValue()), 1);
				PhotonAnimationUiUtil.number(group, "offset", () -> binding.offset,
						value -> update(workspace, binding, target, path, entry -> entry.offset = value.floatValue()), 0);
				PhotonAnimationInputEditor.parameterSelector(group, "scale_parameter", () -> binding.scaleParameter,
						value -> update(workspace, binding, target, path, entry -> entry.scaleParameter = value),
						workspace.selected(), PhotonAnimationInputEditor::scalar);
				PhotonAnimationInputEditor.parameterSelector(group, "offset_parameter", () -> binding.offsetParameter,
						value -> update(workspace, binding, target, path, entry -> entry.offsetParameter = value),
						workspace.selected(), PhotonAnimationInputEditor::scalar);
			}
		}
	}

	public static Component bindingTitle(String target, AnimatedPropertyType property) {
		return Component.translatable(PhotonAnimationTextUtil.key("binding_title"),
				Component.literal(target), Component.translatable(property.displayNameKey()));
	}

	public static List<Integer> componentCandidates(int channels, int selected) {
		var candidates = new ArrayList<>(IntStream.range(-1, channels).boxed().toList());

		if (!candidates.contains(selected)) {
			candidates.add(selected);
		}

		return candidates;
	}

	public static String componentLabel(int channels, int axis) {
		if (axis < -1 || axis >= channels) {
			return Component.translatable(PhotonAnimationTextUtil.key("component.invalid"), axis).getString();
		}

		return PhotonAnimationTextUtil.key(PhotonAnimationTextUtil.componentSuffix(channels, axis));
	}

	private static void update(PhotonAnimationWorkspace workspace, PhotonAnimationBinding binding,
	                           UUID target, String path, Consumer<PhotonAnimationBinding> action) {
		workspace.edit(() -> {
			binding.target = target;
			binding.property = path;

			if (!workspace.selected().bindings.contains(binding)) {
				workspace.selected().bindings.add(binding);
			}

			action.accept(binding);
		});
	}
}
