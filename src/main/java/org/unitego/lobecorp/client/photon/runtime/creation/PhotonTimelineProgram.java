package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.AudioTrack;
import com.lowdragmc.photon.client.fx.timeline.PostProcessTrack;
import com.lowdragmc.photon.client.fx.timeline.PostProcessClip;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.RotationAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.RotationPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ScalePropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.SpeedPropertyType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil;
import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil.IStatePlan;
import org.unitego.lobecorp.util.photon.playback.PhotonTimelineDecodeUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

/// 运行时专用的未绑定时间轴程序；只冻结制作数据，实例各自构造轨道和属性。
public class PhotonTimelineProgram {
    private static final AtomicLong NORMALIZED_TRACK_DECODES = new AtomicLong();
    private static final AtomicLong FALLBACK_TRACK_DECODES = new AtomicLong();
    private static final AtomicLong FALLBACK_PROPERTY_DECODES = new AtomicLong();

    public record DecodeCounts(long normalizedTracks, long fallbackTracks, long fallbackProperties) {
    }

    public static DecodeCounts decodeCounts() {
        return new DecodeCounts(NORMALIZED_TRACK_DECODES.get(), FALLBACK_TRACK_DECODES.get(),
                FALLBACK_PROPERTY_DECODES.get());
    }

    private record Bookmark(double tick, String name) {
    }

    private static class TrackState {
        @Persisted
        private String name = "";
        @Persisted
        private boolean mute;
        @Persisted
        private boolean lock;
    }

    private static class PropertyState {
        @Persisted
        private float minimum;
        @Persisted
        private float maximum;
    }

    private static final class PropertyProgram {
        private final IStatePlan state;
        private final @Nullable AnimatedProperty snapshot;
        private final Supplier<AnimatedProperty> constructor;
        private final CompoundTag canonical;
        private final HolderLookup.Provider provider;
        private final boolean fallback;

        private PropertyProgram(AnimatedProperty property, HolderLookup.Provider provider) {
            this.provider = provider;
            canonical = property.serializeNBT(provider);
            var values = new PropertyState();
            values.minimum = property.rangeMin();
            values.maximum = property.rangeMax();
            var tag = new CompoundTag();
            tag.putFloat("minimum", values.minimum);
            tag.putFloat("maximum", values.maximum);
            state = PhotonStatePlanUtil.compile(values, tag, PropertyState::new, Set.of());
            fallback = !supportedProperty(property);
            snapshot = fallback ? null : property.copy();
            constructor = fallback ? () -> {
                throw new IllegalStateException("未知属性必须使用原生回退。");
            } : propertyConstructor(property, canonical);
        }

        private AnimatedProperty instantiate() {
            if (!fallback && snapshot != null) {
                var result = constructor.get();
                result.restoreFrom(snapshot);
                var values = (PropertyState) state.create();
                result.setRange(values.minimum, values.maximum);
                return result;
            }

            FALLBACK_PROPERTY_DECODES.incrementAndGet();
            var result = AnimatedProperty.deserialize(provider, canonical.copy());

            if (result == null) {
                throw new IllegalArgumentException("编译属性的原生回退无法解析类型。");
            }

            return result;
        }
    }

    private static final class TrackProgram {
        private final IStatePlan state;
        private final Supplier<Track> constructor;
        private final @Nullable Track snapshot;
        private final CompoundTag canonical;
        private final HolderLookup.Provider provider;
        private final List<TrackProgram> children;
        private final List<PropertyProgram> properties;
        private final boolean fallback;

