package org.unitego.lobecorp.conductor.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.unitego.lobecorp.conductor.data.ConductorData.MemberInfo;
import org.unitego.lobecorp.conductor.data.ConductorData.Team;
import org.unitego.lobecorp.conductor.data.ConductorData.Unit;

import java.util.*;

/// 面向客户端同步的不可变指挥家目录快照。
public record ConductorDirectory(Map<String, Team> teams, Map<String, Unit> units, Map<String, MemberInfo> memberInfo) {
	public static final Codec<ConductorDirectory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Team.CODEC).fieldOf("teams").forGetter(ConductorDirectory::teams),
			Codec.unboundedMap(Codec.STRING, Unit.CODEC).fieldOf("members").forGetter(ConductorDirectory::units),
			Codec.unboundedMap(Codec.STRING, MemberInfo.CODEC).fieldOf("info").forGetter(ConductorDirectory::memberInfo)
	).apply(instance, ConductorDirectory::new));

	public ConductorDirectory {
		teams = Map.copyOf(teams);
		units = Map.copyOf(units);
		memberInfo = Map.copyOf(memberInfo);
	}

	public static ConductorDirectory empty() {
		return new ConductorDirectory(Map.of(), Map.of(), Map.of());
	}

	private static <T> Map<String, T> changed(Map<String, T> current, Map<String, T> previous) {
		Map<String, T> changes = new HashMap<>();
		current.forEach((key, value) -> {
			if (!Objects.equals(value, previous.get(key))) changes.put(key, value);
		});
		return changes;
	}

	public ConductorDirectory changesFrom(ConductorDirectory previous) {
		return new ConductorDirectory(changed(teams, previous.teams), changed(units, previous.units),
				changed(memberInfo, previous.memberInfo));
	}

	public ConductorDirectory merge(ConductorDirectory changes, List<String> removedTeams, List<String> removedUnits) {
		Map<String, Team> nextTeams = new HashMap<>(teams);
		Map<String, Unit> nextUnits = new HashMap<>(units);
		Map<String, MemberInfo> nextInfo = new HashMap<>(memberInfo);
		removedTeams.forEach(nextTeams::remove);
		removedUnits.forEach(key -> {
			nextUnits.remove(key);
			nextInfo.remove(key);
		});
		nextTeams.putAll(changes.teams);
		nextUnits.putAll(changes.units);
		nextInfo.putAll(changes.memberInfo);
		return new ConductorDirectory(nextTeams, nextUnits, nextInfo);
	}

	public Team team(String name) {
		return teams.get(name);
	}

	public Unit unit(UUID uuid) {
		return units.get(uuid.toString());
	}

	public MemberInfo memberInfo(UUID uuid) {
		return memberInfo.get(uuid.toString());
	}

	public boolean allied(UUID first, UUID second) {
		Unit left = unit(first);
		Unit right = unit(second);
		return left != null && right != null && left.team().equals(right.team());
	}

	public boolean enemies(UUID first, UUID second) {
		Unit left = unit(first);
		Unit right = unit(second);
		Team team = left == null ? null : team(left.team());
		return team != null && right != null && team.enemies().contains(right.team());
	}

}
