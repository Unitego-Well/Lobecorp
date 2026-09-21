package org.unitego.lobecorp.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;

import static org.unitego.lobecorp.Lobecorp.id;

public record EntitySkillSyncPayload(
		int ownerId,
		Identifier skillId,
		Callback callback,
		EntitySkillRuntime.SkillState state,
		int ticksLeft,
		int activeTicks,
		boolean successful,
		int targetId
) implements ToPayload {
	/// 表示运行态没有目标实体的网络值。
	private static final int NO_TARGET = -1;
	/// 实体技能运行态同步载荷类型。
	public static final CustomPacketPayload.Type<EntitySkillSyncPayload> TYPE = new CustomPacketPayload.Type<>(id("entity_skill_sync"));
	/// 实体技能运行态同步载荷编解码器。
	public static final StreamCodec<RegistryFriendlyByteBuf, EntitySkillSyncPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull EntitySkillSyncPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new EntitySkillSyncPayload(
					buffer.readVarInt(),
					Identifier.STREAM_CODEC.decode(buffer),
					Callback.values()[buffer.readVarInt()],
					EntitySkillRuntime.SkillState.values()[buffer.readVarInt()],
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readBoolean(),
					buffer.readVarInt()
			);
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull EntitySkillSyncPayload payload) {
			buffer.writeVarInt(payload.ownerId);
			Identifier.STREAM_CODEC.encode(buffer, payload.skillId);
			buffer.writeVarInt(payload.callback.ordinal());
			buffer.writeVarInt(payload.state.ordinal());
			buffer.writeVarInt(payload.ticksLeft);
			buffer.writeVarInt(payload.activeTicks);
			buffer.writeBoolean(payload.successful);
			buffer.writeVarInt(payload.targetId);
		}
	};

	public static void send(EntitySkillRuntime<?> runtime, Callback callback) {
		Entity target = runtime.target();
		PacketDistributor.sendToPlayersTrackingEntity(runtime.owner(), new EntitySkillSyncPayload(
				runtime.owner().getId(),
				runtime.skill().id(),
				callback,
				runtime.state(),
				runtime.ticksLeft(),
				runtime.activeTicks(),
				runtime.isSuccessful(),
				target == null ? NO_TARGET : target.getId()
		));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context) {
		Entity entity = context.player().level().getEntity(ownerId);
		if (!(entity instanceof LivingEntity owner)) {
			return;
		}
		IEntitySkill<?> skill = EntitySkillManager.getSkills(owner).stream()
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
		EntitySkillRuntime<T> runtime = new EntitySkillRuntime<>(owner, (IEntitySkill<T>) skill, state, ticksLeft);
		runtime.setActiveTicks(activeTicks);
		if (successful) {
			runtime.markSuccessful();
		}
		if (targetId != NO_TARGET) {
			runtime.setTarget(owner.level().getEntity(targetId));
		}
		callback.run(runtime);
	}

	public enum Callback {
		WINDUP_START(EntitySkillRuntime::onWindupStart),
		ACTIVATE(EntitySkillRuntime::onActivate),
		END(EntitySkillRuntime::onEnd),
		RECOVERY_END(EntitySkillRuntime::onRecoveryEnd),
		CANCEL(EntitySkillRuntime::onCancel);

		private final RuntimeCallback callback;

		Callback(RuntimeCallback callback) {
			this.callback = callback;
		}

		private void run(EntitySkillRuntime<?> runtime) {
			callback.run(runtime);
		}
	}

	@FunctionalInterface
	private interface RuntimeCallback {
		void run(EntitySkillRuntime<?> runtime);
	}
}
