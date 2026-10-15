package org.unitego.lobecorp.registry.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillHolder;

public class EntityAttributeCreation {
	public static void registry(EntityAttributeCreationEvent event) {
		register(event, LcEntityTypes.ENTITY_CORPSE.get(), EntityCorpse.class, EntityCorpse.createAttributes());
		register(event, AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), TheQueenOfHatred.class, TheQueenOfHatred.createAttributes());
		register(event, OrdealEntityTypes.SWEEPER.get(), Sweeper.class, Sweeper.createAttributes());
	}

	/// 在 NeoForge 属性创建事件中补齐技能持有者属性，保留实体手动注册的值。
	private static <T extends LivingEntity> void register(EntityAttributeCreationEvent event, EntityType<T> type,
	                                                      Class<? super T> entityClass, AttributeSupplier.Builder attributes) {
		if (IEntitySkillHolder.class.isAssignableFrom(entityClass)) {
			if (!attributes.hasAttribute(LcAttributes.ENTITY_SKILL_COOLDOWN_SPEED)) {
				attributes.add(LcAttributes.ENTITY_SKILL_COOLDOWN_SPEED);
			}

			if (!attributes.hasAttribute(Attributes.ATTACK_SPEED)) {
				attributes.add(Attributes.ATTACK_SPEED);
			}
		}

		event.put(type, attributes.build());
	}

	public static void addCommonAttributes(EntityAttributeModificationEvent event) {
		event.getTypes().forEach(type -> event.add(type, LcAttributes.DAMAGE_TAKEN_MULTIPLIER));
	}
}
