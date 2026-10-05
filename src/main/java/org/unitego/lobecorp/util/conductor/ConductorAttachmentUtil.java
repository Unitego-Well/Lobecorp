package org.unitego.lobecorp.util.conductor;

import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.conductor.ability.ConductorAbilityState;
import org.unitego.lobecorp.conductor.control.ConductorUnitRuntime;
import org.unitego.lobecorp.conductor.data.ConductorUnitData;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

/**
 * 指挥家实体附件的统一访问入口。
 *
 * <p>该类只封装附件读写和同步，不复制状态，也不保存实体引用；持续状态仍由
 * NeoForge Data Attachment 持有，运行时状态的生命周期由调用方负责。</p>
 */
public class ConductorAttachmentUtil {

	public static boolean hasUnit(Mob mob) {
		return mob.hasData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	public static ConductorUnitData unit(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	public static void syncUnit(Mob mob) {
		mob.syncData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	public static void removeUnit(Mob mob) {
		mob.removeData(LcAttachmentTypes.CONDUCTOR_UNIT);
	}

	public static ConductorUnitRuntime runtime(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_RUNTIME);
	}

	public static void removeRuntime(Mob mob) {
		mob.removeData(LcAttachmentTypes.CONDUCTOR_RUNTIME);
	}

	public static ConductorAbilityState abilities(Mob mob) {
		return mob.getData(LcAttachmentTypes.CONDUCTOR_ABILITIES);
	}

	public static void setAbilities(Mob mob, ConductorAbilityState state) {
		mob.setData(LcAttachmentTypes.CONDUCTOR_ABILITIES, state);
	}

	public static void removeUnitAndRuntime(Mob mob) {
		removeUnit(mob);
		removeRuntime(mob);
	}
}
