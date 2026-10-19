package org.unitego.lobecorp.world.entity.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 阵营关系和群体成员独立保存，控制状态变化不隐式移除归属。
public class CombatData extends SavedData {
	public static final Codec<CombatData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Faction.CODEC).fieldOf("factions").forGetter(data -> data.factions),
			Codec.unboundedMap(Codec.STRING, Affiliation.CODEC).fieldOf("affiliations").forGetter(data -> data.affiliations)
	).apply(instance, CombatData::new));
	private static final SavedDataType<CombatData> TYPE =
			new SavedDataType<>(Lobecorp.id("combat_relations"), CombatData::new, CODEC);
	private final Map<String, Faction> factions;
	private final Map<String, Affiliation> affiliations;

	public CombatData() {
		this(Map.of(), Map.of());
	}

	private CombatData(Map<String, Faction> factions, Map<String, Affiliation> affiliations) {
		this.factions = new HashMap<>(factions);
		this.affiliations = new HashMap<>(affiliations);
	}

	public static CombatData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable Faction faction(String name) {
		return factions.get(name);
	}

	public void putFaction(String name, Set<String> enemies) {
		var next = new Faction(enemies);
		if (!next.equals(factions.put(name, next))) {
			setDirty();
		}
	}

	public @Nullable Affiliation affiliation(UUID entity) {
		return affiliations.get(entity.toString());
	}

	public void assign(UUID entity, String faction, String group) {
		if (!factions.containsKey(faction)) {
			return;
		}

		var next = new Affiliation(faction, group);
		if (!next.equals(affiliations.put(entity.toString(), next))) {
			setDirty();
		}
	}

	public void remove(UUID entity) {
		if (affiliations.remove(entity.toString()) != null) {
			setDirty();
		}
	}

	public boolean allied(UUID first, UUID second) {
		Affiliation left = affiliation(first);
		Affiliation right = affiliation(second);
		return left != null && right != null && left.faction().equals(right.faction());
	}

	public boolean sameGroup(UUID first, UUID second) {
		Affiliation left = affiliation(first);
		Affiliation right = affiliation(second);
		return left != null && right != null && !left.group().isEmpty()
				&& left.faction().equals(right.faction()) && left.group().equals(right.group());
	}

	public boolean enemies(UUID first, UUID second) {
		Affiliation left = affiliation(first);
		Affiliation right = affiliation(second);
		Faction faction = left == null ? null : faction(left.faction());
		return faction != null && right != null && faction.enemies().contains(right.faction());
	}

	public record Faction(Set<String> enemies) {
		public static final Codec<Faction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("enemies").forGetter(Faction::enemies)
		).apply(instance, Faction::new));

		public Faction {
			enemies = Set.copyOf(enemies);
		}
	}

	public record Affiliation(String faction, String group) {
		public static final Codec<Affiliation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("faction").forGetter(Affiliation::faction),
				Codec.STRING.fieldOf("group").forGetter(Affiliation::group)
		).apply(instance, Affiliation::new));
	}
}
