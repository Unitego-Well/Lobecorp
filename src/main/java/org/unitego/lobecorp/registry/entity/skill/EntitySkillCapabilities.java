package org.unitego.lobecorp.registry.entity.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.skill.EntitySkillAttachmentAccess;
import org.unitego.lobecorp.registry.LcCapabilities;

/// 实体技能能力的唯一注册入口。
public final class EntitySkillCapabilities {
	private EntitySkillCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		for (var type : BuiltInRegistries.ENTITY_TYPE) {
			event.registerEntity(LcCapabilities.ENTITY_SKILL, type,
					(entity, context) -> entity instanceof Sweeper sweeper ? new SweeperAccess(sweeper)
							: entity instanceof TheQueenOfHatred queen ? new QueenAccess(queen)
							: entity instanceof LivingEntity living ? new Access(living) : null);
		}
	}

	private static class Access extends EntitySkillAttachmentAccess {
		private Access(LivingEntity entity) {
			super(entity);
		}
	}

	/// 清道夫适配器保留特殊技能资格的扩展位置。
	private static final class SweeperAccess extends Access {
		private SweeperAccess(Sweeper entity) {
			super(entity);
		}
	}

	/// 女皇适配器保留特殊技能资格的扩展位置。
	private static final class QueenAccess extends Access {
		private QueenAccess(TheQueenOfHatred entity) {
			super(entity);
		}
	}
}
