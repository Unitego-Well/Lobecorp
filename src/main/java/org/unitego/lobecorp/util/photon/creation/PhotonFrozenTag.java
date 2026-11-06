package org.unitego.lobecorp.util.photon.creation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/// 私有 NBT 所有权；只有构造和显式导出发生复制，不提供可写借用。
public class PhotonFrozenTag {
    private final Tag value;

    public PhotonFrozenTag(Tag value) {
        this.value = Objects.requireNonNull(value).copy();
    }

    public boolean matches(Tag other) {
        return value.equals(other);
    }

    public boolean matches(PhotonFrozenTag other) {
        return this == other || value.equals(Objects.requireNonNull(other).value);
    }

    public Tag copy() {
        return value.copy();
    }

    public CompoundTag compoundCopy() {
        if (!(value instanceof CompoundTag compound)) {
            throw new IllegalStateException("冻结快照不是复合标签。");
        }

        return compound.copy();
    }
}
