package org.unitego.lobecorp.registry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityState;
import org.unitego.lobecorp.conductor.control.ConductorUnitRuntime;
import org.unitego.lobecorp.conductor.data.ConductorUnitData;
import org.unitego.lobecorp.world.entity.skill.EntitySkillGroup;
import org.unitego.lobecorp.world.entity.skill.EntitySkillCooldownClock;
import org.unitego.lobecorp.world.entity.control.EntityGrabState;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntimeData;
import org.unitego.lobecorp.world.entity.skill.EntitySkillState;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.world.entity.skill.effect.EntitySkillEffectLevelData;
import org.unitego.lobecorp.world.hitbox.HitboxLevelData;
import org.unitego.lobecorp.particle.text.TextParticleLevelData;

import java.util.Map;

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

	/// Level 中不持久化的服务端实体技能效果。
	DeferredHolder<AttachmentType<?>, AttachmentType<EntitySkillEffectLevelData>> ENTITY_SKILL_EFFECT_LEVEL_DATA =
			REGISTER.register("entity_skill_effect_level_data", () -> AttachmentType.builder(EntitySkillEffectLevelData::new).build());

	/// 服务端 Level 中不持久化的可修改文本展示实例。
	DeferredHolder<AttachmentType<?>, AttachmentType<TextParticleLevelData>> TEXT_PARTICLE_LEVEL_DATA =
			REGISTER.register("text_particle_level_data", () -> AttachmentType.builder(TextParticleLevelData::new).build());

	/// 实体技能的增删修正，持久化并通过技能数据包同步；新键不读取旧的完整技能集合。
	DeferredHolder<AttachmentType<?>, AttachmentType<EntitySkillState>> ENTITY_SKILLS = REGISTER.register(
			"entity_skill_patch", () -> AttachmentType.builder(EntitySkillState::empty)
					.serialize(EntitySkillState.CODEC.fieldOf("skills"))
					.build());

	/// 实体动态拥有的技能组及其同时运行上限，持久化并通过技能数据包同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Map<EntitySkillGroup, Integer>>> ENTITY_SKILL_GROUPS = REGISTER.register(
			"entity_skill_groups", () -> AttachmentType.builder(Map::<EntitySkillGroup, Integer>of)
					.serialize(LcCodecs.ENTITY_SKILL_GROUP_MAP_CODEC.fieldOf("groups"))
					.build());

	/// 当前运行的技能。运行态不持久化，生命周期变化由技能同步载荷发送。
	DeferredHolder<AttachmentType<?>, AttachmentType<EntitySkillRuntimeData>> ACTIVE_ENTITY_SKILLS = REGISTER.register(
			"active_entity_skills", () -> AttachmentType.builder(EntitySkillRuntimeData::new).build());

	/// 技能冷却，持久化并通过技能数据包同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Map<IEntitySkill<?>, Long>>> ENTITY_SKILL_COOLDOWNS = REGISTER.register(
			"entity_skill_cooldowns", () -> AttachmentType.builder(Map::<IEntitySkill<?>, Long>of)
					.serialize(LcCodecs.ENTITY_SKILL_COOLDOWN_MAP_CODEC.fieldOf("cooldowns"))
					.build());

	/// 技能冷却的速度计时基准与小数进度，仅持久化；显示沿用冷却到期时间。
	DeferredHolder<AttachmentType<?>, AttachmentType<EntitySkillCooldownClock>> ENTITY_SKILL_COOLDOWN_CLOCK = REGISTER.register(
			"entity_skill_cooldown_clock", () -> AttachmentType.builder(EntitySkillCooldownClock::new)
					.serialize(EntitySkillCooldownClock.CODEC.fieldOf("clock")).build());

	/// 实体临时抓取关系，只同步当前移动限制，不持久化，也不随死亡复制。
	DeferredHolder<AttachmentType<?>, AttachmentType<EntityGrabState>> ENTITY_GRAB = REGISTER.register(
			"entity_grab", () -> AttachmentType.builder(EntityGrabState::new).sync(EntityGrabState.STREAM_CODEC).build());

	/// 实体持有的指挥家能力资格，持久化并通过指挥家数据包同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<ConductorAbilityState>> CONDUCTOR_ABILITIES = REGISTER.register(
			"conductor_ability_patch", () -> AttachmentType.builder(ConductorAbilityState::empty)
					.serialize(ConductorAbilityState.CODEC.fieldOf("abilities"))
					.build());

	/// 攻击段数，运行时不持久化但同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ATTACK_COMBO = REGISTER.register(
			"attack_combo", () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

	/// 实体指挥控制状态，持久化并通过指挥家数据包同步。
	DeferredHolder<AttachmentType<?>, AttachmentType<ConductorUnitData>> CONDUCTOR_UNIT = REGISTER.register(
			"conductor_unit", () -> AttachmentType.serializable(ConductorUnitData::new).build());
	/// 实体指挥控制的运行期上下文，不作为持久化状态保存。
	DeferredHolder<AttachmentType<?>, AttachmentType<ConductorUnitRuntime>> CONDUCTOR_RUNTIME = REGISTER.register(
			"conductor_runtime", () -> AttachmentType.builder(ConductorUnitRuntime::new).build());

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
