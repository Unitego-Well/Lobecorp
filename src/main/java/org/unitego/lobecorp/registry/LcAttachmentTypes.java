package org.unitego.lobecorp.registry;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillGroup;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkillHolder;
import org.unitego.lobecorp.hitbox.HitboxLevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/// NeoForge 数据附件
public interface LcAttachmentTypes {
	DeferredRegister<AttachmentType<?>> REGISTER =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Lobecorp.NAMESPACE);

	/// 尸体上临时存储的重组进度
	DeferredHolder<AttachmentType<?>, AttachmentType<Float>> REASSEMBLY_PROGRESS =
			REGISTER.register("reassembly_progress", () -> AttachmentType.builder(() -> 0.0F).build());

	/// Level 中不持久化的服务端判断框和客户端调试镜像。
	DeferredHolder<AttachmentType<?>, AttachmentType<HitboxLevelData>> HITBOX_LEVEL_DATA =
			REGISTER.register("hitbox_level_data", () -> AttachmentType.builder(HitboxLevelData::new).build());

	/// 实体拥有的技能，持久化并同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Set<IEntitySkill<?>>>> ENTITY_SKILLS = REGISTER.register(
			"entity_skills", () -> AttachmentType.builder(() -> Set.<IEntitySkill<?>>of())
					.serialize(LcCodecs.ENTITY_SKILL_SET_CODEC.fieldOf("skills"))
					.sync((holder, player) -> !(holder instanceof IEntitySkillHolder skillHolder)
							|| skillHolder.shouldSyncEntitySkills(player), ByteBufCodecs.fromCodecWithRegistries(LcCodecs.ENTITY_SKILL_SET_CODEC))
					.build());

	/// 实体动态拥有的技能组及其同时运行上限，持久化并同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Map<EntitySkillGroup, Integer>>> ENTITY_SKILL_GROUPS = REGISTER.register(
			"entity_skill_groups", () -> AttachmentType.builder(Map::<EntitySkillGroup, Integer>of)
					.serialize(LcCodecs.ENTITY_SKILL_GROUP_MAP_CODEC.fieldOf("groups"))
					.sync(ByteBufCodecs.fromCodecWithRegistries(LcCodecs.ENTITY_SKILL_GROUP_MAP_CODEC))
					.build());

	/// 当前运行的技能。运行态不持久化，生命周期变化由技能同步载荷发送。
	DeferredHolder<AttachmentType<?>, AttachmentType<List<EntitySkillRuntime<?>>>> ACTIVE_ENTITY_SKILLS = REGISTER.register(
			"active_entity_skills", () -> AttachmentType.builder(
					(Supplier<List<EntitySkillRuntime<?>>>) ArrayList::new).build());

	/// 技能冷却，持久化并同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Map<IEntitySkill<?>, Long>>> ENTITY_SKILL_COOLDOWNS = REGISTER.register(
			"entity_skill_cooldowns", () -> AttachmentType.builder(Map::<IEntitySkill<?>, Long>of)
					.serialize(LcCodecs.ENTITY_SKILL_COOLDOWN_MAP_CODEC.fieldOf("cooldowns"))
					.sync(ByteBufCodecs.fromCodecWithRegistries(LcCodecs.ENTITY_SKILL_COOLDOWN_MAP_CODEC))
					.build());

	/// 攻击段数，运行时不持久化但同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ATTACK_COMBO = REGISTER.register(
			"attack_combo", () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
