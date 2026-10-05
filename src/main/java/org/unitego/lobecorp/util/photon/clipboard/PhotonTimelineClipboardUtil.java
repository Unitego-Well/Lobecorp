package org.unitego.lobecorp.util.photon.clipboard;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineEditAccess;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

public class PhotonTimelineClipboardUtil {
	/**
	 * 限制嵌套轨道层数，与一次操作的条目上限共同约束外部内容。
	 */
	private static final int MAX_TRACK_DEPTH = 64;

	/**
	 * 记录复制时的轨道位置，粘贴时仍需核对类型、目标及名称。
	 */
	private record ClipBatch(Track track, List<Clip> clips) {
	}

	public static void copyTracks(TimelineContext ctx, Collection<Track> selected) {
		var runtime = ctx.runtime();
		if (runtime == null)
			return;
		var data = new CompoundTag();
		var entries = new ListTag();
		for (var track : selected) {
			if (selected.stream().anyMatch(parent -> parent != track && Timeline.isDescendant(parent, track)))
				continue;
			entries.add(Timeline.writeTrack(track, Platform.getFrozenRegistry()));
		}
		if (entries.isEmpty())
			return;
		data.put("tracks", entries);
		data.put("objects", references(runtime));
		PhotonSystemClipboardUtil.write(PhotonSystemClipboardUtil.TRACKS, data);
	}

	public static void copyClips(TimelineContext ctx) {
		var runtime = ctx.runtime();
		if (runtime == null || ctx.selectedClips().isEmpty())
			return;
		var source = runtime.fxData.timeline().leafTracks(true);
		var entries = new ListTag();
		double anchor = ctx.selectedClips().stream().mapToDouble(Clip::start).min().orElseThrow();
		for (int index = 0; index < source.size(); index++) {
			var track = source.get(index);
			var clips = track.clips().stream().filter(ctx.selectedClips()::contains).toList();
			if (clips.isEmpty())
				continue;
			var copy = track.copy();
			copy.clips().clear();
			for (var clip : clips)
				copy.clips().add(clip.copy().start(clip.start() - anchor));
			var entry = Timeline.writeTrack(copy, Platform.getFrozenRegistry());
			entry.putInt("lane", index);
			entries.add(entry);
		}
		var data = new CompoundTag();
		data.put("tracks", entries);
		data.put("objects", references(runtime));
		PhotonSystemClipboardUtil.write(PhotonSystemClipboardUtil.CLIPS, data);
	}

	public static CompoundTag references(FXRuntime runtime) {
		var result = new CompoundTag();
		for (var entry : runtime.objects.entrySet()) {
			var ref = new CompoundTag();
			ref.putString("name", entry.getValue().getName());
			ref.putString("type", entry.getValue().getFXObjectType().name());
			result.put(entry.getKey().toString(), ref);
		}
		return result;
	}

	public static UUID resolve(FXRuntime runtime, UUID source, CompoundTag references, Map<UUID, UUID> resolved) {
		if (source == null)
			return null;
		if (resolved.containsKey(source))
			return resolved.get(source);
		var ref = references.getCompound(source.toString()).orElseThrow(IllegalArgumentException::new);
		var exact = runtime.objects.get(source);
		if (matches(exact, ref)) {
			resolved.put(source, source);
			return source;
		}
		var candidates = runtime.objects.values().stream().filter(object -> matches(object, ref)).toList();
		if (candidates.size() != 1)
			throw new IllegalArgumentException();
		UUID target = candidates.getFirst().transform().id();
		resolved.put(source, target);
		return target;
	}

	private static boolean matches(IFXObject object, CompoundTag ref) {
		return object != null && object.getName().equals(ref.getStringOr("name", ""))
				&& object.getFXObjectType().name().equals(ref.getStringOr("type", ""));
	}

