package org.unitego.lobecorp.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// {@link net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities} 的泛型版本。<br>
/// 缓存视线检测结果，提供按条件查找最近/所有可见实体的方法。
public class NearestVisibleEntities<E extends Entity> {
    private static final NearestVisibleEntities<?> EMPTY = new NearestVisibleEntities<>();

    private final List<E> entities;
    private final Predicate<E> losTest;

    private NearestVisibleEntities() {
        this.entities = List.of();
        this.losTest = ignored -> false;
    }

    public NearestVisibleEntities(ServerLevel level, Mob body, List<E> entities) {
        this.entities = entities;
        var cache = new Object2BooleanOpenHashMap<E>(entities.size());
        Predicate<E> losCheck = body::hasLineOfSight;
        this.losTest = entity -> cache.computeIfAbsent(entity, losCheck);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> NearestVisibleEntities<T> empty() {
        return (NearestVisibleEntities<T>) EMPTY;
    }

    public List<E> entities() {
        return entities;
    }

    public Optional<E> findClosest(Predicate<E> filter) {
        for (E entity : entities) {
            if (filter.test(entity) && losTest.test(entity)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public Iterable<E> findAll(Predicate<E> filter) {
        return Iterables.filter(entities, entity -> filter.test(entity) && losTest.test(entity));
    }

    public Stream<E> find(Predicate<E> filter) {
        return entities.stream().filter(entity -> filter.test(entity) && losTest.test(entity));
    }

    public boolean contains(E target) {
        return entities.contains(target) && losTest.test(target);
    }

    public boolean contains(Predicate<E> filter) {
        for (E entity : entities) {
            if (filter.test(entity) && losTest.test(entity)) {
                return true;
            }
        }
        return false;
    }
}