        private TrackProgram(Track authored, HolderLookup.Provider provider, boolean normalized) {
            this.provider = provider;
            var type = authored.type();

            if (type == null) {
                throw new IllegalArgumentException("编译时间轴轨道缺少注册类型。");
            }

            constructor = type::create;
            canonical = authored.writeData(provider);

            if (authored instanceof TrackGroup) {
                canonical.remove("children");
            }
            Track prototype = authored;

            if (!normalized) {
                prototype = constructor.get();
                NORMALIZED_TRACK_DECODES.incrementAndGet();
                prototype.readData(provider, canonical.copy());

                if (authored instanceof AnimationTrack source && prototype instanceof AnimationTrack decoded
                        && source.properties().size() != decoded.properties().size()) {
                    throw new IllegalArgumentException("编译时间轴含未解析的属性。");
                }
            }

            var values = new TrackState();
            values.name = prototype.displayName();
            values.mute = prototype.mute();
            values.lock = prototype.lock();
            var tag = new CompoundTag();
            tag.putString("name", values.name);
            tag.putBoolean("mute", values.mute);
            tag.putBoolean("lock", values.lock);
            state = PhotonStatePlanUtil.compile(values, tag, TrackState::new, Set.of());
            fallback = !supportedTrack(prototype);
            snapshot = fallback ? null : prototype.copy();

            if (snapshot instanceof TrackGroup group) {
                group.children().clear();
            }

            if (snapshot instanceof AnimationTrack animation) {
                animation.properties().clear();
            }

            var nested = new ArrayList<TrackProgram>();

            if (authored instanceof TrackGroup group) {
                for (var child : group.children()) {
                    nested.add(new TrackProgram(child, provider, normalized));
                }
            }

            children = List.copyOf(nested);
            var channels = new ArrayList<PropertyProgram>();

            if (prototype instanceof AnimationTrack animation) {
                for (var property : animation.properties()) {
                    channels.add(new PropertyProgram(property, provider));
                }
            }

            properties = List.copyOf(channels);
        }

        private Track instantiate() {
            Track result;

            if (fallback) {
                result = constructor.get();
                FALLBACK_TRACK_DECODES.incrementAndGet();
                result.readData(provider, canonical.copy());
            } else {
                if (snapshot == null) {
                    throw new IllegalStateException("编译轨道快照缺失。");
                }

                result = snapshot.copy();
            }

            var values = (TrackState) state.create();
            result.displayName(values.name).mute(values.mute).lock(values.lock);

            if (result instanceof TrackGroup group) {
                group.children().clear();

                for (var child : children) {
                    group.children().add(child.instantiate());
                }
            }

            if (result instanceof AnimationTrack animation) {
                animation.properties().clear();

                for (var property : properties) {
                    animation.properties().add(property.instantiate());
                }
            }

            return result;
        }

        private void collectFallbacks(List<String> result, String path) {
            if (fallback) {
                result.add(path + ": 轨道类型或采样器使用原生回退");
            }

            for (int i = 0; i < properties.size(); i++) {
                var property = properties.get(i);

                if (property.fallback) {
                    result.add(path + "/property/" + i + ": 不支持编译的属性类型，使用原生回退");
                }
            }

            for (int i = 0; i < children.size(); i++) {
                children.get(i).collectFallbacks(result, path + "/" + i);
            }
        }

        private int trackCount() {
            return 1 + children.stream().mapToInt(TrackProgram::trackCount).sum();
        }
    }

    private final List<TrackProgram> tracks;
    private final List<Bookmark> markers;
    private final List<String> fallbackReasons;
    private final int trackCount;

