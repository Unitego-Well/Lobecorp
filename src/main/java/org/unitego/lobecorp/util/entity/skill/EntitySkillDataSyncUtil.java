package org.unitego.lobecorp.util.entity.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.unitego.lobecorp.network.tc.EntitySkillDataSyncPayload;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.EntitySkillState;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 技能持久化附件的同步入口，不创建或推进技能运行态。
/// 侧别检查读取游戏持有的共享世界，不在本工具中关闭。
public class EntitySkillDataSyncUtil {
	/// 缺失附件也发送空值，覆盖客户端可能保留的旧快照。
	public static void sendInitial(ServerPlayer player, LivingEntity entity) {
		var skills = entity.hasData(LcAttachmentTypes.ENTITY_SKILLS)
				? entity.getData(LcAttachmentTypes.ENTITY_SKILLS) : EntitySkillState.empty();
		Map<EntitySkillGroup, Integer> groups = entity.hasData(LcAttachmentTypes.ENTITY_SKILL_GROUPS)
				? entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS) : Map.of();
		Map<IEntitySkill<?>, Long> cooldowns = entity.hasData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS)
				? entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS) : Map.of();
		PacketDistributor.sendToPlayer(player, new EntitySkillDataSyncPayload(entity.getId(), true,
				skills.overrides(), Set.of(), groups, Set.of(), cooldowns, Set.of()));
	}

	@SuppressWarnings("resource")
	public static void sendSkillsChanged(LivingEntity entity, EntitySkillState before, EntitySkillState after) {
		if (entity.level().isClientSide() || before.equals(after)) {
			return;
		}

		send(entity, new EntitySkillDataSyncPayload(entity.getId(), false,
				updates(before.overrides(), after.overrides()), removals(before.overrides(), after.overrides()),
				Map.of(), Set.of(), Map.of(), Set.of()));
	}

	@SuppressWarnings("resource")
	public static void sendGroupsChanged(LivingEntity entity, Map<EntitySkillGroup, Integer> before,
	                                     Map<EntitySkillGroup, Integer> after) {
		if (entity.level().isClientSide() || before.equals(after)) {
			return;
		}

		send(entity, new EntitySkillDataSyncPayload(entity.getId(), false,
				Map.of(), Set.of(), updates(before, after), removals(before, after), Map.of(), Set.of()));
	}

	@SuppressWarnings("resource")
	public static void sendCooldownsChanged(LivingEntity entity, Map<IEntitySkill<?>, Long> before,
	                                        Map<IEntitySkill<?>, Long> after) {
		if (entity.level().isClientSide() || before.equals(after)) {
			return;
		}

		send(entity, new EntitySkillDataSyncPayload(entity.getId(), false,
				Map.of(), Set.of(), Map.of(), Set.of(), updates(before, after), removals(before, after)));
	}

	private static void send(LivingEntity entity, EntitySkillDataSyncPayload payload) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
	}

	/// 直接合并附件，不调用会触发技能取消、默认组补齐或再次发包的业务入口。
	@SuppressWarnings("resource")
	public static void apply(LivingEntity entity, EntitySkillDataSyncPayload payload) {
		if (!entity.level().isClientSide()) {
			return;
		}

		if (payload.full() || !payload.skills().isEmpty() || !payload.removedSkills().isEmpty()) {
			Map<IEntitySkill<?>, Boolean> before = entity.getData(LcAttachmentTypes.ENTITY_SKILLS).overrides();
			entity.setData(LcAttachmentTypes.ENTITY_SKILLS, new EntitySkillState(
					merge(payload.full(), before, payload.skills(), payload.removedSkills())));
		}

		if (payload.full() || !payload.groups().isEmpty() || !payload.removedGroups().isEmpty()) {
			entity.setData(LcAttachmentTypes.ENTITY_SKILL_GROUPS, Map.copyOf(merge(payload.full(),
					entity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS), payload.groups(), payload.removedGroups())));
		}

		if (payload.full() || !payload.cooldowns().isEmpty() || !payload.removedCooldowns().isEmpty()) {
			entity.setData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS, Map.copyOf(merge(payload.full(),
					entity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS), payload.cooldowns(), payload.removedCooldowns())));
		}
	}

	private static <K, V> Map<K, V> updates(Map<K, V> before, Map<K, V> after) {
		Map<K, V> updates = new LinkedHashMap<>();

		for (var entry : after.entrySet()) {
			if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
				updates.put(entry.getKey(), entry.getValue());
			}
		}

		return updates;
	}

	private static <K, V> Set<K> removals(Map<K, V> before, Map<K, V> after) {
		Set<K> removed = new LinkedHashSet<>(before.keySet());
		removed.removeAll(after.keySet());
		return removed;
	}

	private static <K, V> Map<K, V> merge(boolean full, Map<K, V> before, Map<K, V> updates, Set<K> removed) {
		Map<K, V> result = full ? new LinkedHashMap<>() : new LinkedHashMap<>(before);
		result.putAll(updates);
		removed.forEach(result::remove);
		return result;
	}
}
