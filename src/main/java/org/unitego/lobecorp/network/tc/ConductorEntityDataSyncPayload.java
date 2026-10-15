package org.unitego.lobecorp.network.tc;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityState;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorData.Unit;
import org.unitego.lobecorp.conductor.data.ConductorUnitData;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// 已加载实体的指挥家附件快照和即时变化；远程目录仍由目录载荷同步。
public record ConductorEntityDataSyncPayload(int entityId, UUID entityUuid, ChangeType changeType,
                                             @Nullable ConductorUnitData unit, List<UnitField> fields,
                                             ConductorAbilityState abilities, List<AbilityChange> abilityChanges)
		implements IToClientPayload {
	public static final Type<ConductorEntityDataSyncPayload> TYPE = Lobecorp.type("conductor_entity_data_sync");
	private static final StreamCodec<RegistryFriendlyByteBuf, ConductorAbilityState> ABILITIES_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(ConductorAbilityState.CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, ConductorEntityDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull ConductorEntityDataSyncPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			int entityId = buffer.readVarInt();
			UUID entityUuid = buffer.readUUID();
			ChangeType changeType = buffer.readEnum(ChangeType.class);
			ConductorUnitData unit = null;
			List<UnitField> fields = List.of();
			ConductorAbilityState abilities = ConductorAbilityState.empty();
			List<AbilityChange> abilityChanges = List.of();

			switch (changeType) {
				case FULL -> {
					if (buffer.readBoolean()) {
						unit = ConductorUnitData.STREAM_CODEC.decode(buffer);
					}

					abilities = ABILITIES_CODEC.decode(buffer);
				}
				case UNIT -> {
					unit = new ConductorUnitData();
					unit.revision = buffer.readVarLong();
					var changed = new ArrayList<UnitField>();
					int count = buffer.readVarInt();

					for (int index = 0; index < count; index++) {
						UnitField field = buffer.readEnum(UnitField.class);
						field.read(buffer, unit);
						changed.add(field);
					}

					fields = List.copyOf(changed);
				}
				case ABILITIES -> {
					var changes = new ArrayList<AbilityChange>();
					int count = buffer.readVarInt();

					for (int index = 0; index < count; index++) {
						Identifier id = buffer.readIdentifier();
						Boolean owned = buffer.readBoolean() ? buffer.readBoolean() : null;
						changes.add(new AbilityChange(id, owned));
					}

					abilityChanges = List.copyOf(changes);
				}
				case REMOVE_UNIT -> {
				}
			}

			return new ConductorEntityDataSyncPayload(entityId, entityUuid, changeType, unit, fields, abilities, abilityChanges);
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull ConductorEntityDataSyncPayload payload) {
			buffer.writeVarInt(payload.entityId);
			buffer.writeUUID(payload.entityUuid);
			buffer.writeEnum(payload.changeType);

			switch (payload.changeType) {
				case FULL -> {
					buffer.writeBoolean(payload.unit != null);

					if (payload.unit != null) {
						ConductorUnitData.STREAM_CODEC.encode(buffer, payload.unit);
					}

					ABILITIES_CODEC.encode(buffer, payload.abilities);
				}
				case UNIT -> {
					ConductorUnitData data = Objects.requireNonNull(payload.unit);
					buffer.writeVarLong(data.revision);
					buffer.writeVarInt(payload.fields.size());

					for (UnitField field : payload.fields) {
						buffer.writeEnum(field);
						field.write(buffer, data);
					}
				}
				case ABILITIES -> {
					buffer.writeVarInt(payload.abilityChanges.size());

					for (AbilityChange change : payload.abilityChanges) {
						buffer.writeIdentifier(change.id);
						buffer.writeBoolean(change.owned != null);

						if (change.owned != null) {
							buffer.writeBoolean(change.owned);
						}
					}
				}
				case REMOVE_UNIT -> {
				}
			}
		}
	};

	public static ConductorEntityDataSyncPayload full(Entity entity, @Nullable ConductorUnitData unit,
	                                                  ConductorAbilityState abilities) {
		return new ConductorEntityDataSyncPayload(entity.getId(), entity.getUUID(), ChangeType.FULL,
				copy(unit), List.of(), abilities, List.of());
	}

	public static ConductorEntityDataSyncPayload unit(Entity entity, @Nullable Unit before,
	                                                  ConductorUnitData state) {
		List<UnitField> fields = new ArrayList<>();

		for (UnitField field : UnitField.values()) {
			if (before == null || field.changed(before, state)) {
				fields.add(field);
			}
		}

		return new ConductorEntityDataSyncPayload(entity.getId(), entity.getUUID(), ChangeType.UNIT,
				copy(state), List.copyOf(fields), ConductorAbilityState.empty(), List.of());
	}

	public static ConductorEntityDataSyncPayload removeUnit(Entity entity) {
		return new ConductorEntityDataSyncPayload(entity.getId(), entity.getUUID(), ChangeType.REMOVE_UNIT,
				null, List.of(), ConductorAbilityState.empty(), List.of());
	}

	public static ConductorEntityDataSyncPayload abilities(Entity entity, List<AbilityChange> changes) {
		return new ConductorEntityDataSyncPayload(entity.getId(), entity.getUUID(), ChangeType.ABILITIES,
				null, List.of(), ConductorAbilityState.empty(), List.copyOf(changes));
	}

	private static @Nullable ConductorUnitData copy(@Nullable ConductorUnitData source) {
		if (source == null) {
			return null;
		}

		var copy = new ConductorUnitData();
		copy.read(source.snapshot("", 0, 0));
		copy.revision = source.revision;
		return copy;
	}

	// Level 由玩家持有，收包入口不能关闭共享客户端世界。
	@SuppressWarnings("resource")
	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		ConductorAttachmentUtil.applySync(player.level().getEntity(entityId), this);
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public enum ChangeType {
		FULL, UNIT, REMOVE_UNIT, ABILITIES
	}

	public enum UnitField {
		TEAM, BEHAVIOR, ACTIVITY, ATTACK, ORDER, TARGET, DESTINATION, ORIGIN, COMMANDED_SKILL, MOVE_RESULT, MOVEMENT_SPEED;

		private boolean changed(Unit before, ConductorUnitData next) {
			return switch (this) {
				case TEAM -> !before.team().equals(next.team);
				case BEHAVIOR -> before.combatBehavior() != next.behavior;
				case ACTIVITY -> before.behaviorState() != next.activity;
				case ATTACK -> before.attackState() != next.attack;
				case ORDER -> before.order() != next.order;
				case TARGET -> !before.target().equals(next.target == null ? "" : next.target.toString());
				case DESTINATION -> before.x() != next.destination.x || before.y() != next.destination.y
						|| before.z() != next.destination.z;
				case ORIGIN -> !Objects.equals(before.origin(),
						next.origin == null ? null : ConductorData.Position.of(next.origin));
				case COMMANDED_SKILL ->
						!before.commandedSkill().equals(next.commandedSkill == null ? "" : next.commandedSkill.toString());
				case MOVE_RESULT -> before.moveResult() != next.moveResult;
				case MOVEMENT_SPEED -> before.movementSpeed() != next.movementSpeed;
			};
		}

		private void read(RegistryFriendlyByteBuf buffer, ConductorUnitData data) {
			switch (this) {
				case TEAM -> data.team = buffer.readUtf();
				case BEHAVIOR -> data.behavior = buffer.readEnum(ConductorData.CombatBehavior.class);
				case ACTIVITY -> data.activity = buffer.readEnum(ConductorData.BehaviorState.class);
				case ATTACK -> data.attack = buffer.readEnum(ConductorData.AttackState.class);
				case ORDER -> data.order = buffer.readEnum(ConductorData.OrderType.class);
				case TARGET -> data.target = buffer.readBoolean() ? buffer.readUUID() : null;
				case DESTINATION ->
						data.destination = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
				case ORIGIN -> data.origin = buffer.readBoolean()
						? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) : null;
				case COMMANDED_SKILL -> data.commandedSkill = buffer.readBoolean() ? buffer.readIdentifier() : null;
				case MOVE_RESULT -> data.moveResult = buffer.readEnum(ConductorData.MoveResult.class);
				case MOVEMENT_SPEED -> data.movementSpeed = buffer.readDouble();
			}
		}

		private void write(RegistryFriendlyByteBuf buffer, ConductorUnitData data) {
			switch (this) {
				case TEAM -> buffer.writeUtf(data.team);
				case BEHAVIOR -> buffer.writeEnum(data.behavior);
				case ACTIVITY -> buffer.writeEnum(data.activity);
				case ATTACK -> buffer.writeEnum(data.attack);
				case ORDER -> buffer.writeEnum(data.order);
				case TARGET -> {
					buffer.writeBoolean(data.target != null);

					if (data.target != null) {
						buffer.writeUUID(data.target);
					}
				}
				case DESTINATION -> {
					buffer.writeDouble(data.destination.x);
					buffer.writeDouble(data.destination.y);
					buffer.writeDouble(data.destination.z);
				}
				case ORIGIN -> {
					buffer.writeBoolean(data.origin != null);

					if (data.origin != null) {
						buffer.writeDouble(data.origin.x);
						buffer.writeDouble(data.origin.y);
						buffer.writeDouble(data.origin.z);
					}
				}
				case COMMANDED_SKILL -> {
					buffer.writeBoolean(data.commandedSkill != null);

					if (data.commandedSkill != null) {
						buffer.writeIdentifier(data.commandedSkill);
					}
				}
				case MOVE_RESULT -> buffer.writeEnum(data.moveResult);
				case MOVEMENT_SPEED -> buffer.writeDouble(data.movementSpeed);
			}
		}

		public void apply(ConductorUnitData source, ConductorUnitData destination) {
			switch (this) {
				case TEAM -> destination.team = source.team;
				case BEHAVIOR -> destination.behavior = source.behavior;
				case ACTIVITY -> destination.activity = source.activity;
				case ATTACK -> destination.attack = source.attack;
				case ORDER -> destination.order = source.order;
				case TARGET -> destination.target = source.target;
				case DESTINATION -> destination.destination = source.destination;
				case ORIGIN -> destination.origin = source.origin;
				case COMMANDED_SKILL -> destination.commandedSkill = source.commandedSkill;
				case MOVE_RESULT -> destination.moveResult = source.moveResult;
				case MOVEMENT_SPEED -> destination.movementSpeed = source.movementSpeed;
			}
		}
	}

	/// owned 为 null 时删除修正并恢复默认；false 仍是显式移除能力。
	public record AbilityChange(Identifier id, @Nullable Boolean owned) {
	}
}
