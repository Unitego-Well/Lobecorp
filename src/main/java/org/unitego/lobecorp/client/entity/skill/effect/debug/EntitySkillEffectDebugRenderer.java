package org.unitego.lobecorp.client.entity.skill.effect.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.registry.world.LcAttachmentTypes;
import org.unitego.lobecorp.registry.translation.DebugTranslationKeys;
import org.unitego.lobecorp.world.entity.skill.effect.EntitySkillEffectDebugInfo;
import org.unitego.lobecorp.world.hitbox.HitboxSnapshot;
import org.unitego.lobecorp.world.hitbox.geometry.CylinderSize;

public class EntitySkillEffectDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	/// 技能实体文本的最大渲染距离平方。
	private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0 * 64.0;
	/// 技能实体文本与效果顶部的距离。
	private static final double TEXT_HEIGHT_OFFSET = 0.5;
	/// 相邻调试文本行的高度间隔。
	private static final double TEXT_LINE_SPACING = 0.25;
	/// 技能实体调试文本颜色。
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	private final Minecraft minecraft;

	public EntitySkillEffectDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	private static void emitLine(String key, String value, Vec3 position, int line) {
		String text = Component.translatable(
				key, value).getString();
		Gizmos.billboardText(text, position.add(0.0, -line * TEXT_LINE_SPACING, 0.0),
				TextGizmo.Style.forColorAndCentered(TEXT_COLOR));
	}

	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess debugValues,
	                       @NonNull Frustum frustum, float partialTicks) {
		if (minecraft.level == null) {
			return;
		}

		var cameraPosition = new Vec3(camX, camY, camZ);

		for (HitboxSnapshot snapshot : minecraft.level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA)
				.clientSnapshots()) {
			EntitySkillEffectDebugInfo debugInfo = snapshot.entitySkillEffectDebugInfo();

			if (debugInfo == null || snapshot.position().distanceToSqr(cameraPosition) > MAX_RENDER_DISTANCE_SQUARED) {
				continue;
			}

			double heightOffset = snapshot.size() instanceof CylinderSize cylinder
					? cylinder.height() / 2.0 + TEXT_HEIGHT_OFFSET : TEXT_HEIGHT_OFFSET;
			Vec3 textPosition = snapshot.position().add(0.0, heightOffset, 0.0);
			int ageTicks = debugInfo.lifetimeTicks() - snapshot.remainingTicks();
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_NAME, debugInfo.name(), textPosition, 0);
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_OWNER, debugInfo.owner(), textPosition, 1);
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_SKILL, debugInfo.skill(), textPosition, 2);
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_POSITION, snapshot.position().toString(), textPosition, 3);
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_LIFETIME, ageTicks + "/" + debugInfo.lifetimeTicks() + "t", textPosition, 4);
			emitLine(DebugTranslationKeys.DEBUG_LOBECORP_SKILL_EFFECT_LOBECORP_HITBOX, snapshot.size().toString(), textPosition, 5);
		}
	}
}
