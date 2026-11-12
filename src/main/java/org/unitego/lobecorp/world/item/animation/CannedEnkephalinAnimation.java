package org.unitego.lobecorp.world.item.animation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.registry.animation.CannedEnkephalinAnimationDefinitions;
import org.unitego.lobecorp.registry.world.item.LcItems;

import java.util.IdentityHashMap;
import java.util.Map;

public class CannedEnkephalinAnimation {
	private static final Map<ServerPlayer, LcAnimationInstance> PLAYING = new IdentityHashMap<>();

	public static void play(ServerPlayer player, InteractionHand hand) {
		stop(player);
		var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		var definition = arm == HumanoidArm.RIGHT ? CannedEnkephalinAnimationDefinitions.RIGHT : CannedEnkephalinAnimationDefinitions.LEFT;
		PLAYING.put(player, LcServerAnimations.play(player, definition));
	}

	public static void stop(ServerPlayer player) {
		var instance = PLAYING.remove(player);
		if (instance != null) {
			instance.stop();
		}
	}

	public static void tick(LcAnimationContext context) {
		if (!(context.sourceEntity() instanceof ServerPlayer player) || !player.isUsingItem()
				|| !player.getUseItem().is(LcItems.CANNED_ENKEPHALIN.get())) {
			context.instance().stop();
		}
	}

	public static void finish(LcAnimationInstance instance) {
		if (instance.sourceEntity() instanceof ServerPlayer player) {
			PLAYING.remove(player, instance);
		}
	}
}
