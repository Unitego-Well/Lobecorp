package org.unitego.lobecorp.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.unitego.lobecorp.entity.ai.memory.NearestVisibleEntities;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/// 扫描附近实体的 Sensor。在指定范围内搜索满足条件的所有实体，按距离排序后写入两个记忆：<br>
/// <ul>
///   <li>{@code targetMemory} — 原始实体列表（按距离升序）</li>
///   <li>{@code visibleMemory} — {@link NearestVisibleEntities} 包装器（带视线缓存）</li>
/// </ul>
public class NearbyEntitiesSensor<E extends Entity> extends Sensor<Mob> {
	private final Predicate<Entity> filter;
	private final double range;
	private final MemoryModuleType<List<E>> targetMemory;
	private final MemoryModuleType<NearestVisibleEntities<E>> visibleMemory;

	public NearbyEntitiesSensor(Predicate<Entity> filter, double range,
	                            MemoryModuleType<List<E>> targetMemory,
	                            MemoryModuleType<NearestVisibleEntities<E>> visibleMemory) {
		this.filter = filter;
		this.range = range;
		this.targetMemory = targetMemory;
		this.visibleMemory = visibleMemory;
	}

	/// 创建一个附近实体 Sensor。
	///
	/// @param filter        目标实体条件
	/// @param range         扫描半径
	/// @param targetMemory  存储原始列表的记忆模块
	/// @param visibleMemory 存储 {@link NearestVisibleEntities} 包装器的记忆模块
	public static <T extends Entity> NearbyEntitiesSensor<T> create(
			Predicate<Entity> filter, double range,
			MemoryModuleType<List<T>> targetMemory,
			MemoryModuleType<NearestVisibleEntities<T>> visibleMemory) {
		return new NearbyEntitiesSensor<>(filter, range, targetMemory, visibleMemory);
	}

	@Override
	public Set<MemoryModuleType<?>> requires() {
		return ImmutableSet.of(targetMemory, visibleMemory);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void doTick(ServerLevel level, Mob body) {
		var box = body.getBoundingBox().inflate(range);
		List<E> entities = (List<E>) level.getEntities(body, box, filter);
		entities.sort(Comparator.comparingDouble(body::distanceToSqr));

		body.getBrain().setMemory(targetMemory, entities);
		body.getBrain().setMemory(visibleMemory, entities.isEmpty()
				? NearestVisibleEntities.empty()
				: new NearestVisibleEntities<>(level, body, entities));
	}
}
