package org.unitego.lobecorp.entity.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillGroup;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.LcRegistrys;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EntitySkillDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	/// 调试技能文本的最大渲染距离平方。
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0 * 64.0;
	/// 第一行调试文本高于实体碰撞箱的偏移量。
	private static final double TEXT_HEIGHT_OFFSET = 0.5;
	/// 相邻调试文本行的高度间隔。
	private static final double TEXT_LINE_SPACING = 0.25;
	/// 调试技能文本颜色。
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	/// 当前技能列表文本前缀。
	private static final String ACTIVE_SKILLS_PREFIX = "active: ";
	/// 技能组列表文本前缀。
	private static final String GROUPS_PREFIX = "groups: ";
	/// 技能冷却列表文本前缀。
	private static final String COOLDOWNS_PREFIX = "cooldowns: ";
	/// tick 数的文本后缀。
	private static final String TICKS_SUFFIX = "t";

	private final Minecraft minecraft;

	public EntitySkillDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess debugValues,
			@NonNull Frustum frustum, float partialTicks) {
		if (minecraft.level == null) {
			return;
		}

		Vec3 cameraPosition = new Vec3(camX, camY, camZ);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity livingEntity)
					|| entity.position().distanceToSqr(cameraPosition) > MAX_RENDER_DISTANCE_SQUARED) {
				continue;
			}

			List<EntitySkillRuntime<?>> activeSkills = livingEntity.hasData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS)
					? livingEntity.getData(LcAttachmentTypes.ACTIVE_ENTITY_SKILLS) : List.of();
			Map<EntitySkillGroup, Integer> groups = livingEntity.hasData(LcAttachmentTypes.ENTITY_SKILL_GROUPS)
					? livingEntity.getData(LcAttachmentTypes.ENTITY_SKILL_GROUPS) : Map.of();
			Map<IEntitySkill<?>, Long> cooldowns = livingEntity.hasData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS)
					? livingEntity.getData(LcAttachmentTypes.ENTITY_SKILL_COOLDOWNS) : Map.of();
			long gameTime = livingEntity.level().getGameTime();
			if (activeSkills.isEmpty() && groups.isEmpty()
					&& cooldowns.values().stream().noneMatch(endTime -> endTime > gameTime)) {
				continue;
			}

			String activeSkillText = activeSkills.stream()
					.map(runtime -> runtime.skill().id() + " [" + runtime.state() + " "
							+ runtime.ticksLeft() + TICKS_SUFFIX + "] @ " + groupId(runtime.skill().group()))
					.collect(Collectors.joining(", ", ACTIVE_SKILLS_PREFIX, ""));
			String groupText = groups.entrySet().stream()
					.map(entry -> groupId(entry.getKey()) + "=" + entry.getValue())
					.sorted()
					.collect(Collectors.joining(", ", GROUPS_PREFIX, ""));
			List<String> cooldownTexts = cooldowns.entrySet().stream()
					.filter(entry -> entry.getValue() > gameTime)
					.map(entry -> entry.getKey().id() + "=" + (entry.getValue() - gameTime) + TICKS_SUFFIX)
					.sorted()
					.toList();
			Vec3 textPosition = new Vec3(entity.getX(), entity.getBoundingBox().maxY + TEXT_HEIGHT_OFFSET, entity.getZ());
			emitLine(activeSkillText, textPosition, 0);
			emitLine(groupText, textPosition, 1);
			for (int index = 0; index < cooldownTexts.size(); index++) {
				emitLine(COOLDOWNS_PREFIX + cooldownTexts.get(index), textPosition, index + 2);
			}
		}
	}

	private static Identifier groupId(EntitySkillGroup group) {
		return LcRegistrys.ENTITY_SKILL_GROUP.getKey(group);
	}

	private static void emitLine(String text, Vec3 position, int line) {
		Gizmos.billboardText(text, position.add(0.0, line * TEXT_LINE_SPACING, 0.0),
				TextGizmo.Style.forColorAndCentered(TEXT_COLOR));
	}
}
