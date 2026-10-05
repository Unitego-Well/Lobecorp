package org.unitego.lobecorp.network.tc;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.event.EntitySkillEvent;

import static org.unitego.lobecorp.Lobecorp.id;

public record EntitySkillSyncPayload(
		int ownerId,
		long runtimeId,
		Identifier skillId,
		Callback callback,
		EntitySkillRuntime.SkillState state,
		int ticksLeft,
		int activeTicks,
		int sequenceStage,
		boolean successful,
		int targetId
) implements ToClientPayload {
	/// 实体技能运行态同步载荷类型。
	public static final CustomPacketPayload.Type<EntitySkillSyncPayload> TYPE = new CustomPacketPayload.Type<>(id("entity_skill_sync"));
	/// 实体技能运行态同步载荷编解码器。
	public static final StreamCodec<RegistryFriendlyByteBuf, EntitySkillSyncPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull EntitySkillSyncPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new EntitySkillSyncPayload(
					buffer.readVarInt(),
					buffer.readVarLong(),
					Identifier.STREAM_CODEC.decode(buffer),
					Callback.values()[buffer.readVarInt()],
					EntitySkillRuntime.SkillState.values()[buffer.readVarInt()],
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readBoolean(),
					buffer.readVarInt()
			);
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull EntitySkillSyncPayload payload) {
			buffer.writeVarInt(payload.ownerId);
			buffer.writeVarLong(payload.runtimeId);
			Identifier.STREAM_CODEC.encode(buffer, payload.skillId);
			buffer.writeVarInt(payload.callback.ordinal());
			buffer.writeVarInt(payload.state.ordinal());
			buffer.writeVarInt(payload.ticksLeft);
			buffer.writeVarInt(payload.activeTicks);
			buffer.writeVarInt(payload.sequenceStage);
			buffer.writeBoolean(payload.successful);
			buffer.writeVarInt(payload.targetId);
		}
	};
	/// 表示运行态没有目标实体的网络值。
	private static final int NO_TARGET = -1;

	public static void send(EntitySkillRuntime<?> runtime, Callback callback) {
		Entity target = runtime.target();
		PacketDistributor.sendToPlayersTrackingEntity(runtime.owner(), new EntitySkillSyncPayload(
				runtime.owner().getId(),
				runtime.id(),
				runtime.skill().id(),
				callback,
				runtime.state(),
				runtime.ticksLeft(),
				runtime.activeTicks(),
				runtime.sequenceStage(),
				runtime.isSuccessful(),
				target == null ? NO_TARGET : target.getId()
		));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		Entity entity = player.level().getEntity(ownerId);
		if (!(entity instanceof LivingEntity owner)) {
			return;
		}
		IEntitySkill<?> skill = EntitySkillUtil.getSkills(owner).stream()
				.filter(candidate -> candidate.id().equals(skillId))
				.findFirst()
				.orElse(null);
		if (skill == null) {
			return;
		}
		runCallback(owner, skill);
	}

	@SuppressWarnings("unchecked")
	private <T extends LivingEntity> void runCallback(T owner, IEntitySkill<?> skill) {
		var data = EntitySkillUtil.activeRuntimeData(owner);
		EntitySkillRuntime<?> existing = data.find(runtimeId);
		EntitySkillRuntime<T> runtime;
		if (existing != null) {
			runtime = (EntitySkillRuntime<T>) existing;
			runtime.setState(state);
			runtime.setTicksLeft(ticksLeft);
		} else {
			runtime = new EntitySkillRuntime<>(runtimeId, owner, (IEntitySkill<T>) skill, state, ticksLeft);
			data.active.add(runtime);
		}
		runtime.setActiveTicks(activeTicks);
		runtime.setSequenceStage(sequenceStage);
		if (successful) {
			runtime.markSuccessful();
		}
		if (targetId != NO_TARGET) {
			runtime.setTarget(owner.level().getEntity(targetId));
		}
		callback.run(runtime);
		switch (callback) {
			case WINDUP_START -> NeoForge.EVENT_BUS.post(new EntitySkillEvent.Started(runtime));
			case ACTIVATE -> NeoForge.EVENT_BUS.post(new EntitySkillEvent.Activated(runtime));
			case ENTER_RECOVERY -> NeoForge.EVENT_BUS.post(new EntitySkillEvent.RecoveryStarted(runtime));
			case RECOVERY_END -> NeoForge.EVENT_BUS.post(new EntitySkillEvent.Completed(runtime));
			case CANCEL -> NeoForge.EVENT_BUS.post(new EntitySkillEvent.Cancelled(runtime));
			case END -> {
			}
		}
		if (callback == Callback.RECOVERY_END || callback == Callback.CANCEL) {
			data.active.remove(runtime);
		}
	}

	public enum Callback {
		WINDUP_START(EntitySkillRuntime::onWindupStart),
		ACTIVATE(EntitySkillRuntime::onActivate),
		END(EntitySkillRuntime::onEnd),
		RECOVERY_END(EntitySkillRuntime::onRecoveryEnd),
		CANCEL(EntitySkillRuntime::onCancel),
		ENTER_RECOVERY(null);

		@Nullable
		private final RuntimeCallback callback;

		private Callback(@Nullable RuntimeCallback callback) {
			this.callback = callback;
		}

		private void run(EntitySkillRuntime<?> runtime) {
			if (callback != null) {
				callback.run(runtime);
			}
		}
	}

	@FunctionalInterface
	private interface RuntimeCallback {
		void run(EntitySkillRuntime<?> runtime);
	}
}
