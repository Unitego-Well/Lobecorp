package org.unitego.lobecorp.registry;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.serialization.codecs.SetCodec;

import java.util.Optional;
import java.util.Set;

public interface LcCodecs {
	Codec<Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = LcMapCodecs.ENTITY_TYPE.codec();

	static <E> Codec<Set<E>> set(final Codec<E> elementCodec) {
		return set(elementCodec, 0, Integer.MAX_VALUE);
	}

	static <E> Codec<Set<E>> set(final Codec<E> elementCodec, final int minSize, final int maxSize) {
		return new SetCodec<>(elementCodec, minSize, maxSize);
	}
}
