package org.unitego.lobecorp.util.photon.emitter;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PhotonEmitterAnimationUtil {
	/// 为项目模板带入独立动画库；对象贡献只保留复制子树中的目标，不复制项目全局时间轴。
	public static void include(@Nullable IScene scope, List<IFXObject> copies) {
		if (scope == null) {
			return;
		}

		var controllers = scope.getAllSceneObjects().stream().filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList();

		if (controllers.isEmpty()) {
			return;
		}

		if (controllers.size() != 1) {
			throw new IllegalArgumentException();
		}

		var targets = copies.stream().filter(object -> !(object instanceof PhotonAnimationController))
				.map(IFXObject::id).collect(Collectors.toSet());
		var copiedControllers = copies.stream().filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList();

		if (copiedControllers.size() > 1) {
			throw new IllegalArgumentException();
		}

		var source = controllers.getFirst();
		var library = source.library();

		for (var animation : library.animations) {
			animation.tracks.removeIf(track -> removeTrack(track, targets));
			animation.bindings.removeIf(binding -> !targets.contains(binding.target));
		}

		var controller = copiedControllers.isEmpty() ? copyController(source, copies) : copiedControllers.getFirst();
		controller.library(library);
	}

	private static boolean removeTrack(Track track, Set<UUID> targets) {
		if (track instanceof TrackGroup group) {
			group.children().removeIf(child -> removeTrack(child, targets));
			return group.children().isEmpty();
		}

		if (track instanceof ControlTrack) {
			track.clips().removeIf(clip -> !targets.contains(clip.targetId()));
			return track.clips().isEmpty();
		}

		if (track instanceof AnimationTrack || track instanceof ActivatorTrack) {
			return !targets.contains(track.targetId());
		}

		track.clips().removeIf(clip -> clip.targetId() != null && !targets.contains(clip.targetId()));
		return track.targetId() != null && !targets.contains(track.targetId());
	}

	private static PhotonAnimationController copyController(PhotonAnimationController source, List<IFXObject> copies) {
		if (copies.size() >= PhotonEmitterSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			throw new IllegalArgumentException();
		}

		if (!(source.copy(true) instanceof PhotonAnimationController copy)) {
			throw new IllegalArgumentException();
		}

		copy.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
		copy.transform()._setInternalChildID(new ArrayList<>());
		copies.add(copy);
		return copy;
	}
}
