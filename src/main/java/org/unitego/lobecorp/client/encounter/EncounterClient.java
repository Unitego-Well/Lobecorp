package org.unitego.lobecorp.client.encounter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.client.encounter.EncounterDisplays;

import java.util.UUID;

public class EncounterClient {
	public static void receive(CompoundTag message) {
		try {
			var display = EncounterDisplays.get(Identifier.parse(message.getStringOr("definition", "")));
			if (display != null) {
				display.receive(UUID.fromString(message.getStringOr("id", "")), message.getCompoundOrEmpty("body"));
			}
		} catch (IllegalArgumentException exception) {
			Lobecorp.LOGGER.warn("忽略无效遭遇表现", exception);
		}
	}

	public static void tick() {
		EncounterDisplays.values().forEach(IEncounterDisplay::tick);
	}

	public static void clear() {
		EncounterDisplays.values().forEach(IEncounterDisplay::clear);
	}
}
