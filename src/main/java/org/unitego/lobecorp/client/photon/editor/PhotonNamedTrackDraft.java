package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonTimelineDecodeUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonTrackCopyUtil;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;


/// 原生编辑器只接触独立轨道树；原始包装和片段按对象归属合并，排序不搬错未知字段。
public class PhotonNamedTrackDraft {
	private final Timeline timeline = new Timeline();
	private final boolean projectTest;
	private final Map<Track, CompoundTag> originalTracks = new IdentityHashMap<>();
	private final Map<Clip, CompoundTag> originalClips = new IdentityHashMap<>();
	private final Map<Clip, CompoundTag> clipBaselines = new IdentityHashMap<>();
	private final Map<Track, CompoundTag> baselines = new IdentityHashMap<>();

	public PhotonNamedTrackDraft(ListTag source, boolean projectTest) {
		this.projectTest = projectTest;
		read(timeline.tracks(), source);
	}

	public Timeline timeline() {
		return timeline;
	}

	public Track duplicate(Track track) {
		var copied = new ArrayList<Track>();
		var entries = new ListTag();
		entries.add(PhotonTrackCopyUtil.copy((CompoundTag) write(List.of(track)).getFirst()));
		read(copied, entries);
		return copied.getFirst();
	}

	private void read(List<Track> destination, ListTag source) {
		PhotonTimelineDecodeUtil.checkTracks(source);
		for (var raw : source) {
			if (!(raw instanceof CompoundTag entry)) {
				throw new IllegalArgumentException("制作轨道必须为复合标签。");
			}

			PhotonEditorProjectGuardUtil.requireSupportedAuthoringTrack(entry, projectTest);
			var track = readTrack(entry);
			destination.add(track);
			remember(track, entry);
		}
	}

	private Track readTrack(CompoundTag entry) {
		var type = PhotonRegistries.TIMELINE_TRACKS.get(entry.getStringOr("type", ""));
		if (type == null) {
			throw new IllegalArgumentException("制作轨道类型未注册：" + entry.getStringOr("type", ""));
		}

		var track = type.create();
		var data = entry.getCompoundOrEmpty("data");
		var known = data.copy();
		if (track instanceof TrackGroup) {
			known.remove("children");
		}

		// 草稿已按编辑目标校验；组内读取不能丢失工程测试调用轨道的作用域。
		track.readData(Platform.getFrozenRegistry(), known);
		if (track instanceof TrackGroup group) {
			for (var raw : data.getListOrEmpty("children")) {
				if (!(raw instanceof CompoundTag child)) {
					throw new IllegalArgumentException("制作轨道必须为复合标签。");
				}

				group.children().add(readTrack(child));
			}
		}

		return track;
	}

	private void remember(Track track, CompoundTag entry) {
		originalTracks.put(track, entry.copy());
		baselines.put(track, track.writeData(Platform.getFrozenRegistry()));
		var rawClips = entry.getCompoundOrEmpty("data").getListOrEmpty("clips");
		if (track.clips().size() != rawClips.size()) {
			throw new IllegalArgumentException("原生读取改变了片段数量，拒绝损失原始数据。");
		}

		for (int index = 0; index < rawClips.size(); index++) {
			if (!(rawClips.get(index) instanceof CompoundTag clip)) {
				throw new IllegalArgumentException("制作片段必须为复合标签。");
			}

			originalClips.put(track.clips().get(index), clip.copy());
			clipBaselines.put(track.clips().get(index), ((CompoundTag) baselines.get(track).getListOrEmpty("clips").get(index)).copy());
		}

		if (track instanceof TrackGroup group) {
			var children = entry.getCompoundOrEmpty("data").getListOrEmpty("children");
			if (group.children().size() != children.size()) {
				throw new IllegalArgumentException("原生读取改变了子轨道数量。");
			}

			for (int index = 0; index < children.size(); index++) {
				remember(group.children().get(index), (CompoundTag) children.get(index));
			}
		}
	}

	public ListTag write() {
		return write(timeline.tracks());
	}

	private ListTag write(List<Track> tracks) {
		var result = new ListTag();
		for (var track : tracks) {
			var entry = originalTracks.getOrDefault(track, new CompoundTag()).copy();
			entry.putString("type", track.name());
			var current = track.writeData(Platform.getFrozenRegistry());
			var before = baselines.getOrDefault(track, new CompoundTag());
			var data = PhotonLosslessNbtUtil.changed(entry.getCompoundOrEmpty("data"), before, current);
			var serializedClips = current.getListOrEmpty("clips");
			var clips = new ListTag();
			for (int index = 0; index < track.clips().size(); index++) {
				var raw = originalClips.getOrDefault(track.clips().get(index), new CompoundTag());
				clips.add(PhotonLosslessNbtUtil.changed(raw, clipBaselines.getOrDefault(track.clips().get(index), new CompoundTag()),
						(CompoundTag) serializedClips.get(index)));
			}

			data.put("clips", clips);
			if (track instanceof TrackGroup group) {
				data.put("children", write(group.children()));
			}

			entry.put("data", data);
			result.add(entry);
		}

		return result;
	}

}
