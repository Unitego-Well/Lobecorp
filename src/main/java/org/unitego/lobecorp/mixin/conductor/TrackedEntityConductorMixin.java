package org.unitego.lobecorp.mixin.conductor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.conductor.world.ConductorView;

/// 实体跟踪同时考虑玩家和镜头距离，镜头范围沿用玩家的区块视距。
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityConductorMixin {
	@Shadow
	@Final
	private Entity entity;

	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 lobecorp$observationPosition(ServerPlayer player, Operation<Vec3> original) {
		Vec3 body = original.call(player);
		Vec3 camera = ConductorView.position(player);
		if (camera == null)
			return body;
		Vec3 bodyDelta = body.subtract(entity.position());
		Vec3 cameraDelta = camera.subtract(entity.position());
		return cameraDelta.horizontalDistanceSqr() < bodyDelta.horizontalDistanceSqr() ? camera : body;
	}

	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
	private int lobecorp$observationRange(int a, int b, Operation<Integer> original, ServerPlayer player) {
		Vec3 camera = ConductorView.position(player);
		return camera != null && camera.subtract(entity.position()).horizontalDistanceSqr() <= (double) b * b
				? b : original.call(a, b);
	}
}
