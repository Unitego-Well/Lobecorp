package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.AudioTrack;
import com.lowdragmc.photon.client.fx.timeline.PostProcessTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.util.photon.playback.PhotonTimelineDecodeUtil;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Binding;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTimelineProgram;

/// 每个运行实例准备一次原生轨道与属性；未知轨道/被SDK跳过的属性必须失败，不能空播。
public class PhotonNamedPlan {
	private static final AtomicLong TRACK_DECODES = new AtomicLong();

	public static long trackDecodeCount() {
		return TRACK_DECODES.get();
	}

	public record Property(FXObject target, AnimatedProperty value, Binding binding) {
	}

	public record ModelClipTarget(PhotonModelClipTrack track, UUID targetId) {
	}

	final Definition definition;
	final Timeline timeline;
	final List<Track> leaves;
	final List<Property> properties;
	private final List<ModelClipTarget> modelClips;
	private final List<PhotonChildTrack> childTracks;
	private final List<SpeedTrack> speedTracks;
	private final List<SignalTrack> signalTracks;
	private final boolean hasAudio;
	private final boolean hasPostProcess;
	final double authoredDuration;
	final int unusedBindings;

	public PhotonNamedPlan(HolderLookup.Provider provider, FXRuntime runtime, Definition definition) {
		this(provider, runtime, definition, false);
	}

	public PhotonNamedPlan(HolderLookup.Provider provider, FXRuntime runtime, Definition definition, boolean projectPreview) {
		this(runtime, definition, readTimeline(provider, definition), projectPreview, null);
	}

	public PhotonNamedPlan(FXRuntime runtime, Template template) {
		this(runtime, template.definition, template.program.instantiate(), false, template);
	}

