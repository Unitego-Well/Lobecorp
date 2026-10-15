package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 编辑器历史保存数据，同时保留原生时间轴用作 UI 状态键的轨道与属性引用。
/// 这些引用仅属于当前工作区，不写入特效资源。
public class PhotonAnimationEditSnapshot {
	private final PhotonAnimationWorkspace workspace;
	private final CompoundTag data;
	private final CompoundTag previewData;
	private final Map<UUID, List<TrackState>> tracks = new HashMap<>();
	private final Map<UUID, List<PhotonAnimationDefinition.Marker>> markers = new HashMap<>();
	private final List<UUID> formIdentities;

	public PhotonAnimationEditSnapshot(PhotonAnimationWorkspace workspace, CompoundTag data,
	                                   PhotonAnimationLibrary library) {
		this.workspace = workspace;
		this.data = data.copy();
		var preview = workspace.testDefinition();
		previewData = preview.write(Platform.getFrozenRegistry());
		formIdentities = workspace.captureFormIdentities(formModels(library, preview));

		for (var animation : library.animations) {
			captureDefinition(animation);
		}

		captureDefinition(preview);
	}

	private void captureDefinition(PhotonAnimationDefinition animation) {
		tracks.put(animation.id, animation.tracks.stream().map(TrackState::capture).toList());
		markers.put(animation.id, List.copyOf(animation.markers));
	}

	public CompoundTag data() {
		return data.copy();
	}

	public CompoundTag previewData() {
		return previewData.copy();
	}

	public PhotonAnimationDefinition restorePreview() {
		var definition = PhotonAnimationDefinition.read(Platform.getFrozenRegistry(), previewData, PhotonAnimationDataUtil.VERSION);
		restoreDefinition(definition);
		return definition;
	}

	public boolean belongsTo(PhotonAnimationWorkspace owner) {
		return workspace == owner;
	}

	public PhotonAnimationLibrary restore() {
		var library = PhotonAnimationLibrary.read(Platform.getFrozenRegistry(), data);

		for (var animation : library.animations) {
			restoreDefinition(animation);
		}

		workspace.restoreFormIdentities(formModels(library, workspace.testDefinition()), formIdentities);
		return library;
	}

	private void restoreDefinition(PhotonAnimationDefinition animation) {
		restoreMarkers(animation);
		var references = tracks.get(animation.id);

		if (references == null || references.size() != animation.tracks.size()) {
			return;
		}

		for (int index = 0; index < references.size(); index++) {
			animation.tracks.set(index, references.get(index).restore(animation.tracks.get(index)));
		}
	}

	private static List<Object> formModels(PhotonAnimationLibrary library, PhotonAnimationDefinition preview) {
		List<Object> models = new ArrayList<>();

		for (var rule : library.rules) {
			models.add(rule);
			models.addAll(rule.inputs);
		}

		var definitions = new ArrayList<>(library.animations);
		definitions.add(preview);

		for (var animation : definitions) {
			models.addAll(animation.parameters);
			models.addAll(animation.markers);
			models.addAll(animation.bindings);

			for (var track : animation.leafTracks(true)) {
				if (track instanceof AnimationTrack animated) {
					models.addAll(animated.properties());
				}

				for (var clip : track.clips()) {
					if (clip instanceof PhotonChildAnimationClip child) {
						models.addAll(child.inputs);
					}
				}
			}
		}

		return models;
	}

	private void restoreMarkers(PhotonAnimationDefinition animation) {
		var references = markers.get(animation.id);

		if (references == null || references.size() != animation.markers.size()) {
			return;
		}

		for (int index = 0; index < references.size(); index++) {
			var original = references.get(index);

			if (original.equals(animation.markers.get(index))) {
				animation.markers.set(index, original);
			}
		}
	}

	private record TrackState(Track track, List<AnimatedProperty> properties, List<TrackState> children,
	                          List<PhotonChildAnimationClip> childClips) {
		private static TrackState capture(Track track) {
			var properties = track instanceof AnimationTrack animation ? List.copyOf(animation.properties()) : List.<AnimatedProperty>of();
			var children = track instanceof TrackGroup group ? group.children().stream().map(TrackState::capture).toList() : List.<TrackState>of();
			var clips = track.clips().stream().filter(PhotonChildAnimationClip.class::isInstance)
					.map(PhotonChildAnimationClip.class::cast).toList();
			return new TrackState(track, properties, children, clips);
		}

		private Track restore(Track value) {
			var provider = Platform.getFrozenRegistry();

			if (track.writeData(provider).equals(value.writeData(provider))
					&& (!(track instanceof AnimationTrack animation) || animation.properties().equals(properties))
					&& (!(track instanceof TrackGroup group) || group.children().equals(children.stream().map(TrackState::track).toList()))) {
				return track;
			}

			track.readData(provider, value.writeData(provider));

			if (track instanceof AnimationTrack animation && value instanceof AnimationTrack restored) {
				for (int index = 0; index < properties.size() && index < restored.properties().size(); index++) {
					var property = properties.get(index);
					var source = restored.properties().get(index);

					if (!property.type().key().equals(source.type().key())) {
						continue;
					}

					property.restoreFrom(source);
					animation.properties().set(index, property);
				}
			}

			if (track instanceof TrackGroup group && value instanceof TrackGroup restored) {
				for (int index = 0; index < children.size() && index < restored.children().size(); index++) {
					group.children().set(index, children.get(index).restore(restored.children().get(index)));
				}
			}

			restoreChildClips();
			return track;
		}

		private void restoreChildClips() {
			for (int index = 0; index < track.clips().size(); index++) {
				if (!(track.clips().get(index) instanceof PhotonChildAnimationClip restored)) {
					continue;
				}

				var original = childClips.stream().filter(clip -> clip.id.equals(restored.id)).findFirst().orElse(null);

				if (original != null) {
					original.restoreFrom(restored);
					track.clips().set(index, original);
				}
			}
		}
	}
}
