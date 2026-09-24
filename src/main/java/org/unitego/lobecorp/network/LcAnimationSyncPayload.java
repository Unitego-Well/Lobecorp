package org.unitego.lobecorp.network;

import com.geckolib.animatable.SingletonGeoAnimatable;
import com.geckolib.animation.RawAnimation;
import com.geckolib.cache.SyncedSingletonAnimatableCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimatable;
import org.unitego.lobecorp.animation.LcAnimationController;
import org.unitego.lobecorp.animation.LcTransitionMode;

import static org.unitego.lobecorp.Lobecorp.id;

public record LcAnimationSyncPayload(
		TargetType targetType,
		int entityId,
		int blockX,
		int blockY,
		int blockZ,
		long instanceId,
		String animatableId,
		boolean playing,
		String layerName,
		RawAnimation animation,
		double speed,
		boolean reversed,
		int transitionTicks,
		@Nullable LcTransitionMode transitionMode
) implements ToPayload {
	public static final CustomPacketPayload.Type<LcAnimationSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(id("layered_animation_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LcAnimationSyncPayload> STREAM_CODEC =
			new StreamCodec<>() {
				@Override
				public LcAnimationSyncPayload decode(RegistryFriendlyByteBuf buffer) {
					return new LcAnimationSyncPayload(
							TargetType.values()[buffer.readVarInt()],
							buffer.readVarInt(),
							buffer.readInt(),
							buffer.readInt(),
							buffer.readInt(),
							buffer.readLong(),
							buffer.readUtf(),
							buffer.readBoolean(),
							buffer.readUtf(),
							RawAnimation.STREAM_CODEC.decode(buffer),
							buffer.readDouble(),
							buffer.readBoolean(),
							buffer.readVarInt(),
							readTransitionMode(buffer.readVarInt())
					);
				}

				@Override
				public void encode(RegistryFriendlyByteBuf buffer, LcAnimationSyncPayload payload) {
					buffer.writeVarInt(payload.targetType.ordinal());
					buffer.writeVarInt(payload.entityId);
					buffer.writeInt(payload.blockX);
					buffer.writeInt(payload.blockY);
					buffer.writeInt(payload.blockZ);
					buffer.writeLong(payload.instanceId);
					buffer.writeUtf(payload.animatableId);
					buffer.writeBoolean(payload.playing);
					buffer.writeUtf(payload.layerName);
					RawAnimation.STREAM_CODEC.encode(buffer, payload.animation);
					buffer.writeDouble(payload.speed);
					buffer.writeBoolean(payload.reversed);
					buffer.writeVarInt(payload.transitionTicks);
					buffer.writeVarInt(payload.transitionMode == null ? -1 : payload.transitionMode.ordinal());
				}
			};

	public static void sendEntity(Entity entity, String layerName, RawAnimation animation,
			double speed, boolean reversed, int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		send(entity, TargetType.ENTITY, entity.getId(), 0L, "", layerName, animation,
				speed, reversed, transitionTicks, transitionMode, true);
	}

	public static void stopEntity(Entity entity, String layerName, int transitionTicks) {
		send(entity, TargetType.ENTITY, entity.getId(), 0L, "", layerName, RawAnimation.begin(),
				LcAnimationController.DEFAULT_SPEED, false, transitionTicks, null, false);
	}

	public static void sendBlockEntity(BlockEntity blockEntity, String layerName, RawAnimation animation,
			double speed, boolean reversed, int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			send(level, blockEntity.getBlockPos(), TargetType.BLOCK_ENTITY, 0, 0L, "", layerName,
					animation, speed, reversed, transitionTicks, transitionMode, true);
		}
	}

	public static void stopBlockEntity(BlockEntity blockEntity, String layerName, int transitionTicks) {
		if (blockEntity.getLevel() instanceof ServerLevel level) {
			send(level, blockEntity.getBlockPos(), TargetType.BLOCK_ENTITY, 0, 0L, "", layerName,
					RawAnimation.begin(), LcAnimationController.DEFAULT_SPEED, false, transitionTicks, null, false);
		}
	}

	public static void sendItem(LcAnimatable animatable, Entity relatedEntity, long instanceId,
			String layerName, RawAnimation animation, double speed, boolean reversed,
			int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		String animatableId = SyncedSingletonAnimatableCache.getOrCreateId((SingletonGeoAnimatable)animatable);
		send(relatedEntity, TargetType.ITEM, relatedEntity.getId(), instanceId, animatableId, layerName, animation,
				speed, reversed, transitionTicks, transitionMode, true);
	}

	public static void stopItem(LcAnimatable animatable, Entity relatedEntity, long instanceId,
			String layerName, int transitionTicks) {
		String animatableId = SyncedSingletonAnimatableCache.getOrCreateId((SingletonGeoAnimatable)animatable);
		send(relatedEntity, TargetType.ITEM, relatedEntity.getId(), instanceId, animatableId, layerName,
				RawAnimation.begin(), LcAnimationController.DEFAULT_SPEED, false, transitionTicks, null, false);
	}

	public static void sendSingleton(LcAnimatable animatable, Entity relatedEntity, long instanceId,
			String layerName, RawAnimation animation, double speed, boolean reversed,
			int transitionTicks, @Nullable LcTransitionMode transitionMode) {
		String animatableId = SyncedSingletonAnimatableCache.getOrCreateId((SingletonGeoAnimatable)animatable);
		send(relatedEntity, TargetType.SINGLETON, relatedEntity.getId(), instanceId, animatableId, layerName, animation,
				speed, reversed, transitionTicks, transitionMode, true);
	}

	public static void stopSingleton(LcAnimatable animatable, Entity relatedEntity, long instanceId,
			String layerName, int transitionTicks) {
		String animatableId = SyncedSingletonAnimatableCache.getOrCreateId((SingletonGeoAnimatable)animatable);
		send(relatedEntity, TargetType.SINGLETON, relatedEntity.getId(), instanceId, animatableId, layerName,
				RawAnimation.begin(), LcAnimationController.DEFAULT_SPEED, false, transitionTicks, null, false);
	}

	private static void send(Entity entity, TargetType targetType, int entityId, long instanceId,
			String animatableId,
			String layerName, RawAnimation animation, double speed, boolean reversed,
			int transitionTicks, @Nullable LcTransitionMode transitionMode, boolean playing) {
		if (!(entity.level() instanceof ServerLevel)) {
			return;
		}
		PacketDistributor.sendToPlayersTrackingEntity(entity, new LcAnimationSyncPayload(targetType,
				entityId, 0, 0, 0, instanceId, animatableId, playing, layerName, animation, speed, reversed,
				transitionTicks, transitionMode));
	}

	private static void send(ServerLevel level, BlockPos position,
			TargetType targetType, int entityId, long instanceId, String animatableId, String layerName,
			RawAnimation animation, double speed, boolean reversed, int transitionTicks,
			@Nullable LcTransitionMode transitionMode, boolean playing) {
		PacketDistributor.sendToPlayersTrackingChunk(level,
				new ChunkPos(position.getX() >> 4, position.getZ() >> 4),
				new LcAnimationSyncPayload(targetType, entityId, position.getX(), position.getY(),
						position.getZ(), instanceId, animatableId, playing, layerName, animation, speed, reversed,
						transitionTicks, transitionMode));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context) {
		var level = context.player().level();
		if (!level.isClientSide()) {
			return;
		}
		Object target = switch (targetType) {
			case ENTITY -> level.getEntity(entityId);
			case BLOCK_ENTITY -> level.getBlockEntity(new BlockPos(blockX, blockY, blockZ));
			case ITEM, SINGLETON -> SyncedSingletonAnimatableCache.getSyncedAnimatable(animatableId);
		};
		if (!(target instanceof LcAnimatable animatable)) {
			if (targetType == TargetType.ENTITY) {
				Lobecorp.LOGGER.debug("Could not resolve entity animation sync: entity={}, layer={}, playing={}",
						entityId, layerName, playing);
			}
			return;
		}
		if (playing) {
			animatable.playAnimation(instanceId, layerName, animation, speed, reversed,
					transitionTicks, transitionMode);
		} else {
			animatable.stopAnimation(instanceId, layerName, transitionTicks);
		}
	}

	private static @Nullable LcTransitionMode readTransitionMode(int ordinal) {
		return ordinal < 0 ? null : LcTransitionMode.values()[ordinal];
	}

	private enum TargetType {
		ENTITY,
		BLOCK_ENTITY,
		ITEM,
		SINGLETON
	}

}
