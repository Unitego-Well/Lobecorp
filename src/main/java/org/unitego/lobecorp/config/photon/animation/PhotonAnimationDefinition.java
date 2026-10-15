package org.unitego.lobecorp.config.photon.animation;

import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PhotonAnimationDefinition {
	public enum Completion {
		HOLD, RELEASE
	}

	public UUID id = UUID.randomUUID();
	public String name = "";
	public double duration = PhotonAnimationDataUtil.DEFAULT_DURATION;
	/// 默认播放长度随完整内容变化；关闭时使用 duration 重定时。
	public boolean autoDuration = true;
	/// 兼容旧动画对持续模块的隐式开启；新动画遵循原生模块开关。
	public boolean autoEnableModules;
	public boolean loop;
	public int priority;
	public Completion completion = Completion.HOLD;
	private final Timeline timeline = new Timeline();
	public final List<Track> tracks = timeline.tracks();
	public final List<PhotonAnimationBinding> bindings = new ArrayList<>();
	public final List<PhotonAnimationParameter> parameters = new ArrayList<>();
	/// 可选的数值形参 ID，用于独立播放时的时长；受控子片段不采用此计时来源。
	public String durationParameter = "";
	public final List<Marker> markers = new ArrayList<>();

	public record Marker(double time, String name, CompoundTag data) {
		public Marker {
			PhotonAnimationDataUtil.duration(time);
			data = data.copy();
		}
	}

	/// 原生轨道视图共享轨道对象；命名标记由独立事件通道管理。
	public Timeline timeline() {
		return timeline;
	}

	public List<Track> leafTracks(boolean includeMuted) {
		return timeline.leafTracks(includeMuted);
	}

	public double authoredDuration() {
		var end = timeline.getDuration();

		for (var marker : markers) {
			end = Math.max(end, marker.time());
		}

		return end > 0 ? end : PhotonAnimationDataUtil.DEFAULT_DURATION;
	}

	public double playbackDuration() {
		return autoDuration ? authoredDuration() : duration;
	}

	public @Nullable PhotonAnimationParameter parameter(String idOrName) {
		return parameters.stream().filter(value -> value.id.toString().equals(idOrName)
				|| value.name.equals(idOrName)).findFirst().orElse(null);
	}

	public PhotonAnimationBinding binding(UUID target, String property) {
		for (var binding : bindings) {
			if (binding.matches(target, property)) {
				return binding;
			}
		}

		var binding = new PhotonAnimationBinding();
		binding.target = target;
		binding.property = property;
		bindings.add(binding);
		return binding;
	}

	public CompoundTag write(HolderLookup.Provider provider) {
		var tag = new CompoundTag();
		tag.putString("id", id.toString());
		tag.putString("name", name);
		tag.putDouble("duration", duration);
		tag.putBoolean("autoDuration", autoDuration);
		tag.putBoolean("autoEnableModules", autoEnableModules);
		tag.putBoolean("loop", loop);
		tag.putInt("priority", priority);
		tag.putString("completion", completion.name());
		tag.putString("durationParameter", durationParameter);
		var parameterTags = new ListTag();
		parameters.forEach(parameter -> parameterTags.add(parameter.write()));
		tag.put("parameters", parameterTags);
		var trackTags = new ListTag();
		tracks.forEach(track -> trackTags.add(Timeline.writeTrack(track, provider)));
		tag.put("tracks", trackTags);
		var bindingTags = new ListTag();
		bindings.forEach(binding -> bindingTags.add(binding.write()));
		tag.put("bindings", bindingTags);
		var markerTags = new ListTag();

		for (var marker : markers) {
			var entry = new CompoundTag();
			entry.putDouble("time", marker.time());
			entry.putString("name", marker.name());
			entry.put("data", marker.data().copy());
			markerTags.add(entry);
		}

		tag.put("markers", markerTags);
		return tag;
	}

	public static PhotonAnimationDefinition read(HolderLookup.Provider provider, CompoundTag tag) {
		return read(provider, tag, tag.contains("autoDuration") ? PhotonAnimationDataUtil.VERSION : PhotonAnimationDataUtil.LEGACY_VERSION);
	}

	public static PhotonAnimationDefinition read(HolderLookup.Provider provider, CompoundTag tag, int version) {
		var value = new PhotonAnimationDefinition();
		value.id = PhotonAnimationDataUtil.uuid(tag, "id");
		value.name = tag.getStringOr("name", "");
		value.duration = PhotonAnimationDataUtil.duration(tag.getDoubleOr("duration", PhotonAnimationDataUtil.DEFAULT_DURATION));
		value.autoDuration = tag.getBooleanOr("autoDuration", version >= PhotonAnimationDataUtil.AUTO_DURATION_VERSION);
		value.autoEnableModules = tag.getBooleanOr("autoEnableModules", version < PhotonAnimationDataUtil.AUTO_DURATION_VERSION);
		value.loop = tag.getBooleanOr("loop", false);
		value.priority = tag.getIntOr("priority", 0);
		value.completion = PhotonAnimationDataUtil.strictEnumeration(tag, "completion", Completion.HOLD);
		value.durationParameter = tag.getStringOr("durationParameter", "");

		for (var entry : tag.getListOrEmpty("parameters")) {
			if (entry instanceof CompoundTag parameter) {
				value.parameters.add(PhotonAnimationParameter.read(parameter));
			}
		}

		for (var entry : tag.getListOrEmpty("tracks")) {
			if (entry instanceof CompoundTag trackTag) {
				var track = readTrack(provider, trackTag);

				if (track != null) {
					value.tracks.add(track);
				}
			}
		}

		for (var entry : tag.getListOrEmpty("bindings")) {
			if (entry instanceof CompoundTag bindingTag) {
				value.bindings.add(PhotonAnimationBinding.read(bindingTag));
			}
		}

		for (var entry : tag.getListOrEmpty("markers")) {
			if (entry instanceof CompoundTag markerTag) {
				value.markers.add(new Marker(markerTag.getDoubleOr("time", 0),
						markerTag.getStringOr("name", ""), markerTag.getCompoundOrEmpty("data")));
			}
		}

		return value;
	}

	private static Track readTrack(HolderLookup.Provider provider, CompoundTag tag) {
		if (tag.contains("type")) {
			return Timeline.readTrack(provider, tag);
		}

		var track = new AnimationTrack();
		track.setType(PhotonRegistries.TIMELINE_TRACKS.get("animation"));
		track.readData(provider, tag);
		return track;
	}
}
