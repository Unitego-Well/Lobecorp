package org.unitego.lobecorp.entity.ordeal;

import net.minecraft.world.entity.Mob;

public interface IOrdeal {
    void addTargetSelector();

    default Mob getMob() {
        return (Mob) this;
    }
}
