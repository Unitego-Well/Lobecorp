package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.gui.ui.utils.HistoryStack;
import com.lowdragmc.lowdraglib2.gui.ui.utils.IHistoryStack;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Bone;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Morph;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.TransformKey;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelValidationUtil;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.ValueKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Weight;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.SelectionMode;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class PhotonModelEditSession {
	private PhotonModelAsset asset;
	private IHistoryStack history = new HistoryStack();
	private Runnable changed = () -> {
	};
	public SelectionMode selectionMode = SelectionMode.PART;
	public String selectedPart = "";
	public String selectedBone = "";
	public String selectedMorph = "";
	public String selectedClip = "";
	public final Set<Integer> selectedVertices = new LinkedHashSet<>();
	public final Set<Integer> selectedFaces = new LinkedHashSet<>();
	public final Set<Edge> selectedEdges = new LinkedHashSet<>();
	public final Map<String, Transform> partPose = new LinkedHashMap<>();
	public final Map<String, Transform> bonePose = new LinkedHashMap<>();
	public final Map<String, Float> morphPose = new LinkedHashMap<>();
	public float time;
	public boolean playing;
	private long revision;
	private PhotonModelAsset liveBefore;

	public PhotonModelEditSession(PhotonModelAsset original) {
		asset = original.copy();
		selectFirstPart();
	}

	/// 独立工程复用原编辑器历史面板，保持现有编辑动作和深复制语义。
	public void setHistory(IHistoryStack history) {
		this.history = history;
	}

	/// 原生历史快照和工程加载恢复资产，不向历史栈追加新动作。
	public void restore(PhotonModelAsset replacement) {
		replacement.validate();
		replace(replacement);
	}

	public PhotonModelAsset asset() {
		return asset;
	}

	public long revision() {
		return revision;
	}

	public void onChanged(Runnable changed) {
		this.changed = changed;
	}

	public void change(String label, Consumer<PhotonModelAsset> edit) {
		var before = asset.copy();
		var after = asset.copy();
		edit.accept(after);
		after.validate();
		history.pushHistory(Component.translatable(PhotonModelTextUtil.key(label)),
				EditAction.of(() -> replace(after), () -> replace(before)));
	}

	private void replace(PhotonModelAsset replacement) {
		asset = replacement.copy();
		partPose.clear();
		bonePose.clear();
		morphPose.clear();
		sanitizeSelection();
		revision++;
		changed.run();
	}

	public void beginLiveEdit() {
		if (liveBefore == null) {
			liveBefore = asset.copy();
		}
	}

	public void liveEdit(Consumer<PhotonModelAsset> edit) {
		beginLiveEdit();
		edit.accept(asset);
		revision++;
	}

	public void endLiveEdit(String label) {
		if (liveBefore == null) {
			return;
		}

		var before = liveBefore;
		var after = asset.copy();
		liveBefore = null;

		try {
			after.validate();
		} catch (RuntimeException error) {
			replace(before);
			throw error;
		}

		history.pushHistory(Component.translatable(PhotonModelTextUtil.key(label)),
				EditAction.of(() -> replace(after), () -> replace(before)), false);
		changed.run();
	}

	/// 原生 Gizmo 取消拖动时精确恢复事务快照，不产生撤销记录或保留坐标换算误差。
	public void cancelLiveEdit() {
		if (liveBefore == null) {
			return;
		}

		var before = liveBefore;
		liveBefore = null;
		replace(before);
	}

	public void replaceFromFile(PhotonModelAsset loaded) {
		loaded.validate();
		var before = asset.copy();
		var after = loaded.copy();
		history.pushHistory(Component.translatable(PhotonModelTextUtil.key("editor.load")),
				EditAction.of(() -> replace(after), () -> replace(before)));
	}

	public void undo() {
		history.undo();
	}

	public void redo() {
		history.redo();
	}

	public void selectionChanged() {
		changed.run();
	}

	public Set<Integer> vertices() {
		return PhotonModelEditUtil.selectedVertices(asset, selectedPart, selectedVertices, selectedFaces, selectionMode);
	}

	public Part part() {
		return asset.parts.stream().filter(part -> part.id.equals(selectedPart)).findFirst().orElse(null);
	}

	public Bone bone() {
		return asset.bones.stream().filter(bone -> bone.id.equals(selectedBone)).findFirst().orElse(null);
	}

	public Morph morph() {
		return asset.morphs.stream().filter(morph -> morph.id.equals(selectedMorph)).findFirst().orElse(null);
	}

	public Clip clip() {
		return asset.clips.stream().filter(clip -> clip.id.equals(selectedClip)).findFirst().orElse(null);
	}

	private void selectFirstPart() {
		selectedPart = asset.parts.isEmpty() ? "" : asset.parts.getFirst().id;
	}

	private void sanitizeSelection() {
		selectedVertices.removeIf(index -> index < 0 || index >= asset.vertices.size());
		selectedFaces.removeIf(index -> index < 0 || index >= asset.faces.size());
		selectedEdges.removeIf(edge -> edge.first() < 0 || edge.first() >= asset.vertices.size()
				|| edge.second() < 0 || edge.second() >= asset.vertices.size());

		if (part() == null) {
			selectFirstPart();
		}

		if (bone() == null) {
			selectedBone = "";
		}

		if (morph() == null) {
			selectedMorph = "";
		}

		if (clip() == null) {
			selectedClip = "";
		}
	}

	public void addClip() {
		String id = UUID.randomUUID().toString();
		change("editor.add_clip", current -> {
			var clip = new Clip();
			clip.id = id;
			current.clips.add(clip);
		});
		selectedClip = id;
		changed.run();
	}

	public void addBone() {
		String id = UUID.randomUUID().toString();
		change("editor.add_bone", current -> {
			var bone = new Bone();
			bone.id = id;
			bone.parentId = selectedBone;
			current.bones.add(bone);
		});
		selectedBone = id;
		changed.run();
	}

	public void addMorph() {
		String id = UUID.randomUUID().toString();
		change("editor.add_morph", current -> {
			var morph = new Morph();
			morph.id = id;
			current.morphs.add(morph);
		});
		selectedMorph = id;
		changed.run();
	}

	public void assignWeight(float value) {
		var selected = vertices();

		if (bone() == null || selected.isEmpty()) {
			return;
		}

		change("editor.assign_weight", current -> {
			for (int vertex : selected) {
				var influences = current.weights.computeIfAbsent(vertex, _ -> new ArrayList<>());
				influences.removeIf(weight -> weight.boneId.equals(selectedBone));

				if (value > 0) {
					var weight = new Weight();
					weight.boneId = selectedBone;
					weight.value = value;
					influences.add(weight);
				}
			}
		});
	}

	public void setMorphDelta(Vector3f delta) {
		var selected = vertices();

		if (morph() == null || selected.isEmpty()) {
			return;
		}

		change("editor.morph_delta", current -> {
			var morph = current.morphs.stream().filter(value -> value.id.equals(selectedMorph)).findFirst().orElseThrow();

			for (int vertex : selected) {
				morph.deltas.put(vertex, new Vector3f(delta));
			}
		});
	}

	public Transform pose(boolean boneTrack) {
		String target = boneTrack ? selectedBone : selectedPart;
		var poses = boneTrack ? bonePose : partPose;
		var current = poses.get(target);

		if (current != null) {
			return current.copy();
		}

		var fallback = boneTrack ? bone().bind : part().transform;
		var clip = clip();

		if (clip == null) {
			return fallback.copy();
		}

		var tracks = boneTrack ? clip.boneTracks : clip.partTracks;
		float sampledTime = playing && clip.loop ? Math.max(0, time) % clip.duration : Math.clamp(time, 0, clip.duration);
		return PhotonModelSamplerUtil.sampleTransform(tracks.get(target), sampledTime, fallback);
	}

	public void changePose(boolean boneTrack, Consumer<Transform> edit) {
		var before = copyPoses(boneTrack ? bonePose : partPose);
		var after = copyPoses(before);
		var pose = pose(boneTrack);
		edit.accept(pose);
		PhotonModelValidationUtil.transform(pose);
		after.put(boneTrack ? selectedBone : selectedPart, pose);
		history.pushHistory(Component.translatable(PhotonModelTextUtil.key("editor.transform")),
				EditAction.of(() -> replacePoses(boneTrack, after), () -> replacePoses(boneTrack, before)));
	}

	private Map<String, Transform> copyPoses(Map<String, Transform> source) {
		var copy = new LinkedHashMap<String, Transform>();
		source.forEach((id, transform) -> copy.put(id, transform.copy()));
		return copy;
	}

	private void replacePoses(boolean boneTrack, Map<String, Transform> replacement) {
		var target = boneTrack ? bonePose : partPose;
		target.clear();
		target.putAll(copyPoses(replacement));
		revision++;
		changed.run();
	}

	public void clearPose() {
		partPose.clear();
		bonePose.clear();
		morphPose.clear();
		revision++;
		changed.run();
	}

	public void recordTransform(boolean boneTrack) {
		if (clip() == null || (boneTrack ? bone() == null : part() == null)) {
			return;
		}

		var transform = pose(boneTrack);
		String target = boneTrack ? selectedBone : selectedPart;
		change("editor.record_key", current -> {
			var clip = current.clips.stream().filter(value -> value.id.equals(selectedClip)).findFirst().orElseThrow();
			var tracks = boneTrack ? clip.boneTracks : clip.partTracks;
			var keys = tracks.computeIfAbsent(target, _ -> new ArrayList<>());
			keys.removeIf(key -> Float.compare(key.time, time) == 0);
			var key = new TransformKey();
			key.time = time;
			key.transform = transform;
			keys.add(key);
			keys.sort(Comparator.comparingDouble(frame -> frame.time));
			clip.duration = Math.max(clip.duration, time);
		});
	}

	public float morphWeight() {
		var weight = morphPose.get(selectedMorph);

		if (weight != null) {
			return weight;
		}

		var clip = clip();

		if (clip == null) {
			return morph().weight;
		}

		float sampledTime = playing && clip.loop ? Math.max(0, time) % clip.duration : Math.clamp(time, 0, clip.duration);
		return PhotonModelSamplerUtil.sampleValue(clip.morphTracks.get(selectedMorph), sampledTime, morph().weight);
	}

	public void changeMorphPose(float value) {
		if (!Float.isFinite(value)) {
			throw new IllegalArgumentException(Component.translatable(PhotonModelTextUtil.key("morph_weight")).getString());
		}

		String id = selectedMorph;
		Float before = morphPose.get(id);
		history.pushHistory(Component.translatable(PhotonModelTextUtil.key("morph_weight")),
				EditAction.of(() -> replaceMorphPose(id, value), () -> replaceMorphPose(id, before)));
	}

	private void replaceMorphPose(String id, Float value) {
		if (value == null) {
			morphPose.remove(id);
		} else {
			morphPose.put(id, value);
		}

		revision++;
		changed.run();
	}

	public void recordMorph() {
		if (clip() == null || morph() == null) {
			return;
		}

		float weight = morphWeight();
		change("editor.record_key", current -> {
			var clip = current.clips.stream().filter(value -> value.id.equals(selectedClip)).findFirst().orElseThrow();
			var keys = clip.morphTracks.computeIfAbsent(selectedMorph, _ -> new ArrayList<>());
			keys.removeIf(key -> Float.compare(key.time, time) == 0);
			var key = new ValueKey();
			key.time = time;
			key.value = weight;
			keys.add(key);
			keys.sort(Comparator.comparingDouble(frame -> frame.time));
			clip.duration = Math.max(clip.duration, time);
		});
	}

	public void deleteCurrentKeys() {
		if (clip() == null) {
			return;
		}

		change("editor.delete_key", current -> {
			var clip = current.clips.stream().filter(value -> value.id.equals(selectedClip)).findFirst().orElseThrow();
			clip.partTracks.values().forEach(keys -> keys.removeIf(key -> Float.compare(key.time, time) == 0));
			clip.boneTracks.values().forEach(keys -> keys.removeIf(key -> Float.compare(key.time, time) == 0));
			clip.morphTracks.values().forEach(keys -> keys.removeIf(key -> Float.compare(key.time, time) == 0));
			clip.opacityKeys.removeIf(key -> Float.compare(key.time, time) == 0);
		});
	}
}
