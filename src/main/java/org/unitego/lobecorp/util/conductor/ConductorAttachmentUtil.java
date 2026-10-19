package org.unitego.lobecorp.util.conductor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityState;
import org.unitego.lobecorp.conductor.control.ConductorUnitRuntime;
import org.unitego.lobecorp.conductor.data.ConductorData.Unit;
import org.unitego.lobecorp.conductor.data.ConductorUnitData;
import org.unitego.lobecorp.network.tc.ConductorEntityDataSyncPayload;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

/// 指挥家实体附件的统一访问入口。
///
/// 该类只封装附件读写和同步，不复制状态，也不保存实体引用；持续状态仍由
/// NeoForge Data Attachment 持有，运行时状态的生命周期由调用方负责。
public class ConductorAttachmentUtil {

	public static boolean hasUnit(Mob mob) {
		return mob.hasData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	public static ConductorUnitData unit(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	// Level 由实体持有，同步入口不能关闭共享世界。
	@SuppressWarnings("resource")
	public static void syncUnit(Mob mob, @Nullable Unit before) {
		if (!mob.level().isClientSide() && hasUnit(mob)) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob,
					ConductorEntityDataSyncPayload.unit(mob, before, unit(mob)));
		}
	}

	// Level 由实体持有，附件清理不能关闭共享世界。
	@SuppressWarnings("resource")
	public static void removeUnit(Mob mob) {
		if (mob.removeData(LcAttachmentTypes.CONDUCTOR_UNIT) != null && !mob.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob, ConductorEntityDataSyncPayload.removeUnit(mob));
		}
	}

	public static ConductorUnitRuntime runtime(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_RUNTIME);
	}

	public static void removeRuntime(Mob mob) {
		CombatUtil.rememberSharedTarget(mob, null);
		mob.removeData(LcAttachmentTypes.CONDUCTOR_RUNTIME);
	}

	public static ConductorAbilityState abilities(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_ABILITIES);
	}

	// Level 由实体持有，资格更新不能关闭共享世界。
	@SuppressWarnings("resource")
	public static void setAbilities(Mob mob, ConductorAbilityState state) {
		ConductorAbilityState before = abilities(mob);
		if (before.equals(state)) {
			return;
		}

		mob.setData(LcAttachmentTypes.CONDUCTOR_ABILITIES, state);
		if (mob.level().isClientSide()) {
			return;
		}

		var keys = new LinkedHashSet<>(before.overrides().keySet());
		keys.addAll(state.overrides().keySet());
		var changes = new ArrayList<ConductorEntityDataSyncPayload.AbilityChange>();

		for (var id : keys) {
			Boolean previous = before.overrides().get(id);
			Boolean next = state.overrides().get(id);
			if (!Objects.equals(previous, next)) {
				changes.add(new ConductorEntityDataSyncPayload.AbilityChange(id, next));
			}
		}

		PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob, ConductorEntityDataSyncPayload.abilities(mob, changes));
	}

	/// 新追踪者取得完整基线；未编队的生物也可能拥有独立的能力资格修正。
	public static void sendInitial(ServerPlayer player, Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}

		ConductorAbilityState abilities = mob.hasData(LcAttachmentTypes.CONDUCTOR_ABILITIES)
				? abilities(mob) : ConductorAbilityState.empty();
		PacketDistributor.sendToPlayer(player, ConductorEntityDataSyncPayload.full(mob,
				hasUnit(mob) ? unit(mob) : null, abilities));
	}

	// Level 由实体持有，客户端合并不能关闭共享世界。
	@SuppressWarnings("resource")
	public static void applySync(@Nullable Entity entity, ConductorEntityDataSyncPayload payload) {
		if (!(entity instanceof Mob mob) || !mob.level().isClientSide() || !mob.getUUID().equals(payload.entityUuid())) {
			return;
		}

		switch (payload.changeType()) {
			case FULL -> {
				if (payload.unit() == null) {
					mob.removeData(LcAttachmentTypes.CONDUCTOR_UNIT);
				} else {
					mob.setData(LcAttachmentTypes.CONDUCTOR_UNIT, payload.unit());
				}

				mob.setData(LcAttachmentTypes.CONDUCTOR_ABILITIES, payload.abilities());
			}
			case UNIT -> applyUnitChanges(mob, payload);
			case REMOVE_UNIT -> mob.removeData(LcAttachmentTypes.CONDUCTOR_UNIT);
			case ABILITIES -> {
				var overrides = new HashMap<>(abilities(mob).overrides());

				for (var change : payload.abilityChanges()) {
					if (change.owned() == null) {
						overrides.remove(change.id());
					} else {
						overrides.put(change.id(), change.owned());
					}
				}

				mob.setData(LcAttachmentTypes.CONDUCTOR_ABILITIES, new ConductorAbilityState(overrides));
			}
		}
	}

	private static void applyUnitChanges(Mob mob, ConductorEntityDataSyncPayload payload) {
		ConductorUnitData source = payload.unit();
		if (source == null || !hasUnit(mob)
				&& payload.fields().size() != ConductorEntityDataSyncPayload.UnitField.values().length) {
			return;
		}

		ConductorUnitData destination = unit(mob);
		if (destination.revision > source.revision) {
			return;
		}

		for (var field : payload.fields()) {
			field.apply(source, destination);
		}

		destination.revision = source.revision;
	}

	public static void removeUnitAndRuntime(Mob mob) {
		removeUnit(mob);
		removeRuntime(mob);
	}
}
