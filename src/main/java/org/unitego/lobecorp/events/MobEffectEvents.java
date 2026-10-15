package org.unitego.lobecorp.events;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.world.effect.StunMobEffect;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class MobEffectEvents {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectRemoved(MobEffectEvent.Remove event) {
		if (!event.isCanceled() && event.getEffect().equals(LcMobEffects.STUN)) {
			LcMobEffects.STUN.get().restoreAi(event.getEntity());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectExpired(MobEffectEvent.Expired event) {
		MobEffectInstance effect = event.getEffectInstance();

		if (!event.isCanceled() && effect != null && effect.getEffect().equals(LcMobEffects.STUN)) {
			LcMobEffects.STUN.get().restoreAi(event.getEntity());
		}
	}

	@SubscribeEvent
	public static void onAttackEntity(AttackEntityEvent event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			// 单纯取消 tick 会进入原版使用完成流程，先停止使用以免结算物品效果。
			event.getEntity().stopUsingItem();
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCancellationResult(InteractionResult.FAIL);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCancellationResult(InteractionResult.FAIL);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCancellationResult(InteractionResult.FAIL);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (StunMobEffect.preventsActions(event.getEntity())) {
			event.setCancellationResult(InteractionResult.FAIL);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.ABORT
				&& StunMobEffect.preventsActions(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onBreakBlock(BreakBlockEvent event) {
		if (StunMobEffect.preventsActions(event.getPlayer())) {
			event.setCanceled(true);
		}
	}
}
