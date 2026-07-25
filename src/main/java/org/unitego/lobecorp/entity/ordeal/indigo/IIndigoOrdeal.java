package org.unitego.lobecorp.entity.ordeal.indigo;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.unitego.lobecorp.entity.ordeal.IOrdeal;

public interface IIndigoOrdeal extends IOrdeal {
    default void addTargetSelector() {
        GoalSelector targetSelector = getMob().targetSelector;
//        targetSelector.addGoal();
    }
}
