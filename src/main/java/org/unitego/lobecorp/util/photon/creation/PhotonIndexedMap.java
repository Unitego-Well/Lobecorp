package org.unitego.lobecorp.util.photon.creation;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/// 对等当前 Photon 原生 LinkedHashMap；布局按首次插入顺序以 O(n) 空间编译。
public class PhotonIndexedMap<V> extends AbstractMap<UUID, V> {
	private final Layout layout;
	private final V[] objects;
	private final boolean[] presence;
	private final int[] insertionOrder;
	private LinkedHashMap<UUID, V> materialized;
	private int size;
	private int modifications;
	private long leaseGeneration;
	private long materializations;

	public PhotonIndexedMap(Layout layout, IntFunction<V[]> arrayFactory) {
		this.layout = Objects.requireNonNull(layout);
		objects = arrayFactory.apply(layout.keys.length);
		presence = new boolean[layout.keys.length];
		insertionOrder = new int[layout.keys.length];
	}

	public static Layout compile(List<UUID> insertionOrder) {
		Objects.requireNonNull(insertionOrder);
		var slots = new HashMap<UUID, Integer>();
		var keys = new ArrayList<UUID>();
		for (var key : insertionOrder) {
			if (slots.containsKey(key)) {
				continue;
			}

			int slot = keys.size();
			slots.put(key, slot);
			keys.add(key);
		}

		return new Layout(keys.toArray(UUID[]::new), slots);
	}

	@Override
	public int size() {
		return materialized == null ? size : materialized.size();
	}

	@Override
	public boolean containsKey(Object key) {
		if (materialized != null) {
			return materialized.containsKey(key);
		}

		if (key != null && !(key instanceof UUID)) {
			return false;
		}

		var slot = layout.slots.get(key);
		return slot != null && presence[slot];
	}

	@Override
	public V get(Object key) {
		if (materialized != null) {
			return materialized.get(key);
		}

		if (key != null && !(key instanceof UUID)) {
			return null;
		}

		var slot = layout.slots.get(key);
		return slot == null || !presence[slot] ? null : objects[slot];
	}

	@Override
	public V put(UUID key, V value) {
		if (materialized != null) {
			boolean existed = materialized.containsKey(key);
			var previous = materialized.put(key, value);
			if (!existed) {
				modifications++;
			}

			return previous;
		}

		var slot = layout.slots.get(key);
		if (slot == null) {
			materialize();
			return put(key, value);
		}

		var previous = objects[slot];
		if (!presence[slot]) {
			presence[slot] = true;
			insertionOrder[size++] = slot;
			modifications++;
		}

		objects[slot] = value;
		return previous;
	}

	@Override
	public V remove(Object key) {
		if (!containsKey(key)) {
			return null;
		}

		modifications++;
		if (materialized != null) {
			return materialized.remove(key);
		}

		int slot = layout.slots.get((UUID) key);
		var previous = objects[slot];
		objects[slot] = null;
		presence[slot] = false;
		for (int index = 0; index < size; index++) {
			if (insertionOrder[index] == slot) {
				System.arraycopy(insertionOrder, index + 1, insertionOrder, index, size - index - 1);
				insertionOrder[--size] = 0;
				break;
			}
		}

		return previous;
	}

	@Override
	public void clear() {
		// 物化后继续委托同一张原生表，保留动态编辑及所有视图的语义。
		materialize();
		modifications++;
		materialized.clear();
	}

	/// 仅在旧独占租约失效且完成排空屏障后重建数组状态；旧租约的视图和条目不得跨代使用。
	public void resetForLease() {
		Arrays.fill(objects, null);
		Arrays.fill(presence, false);
		materialized = null;
		size = 0;
		modifications++;
		leaseGeneration++;
	}

	private void checkLease(long generation) {
		if (generation != leaseGeneration) {
			throw new ConcurrentModificationException();
		}
	}

	@Override
	public V compute(UUID key, @NotNull BiFunction<? super UUID, ? super V, ? extends V> function) {
		Objects.requireNonNull(function);
		return dynamicEdit(() -> materialized.compute(key, function));
	}

	@Override
	public V computeIfAbsent(UUID key, @NotNull Function<? super UUID, ? extends V> function) {
		Objects.requireNonNull(function);
		return dynamicEdit(() -> materialized.computeIfAbsent(key, function));
	}

	@Override
	public V computeIfPresent(UUID key, @NotNull BiFunction<? super UUID, ? super V, ? extends V> function) {
		Objects.requireNonNull(function);
		return dynamicEdit(() -> materialized.computeIfPresent(key, function));
	}

	@Override
	public V merge(UUID key, @NotNull V value, @NotNull BiFunction<? super V, ? super V, ? extends V> function) {
		Objects.requireNonNull(value);
		Objects.requireNonNull(function);
		return dynamicEdit(() -> materialized.merge(key, value, function));
	}

	private V dynamicEdit(Supplier<V> operation) {
		materialize();
		int previousSize = materialized.size();
		try {
			return operation.get();
		} finally {
			if (previousSize != materialized.size()) {
				modifications++;
			}
		}
	}

	@Override
	public void putAll(@NotNull Map<? extends UUID, ? extends V> source) {
		Objects.requireNonNull(source);
		materialize();
		int previousSize = materialized.size();
		try {
			materialized.putAll(source);
		} finally {
			if (previousSize != materialized.size()) {
				modifications++;
			}
		}
	}

