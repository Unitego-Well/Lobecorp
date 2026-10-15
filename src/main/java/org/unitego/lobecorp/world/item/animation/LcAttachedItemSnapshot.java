package org.unitego.lobecorp.world.item.animation;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;

import java.util.Objects;
import java.util.UUID;

/// 物品只同步显示数据；局部动画独立于绑定位置，停止时先淡回单位变换再结束对象。
public record LcAttachedItemSnapshot(UUID bindingId, ItemStack stack, ItemDisplayContext displayContext,
                                     @Nullable UUID animationId, @Nullable String bone,
                                     LcAnimationTransitionSettings transitions,
                                     long startedTick, long stoppingTick, long serverTick) {
	private static final String EMPTY_STACK = "Item attachment display stack must not be empty";

	public LcAttachedItemSnapshot {
		Objects.requireNonNull(bindingId);
		if (stack.isEmpty()) {
			throw new IllegalArgumentException(EMPTY_STACK);
		}

		stack = stack.copy();
		Objects.requireNonNull(displayContext);
		Objects.requireNonNull(transitions);
	}

	@Override
	public ItemStack stack() {
		return stack.copy();
	}
}
