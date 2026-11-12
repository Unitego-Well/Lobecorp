package org.unitego.lobecorp.registry.world.horde;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.horde.IHordeProfile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HordeProfiles {
	private static final Map<Identifier, IHordeProfile> profiles = new LinkedHashMap<>();

	public static void register(IHordeProfile profile) {
		if (profiles.putIfAbsent(profile.id(), profile) != null) {
			throw new IllegalArgumentException("重复的浪潮定义：" + profile.id());
		}
	}

	public static @Nullable IHordeProfile get(Identifier id) {
		return profiles.get(id);
	}

	public static Collection<IHordeProfile> values() {
		return List.copyOf(profiles.values());
	}
}