    private PhotonTimelineProgram(Timeline authored, HolderLookup.Provider provider, boolean normalized) {
        var prepared = new ArrayList<TrackProgram>();
        var tags = new ListTag();

        for (var track : authored.tracks()) {
            if (track.type() == null) {
                throw new IllegalArgumentException("编译时间轴轨道缺少注册类型。");
            }

            tags.add(Timeline.writeTrack(track, provider));
        }

        PhotonTimelineDecodeUtil.checkTracks(tags);

        for (var track : authored.tracks()) {
            prepared.add(new TrackProgram(track, provider, normalized));
        }

        tracks = List.copyOf(prepared);
        markers = authored.markers().stream().map(marker -> new Bookmark(marker.tick(), marker.name())).toList();
        var reasons = new ArrayList<String>();

        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).collectFallbacks(reasons, Integer.toString(i));
        }

        fallbackReasons = List.copyOf(reasons);
        trackCount = tracks.stream().mapToInt(TrackProgram::trackCount).sum();
    }

    public static PhotonTimelineProgram compile(Timeline authored) {
        return new PhotonTimelineProgram(authored, Platform.getFrozenRegistry(), false);
    }

    /// 只用于刚完成严格解码、尚未绑定或采样的命名轨道，避免再次解码同一份数据。
    public static PhotonTimelineProgram compilePrepared(Timeline authored, HolderLookup.Provider provider) {
        return new PhotonTimelineProgram(authored, provider, true);
    }

    public Timeline instantiate() {
        var result = new Timeline();

        for (var track : tracks) {
            result.tracks().add(track.instantiate());
        }

        for (var marker : markers) {
            result.markers().add(new Marker(marker.tick(), marker.name()));
        }

        return result;
    }

    public int trackCount() {
        return trackCount;
    }

    /// 非空时保留明确的原生解码回退，不能将它计为完全消除每实例解码。
    public List<String> fallbackReasons() {
        return fallbackReasons;
    }

    private static boolean supportedTrack(Track track) {
        var type = track.getClass();

        if (track instanceof PostProcessTrack && track.clips().stream()
                .anyMatch(clip -> clip instanceof PostProcessClip post && post.params().values().stream()
                        .anyMatch(value -> value.sampler() != null))) {
            return false;
        }

        return type == TrackGroup.class || type == AnimationTrack.class || type == SpeedTrack.class
                || type == ActivatorTrack.class || type == ControlTrack.class || type == SignalTrack.class
                || type == AudioTrack.class || type == PostProcessTrack.class || type == PhotonModelClipTrack.class
                || type == PhotonChildTrack.class || type == PhotonCallTrack.class;
    }

    private static boolean supportedProperty(AnimatedProperty property) {
        var type = property.getClass();
        var valueType = property.type().getClass();
        boolean knownType = valueType == ConfigPropertyType.class || valueType == ColorPropertyType.class
                || valueType == PositionPropertyType.class || valueType == RotationPropertyType.class
                || valueType == ScalePropertyType.class || valueType == SpeedPropertyType.class;
        return knownType && (type == AnimatedProperty.class || type == ConfigAnimatedProperty.class
                || type == RotationAnimatedProperty.class || type == ColorAnimatedProperty.class);
    }

    private static Supplier<AnimatedProperty> propertyConstructor(AnimatedProperty property, CompoundTag tag) {
        var runtimeClass = property.getClass();
        var typeFactory = propertyTypeConstructor(property.type(), tag);
        var authoredBase = property.base().clone();
        float minimum = property.rangeMin();
        float maximum = property.rangeMax();

        return () -> {
            var type = typeFactory.get();
            var base = authoredBase.clone();

            if (runtimeClass == ConfigAnimatedProperty.class) {
                return new ConfigAnimatedProperty(type, base, AnimatedProperty.seedChannels(base), minimum, maximum);
            }

            if (runtimeClass == RotationAnimatedProperty.class) {
                return new RotationAnimatedProperty(type, base, AnimatedProperty.seedChannels(base), minimum, maximum);
            }

            if (runtimeClass == ColorAnimatedProperty.class) {
                return new ColorAnimatedProperty(type);
            }

            return new AnimatedProperty(type, base, AnimatedProperty.seedChannels(base), minimum, maximum);
        };
    }

    private static Supplier<AnimatedPropertyType> propertyTypeConstructor(AnimatedPropertyType type, CompoundTag tag) {
        if (type instanceof ConfigPropertyType config) {
            var key = config.storeKey();
            var valueType = config.valueType();
            var label = tag.getStringOr("label", "");
            return () -> new ConfigPropertyType(key, valueType, label);
        }

        if (type instanceof ColorPropertyType) {
            var key = tag.getStringOr("path", "");
            var label = tag.getStringOr("label", "");
            return () -> new ColorPropertyType(key, label);
        }

        return switch (type) {
            case PositionPropertyType _ -> () -> PositionPropertyType.INSTANCE;
            case RotationPropertyType _ -> () -> RotationPropertyType.INSTANCE;
            case ScalePropertyType _ -> () -> ScalePropertyType.INSTANCE;
            case SpeedPropertyType _ -> () -> SpeedPropertyType.INSTANCE;
            default -> throw new IllegalArgumentException("属性类型必须使用显式原生回退。");
        };
    }
}

