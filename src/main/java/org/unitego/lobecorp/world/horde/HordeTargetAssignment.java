package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/// 转派可高于当前增援配额，但有效成员与预约合计不超过单目标硬上限。
public class HordeTargetAssignment {
	public final String key;
	public final String domain;
	public final UUID target;
	public final HordeRules rules;
	public final Set<UUID> members = new HashSet<>();
	public Vec3 position;
	public UUID region;
	public int quota;
	public int reservations;
	public boolean engaged;
	public int fightTicks;
	public long retryUntil;
	public long outsideSince = -1;
	public long splitSince = -1;
	public long seenAt;

	public HordeTargetAssignment(String key, String domain, UUID target, Vec3 position, HordeRules rules) {
		this.key = key;
		this.domain = domain;
		this.target = target;
		this.position = position;
		this.rules = rules;
		this.region = regionId(domain, target);
        this.quota = Math.min(rules.initialQuota(), rules.maximumQuota());
	}

	public int occupied() {
		return members.size() + reservations;
	}

	public static UUID regionId(String domain, UUID leader) {
		return UUID.nameUUIDFromBytes((domain + ":" + leader).getBytes(StandardCharsets.UTF_8));
	}

	public int missing() {
		return Math.max(0, Math.min(quota, HordeRules.MAXIMUM_TARGET_QUOTA) - occupied());
	}

	public void death() {
		quota = Math.min(rules.maximumQuota(), quota + 1);
		fightTicks = 0;
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		tag.putString("key", key);
		tag.putString("domain", domain);
		tag.putString("target", target.toString());
		tag.putString("region", region.toString());
		tag.store("position", Vec3.CODEC, position);
		tag.put("rules", rules.save());
		tag.putInt("quota", quota);
		tag.putBoolean("engaged", engaged);
		tag.putInt("fight_ticks", fightTicks);
		tag.putLong("retry", retryUntil);
		var list = new ListTag();
		members.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
		tag.put("members", list);
		return tag;
	}

	public static HordeTargetAssignment load(CompoundTag tag) {
		var result = new HordeTargetAssignment(tag.getStringOr("key", ""), tag.getStringOr("domain", ""),
				UUID.fromString(tag.getStringOr("target", "")), tag.read("position", Vec3.CODEC).orElse(Vec3.ZERO),
				HordeRules.load(tag.getCompoundOrEmpty("rules")));
		result.region = UUID.fromString(tag.getStringOr("region", result.target.toString()));
		result.quota = Math.clamp(tag.getIntOr("quota", result.rules.initialQuota()), 1, result.rules.maximumQuota());
		result.engaged = tag.getBooleanOr("engaged", false);
		result.fightTicks = Math.max(0, tag.getIntOr("fight_ticks", 0));
		result.retryUntil = tag.getLongOr("retry", 0);
		for (var element : tag.getListOrEmpty("members")) {
			element.asString().ifPresent(value -> result.members.add(UUID.fromString(value)));
		}
		return result;
	}
}
