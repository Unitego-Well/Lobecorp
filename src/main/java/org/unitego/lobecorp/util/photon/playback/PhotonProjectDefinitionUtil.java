package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Set;
import java.util.UUID;

/// PROJECT 子树只保留实际复制目标的贡献，原始库保持不变。
public class PhotonProjectDefinitionUtil {
    public static CompoundTag filter(CompoundTag original, Set<UUID> targets) {
        return filterOwned(original.copy(), targets);
    }

    public static CompoundTag filterOwned(CompoundTag definition, Set<UUID> targets) {
        for (var value : definition.getListOrEmpty("animations")) {
            if (!(value instanceof CompoundTag animation)) {
                throw new IllegalArgumentException("项目命名库含无效动画。");
            }
            animation.getListOrEmpty("tracks").removeIf(track -> !retainTrack((CompoundTag) track, targets));
            animation.getListOrEmpty("bindings").removeIf(binding ->
                    !targets.contains(UUID.fromString(((CompoundTag) binding).getStringOr("target", ""))));
        }
        return definition;
    }

    public static boolean retainTrack(CompoundTag source, Set<UUID> targets) {
        var data = source.getCompoundOrEmpty("data");
        var type = source.getStringOr("type", "");
        if (type.equals("group")) {
            var children = data.getListOrEmpty("children");
            children.removeIf(value -> !retainTrack((CompoundTag) value, targets));
            return !children.isEmpty();
        }
        if (type.equals("control")) {
            var clips = data.getListOrEmpty("clips");
            clips.removeIf(value -> ((CompoundTag) value).read("clipTarget", UUIDUtil.CODEC).filter(targets::contains).isEmpty());
            return !clips.isEmpty();
        }
        var target = data.read("target", UUIDUtil.CODEC);
        if (target.isPresent() && !targets.contains(target.get())) {
            return false;
        }
        if (data.get("clips") instanceof ListTag clips) {
            clips.removeIf(value -> {
                var clipTarget = ((CompoundTag) value).read("clipTarget", UUIDUtil.CODEC);
                return clipTarget.isPresent() && !targets.contains(clipTarget.get());
            });
        }
        return true;
    }
}
