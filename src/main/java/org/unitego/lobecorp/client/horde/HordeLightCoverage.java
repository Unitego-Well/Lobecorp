package org.unitego.lobecorp.client.horde;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongPredicate;

/// 区段几何跨帧复用；区块加载状态只在当前逻辑刻内复用。
public class HordeLightCoverage {
	private static final long RETAIN_TICKS = 100;
	private final Map<AABB, Entry> entries = new HashMap<>();
	private final Long2BooleanOpenHashMap loadedColumns = new Long2BooleanOpenHashMap();
	private long tick = Long.MIN_VALUE;
	private long builds;
	private long queries;

	public long[] candidates(AABB bounds, int minimum, int maximum, long now) {
		advanceTick(now);
		var entry = entries.get(bounds);
		if (entry == null || entry.minimum != minimum || entry.maximum != maximum) {
			var sections = new LongArrayList();
			int low = Math.max((int) Math.floor(bounds.minY) >> 4, minimum);
			int high = Math.min((int) Math.floor(bounds.maxY) >> 4, maximum - 1);
			for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
				for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
					for (int y = low; y <= high; y++) {
						sections.add(SectionPos.asLong(x, y, z));
					}
				}
			}
			entry = new Entry(minimum, maximum, sections.toLongArray(), now);
			entries.put(bounds, entry);
			builds++;
		}
		entry.seen = now;
		return entry.sections;
	}

	private void advanceTick(long now) {
		if (tick != now) {
			loadedColumns.clear();
			entries.values().removeIf(entry -> now - entry.seen > RETAIN_TICKS);
			tick = now;
		}
	}

	public boolean loaded(long section, long now, LongPredicate probe) {
		advanceTick(now);
		long column = SectionPos.asLong(SectionPos.x(section), 0, SectionPos.z(section));
		if (!loadedColumns.containsKey(column)) {
			loadedColumns.put(column, probe.test(column));
			queries++;
		}
		return loadedColumns.get(column);
	}

	public long builds() {
		return builds;
	}

	public long queries() {
		return queries;
	}

	public void clear() {
		entries.clear();
		loadedColumns.clear();
		tick = Long.MIN_VALUE;
		builds = 0;
		queries = 0;
	}

	private static class Entry {
		private final int minimum;
		private final int maximum;
		private final long[] sections;
		private long seen;

		private Entry(int minimum, int maximum, long[] sections, long seen) {
			this.minimum = minimum;
			this.maximum = maximum;
			this.sections = sections;
			this.seen = seen;
		}
	}
}
