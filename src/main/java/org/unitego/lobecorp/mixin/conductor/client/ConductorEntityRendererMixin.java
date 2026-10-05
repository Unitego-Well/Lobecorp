package org.unitego.lobecorp.mixin.conductor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.world.entity.projectile.MagicStarProjectile;

/// 指挥家模式下星星保留视锥裁剪，避免小碰撞箱的距离阈值遮掉俯视镜头中的星星。
@Mixin(EntityRenderer.class)
public abstract class ConductorEntityRendererMixin {
	/// 原版抛掷物生成前两 tick 的近距离隐藏范围，单位为格。
	@Unique
	private static final double NEAR_PROJECTILE_HIDE_DISTANCE = 3.5D;
	/// 原版抛掷物生成后的近距离隐藏时长，单位为 tick。
	@Unique
	private static final int NEAR_PROJECTILE_HIDE_TICKS = 2;

	@WrapOperation(method = "shouldRender", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;shouldRender(DDD)Z"))
	private boolean lobecorp$renderObservedStar(Entity entity, double camX, double camY, double camZ, Operation<Boolean> original) {
		return original.call(entity, camX, camY, camZ) || ConductorControls.active() && entity instanceof MagicStarProjectile
				&& (entity.tickCount >= NEAR_PROJECTILE_HIDE_TICKS
				|| entity.distanceToSqr(camX, camY, camZ) >= NEAR_PROJECTILE_HIDE_DISTANCE * NEAR_PROJECTILE_HIDE_DISTANCE);
	}
}