	private PhotonNamedPlan(FXRuntime runtime, Definition definition, Timeline timeline, boolean projectPreview, @Nullable Template template) {
		this.definition = definition;
		this.timeline = timeline;
		leaves = List.copyOf(timeline.leafTracks(false));
		var prepared = new ArrayList<Property>();
		var preparedModelClips = new ArrayList<ModelClipTarget>();
		var preparedChildTracks = new ArrayList<PhotonChildTrack>();
		var preparedSpeedTracks = new ArrayList<SpeedTrack>();
		var preparedSignalTracks = new ArrayList<SignalTrack>();
		boolean audio = false;
		boolean postProcess = false;

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof AnimationTrack animation) {
				if (!(runtime.objects.get(track.targetId()) instanceof FXObject target)) {
					throw new IllegalArgumentException("命名属性轨道目标不存在：" + track.targetId());
				}

				for (var property : animation.properties()) {
					var bindings = template == null ? definition.bindings().stream().filter(value -> value.target().equals(track.targetId())
							&& value.property().equals(property.type().key())).toList()
							: template.bindings.getOrDefault(new BindingKey(track.targetId(), property.type().key()), List.of());

					if (bindings.isEmpty()) {
						bindings = List.of(new Binding(track.targetId(), property.type().key(), Blend.OVERRIDE, ValueSource.CURVE, "",
								-1, 1, 0, "", ""));
					}

					new PhotonPropertyTarget(target, property.type());
					boolean discrete = property.type().stepped() && (!(property.type() instanceof ConfigPropertyType config)
							|| config.valueType() != ConfigValueType.INT);

					for (var binding : bindings) {
						if (!discrete || binding.blend() == Blend.OVERRIDE) {
							prepared.add(new Property(target, property, binding));
						}
					}
				}
			} else if (track instanceof PhotonCallTrack && !projectPreview || !(track instanceof ActivatorTrack || track instanceof ControlTrack
					|| track instanceof PhotonModelClipTrack || track instanceof PhotonChildTrack
					|| track instanceof SpeedTrack || track instanceof SignalTrack || track instanceof AudioTrack || track instanceof PostProcessTrack)) {
				throw new IllegalArgumentException("命名播放尚未支持该轨道：" + track.name());
			} else if (track instanceof PhotonModelClipTrack modelClip) {
				preparedModelClips.add(new ModelClipTarget(modelClip, modelClip.targetId()));
			} else if (track instanceof PhotonChildTrack child) {
				preparedChildTracks.add(child);
			} else if (track instanceof SpeedTrack speed) {
				preparedSpeedTracks.add(speed);
			} else if (track instanceof SignalTrack signal) {
				preparedSignalTracks.add(signal);
			} else if (track instanceof AudioTrack) {
				audio = true;
			} else if (track instanceof PostProcessTrack) {
				postProcess = true;
			}
		}

		properties = List.copyOf(prepared);
		modelClips = List.copyOf(preparedModelClips);
		childTracks = List.copyOf(preparedChildTracks);
		speedTracks = List.copyOf(preparedSpeedTracks);
		signalTracks = List.copyOf(preparedSignalTracks);
		hasAudio = audio;
		hasPostProcess = postProcess;

		int unused = 0;

		for (var binding : definition.bindings()) {
			if (!(runtime.objects.get(binding.target()) instanceof FXObject target)) {
				throw new IllegalArgumentException("命名绑定对象引用失效：" + binding.target());
			}

			if (properties.stream().noneMatch(property -> property.target().id().equals(binding.target())
					&& property.value().type().key().equals(binding.property()))) {
				boolean known = target.getFXObjectType().animatableProperties(target).stream()
						.anyMatch(type -> type.key().equals(binding.property()));

				if (!known && binding.property().startsWith("config.")) {
					known = target.getFXObjectType().resolveRuntimeBinding(target, binding.property().substring("config.".length())) != null;
				}

				if (!known) {
					throw new IllegalArgumentException("未使用的命名绑定属性也必须有效：" + binding.property());
				}

				// 绑定是已有轨道属性的采样规则，不会单独生成轨道或写入目标。
				unused++;
			}
		}

		unusedBindings = unused;

		double duration = timeline.getDuration();

		for (var marker : definition.markers()) {
			duration = Math.max(duration, marker.time());
		}

		authoredDuration = duration == 0 ? PhotonNamedLibrary.DEFAULT_DURATION : duration;
	}

	private record BindingKey(UUID target, String property) {
	}

	public List<ModelClipTarget> modelClips() {
		return modelClips;
	}

	public List<PhotonChildTrack> childTracks() {
		return childTracks;
	}

	public List<SpeedTrack> speedTracks() {
		return speedTracks;
	}

	public List<SignalTrack> signalTracks() {
		return signalTracks;
	}

	public boolean hasAudio() {
		return hasAudio;
	}

	public boolean hasPostProcess() {
		return hasPostProcess;
	}

	/// 共享未绑定的定义和轨道初始化计划；Property 始终在实例构造时生成。
	public static final class Template {
		private final Definition definition;
		private final PhotonTimelineProgram program;
		private final Map<BindingKey, List<Binding>> bindings;

		private Template(Definition definition, PhotonTimelineProgram program) {
			this.definition = definition;
			this.program = program;
			var indexed = new LinkedHashMap<BindingKey, List<Binding>>();

			for (var binding : definition.bindings()) {
				indexed.computeIfAbsent(new BindingKey(binding.target(), binding.property()), _ -> new ArrayList<>()).add(binding);
			}

			indexed.replaceAll((_, values) -> List.copyOf(values));
			bindings = Map.copyOf(indexed);
		}

		public int decodedTracks() {
			return program.trackCount();
		}

		public List<String> fallbackReasons() {
			return program.fallbackReasons();
		}
	}

	public static Template compile(HolderLookup.Provider provider, Definition definition) {
		var timeline = readTimeline(provider, definition);

		for (var track : timeline.leafTracks(false)) {
			if (!track.mute() && (track instanceof PhotonCallTrack || !(track instanceof AnimationTrack || track instanceof ActivatorTrack
					|| track instanceof ControlTrack || track instanceof SpeedTrack || track instanceof PhotonModelClipTrack || track instanceof PhotonChildTrack
					|| track instanceof SignalTrack || track instanceof AudioTrack || track instanceof PostProcessTrack))) {
				throw new IllegalArgumentException("命名播放尚未支持该轨道：" + track.name());
			}
		}

		return new Template(definition, PhotonTimelineProgram.compilePrepared(timeline, provider));
	}

	private static Timeline readTimeline(HolderLookup.Provider provider, Definition definition) {
		var tracks = definition.tracks();
		var sourceTracks = new ListTag();
		sourceTracks.addAll(tracks);
		PhotonTimelineDecodeUtil.checkTracks(sourceTracks);
		var result = new Timeline();

		for (var source : tracks) {
			result.tracks().add(read(provider, source));
		}

		return result;
	}

	private static Track read(HolderLookup.Provider provider, CompoundTag source) {
		var type = PhotonRegistries.TIMELINE_TRACKS.get(source.getStringOr("type", ""));

		if (type == null) {
			throw new IllegalArgumentException("命名轨道类型未注册：" + source.getStringOr("type", ""));
		}

		var track = type.create();
		var data = source.getCompoundOrEmpty("data");
		var known = data.copy();

		if (track instanceof TrackGroup) {
			known.remove("children");
		}

		// 内存计划直接进入已登记类型；文件入口及保存保护仍由独立门禁负责。
		TRACK_DECODES.incrementAndGet();
		track.readData(provider, known);

		if (track instanceof ActivatorTrack && !data.contains(PhotonActivationOptions.TAG)) {
			((PhotonActivationTrackAccess) track).lobecorp$activationOptions(PhotonActivationOptions.LEGACY);
		}

		if (track instanceof AnimationTrack animation && animation.properties().size() != data.getListOrEmpty("properties").size()) {
			throw new IllegalArgumentException("命名轨道含未解析的属性。");
		}

		if (track instanceof TrackGroup group) {
			group.children().clear();

			for (var value : data.getListOrEmpty("children")) {
				if (!(value instanceof CompoundTag child)) {
					throw new IllegalArgumentException("命名轨道组包含无效子轨道。");
				}

				group.children().add(read(provider, child));
			}
		}

		return track;
	}
}
