package org.unitego.lobecorp.conductor.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.unitego.lobecorp.conductor.config.ConductorRules;

import java.util.UUID;

/// 单个编队单位的持久化附件字段与网络快照转换。
public class ConductorUnitData implements ValueIOSerializable {
	public static final StreamCodec<RegistryFriendlyByteBuf, ConductorUnitData> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ConductorUnitData decode(RegistryFriendlyByteBuf buffer) {
			ConductorUnitData data = new ConductorUnitData();
			data.read(ByteBufCodecs.fromCodecWithRegistries(ConductorData.Unit.CODEC).decode(buffer));
			data.revision = buffer.readVarLong();
			return data;
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ConductorUnitData data) {
			ByteBufCodecs.fromCodecWithRegistries(ConductorData.Unit.CODEC).encode(buffer, data.snapshot("", 0, 0));
			buffer.writeVarLong(data.revision);
		}
	};
	private static final String STATE_KEY = "state";
	private static final String REVISION_KEY = "revision";
	public String team = "";
	public ConductorData.CombatBehavior behavior = ConductorData.CombatBehavior.NEUTRAL;
	public ConductorData.BehaviorState activity = ConductorData.BehaviorState.GUARD;
	public ConductorData.AttackState attack = ConductorData.AttackState.AUTO;
	public ConductorData.OrderType order = ConductorData.OrderType.NONE;
	public UUID target;
	public Vec3 destination = Vec3.ZERO;
	public Vec3 origin;
	public Identifier commandedSkill;
	public ConductorData.MoveResult moveResult = ConductorData.MoveResult.NONE;
	public double movementSpeed = ConductorRules.NATURAL_MOVEMENT_SPEED;
	public long revision;

	public void command(ConductorData.OrderType nextOrder, UUID nextTarget, Vec3 position) {
		order = nextOrder;
		target = nextTarget;
		destination = position;
		commandedSkill = null;
		moveResult = ConductorData.MoveResult.NONE;
		movementSpeed = ConductorRules.NATURAL_MOVEMENT_SPEED;
	}

	public ConductorData.Unit snapshot(Mob mob) {
		return snapshot(mob.level().dimension().identifier().toString(), mob.chunkPosition().x(), mob.chunkPosition().z());
	}

	public ConductorData.Unit snapshot(String dimension, int chunkX, int chunkZ) {
		return new ConductorData.Unit(dimension, team, activity, order, target == null ? "" : target.toString(),
				destination.x, destination.y, destination.z, chunkX, chunkZ, attack,
				commandedSkill == null ? "" : commandedSkill.toString(),
				origin == null ? null : ConductorData.Position.of(origin), moveResult, movementSpeed, behavior);
	}

	public void read(ConductorData.Unit value) {
		team = value.team();
		behavior = value.combatBehavior();
		activity = value.behaviorState();
		attack = value.attackState();
		order = value.order();
		target = value.target().isEmpty() ? null : UUID.fromString(value.target());
		destination = new Vec3(value.x(), value.y(), value.z());
		origin = value.origin() == null ? null : value.origin().vector();
		commandedSkill = value.commandedSkill().isEmpty() ? null : Identifier.parse(value.commandedSkill());
		moveResult = value.moveResult();
		movementSpeed = value.movementSpeed();
	}

	@Override
	public void serialize(ValueOutput output) {
		output.store(STATE_KEY, ConductorData.Unit.CODEC, snapshot("", 0, 0));
		output.putLong(REVISION_KEY, revision);
	}

	@Override
	public void deserialize(ValueInput input) {
		input.read(STATE_KEY, ConductorData.Unit.CODEC).ifPresent(this::read);
		revision = input.getLongOr(REVISION_KEY, 0);
		if (order == ConductorData.OrderType.ATTACK_POINT || commandedSkill != null) {
			order = ConductorData.OrderType.NONE;
			target = null;
			commandedSkill = null;
		}
	}
}
