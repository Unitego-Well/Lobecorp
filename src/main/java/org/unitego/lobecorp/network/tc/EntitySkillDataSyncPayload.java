package org.unitego.lobecorp.network.tc;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcRegistrys;
import org.unitego.lobecorp.util.entity.skill.EntitySkillDataSyncUtil;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// 技能附件的完整快照或条目变化；删除修正与显式 false 分别编码。
public record EntitySkillDataSyncPayload(
		int ownerId, boolean full,
		Map<IEntitySkill<?>, Boolean> skills, Set<IEntitySkill<?>> removedSkills,
		Map<EntitySkillGroup, Integer> groups, Set<EntitySkillGroup> removedGroups,
		Map<IEntitySkill<?>, Long> cooldowns, Set<IEntitySkill<?>> removedCooldowns
) implements IToClientPayload {
	public static final Type<EntitySkillDataSyncPayload> TYPE = Lobecorp.type("entity_skill_data_sync");
	private static final StreamCodec<RegistryFriendlyByteBuf, IEntitySkill<?>> SKILL_CODEC =
			ByteBufCodecs.registry(LcRegistrys.ENTITY_SKILL_KEY);
	private static final StreamCodec<RegistryFriendlyByteBuf, EntitySkillGroup> GROUP_CODEC =
			ByteBufCodecs.registry(LcRegistrys.ENTITY_SKILL_GROUP_KEY);
	private static final StreamCodec<RegistryFriendlyByteBuf, Map<IEntitySkill<?>, Boolean>> SKILLS_CODEC =
			ByteBufCodecs.map(LinkedHashMap::new, SKILL_CODEC, ByteBufCodecs.BOOL);
	private static final StreamCodec<RegistryFriendlyByteBuf, Set<IEntitySkill<?>>> SKILL_KEYS_CODEC =
			ByteBufCodecs.collection(LinkedHashSet::new, SKILL_CODEC);
	private static final StreamCodec<RegistryFriendlyByteBuf, Map<EntitySkillGroup, Integer>> GROUPS_CODEC =
			ByteBufCodecs.map(LinkedHashMap::new, GROUP_CODEC, ByteBufCodecs.VAR_INT);
	private static final StreamCodec<RegistryFriendlyByteBuf, Set<EntitySkillGroup>> GROUP_KEYS_CODEC =
			ByteBufCodecs.collection(LinkedHashSet::new, GROUP_CODEC);
	private static final StreamCodec<RegistryFriendlyByteBuf, Map<IEntitySkill<?>, Long>> COOLDOWNS_CODEC =
			ByteBufCodecs.map(LinkedHashMap::new, SKILL_CODEC, ByteBufCodecs.VAR_LONG);
	public static final StreamCodec<RegistryFriendlyByteBuf, EntitySkillDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull EntitySkillDataSyncPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new EntitySkillDataSyncPayload(buffer.readVarInt(), buffer.readBoolean(),
					SKILLS_CODEC.decode(buffer), SKILL_KEYS_CODEC.decode(buffer),
					GROUPS_CODEC.decode(buffer), GROUP_KEYS_CODEC.decode(buffer),
					COOLDOWNS_CODEC.decode(buffer), SKILL_KEYS_CODEC.decode(buffer));
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull EntitySkillDataSyncPayload payload) {
			buffer.writeVarInt(payload.ownerId);
			buffer.writeBoolean(payload.full);
			SKILLS_CODEC.encode(buffer, payload.skills);
			SKILL_KEYS_CODEC.encode(buffer, payload.removedSkills);
			GROUPS_CODEC.encode(buffer, payload.groups);
			GROUP_KEYS_CODEC.encode(buffer, payload.removedGroups);
			COOLDOWNS_CODEC.encode(buffer, payload.cooldowns);
			SKILL_KEYS_CODEC.encode(buffer, payload.removedCooldowns);
		}
	};

	public EntitySkillDataSyncPayload {
		skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
		removedSkills = Set.copyOf(removedSkills);
		groups = Map.copyOf(groups);
		removedGroups = Set.copyOf(removedGroups);
		cooldowns = Map.copyOf(cooldowns);
		removedCooldowns = Set.copyOf(removedCooldowns);
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/// 客户端世界由游戏持有，派发时不关闭。
	@Override
	@SuppressWarnings("resource")
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		if (player.level().getEntity(ownerId) instanceof LivingEntity entity) {
			EntitySkillDataSyncUtil.apply(entity, this);
		}
	}
}
