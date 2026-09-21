package org.unitego.lobecorp.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/// 通用的最近实体 Sensor。在指定范围内扫描满足条件的实体，按距离排序，<br>
/// 过滤出可见的最近实体并写入指定记忆。
/// <p>
/// 用法示例：
/// <pre>{@code
/// // 扫描 ItemEntity（替代原版 NearestItemSensor）
/// NearestEntitySensor.create(e -> e instanceof ItemEntity, 32, 16,
///     MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
///
/// // 扫描 EntityCorpse
/// NearestEntitySensor.create(e -> e instanceof EntityCorpse, 16, 8,
///     LcMemoryModuleTypes.NEAREST_CORPSE.get());
/// }</pre>
public class NearestEntitySensor<E extends Entity> extends Sensor<Mob> {
	private final BiPredicate<Mob, Entity> filter;
	private final long xzRange;
	private final long yRange;
	private final MemoryModuleType<E> targetMemory;

	private NearestEntitySensor(Predicate<Entity> filter, long xzRange, long yRange,
	                            MemoryModuleType<E> targetMemory) {
		this.filter = (ignoredBody, entity) -> filter.test(entity);
		this.xzRange = xzRange;
		this.yRange = yRange;
		this.targetMemory = targetMemory;
	}

	private NearestEntitySensor(Predicate<Entity> filter, long xzRange, long yRange,
	                            MemoryModuleType<E> targetMemory, int scanRate) {
		super(scanRate);
		this.filter = (ignoredBody, entity) -> filter.test(entity);
		this.xzRange = xzRange;
		this.yRange = yRange;
		this.targetMemory = targetMemory;
	}

	private NearestEntitySensor(BiPredicate<Mob, Entity> filter, long xzRange, long yRange,
	                            MemoryModuleType<E> targetMemory, int scanRate) {
		super(scanRate);
		this.filter = filter;
		this.xzRange = xzRange;
		this.yRange = yRange;
		this.targetMemory = targetMemory;
	}

	/// 创建一个最近实体 Sensor。
	///
	/// @param filter       目标实体条件
	/// @param xzRange      水平扫描半径
	/// @param yRange       垂直扫描半径
	/// @param targetMemory 存储最近实体的记忆模块
	public static <T extends Entity> NearestEntitySensor<T> create(Predicate<Entity> filter, long xzRange, long yRange,
	                                                               MemoryModuleType<T> targetMemory) {
		return new NearestEntitySensor<>(filter, xzRange, yRange, targetMemory);
	}

	public static <T extends Entity> NearestEntitySensor<T> create(Predicate<Entity> filter, long xzRange, long yRange,
	                                                               MemoryModuleType<T> targetMemory, int scanRate) {
		return new NearestEntitySensor<>(filter, xzRange, yRange, targetMemory, scanRate);
	}

	/// 创建一个可根据扫描者动态筛选目标的最近实体 Sensor。
	public static <T extends Entity> NearestEntitySensor<T> create(BiPredicate<Mob, Entity> filter,
	                                                               long xzRange, long yRange,
	                                                               MemoryModuleType<T> targetMemory, int scanRate) {
		return new NearestEntitySensor<>(filter, xzRange, yRange, targetMemory, scanRate);
	}

	@Override
	public Set<MemoryModuleType<?>> requires() {
		return ImmutableSet.of(targetMemory);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void doTick(ServerLevel level, Mob body) {
		List<E> entities = (List<E>) level.getEntities(body, body.getBoundingBox().inflate(xzRange, yRange, xzRange),
				entity -> filter.test(body, entity));
		E nearest = null;
		double nearestDistanceSqr = Double.MAX_VALUE;
		double range = Math.max(xzRange, yRange);
		for (E entity : entities) {
			double distanceSqr = body.distanceToSqr(entity);
			if (distanceSqr < nearestDistanceSqr && entity.closerThan(body, range) && body.hasLineOfSight(entity)) {
				nearest = entity;
				nearestDistanceSqr = distanceSqr;
			}
		}

		body.getBrain().setMemory(targetMemory, nearest);
	}
}
