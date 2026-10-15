package org.unitego.lobecorp.particle.text;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.network.tc.text.TextParticleStatePayload;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

import java.util.List;
import java.util.UUID;

/// 服务端文本实例的世界入口；同步给同一 Level 的玩家。
public class TextParticles {
	/// 每秒完整补发一次，使新加入的玩家恢复当前展示实例。
	public static final int SYNC_INTERVAL_TICKS = 20;
	private static final String INVALID_INSTANCE_MESSAGE = "文本展示实例已加入或已移除";

	public static TextParticleInstance create(ServerLevel level, Vec3 position, Component text, float size) {
		return add(new TextParticleInstance(level, position, text, size));
	}

	public static TextParticleInstance add(TextParticleInstance instance) {
		var data = data(instance.level());
		if (instance.isRemoved() || data.get(instance.id()) != null) {
			throw new IllegalStateException(INVALID_INSTANCE_MESSAGE);
		}

		instance.restart();
		data.add(instance);
		update(instance);
		return instance;
	}

	@Nullable
	public static TextParticleInstance get(ServerLevel level, UUID id) {
		return data(level).get(id);
	}

	// Level 由服务器拥有，实例只借用引用，不能在同步后关闭。
	@SuppressWarnings("resource")
	public static void update(TextParticleInstance instance) {
		if (instance.isRemoved() || instance.level().players().isEmpty()
				|| data(instance.level()).get(instance.id()) != instance) {
			return;
		}

		send(instance.level(), new TextParticleStatePayload(false, List.of(instance.snapshot()), List.of()));
	}

	public static void remove(TextParticleInstance instance) {
		if (!instance.isRemoved()) {
			instance.remove();
			return;
		}

		if (data(instance.level()).remove(instance)) {
			send(instance.level(), new TextParticleStatePayload(false, List.of(), List.of(instance.id())));
		}
	}

	/// 只调度状态补发，不推进逐字进度或动画。
	public static void tick(ServerLevel level) {
		if (level.players().isEmpty() || level.getGameTime() % SYNC_INTERVAL_TICKS != 0L) {
			return;
		}

		send(level, new TextParticleStatePayload(true, data(level).snapshots(), List.of()));
	}

	public static void clear(MinecraftServer server) {
		for (var level : server.getAllLevels()) {
			for (var instance : data(level).instances()) {
				instance.remove();
			}
		}
	}

	private static TextParticleLevelData data(ServerLevel level) {
		return level.getData(LcAttachmentTypes.TEXT_PARTICLE_LEVEL_DATA);
	}

	private static void send(ServerLevel level, TextParticleStatePayload payload) {
		for (var player : level.players()) {
			PacketDistributor.sendToPlayer(player, payload);
		}
	}
}
