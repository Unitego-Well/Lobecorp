package org.unitego.lobecorp.registry;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillGroup;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.serialization.codecs.SetCodec;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LcCodecs {
	Codec<Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = LcMapCodecs.ENTITY_TYPE.codec();
	Codec<Set<IEntitySkill<?>>> ENTITY_SKILL_SET_CODEC = LcCodecs.set(LcRegistrys.ENTITY_SKILL.byNameCodec());
	Codec<Map<EntitySkillGroup, Integer>> ENTITY_SKILL_GROUP_MAP_CODEC = Codec.dispatchedMap(
			LcRegistrys.ENTITY_SKILL_GROUP.byNameCodec(), group -> Codec.INT);
	Codec<Map<IEntitySkill<?>, Long>> ENTITY_SKILL_COOLDOWN_MAP_CODEC = Codec.dispatchedMap(
			LcRegistrys.ENTITY_SKILL.byNameCodec(), skill -> Codec.LONG);

	static <E> Codec<Set<E>> set(final Codec<E> elementCodec) {
		return set(elementCodec, 0, Integer.MAX_VALUE);
	}

	static <E> Codec<Set<E>> set(final Codec<E> elementCodec, final int minSize, final int maxSize) {
		return new SetCodec<>(elementCodec, minSize, maxSize);
	}
}
