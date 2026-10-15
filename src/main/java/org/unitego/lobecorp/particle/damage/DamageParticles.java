package org.unitego.lobecorp.particle.damage;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;

import java.util.Optional;
import java.util.UUID;

/// 服务端确认伤害后发送一次屏幕粒子通知；不保存表现实例或推进客户端动画。
public class DamageParticles {
	/// 伤害发生位置周围的接收半径，单位为格。
	private static final double SYNC_RANGE = 32.0;

	@SuppressWarnings("resource")
	public static void onDamage(LivingDamageEvent.Post event) {
		var target = event.getEntity();
		float amount = event.getHealthDamage();
		if (!(target.level() instanceof ServerLevel level) || !Float.isFinite(amount) || amount <= 0.0F) {
			return;
		}

		DamageSource source = event.getSource();
		Identifier damageType = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getKey(source.type());
		if (damageType == null) {
			return;
		}

		DamageParticleHitContext.Hit hit = DamageParticleHitContext.resolve(target, source);
		Vec3 position = hit.position();
		Entity causingEntity = source.getEntity();
		Optional<UUID> attacker = causingEntity == null ? Optional.empty() : Optional.of(causingEntity.getUUID());
		var payload = new DamageParticlePayload(level.dimension(), target.getUUID(), attacker,
				damageType, position, amount, target.getMaxHealth(), hit.melee());
		PacketDistributor.sendToPlayersNear(level, null, position.x, position.y, position.z, SYNC_RANGE, payload);
	}

	/// 为自定义命中传入碰撞点和近战语义；作用域覆盖同步伤害调用，并在所有退出路径释放。
	@CanIgnoreReturnValue
	public static boolean hurtAt(ServerLevel level, Entity target, DamageSource source, float amount,
	                             Vec3 position, boolean melee) {
		try (var _ = DamageParticleHitContext.at(target, source, position, melee)) {
			return target.hurtServer(level, source, amount);
		}
	}
}
