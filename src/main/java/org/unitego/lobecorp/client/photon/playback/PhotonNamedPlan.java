package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.*;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTimelineProgram;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Binding;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ValueSource;
import org.unitego.lobecorp.util.photon.playback.PhotonTimelineDecodeUtil;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/// 每个运行实例准备一次原生轨道与属性；未知轨道/被SDK跳过的属性必须失败，不能空播。
public class PhotonNamedPlan {
	private static final AtomicLong TRACK_DECODES = new AtomicLong();
	final Definition definition;
	final Timeline timeline;
	final List<Track> leaves;
	final List<Property> properties;
	final double authoredDuration;
	final int unusedBindings;
	private final List<PhotonChildTrack> childTracks;
	private final List<SpeedTrack> speedTracks;
	private final List<SignalTrack> signalTracks;
	private final boolean hasAudio;
	private final boolean hasPostProcess;
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
		var preparedChildTracks = new ArrayList<PhotonChildTrack>();
		var preparedSpeedTracks = new ArrayList<SpeedTrack>();
		var preparedSignalTracks = new ArrayList<SignalTrack>();
		boolean audio = false;
		boolean postProcess = false;
		boolean staticDescriptions = template != null && template.staticContributions;

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof AnimationTrack animation) {
				if (!(runtime.objects.get(track.targetId()) instanceof FXObject target)) {
					throw new IllegalArgumentException("命名属性轨道目标不存在：" + track.targetId());
				}

				for (var property : animation.properties()) {
					List<Binding> bindings = null;

					if (staticDescriptions) {
						bindings = template.contributions.get(new PropertyKey(track.targetId(), property.type().key(),
								isDiscrete(property.type())));
						if (bindings == null) {
							staticDescriptions = false;
						}
					}

					if (bindings != null) {
						new PhotonPropertyTarget(target, property.type());
						if (!target.id().equals(track.targetId())) {
							staticDescriptions = false;
						}

						for (var binding : bindings) {
							prepared.add(new Property(target, property, binding));
						}
						continue;
					}

					bindings = selectBindings(definition, animation, property, template);
					if (bindings.isEmpty()) {
						bindings = List.of(new Binding(track.targetId(), property.type().key(), Blend.OVERRIDE, ValueSource.CURVE, "",
								-1, 1, 0, "", ""));
					}

					new PhotonPropertyTarget(target, property.type());
					boolean discrete = isDiscrete(property.type());
					for (var binding : bindings) {
						if (!discrete || binding.blend() == Blend.OVERRIDE) {
							prepared.add(new Property(target, property, binding));
						}
					}
				}
			} else if (track instanceof PhotonCallTrack && !projectPreview || !(track instanceof ActivatorTrack || track instanceof ControlTrack
					|| track instanceof PhotonChildTrack
					|| track instanceof SpeedTrack || track instanceof SignalTrack || track instanceof AudioTrack || track instanceof PostProcessTrack)) {
				throw new IllegalArgumentException("命名播放尚未支持该轨道：" + track.name());
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
		childTracks = List.copyOf(preparedChildTracks);
		speedTracks = List.copyOf(preparedSpeedTracks);
		signalTracks = List.copyOf(preparedSignalTracks);
		hasAudio = audio;
		hasPostProcess = postProcess;

		int unused = 0;
		boolean staticUsed = template != null && template.staticContributions && staticDescriptions;

		for (var binding : definition.bindings()) {
			if (!(runtime.objects.get(binding.target()) instanceof FXObject target)) {
				throw new IllegalArgumentException("命名绑定对象引用失效：" + binding.target());
			}

			boolean used = staticUsed
					? template.usedKeys.contains(new BindingKey(binding.target(), binding.property()))
					: properties.stream().anyMatch(property -> property.target().id().equals(binding.target())
					&& property.value().type().key().equals(binding.property()));

			if (!used) {
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

	public static long trackDecodeCount() {
		return TRACK_DECODES.get();
	}

	private static boolean isDiscrete(AnimatedPropertyType type) {
		return type.stepped() && (!(type instanceof ConfigPropertyType config) || config.valueType() != ConfigValueType.INT);
	}

	private static List<Binding> filterBindings(List<Binding> source, UUID target, String property, boolean discrete) {
		var bindings = source.isEmpty()
				? List.of(new Binding(target, property, Blend.OVERRIDE, ValueSource.CURVE, "", -1, 1, 0, "", ""))
				: source;
		var filtered = new ArrayList<Binding>(bindings.size());

		for (var binding : bindings) {
			if (!discrete || binding.blend() == Blend.OVERRIDE) {
				filtered.add(binding);
			}
		}

		return List.copyOf(filtered);
	}

	private static List<Binding> selectBindings(Definition definition, AnimationTrack track, AnimatedProperty property,
	                                            @Nullable Template template) {
		if (template == null) {
			return definition.bindings().stream().filter(value -> value.target().equals(track.targetId())
					&& value.property().equals(property.type().key())).toList();
		}

		return template.bindings.getOrDefault(new BindingKey(track.targetId(), property.type().key()), List.of());
	}

	public static Template compile(HolderLookup.Provider provider, Definition definition) {
		var timeline = readTimeline(provider, definition);

		for (var track : timeline.leafTracks(false)) {
			if (!track.mute() && (track instanceof PhotonCallTrack || !(track instanceof AnimationTrack || track instanceof ActivatorTrack
					|| track instanceof ControlTrack || track instanceof SpeedTrack || track instanceof PhotonChildTrack
					|| track instanceof SignalTrack || track instanceof AudioTrack || track instanceof PostProcessTrack))) {
				throw new IllegalArgumentException("命名播放尚未支持该轨道：" + track.name());
			}
		}

		return new Template(definition, PhotonTimelineProgram.compilePrepared(timeline, provider), timeline);
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

	public record Property(FXObject target, AnimatedProperty value, Binding binding) {
	}


	private record BindingKey(UUID target, String property) {
	}

	private record PropertyKey(UUID target, String property, boolean discrete) {
	}

	/// 共享未绑定的定义和轨道初始化计划；Property 始终在实例构造时生成。
	public static final class Template {
		private final Definition definition;
		private final PhotonTimelineProgram program;
		private final Map<BindingKey, List<Binding>> bindings;
		private final Map<PropertyKey, List<Binding>> contributions;
		private final Set<BindingKey> usedKeys;
		private final boolean staticContributions;

		private Template(Definition definition, PhotonTimelineProgram program, Timeline authored) {
			this.definition = definition;
			this.program = program;
			var indexed = new LinkedHashMap<BindingKey, List<Binding>>();

			for (var binding : definition.bindings()) {
				indexed.computeIfAbsent(new BindingKey(binding.target(), binding.property()), _ -> new ArrayList<>()).add(binding);
			}

			indexed.replaceAll((_, values) -> List.copyOf(values));
			bindings = Map.copyOf(indexed);
			var preparedContributions = new LinkedHashMap<PropertyKey, List<Binding>>();
			var preparedUsedKeys = new HashSet<BindingKey>();
			boolean staticReady = program.fallbackReasons().isEmpty();

			if (staticReady) {
				for (var track : authored.leafTracks(false)) {
					if (track.mute() || !(track instanceof AnimationTrack animation)) {
						continue;
					}

					for (var property : animation.properties()) {
						var key = new BindingKey(track.targetId(), property.type().key());
						boolean discrete = isDiscrete(property.type());
						var filtered = filterBindings(bindings.getOrDefault(key, List.of()), track.targetId(),
								property.type().key(), discrete);

						preparedContributions.computeIfAbsent(new PropertyKey(track.targetId(), property.type().key(), discrete),
								_ -> filtered);

						if (!filtered.isEmpty()) {
							preparedUsedKeys.add(key);
						}
					}
				}
			}

			contributions = staticReady ? Map.copyOf(preparedContributions) : Map.of();
			usedKeys = staticReady ? Set.copyOf(preparedUsedKeys) : Set.of();
			staticContributions = staticReady;
		}

		public int decodedTracks() {
			return program.trackCount();
		}

		public List<String> fallbackReasons() {
			return program.fallbackReasons();
		}
	}
}
