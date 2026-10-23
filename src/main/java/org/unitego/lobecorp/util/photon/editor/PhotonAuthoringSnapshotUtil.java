package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonTimelineStateAccess;

/// 只在序列化副本中排除预览持有的姿态，不暂停播放或临时回写当前工程。
public class PhotonAuthoringSnapshotUtil {
	@SuppressWarnings("resource") // 命名运行态为借用，只在序列化副本中读取姿态。
	public static CompoundTag serialize(FX fx, @Nullable FXRuntime runtime) {
        var result = fx.serializeNBT(Platform.getFrozenRegistry());
        if (runtime == null) {
            return result;
        }
        var named = ((PhotonNamedAccess) runtime).lobecorp$namedRuntime();
        var timeline = (PhotonTimelineStateAccess) runtime.timelinePlayer;
        if (timeline.lobecorp$recording()) {
            return result;
        }
        var wrappers = result.getCompoundOrEmpty("fxData").getListOrEmpty("fxObjects");
        var objects = fx.getFxData().objects();
        if (wrappers.size() != objects.size()) {
            throw new IllegalStateException("制作对象序列化身份不一致。");
        }
        for (int index = 0; index < objects.size(); index++) {
            if (!(objects.get(index) instanceof FXObject object)) {
                continue;
            }
            var current = PhotonAuthoringPose.capture(object);
            var pose = named == null ? current : named.authoredPose(object, current);
            pose = timeline.lobecorp$authoredPose(object, pose);
            if (pose.equals(current)) {
                continue;
            }
            var copy = object.copy(true);
            if (!(copy instanceof FXObject target)) {
                throw new IllegalStateException("无法复制制作对象姿态。");
            }
            pose.apply(target);
            var serialized = target.serializeWrapper();
            if (serialized == null) {
                throw new IllegalStateException("无法序列化制作对象姿态。");
            }
            var wrapper = (CompoundTag) wrappers.get(index);
            wrapper.getCompoundOrEmpty("data").put("transform", serialized.getCompoundOrEmpty("data").getCompoundOrEmpty("transform").copy());
        }
        return result;
    }
}
