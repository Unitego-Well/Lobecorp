package org.unitego.lobecorp.registry.entity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryNoop;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.client.debug.EntitySkillDebugRenderer;
import org.unitego.lobecorp.entity.client.debug.EntityStateDebugRenderer;
import org.unitego.lobecorp.hitbox.client.HitboxDebugRenderer;

import static org.unitego.lobecorp.Lobecorp.id;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class LcDebugEntries {
	/// 实体状态调试项标识符。
	public static final Identifier ENTITY_STATES = id("entity_states");
	/// 实体技能调试项标识符。
	public static final Identifier ENTITY_SKILLS = id("entity_skills");
	/// 判断框调试项标识符。
	public static final Identifier HITBOXES = id("hitboxes");
	/// 原版总调试渲染开关标识符。
	public static final Identifier DEBUG_ENABLED = id("mc_debug_enabled");
	/// 原版寻路调试项标识符。
	public static final Identifier DEBUG_PATHFINDING = id("mc_debug_pathfinding");
	/// 原版目标选择器调试项标识符。
	public static final Identifier DEBUG_GOAL_SELECTOR = id("mc_debug_goal_selector");
	/// 原版本地服务端实体碰撞箱调试项标识符。
	public static final Identifier DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES = id("mc_debug_show_local_server_entity_hit_boxes");
	/// 原版形状调试项标识符。
	public static final Identifier DEBUG_SHAPES = id("mc_debug_shapes");
	/// 原版 tick 时间监控调试项标识符。
	public static final Identifier DEBUG_MONITOR_TICK_TIMES = id("mc_debug_monitor_tick_times");

	@SubscribeEvent
	public static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
		event.register(ENTITY_STATES, new DebugEntryNoop());
		event.register(ENTITY_SKILLS, new DebugEntryNoop());
		event.register(HITBOXES, new DebugEntryNoop());
		event.register(DEBUG_ENABLED, new DebugEntryNoop());
		event.register(DEBUG_PATHFINDING, new DebugEntryNoop());
		event.register(DEBUG_GOAL_SELECTOR, new DebugEntryNoop());
		event.register(DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES, new DebugEntryNoop());
		event.register(DEBUG_SHAPES, new DebugEntryNoop());
		event.register(DEBUG_MONITOR_TICK_TIMES, new DebugEntryNoop());
	}

	@SubscribeEvent
	public static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
		if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(ENTITY_STATES)) {
			event.register(EntityStateDebugRenderer::new);
		}
		if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(ENTITY_SKILLS)) {
			event.register(EntitySkillDebugRenderer::new);
		}
		if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(HITBOXES)) {
			event.register(HitboxDebugRenderer::new);
		}
		boolean localServerHitboxesEnabled = isDebugRenderingEnabled(DEBUG_SHOW_LOCAL_SERVER_ENTITY_HIT_BOXES);
		boolean clientHitboxesEnabled = isEnabled(DebugScreenEntries.ENTITY_HITBOXES);
		if (localServerHitboxesEnabled && !clientHitboxesEnabled) {
			event.register(EntityHitboxDebugRenderer::new);
		}
	}

	public static boolean isEnabled(Identifier entry) {
		return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(entry);
	}

	public static boolean isDebugRenderingEnabled(Identifier entry) {
		return isEnabled(DEBUG_ENABLED) || isEnabled(entry);
	}
}
