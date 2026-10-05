package org.unitego.lobecorp.util.entity.skill;

import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

/**
 * 重组技能对尸体进度附件的统一访问入口。
 */
public class ReassemblyProgressUtil {

	public static float get(EntityCorpse<?> corpse) {
		return corpse.getData(LcAttachmentTypes.REASSEMBLY_PROGRESS);
	}

	public static void set(EntityCorpse<?> corpse, float progress) {
		corpse.setData(LcAttachmentTypes.REASSEMBLY_PROGRESS, progress);
	}
}
