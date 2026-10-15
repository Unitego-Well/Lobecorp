package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/// 命名动画投影到原 TL 的模型；不替换 FXData 自己的时间轴。
public class PhotonAnimationTimelineModel {
	private final Timeline timeline = new Timeline();
	private final Map<Marker, PhotonAnimationDefinition.Marker> markerData = new IdentityHashMap<>();
	private @Nullable UUID animationId;

	public Timeline timeline() {
		return timeline;
	}

	public void load(@Nullable PhotonAnimationDefinition definition) {
		var previous = new IdentityHashMap<PhotonAnimationDefinition.Marker, Marker>();
		markerData.forEach((marker, data) -> previous.put(data, marker));
		var currentId = definition == null ? null : definition.id;
		var reuse = Objects.equals(animationId, currentId);
		timeline.tracks().clear();
		timeline.markers().clear();
		markerData.clear();
		animationId = currentId;

		if (definition == null) {
			return;
		}

		timeline.tracks().addAll(definition.tracks);

		for (var data : definition.markers) {
			var marker = reuse ? previous.getOrDefault(data, new Marker()) : new Marker();
			marker.tick(data.time()).name(data.name());
			timeline.markers().add(marker);
			markerData.put(marker, data);
		}
	}

	public void store(PhotonAnimationDefinition definition) {
		store(definition, (_, _) -> {
		});
	}

	public void store(PhotonAnimationDefinition definition,
	                  BiConsumer<PhotonAnimationDefinition.Marker, PhotonAnimationDefinition.Marker> replaced) {
		definition.tracks.clear();
		definition.tracks.addAll(timeline.tracks());
		definition.markers.clear();

		for (var marker : timeline.markers()) {
			var previous = markerData.get(marker);
			var data = new PhotonAnimationDefinition.Marker(marker.tick(), marker.name(),
					previous == null ? new CompoundTag() : previous.data());

			if (previous != null) {
				if (previous.equals(data)) {
					data = previous;
				} else {
					replaced.accept(previous, data);
				}
			}

			definition.markers.add(data);
			markerData.put(marker, data);
		}

		markerData.keySet().retainAll(timeline.markers());
	}
}
