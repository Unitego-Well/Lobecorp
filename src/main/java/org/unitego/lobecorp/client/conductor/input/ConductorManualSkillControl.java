package org.unitego.lobecorp.client.conductor.input;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.conductor.render.ConductorCamera;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorHud;
import org.unitego.lobecorp.conductor.ability.EntitySkillConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.util.conductor.ConductorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 指挥家手动技能会话：只有选中单位可接管，取消选中恢复生物目标，松开左键仅暂停瞄准。
public class ConductorManualSkillControl {
	/// 手动施放等待客户端收到开始同步的最长时间，单位为 tick。
	private static final int START_WAIT_TICKS = 3 * TICKS_PER_SECOND;
	private static final Map<Key, Session> SESSIONS = new HashMap<>();

	public static void track(List<UUID> members, Identifier skill) {
		for (UUID member : members)
			SESSIONS.put(new Key(member, skill), new Session());
	}

	public static void tick(Minecraft minecraft, Function<Predicate<LivingEntity>, LivingEntity> pickTarget) {
		if (minecraft.level == null)
			return;
		var iterator = SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			Key key = entry.getKey();
			Session session = entry.getValue();
			ConductorData.Unit unit = ConductorClient.unit(key.member());
			if (unit == null || !(minecraft.level.getEntity(key.member()) instanceof Mob mob) || !mob.isAlive()
					|| !(ConductorUtil.ability(mob, key.skill()) instanceof EntitySkillConductorAbility ability)) {
				if (session.manual)
					send(key, ConductorCommandPayload.Action.END_MANUAL_CONTROL);
				iterator.remove();
				continue;
			}
			if (ability.isCasting(mob))
				session.waitTicks = 0;
			else if (session.waitTicks == 0) {
				if (session.manual)
					send(key, ConductorCommandPayload.Action.END_MANUAL_CONTROL);
				iterator.remove();
				continue;
			} else
				session.waitTicks--;
			if (!ConductorControls.isSelected(key.member())) {
				if (session.manual)
					send(key, ConductorCommandPayload.Action.END_MANUAL_CONTROL);
				session.manual = false;
				session.following = false;
				continue;
			}
			if (minecraft.screen != null || !minecraft.isWindowActive() || ConductorHud.INSTANCE.contains(minecraft)
					|| GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
				if (session.following)
					send(key, ConductorCommandPayload.Action.AIM);
				session.following = false;
				continue;
			}
			LivingEntity target = pickTarget.apply(entity -> entity != minecraft.player && entity != mob
					&& ability.canTarget(mob, entity) && ability.isWithinRange(mob, entity.position())
					&& !ConductorClient.snapshot().allied(mob.getUUID(), entity.getUUID()));
			Vec3 position = ConductorCamera.pickGround(minecraft);
			if (target != null)
				position = target.getBoundingBox().getCenter();
			if (position == null)
				continue;
			ConductorClient.sendTarget(ConductorCommandPayload.Action.AIM, unit.team(), "", List.of(key.member()),
					target == null ? null : target.getUUID(), position.x, position.y, position.z,
					ConductorData.ControlMode.FULL, true, key.skill().toString(),
					target == null ? ConductorCommandPayload.TargetSelection.POSITION : ConductorCommandPayload.TargetSelection.ENTITY);
			session.manual = true;
			session.following = true;
		}
	}

	public static void clear() {
		SESSIONS.forEach((key, session) -> {
			if (session.manual)
				send(key, ConductorCommandPayload.Action.END_MANUAL_CONTROL);
		});
		SESSIONS.clear();
	}

	private static void send(Key key, ConductorCommandPayload.Action action) {
		ConductorData.Unit unit = ConductorClient.unit(key.member());
		if (unit != null)
			ConductorClient.sendTarget(action, unit.team(), "", List.of(key.member()),
					null, 0.0, 0.0, 0.0, ConductorData.ControlMode.FULL, false,
					key.skill().toString(), ConductorCommandPayload.TargetSelection.NONE);
	}

	private record Key(UUID member, Identifier skill) {
	}

	private static class Session {
		private int waitTicks = START_WAIT_TICKS;
		private boolean manual;
		private boolean following;
	}
}
