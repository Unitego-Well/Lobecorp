package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.FXObject;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/// 原生对象历史只保存基础值；预览姿态在快照完成后恢复。
public class PhotonAnimationHistoryUtil {
	@SuppressWarnings("resource") // 快照只借用当前运行态，不转移其所有权。
	public static void serialize(ValueIOSerializable serializable, Runnable serializer) {
		var target = serializable instanceof Transform transform ? transform.sceneObject() : serializable;

		if (!(target instanceof FXObject object) || !(object.getScene() instanceof FXRuntime runtime)) {
			serializer.run();
			return;
		}

		var animations = PhotonAnimationUtil.of(runtime);

		if (animations == null) {
			serializer.run();
			return;
		}

		animations.serializeBase(() -> {
			serializer.run();
			return null;
		});
	}
}
