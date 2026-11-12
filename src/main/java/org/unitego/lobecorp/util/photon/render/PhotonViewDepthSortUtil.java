package org.unitego.lobecorp.util.photon.render;

import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonCameraUtils;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.render.PhotonViewDepthSortAccess;

import java.util.*;

public class PhotonViewDepthSortUtil {
	/// 标记只属于当前绘制项身份，帧结束清理，避免值相同的其他发射器被误标记。
	private static final Map<DrawJob, Float> VIEW_DEPTHS = new IdentityHashMap<>();

	public static void clearFrame() {
		VIEW_DEPTHS.clear();
	}

	public static void mark(List<DrawJob> jobs, int start, Camera camera, Vector3f position, RendererSetting.Runtime renderer) {
		if (!((PhotonViewDepthSortAccess) renderer).lobecorp$isViewDepthSortEnabled()) {
			return;
		}

		float depth = new Vector3f(position).sub(PhotonCameraUtils.facingEye(camera)).dot(camera.forwardVector());

		for (int index = start; index < jobs.size(); index++) {
			VIEW_DEPTHS.put(jobs.get(index), depth);
		}
	}

	/// 保持默认绘制项所在的槽位，只重排相同 Order 中启用新模式的项。
	public static void reorder(List<DrawJob> jobs) {
		if (VIEW_DEPTHS.isEmpty() || jobs.size() < 2) {
			return;
		}

		int start = 0;

		while (start < jobs.size()) {
			int end = start + 1;

			while (end < jobs.size() && jobs.get(end).orderInLayer() == jobs.get(start).orderInLayer()) {
				end++;
			}

			List<Integer> slots = new ArrayList<>();
			List<DrawJob> selected = new ArrayList<>();

			for (int index = start; index < end; index++) {
				DrawJob job = jobs.get(index);

				if (VIEW_DEPTHS.containsKey(job)) {
					slots.add(index);
					selected.add(job);
				}
			}

			selected.sort(Comparator.comparingDouble(PhotonViewDepthSortUtil::depth).reversed());

			for (int index = 0; index < slots.size(); index++) {
				jobs.set(slots.get(index), selected.get(index));
			}

			start = end;
		}
	}

	private static double depth(DrawJob job) {
		return VIEW_DEPTHS.get(job);
	}
}
