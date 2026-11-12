package org.unitego.lobecorp.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.world.item.animation.CannedEnkephalinAnimation;

public class CannedEnkephalinItem extends Item {
	/// 参考物品的饮用耗时为84游戏刻，包含开罐、饮用和收手。
	private static final int DRINK_TICKS = 84;

	public CannedEnkephalinItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		if (player instanceof ServerPlayer serverPlayer && player.isUsingItem()) {
			CannedEnkephalinAnimation.play(serverPlayer, hand);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return DRINK_TICKS;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTime) {
		if (user instanceof ServerPlayer player) {
			CannedEnkephalinAnimation.stop(player);
		}
		return false;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (user instanceof ServerPlayer player) {
			CannedEnkephalinAnimation.stop(player);
			stack.consume(1, player);
		}
		return stack;
	}
}
