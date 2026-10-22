package org.unitego.lobecorp.client.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.util.ArrayList;
import java.util.List;

/// 少量聚合声源和近处扬尘，数量不随全部虚拟个体逐个增长。
public class HordeAmbient {
	private static final List<Source> SOURCES = new ArrayList<>();
	private static long tick;

	public static void tick() {
		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		tick++;
		var nearest = HordeClient.instances().stream()
				.filter(instance -> instance.distance() < HordeVisualConfig.NEAR.get()).toList();
		if (nearest.isEmpty()) {
			SOURCES.clear();
			return;
		}
		if (HordeVisualConfig.SOUND.get() && tick % 6 == 0) {
			int count = Math.min((int) Math.ceil(HordeVisualConfig.SOUND_VOICES.get() * HordeClient.quality()), nearest.size());
			while (SOURCES.size() < count) {
				SOURCES.add(new Source());
			}
			for (int i = 0; i < count; i++) {
				var source = SOURCES.get(i);
				var point = nearest.get(i * nearest.size() / count).position();
				if (tick - source.checkedAt >= 20) {
					source.blocked = minecraft.level.clip(new ClipContext(minecraft.player.getEyePosition(), point.add(0, 1, 0),
							ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player)).getType() != HitResult.Type.MISS;
					source.checkedAt = tick;
				}
				float volume = (float) Math.min(1, nearest.size() / 32d) * (source.blocked ? 0.2f : 0.65f);
				minecraft.level.playLocalSound(point.x, point.y, point.z, SoundEvents.STONE_STEP,
						SoundSource.HOSTILE, volume, 0.8f + i * 0.07f, false);
			}
		}
		if (HordeVisualConfig.DUST.get()) {
			int count = Math.min(nearest.size(), (int) Math.ceil(HordeVisualConfig.DUST_PER_TICK.get() * HordeClient.quality()));
			for (int i = 0; i < count; i++) {
				var instance = nearest.get((int) Math.floorMod(tick * 31 + i * 17, nearest.size()));
				var point = instance.position();
				var block = minecraft.level.getBlockState(BlockPos.containing(point).below());
				if (!block.isAir()) {
					minecraft.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, block),
							point.x, point.y + 0.05, point.z, 0, 0.02, 0);
				}
			}
		}
	}

	public static void clear() {
		SOURCES.clear();
		tick = 0;
	}

	private static class Source {
		private long checkedAt = -20;
		private boolean blocked;
	}
}
