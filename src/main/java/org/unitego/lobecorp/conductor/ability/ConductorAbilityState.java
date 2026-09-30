package org.unitego.lobecorp.conductor.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 指挥家能力学习与屏蔽状态的持久化值对象。
public record ConductorAbilityState(Set<Identifier> learned, Set<Identifier> blocked) {
	/// 能力标识集合的序列化编解码器。
	private static final Codec<Set<Identifier>> IDS = Codec.STRING.xmap(Identifier::parse, Identifier::toString)
			.listOf().xmap(Set::copyOf, List::copyOf);

	/// 指挥家能力状态的持久化编解码器。
	public static final Codec<ConductorAbilityState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			IDS.fieldOf("learned").forGetter(ConductorAbilityState::learned),
			IDS.fieldOf("blocked").forGetter(ConductorAbilityState::blocked)
	).apply(instance, ConductorAbilityState::new));

	public ConductorAbilityState {
		learned = Set.copyOf(learned);
		blocked = Set.copyOf(blocked);
	}

	public static ConductorAbilityState empty() {
		return new ConductorAbilityState(Set.of(), Set.of());
	}

	public ConductorAbilityState add(Identifier id, boolean initial) {
		Set<Identifier> nextLearned = new HashSet<>(learned);
		Set<Identifier> nextBlocked = new HashSet<>(blocked);
		if (!initial) nextLearned.add(id);
		nextBlocked.remove(id);
		return new ConductorAbilityState(nextLearned, nextBlocked);
	}

	public ConductorAbilityState remove(Identifier id, boolean initial) {
		Set<Identifier> nextLearned = new HashSet<>(learned);
		Set<Identifier> nextBlocked = new HashSet<>(blocked);
		if (initial) nextBlocked.add(id);
		else nextLearned.remove(id);
		return new ConductorAbilityState(nextLearned, nextBlocked);
	}
}