	private static void validateEntry(CompoundTag entry, int depth, int[] count) {
		if (depth > MAX_TRACK_DEPTH || ++count[0] > PhotonSystemClipboardUtil.MAX_ITEMS
				|| PhotonRegistries.TIMELINE_TRACKS.get(entry.getStringOr("type", "")) == null)
			throw new IllegalArgumentException();
		var data = entry.getCompound("data").orElseThrow(IllegalArgumentException::new);
		for (var child : data.getListOrEmpty("children")) {
			if (!(child instanceof CompoundTag tag))
				throw new IllegalArgumentException();
			validateEntry(tag, depth + 1, count);
		}
	}

	private static void remap(TimelineContext ctx, Track track, CompoundTag refs, Map<UUID, UUID> resolved) {
		var runtime = ctx.runtime();
		track.targetId(resolve(runtime, track.targetId(), refs, resolved));
		if (track instanceof TrackGroup group) {
			for (var child : group.children())
				remap(ctx, child, refs, resolved);
		}
		if (track instanceof AnimationTrack animation) {
			for (var property : animation.properties()) {
				for (int axis = 0; axis < property.channelCount(); axis++) {
					for (int i = 0; i < property.keyCount(axis); i++) {
						var point = property.key(axis, i);
						if (!Float.isFinite(point.x) || point.x < 0 || !Float.isFinite(point.y))
							throw new IllegalArgumentException();
					}
				}
			}
		}
		for (var clip : track.clips()) {
			clip.targetId(resolve(runtime, clip.targetId(), refs, resolved));
			if (!Double.isFinite(clip.start()) || clip.start() < 0 || !Double.isFinite(clip.duration())
					|| clip.duration() <= 0 || !Double.isFinite(clip.end()) || !Float.isFinite(clip.speed()))
				throw new IllegalArgumentException();
		}
	}

	private static void validateDecoded(Track track, CompoundTag entry) {
		var data = entry.getCompoundOrEmpty("data");
		if (track.clips().size() != data.getListOrEmpty("clips").size())
			throw new IllegalArgumentException();
		if (track instanceof AnimationTrack animation
				&& animation.properties().size() != data.getListOrEmpty("properties").size())
			throw new IllegalArgumentException();
		if (track instanceof TrackGroup group) {
			var children = data.getListOrEmpty("children");
			if (group.children().size() != children.size())
				throw new IllegalArgumentException();
			for (int i = 0; i < children.size(); i++)
				validateDecoded(group.children().get(i), children.getCompoundOrEmpty(i));
		}
	}

	private static List<Track> decode(TimelineContext ctx, CompoundTag data, Track selected, boolean clips) {
		if (ctx.runtime() == null)
			throw new IllegalArgumentException();
		var entries = data.getListOrEmpty("tracks");
		if (entries.isEmpty() || entries.size() > PhotonSystemClipboardUtil.MAX_ITEMS)
			throw new IllegalArgumentException();
		var result = new ArrayList<Track>();
		var resolved = new HashMap<UUID, UUID>();
		if (clips && entries.size() == 1 && selected != null && selected.targetId() != null) {
			var entry = (CompoundTag) entries.getFirst();
			var source = Timeline.readTrack(Platform.getFrozenRegistry(), entry);
			if (source != null && source.targetId() != null && source.name().equals(selected.name()))
				resolved.put(source.targetId(), selected.targetId());
		}
		int[] count = {0};
		for (var value : entries) {
			if (!(value instanceof CompoundTag entry))
				throw new IllegalArgumentException();
			validateEntry(entry, 0, count);
			var track = Timeline.readTrack(Platform.getFrozenRegistry(), entry);
			if (track == null)
				throw new IllegalArgumentException();
			validateDecoded(track, entry);
			remap(ctx, track, data.getCompoundOrEmpty("objects"), resolved);
			result.add(track);
		}
		return result;
	}