	@Override
	public @NotNull Set<UUID> keySet() {
		long generation = leaseGeneration;
		return new AbstractSet<>() {
			@Override
			public int size() {
				checkLease(generation);
				return PhotonIndexedMap.this.size();
			}

			@Override
			public boolean contains(Object key) {
				checkLease(generation);
				return containsKey(key);
			}

			@Override
			public boolean remove(Object key) {
				checkLease(generation);
				if (!containsKey(key)) {
					return false;
				}
				PhotonIndexedMap.this.remove(key);
				return true;
			}

			@Override
			public void clear() {
				checkLease(generation);
				PhotonIndexedMap.this.clear();
			}

			@Override
			public @NotNull Iterator<UUID> iterator() {
				checkLease(generation);
				var entries = new ObjectIterator();
				return new Iterator<>() {
					@Override
					public boolean hasNext() {
						return entries.hasNext();
					}

					@Override
					public UUID next() {
						return entries.next().getKey();
					}

					@Override
					public void remove() {
						entries.remove();
					}
				};
			}
		};
	}

	@Override
	public @NotNull Collection<V> values() {
		long generation = leaseGeneration;
		return new AbstractCollection<>() {
			@Override
			public int size() {
				checkLease(generation);
				return PhotonIndexedMap.this.size();
			}

			@Override
			public void clear() {
				checkLease(generation);
				PhotonIndexedMap.this.clear();
			}

			@Override
			public @NotNull Iterator<V> iterator() {
				checkLease(generation);
				var entries = new ObjectIterator();
				return new Iterator<>() {
					@Override
					public boolean hasNext() {
						return entries.hasNext();
					}

					@Override
					public V next() {
						return entries.next().getValue();
					}

					@Override
					public void remove() {
						entries.remove();
					}
				};
			}
		};
	}

	public boolean isMaterialized() {
		return materialized != null;
	}

	public long materializationCount() {
		return materializations;
	}

	private void materialize() {
		if (materialized != null) {
			return;
		}

		materialized = new LinkedHashMap<>();
		for (int index = 0; index < size; index++) {
			int slot = insertionOrder[index];
			materialized.put(layout.keys[slot], objects[slot]);
			objects[slot] = null;
			presence[slot] = false;
		}

		materializations++;
	}

	@Override
	public @NotNull Set<Entry<UUID, V>> entrySet() {
		long generation = leaseGeneration;
		return new AbstractSet<>() {
			@Override
			public int size() {
				checkLease(generation);
				return PhotonIndexedMap.this.size();
			}

			@Override
			public void clear() {
				checkLease(generation);
				PhotonIndexedMap.this.clear();
			}

			@Override
			public boolean contains(Object value) {
				checkLease(generation);
				return value instanceof Entry<?, ?> entry && containsKey(entry.getKey())
						&& Objects.equals(get(entry.getKey()), entry.getValue());
			}

			@Override
			public boolean remove(Object value) {
				checkLease(generation);
				if (!(value instanceof Entry<?, ?> entry) || !contains(value)) {
					return false;
				}
				PhotonIndexedMap.this.remove(entry.getKey());
				return true;
			}

			@Override
			public @NotNull Iterator<Entry<UUID, V>> iterator() {
				checkLease(generation);
				return new ObjectIterator();
			}
		};
	}

	/// 所有数组和 UUID 到槽位的映射只在编译阶段创建，不向实例调用者暴露。
	public static class Layout {
		private final UUID[] keys;
		private final Map<UUID, Integer> slots;

		private Layout(UUID[] keys, Map<UUID, Integer> slots) {
			this.keys = keys;
			this.slots = slots;
		}

		public int size() {
			return keys.length;
		}
	}

	private class ObjectIterator implements Iterator<Entry<UUID, V>> {
		private int limit;
		private Iterator<Entry<UUID, V>> delegate;
		private int expected = modifications;
		private int cursor;
		private UUID last;
		private boolean removable;

		private ObjectIterator() {
			if (materialized != null) {
				limit = 0;
				delegate = materialized.entrySet().iterator();
			} else {
				delegate = null;
				limit = size;
			}
		}

		private void checkModification() {
			if (expected != modifications) {
				throw new ConcurrentModificationException();
			}

			if (delegate == null && materialized != null) {
				// 非结构修改也会物化；迁移游标及最近条目的 remove 权限。
				delegate = materialized.entrySet().iterator();
				for (int index = 0; index < cursor; index++) {
					delegate.next();
				}
			}
		}

		@Override
		public boolean hasNext() {
			checkModification();
			return delegate == null ? cursor < limit : delegate.hasNext();
		}

		@Override
		public Entry<UUID, V> next() {
			checkModification();
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			if (delegate != null) {
				last = delegate.next().getKey();
				removable = true;
				return new ObjectEntry(last);
			}

			last = layout.keys[insertionOrder[cursor++]];
			removable = true;
			return new ObjectEntry(last);
		}

		@Override
		public void remove() {
			checkModification();
			if (!removable) {
				throw new IllegalStateException();
			}

			if (delegate != null) {
				delegate.remove();
				modifications++;
			} else {
				boolean indexed = materialized == null;
				PhotonIndexedMap.this.remove(last);
				if (indexed) {
					cursor--;
					limit--;
				}
			}

			expected = modifications;
			removable = false;
		}
	}

	private class ObjectEntry implements Entry<UUID, V> {
		private final UUID key;
		private final long generation = leaseGeneration;

		private ObjectEntry(UUID key) {
			this.key = key;
		}

		@Override
		public UUID getKey() {
			checkLease(generation);
			return key;
		}

		@Override
		public V getValue() {
			checkLease(generation);
			return get(key);
		}

		@Override
		public V setValue(V value) {
			checkLease(generation);
			return put(key, value);
		}

		@Override
		public boolean equals(Object other) {
			checkLease(generation);
			return other instanceof Entry<?, ?> entry && Objects.equals(key, entry.getKey())
					&& Objects.equals(getValue(), entry.getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(key) ^ Objects.hashCode(getValue());
		}
	}
}
