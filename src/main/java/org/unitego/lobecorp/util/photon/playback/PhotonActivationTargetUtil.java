package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;

import java.util.*;

/// 按当前轨道快照建立激活目标到相关轨道的局部分组。
public class PhotonActivationTargetUtil {
	/// 每次调用重新读取轨道状态，不跨求值缓存。
	public static Map<UUID, List<Track>> group(List<Track> leaves) {
		var grouped = new LinkedHashMap<UUID, List<Track>>();

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof ActivatorTrack && track.targetId() != null) {
				grouped.computeIfAbsent(track.targetId(), _ -> new ArrayList<>()).add(track);
			} else if (track instanceof ControlTrack controls) {
				var seen = new HashSet<UUID>();

				for (var clip : controls.clips()) {
					var target = clip.targetId();

					if (target != null && seen.add(target)) {
						grouped.computeIfAbsent(target, _ -> new ArrayList<>()).add(track);
					}
				}
			}
		}

		return grouped;
	}
}