	public static void paste(TimelineContext ctx, Track selected, int index, boolean clips) {
		try {
			var data = PhotonSystemClipboardUtil.read(clips ? PhotonSystemClipboardUtil.CLIPS : PhotonSystemClipboardUtil.TRACKS);
			if (data == null)
				throw new IllegalArgumentException();
			var decoded = decode(ctx, data, selected, clips);
			if (clips)
				pasteClips(ctx, selected, data, decoded);
			else {
				var tracks = Objects.requireNonNull(ctx.runtime()).fxData.timeline().tracks();
				int at = Math.clamp(index, 0, tracks.size());
				ctx.pushEdit("photon.gui.editor.timeline.add_track",
						() -> {
							tracks.addAll(Math.min(at, tracks.size()), decoded);
							refresh(ctx);
						},
						() -> {
							tracks.removeAll(decoded);
							refresh(ctx);
						});
			}
		} catch (RuntimeException ignored) {
			invalid(ctx);
		}
	}

	private static void pasteClips(TimelineContext ctx, Track selected, CompoundTag data, List<Track> decoded) {
		var timeline = Objects.requireNonNull(ctx.runtime()).fxData.timeline();
		var lanes = timeline.leafTracks(true);
		var entries = data.getListOrEmpty("tracks");
		var batches = new ArrayList<ClipBatch>();
		var destinations = new LinkedHashMap<Track, List<Clip>>();
		double playhead = Math.max(0, ctx.currentTimeTicks());
		for (int i = 0; i < decoded.size(); i++) {
			var source = decoded.get(i);
			Track destination = decoded.size() == 1 && selected != null ? selected : null;
			if (destination == null) {
				int lane = ((CompoundTag) entries.get(i)).getIntOr("lane", -1);
				if (lane >= 0 && lane < lanes.size() && sameLane(source, lanes.get(lane)))
					destination = lanes.get(lane);
				else {
					var candidates = lanes.stream().filter(track -> sameLane(source, track)).toList();
					if (candidates.size() != 1)
						throw new IllegalArgumentException();
					destination = candidates.getFirst();
				}
			}
			if (destination.lock() || timeline.parentListOf(destination) == null || !destination.name().equals(source.name()))
				throw new IllegalArgumentException();
			var access = (PhotonTimelineEditAccess) ctx;
			if (!(access.lobecorp$trackEditor(destination) instanceof ClipTrackEditor editor))
				throw new IllegalArgumentException();
			var pending = destinations.computeIfAbsent(destination, _ -> new ArrayList<>());
			for (var clip : source.clips()) {
				clip.start(playhead + clip.start());
				if (!Double.isFinite(clip.end()) || destination.clips().stream().anyMatch(other -> editor.clipsConflict(clip, other))
						|| pending.stream().anyMatch(other -> editor.clipsConflict(clip, other)))
					throw new IllegalArgumentException();
				pending.add(clip);
			}
		}
		destinations.forEach((track, values) -> batches.add(new ClipBatch(track, List.copyOf(values))));
		var selection = batches.stream().flatMap(batch -> batch.clips().stream()).toList();
		if (selection.isEmpty())
			throw new IllegalArgumentException();
		ctx.pushEdit("photon.gui.editor.timeline.add_clip", () -> {
			batches.forEach(batch -> batch.track().clips().addAll(batch.clips()));
			ctx.requestRebuild();
			ctx.selectClips(selection, false);
			ctx.refreshPreview();
		}, () -> {
			batches.forEach(batch -> batch.track().clips().removeAll(batch.clips()));
			refresh(ctx);
		});
	}

	private static boolean sameLane(Track source, Track target) {
		return source.name().equals(target.name()) && Objects.equals(source.targetId(), target.targetId())
				&& source.displayName().equals(target.displayName());
	}

	private static void refresh(TimelineContext ctx) {
		ctx.requestRebuild();
		ctx.refreshPreview();
	}

	public static void invalid(TimelineContext ctx) {
		Dialog.showNotification(PhotonEditorTextUtil.key("clipboard"), PhotonEditorTextUtil.key("clipboard_invalid"), null).show(ctx.editor());
	}
}
