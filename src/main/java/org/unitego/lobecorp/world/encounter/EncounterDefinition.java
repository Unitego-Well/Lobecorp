package org.unitego.lobecorp.world.encounter;

import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class EncounterDefinition<T extends IEncounterDefinition> implements Supplier<T> {
	private final Identifier id;
	private final String nameKey;
	private final T implementation;

	public EncounterDefinition(Identifier id, String nameKey, T implementation) {
		this.id = id;
		this.nameKey = nameKey;
		this.implementation = implementation;
	}

	public Identifier id() {
		return id;
	}

	public String nameKey() {
		return nameKey;
	}

	@Override
	public T get() {
		return implementation;
	}
}
