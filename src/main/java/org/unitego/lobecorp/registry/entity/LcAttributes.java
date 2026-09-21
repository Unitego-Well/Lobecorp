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
import org.unitego.lobecorp.entity.attribute.BasicAttribute;
import org.unitego.lobecorp.entity.attribute.MaxAttribute;
import org.unitego.lobecorp.entity.attribute.MinAttribute;

import java.util.function.Consumer;
import java.util.function.Function;

public interface LcAttributes {
	DeferredRegister<Attribute> REGISTER = Lobecorp.register(BuiltInRegistries.ATTRIBUTE);

	DeferredHolder<Attribute, RangedAttribute> ENTITY_SKILL_COOLDOWN_MULTIPLIER = registerRanged(
			"entity_skill_cooldown_multiplier", 1.0, 0.0, 1024.0, attribute -> attribute.setSyncable(true));

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
		REGISTER.register(eventBus);
	}
}
