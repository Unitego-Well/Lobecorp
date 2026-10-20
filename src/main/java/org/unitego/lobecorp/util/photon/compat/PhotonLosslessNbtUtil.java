package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/// 在原树上应用已知模型的变化，未知列表归属无法确认时明确拒绝。
public class PhotonLosslessNbtUtil {
	public static CompoundTag changed(CompoundTag original, CompoundTag before, CompoundTag after) {
		var result = original.copy();
		for (var key : before.keySet()) {
			if (!after.contains(key)) {
				result.remove(key);
			}
		}

		for (var key : after.keySet()) {
			var value = Objects.requireNonNull(after.get(key));
			if (Objects.equals(before.get(key), value)) {
				continue;
			}

			if (value instanceof CompoundTag child && before.get(key) instanceof CompoundTag previous) {
				result.put(key, changed(result.getCompoundOrEmpty(key), previous, child));
			} else if (value instanceof ListTag afterList && before.get(key) instanceof ListTag beforeList && result.get(key) instanceof ListTag raw) {
				result.put(key, changedList(raw, beforeList, afterList));
			} else {
				result.put(key, value.copy());
			}
		}

		return result;
	}

	private static ListTag changedList(ListTag raw, ListTag before, ListTag after) {
		if (!hasUnknown(raw, before)) {
			return after.copy();
		}
		if (raw.size() != before.size()) {
			throw new IllegalArgumentException("原生列表读取已跳过未知条目，拒绝有损编辑。");
		}
		var used = new HashSet<Integer>();
		var result = new ListTag();
		for (int index = 0; index < after.size(); index++) {
			var current = after.get(index);
			int match = match(before, current, used);
			if (match < 0 && before.size() == after.size() && !used.contains(index)) {
				var previous = before.get(index);
				if (identity(previous) == null && identity(current) == null) {
					match = index;
				}
			}
			if (match < 0) {
				boolean ambiguous = false;
				for (int old = 0; old < before.size(); old++) {
					if (!used.contains(old) && hasUnknown(raw.get(old), before.get(old))) {
						ambiguous = true;
						break;
					}
				}
				if (ambiguous) {
					throw new IllegalArgumentException("含未知字段的列表无法确定条目归属，请使用原树列表事务。");
				}
				result.add(current.copy());
				continue;
			}
			used.add(match);
			var previous = before.get(match);
			var source = raw.get(match);
			if (previous.equals(current)) {
				result.add(source.copy());
			} else if (source instanceof CompoundTag original && previous instanceof CompoundTag known && current instanceof CompoundTag changed) {
				result.add(changed(original, known, changed));
			} else if (source instanceof ListTag original && previous instanceof ListTag known && current instanceof ListTag changed) {
				result.add(changedList(original, known, changed));
			} else {
				result.add(current.copy());
			}
		}
		return result;
	}

	private static int match(ListTag before, Tag current, Set<Integer> used) {
		String identity = identity(current);
		int match = -1;
		for (int index = 0; index < before.size(); index++) {
			if (used.contains(index)) {
				continue;
			}
			var previous = before.get(index);
			if (previous.equals(current)) {
				return index;
			}
			if (identity != null && identity.equals(identity(previous))) {
				if (match >= 0) {
					return -1;
				}
				match = index;
			}
		}
		return match;
	}

	private static String identity(Tag tag) {
		if (!(tag instanceof CompoundTag compound)) {
			return null;
		}
		if (compound.get("transform") instanceof CompoundTag transform && transform.contains("id")) {
			return "transform:" + transform.get("id");
		}
		if (compound.get("data") instanceof CompoundTag data && data.get("transform") instanceof CompoundTag transform && transform.contains("id")) {
			return "transform:" + transform.get("id");
		}
		for (var key : List.of("id", "property", "path", "name", "propertyName")) {
			if (compound.contains(key)) {
				return key + ":" + compound.get(key);
			}
		}
		if (compound.get("data") instanceof CompoundTag data) {
			var child = identity(data);
			if (child != null) {
				return compound.getStringOr("type", "") + "/" + child;
			}
		}
		return null;
	}

	private static boolean hasUnknown(Tag raw, Tag known) {
		if (raw instanceof CompoundTag original && known instanceof CompoundTag saved) {
			for (var entry : original.entrySet()) {
				if (!saved.contains(entry.getKey()) || hasUnknown(entry.getValue(), saved.get(entry.getKey()))) {
					return true;
				}
			}
		} else if (raw instanceof ListTag original && known instanceof ListTag saved) {
			if (original.size() != saved.size()) {
				return true;
			}
			for (int index = 0; index < original.size(); index++) {
				if (hasUnknown(original.get(index), saved.get(index))) {
					return true;
				}
			}
		}
		return false;
	}
}
