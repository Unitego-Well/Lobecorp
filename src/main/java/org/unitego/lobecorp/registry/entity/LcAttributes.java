package org.unitego.lobecorp.registry.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.BooleanAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;
import org.unitego.lobecorp.world.entity.attribute.BasicAttribute;
import org.unitego.lobecorp.world.entity.attribute.MaxAttribute;
import org.unitego.lobecorp.world.entity.attribute.MinAttribute;

import java.util.function.Consumer;
import java.util.function.Function;

public interface LcAttributes {
	DeferredRegister<Attribute> REGISTER = Lobecorp.register(BuiltInRegistries.ATTRIBUTE);

	double LC_DAMAGE_ATTRIBUTE_LIMIT = 1024.0;
	double DEFAULT_RESOURCE_MAXIMUM = 20.0;

	DeferredHolder<Attribute, RangedAttribute> PHYSICS_VULNERABLE = registerRanged(
			"physics_vulnerable", 1.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> SPIRIT_VULNERABLE = registerRanged(
			"spirit_vulnerable", 1.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> EROSION_VULNERABLE = registerRanged(
			"erosion_vulnerable", 1.5, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> THE_SOUL_VULNERABLE = registerRanged(
			"the_soul_vulnerable", 2.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> PHYSICS_DEFENSE = registerRanged(
			"physics_defense", 0.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> SPIRIT_DEFENSE = registerRanged(
			"spirit_defense", 0.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> EROSION_DEFENSE = registerRanged(
			"erosion_defense", 0.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, RangedAttribute> THE_SOUL_DEFENSE = registerRanged(
			"the_soul_defense", 0.0, -LC_DAMAGE_ATTRIBUTE_LIMIT, LC_DAMAGE_ATTRIBUTE_LIMIT, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, MinAttribute> MAX_RATIONALITY = registerMin(
			"max_rationality", DEFAULT_RESOURCE_MAXIMUM, 0.0, attribute -> attribute.setSyncable(true));
	DeferredHolder<Attribute, MinAttribute> MAX_THE_SOUL = registerMin(
			"max_the_soul", DEFAULT_RESOURCE_MAXIMUM, 0.0, attribute -> attribute.setSyncable(true));

	DeferredHolder<Attribute, RangedAttribute> ENTITY_SKILL_COOLDOWN_MULTIPLIER = registerRanged(
			"entity_skill_cooldown_multiplier", 1.0, 0.0, 1024.0, attribute -> attribute.setSyncable(true));

	/// 每 tick 推进的技能冷却 tick 数；1 为正常速度，0 暂停，实体须显式持有该属性。
	DeferredHolder<Attribute, RangedAttribute> ENTITY_SKILL_COOLDOWN_SPEED = registerRanged(
			"entity_skill_cooldown_speed", 1.0, 0.0, 1024.0, attribute -> attribute.setSyncable(true));

	DeferredHolder<Attribute, RangedAttribute> DAMAGE_TAKEN_MULTIPLIER = registerRanged(
			"damage_taken_multiplier", 1.0, 0.0, 1024.0, attribute -> attribute.setSyncable(true));

	static <T extends Attribute> @NonNull DeferredHolder<Attribute, T> register(
			String id, Function<String, T> factory, Consumer<T> configurator
	) {
		return REGISTER.register(id, () -> {
			T attribute = factory.apply(descriptionId(id));
			configurator.accept(attribute);
			return attribute;
		});
	}

	static @NonNull DeferredHolder<Attribute, BasicAttribute> registerBasic(
			String id, double defaultValue, Consumer<BasicAttribute> configurator
	) {
		return register(id, descriptionId -> new BasicAttribute(descriptionId, defaultValue), configurator);
	}

	static @NonNull DeferredHolder<Attribute, MinAttribute> registerMin(
			String id, double defaultValue, double minValue, Consumer<MinAttribute> configurator
	) {
		return register(id, descriptionId -> new MinAttribute(descriptionId, defaultValue, minValue), configurator);
	}

	static @NonNull DeferredHolder<Attribute, MaxAttribute> registerMax(
			String id, double defaultValue, double maxValue, Consumer<MaxAttribute> configurator
	) {
		return register(id, descriptionId -> new MaxAttribute(descriptionId, defaultValue, maxValue), configurator);
	}

	static @NonNull DeferredHolder<Attribute, RangedAttribute> registerRanged(
			String id, double defaultValue, double minValue, double maxValue, Consumer<RangedAttribute> configurator
	) {
		return register(id,
				descriptionId -> new RangedAttribute(descriptionId, defaultValue, minValue, maxValue), configurator);
	}

	static @NonNull DeferredHolder<Attribute, BooleanAttribute> registerBoolean(
			String id, boolean defaultValue, Consumer<BooleanAttribute> configurator
	) {
		return register(id, descriptionId -> new BooleanAttribute(descriptionId, defaultValue), configurator);
	}

	private static String descriptionId(String id) {
		return "attribute.name." + Lobecorp.NAMESPACE + "." + id;
	}

	static void init(IEventBus eventBus) {
		LangHandler.creates(REGISTER, "Physical Damage Multiplier", "物理承伤系数",
				(langSet, text) -> langSet.attributeText(PHYSICS_VULNERABLE, text));
		LangHandler.creates(REGISTER, "Spirit Damage Multiplier", "精神承伤系数",
				(langSet, text) -> langSet.attributeText(SPIRIT_VULNERABLE, text));
		LangHandler.creates(REGISTER, "Erosion Damage Multiplier", "侵蚀承伤系数",
				(langSet, text) -> langSet.attributeText(EROSION_VULNERABLE, text));
		LangHandler.creates(REGISTER, "Soul Damage Multiplier", "灵魂承伤系数",
				(langSet, text) -> langSet.attributeText(THE_SOUL_VULNERABLE, text));
		LangHandler.creates(REGISTER, "Physical Defense", "物理防御",
				(langSet, text) -> langSet.attributeText(PHYSICS_DEFENSE, text));
		LangHandler.creates(REGISTER, "Spirit Defense", "精神防御",
				(langSet, text) -> langSet.attributeText(SPIRIT_DEFENSE, text));
		LangHandler.creates(REGISTER, "Erosion Defense", "侵蚀防御",
				(langSet, text) -> langSet.attributeText(EROSION_DEFENSE, text));
		LangHandler.creates(REGISTER, "Soul Defense", "灵魂防御",
				(langSet, text) -> langSet.attributeText(THE_SOUL_DEFENSE, text));
		LangHandler.creates(REGISTER, "Maximum Rationality", "精神值上限",
				(langSet, text) -> langSet.attributeText(MAX_RATIONALITY, text));
		LangHandler.creates(REGISTER, "Maximum Soul", "灵魂值上限",
				(langSet, text) -> langSet.attributeText(MAX_THE_SOUL, text));
		LangHandler.creates(REGISTER, "Entity Skill Cooldown Speed", "实体技能冷却速度",
				(langSet, text) -> langSet.attributeText(ENTITY_SKILL_COOLDOWN_SPEED, text));
		REGISTER.register(eventBus);
	}
}
