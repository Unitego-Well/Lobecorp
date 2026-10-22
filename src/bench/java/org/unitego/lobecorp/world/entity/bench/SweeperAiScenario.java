package org.unitego.lobecorp.world.entity.bench;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.server.BenchServerContext;
import com.zhongbai233.bench.api.neoforge.server.BenchServerScenario;
import com.zhongbai233.bench.api.neoforge.server.BenchStepResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperVariant;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SweeperAiScenario implements BenchServerScenario {
	private static final BenchMetricDescriptor LOADED_ENTITIES = new BenchMetricDescriptor(
			"lobecorp.sweeper_ai.loaded_entities", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor ENTITY_TICKS = new BenchMetricDescriptor(
			"lobecorp.sweeper_ai.entity_ticks", "count", MetricDirection.NEUTRAL);
	private final List<Sweeper> entities = new ArrayList<>();
	private final List<ChunkPos> forcedChunks = new ArrayList<>();
	private final int[] initialTicks = new int[LobecorpBenchConfig.ENTITY_COUNT];
	private int stabilizeTicks;
	private int warmupTicks;
	private int measureTicks;

	@Override
	public void setup(BenchServerContext context) {
		var level = context.level();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			throw new IllegalStateException("Sweeper AI requires a non-peaceful benchmark world");
		}

		for (int index = 0; index < LobecorpBenchConfig.ENTITY_COUNT; index++) {
			createEntity(level, context.seed(), index);
		}
	}

	private void createEntity(ServerLevel level, long seed, int index) {
		int x = index % LobecorpBenchConfig.GRID_WIDTH * LobecorpBenchConfig.GRID_SPACING;
		int z = index / LobecorpBenchConfig.GRID_WIDTH * LobecorpBenchConfig.GRID_SPACING;
		var chunk = new ChunkPos(x >> 4, z >> 4);
		if (!forcedChunks.contains(chunk) && level.getChunkSource().updateChunkForced(chunk, true)) {
			forcedChunks.add(chunk);
		}

		level.getChunk(chunk.x(), chunk.z());
		var uuid = UUID.nameUUIDFromBytes(("lobecorp-bench:" + seed + ":" + index).getBytes(StandardCharsets.UTF_8));
		if (level.getEntity(uuid) != null) {
			throw new IllegalStateException("A benchmark entity from an earlier run is still present: " + uuid);
		}

		var sweeper = new Sweeper(OrdealEntityTypes.SWEEPER.get(), level);
		entities.add(sweeper);
		sweeper.setUUID(uuid);
		sweeper.getRandom().setSeed(seed + index);
		sweeper.setVariant(SweeperVariant.values()[index % SweeperVariant.values().length]);
		sweeper.setPersistenceRequired();
		sweeper.setInvulnerable(true);
		sweeper.setPos(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
		if (!level.addFreshEntity(sweeper)) {
			throw new IllegalStateException("Sweeper benchmark spawn was rejected");
		}
	}

	@Override
	public BenchStepResult stabilize(BenchServerContext context) {
		return ++stabilizeTicks >= LobecorpBenchConfig.STABILIZE_TICKS
				? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
	}

	@Override
	public BenchStepResult warmup(BenchServerContext context) {
		requireOwnedEntities(context.level());
		if (++warmupTicks < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchStepResult.CONTINUE;
		}

		for (int index = 0; index < entities.size(); index++) {
			initialTicks[index] = entities.get(index).tickCount;
		}
		return BenchStepResult.COMPLETE;
	}

	@Override
	public BenchStepResult measure(BenchServerContext context) {
		requireOwnedEntities(context.level());
		context.metrics().record(LOADED_ENTITIES, entities.size());
		context.metrics().record(ENTITY_TICKS, entities.stream().mapToLong(entity -> entity.tickCount).sum());
		return ++measureTicks >= LobecorpBenchConfig.MEASURE_TICKS
				? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
	}

	@Override
	public void verify(BenchServerContext context) {
		requireOwnedEntities(context.level());
		if (measureTicks != LobecorpBenchConfig.MEASURE_TICKS) {
			throw new AssertionError("Sweeper measurement did not finish");
		}

		for (int index = 0; index < entities.size(); index++) {
			if (entities.get(index).tickCount <= initialTicks[index]) {
				throw new AssertionError("A sweeper did not tick during measurement");
			}
		}
	}

	private void requireOwnedEntities(ServerLevel level) {
		if (entities.size() != LobecorpBenchConfig.ENTITY_COUNT
				|| entities.stream().anyMatch(entity -> entity.isRemoved() || level.getEntity(entity.getUUID()) != entity)) {
			throw new AssertionError("The sweeper workload changed during the benchmark");
		}
	}

	@Override
	public void teardown(BenchServerContext context) {
		try {
			for (var entity : entities) {
				entity.discard();
			}
		} finally {
			entities.clear();
			for (var chunk : forcedChunks) {
				context.level().getChunkSource().updateChunkForced(chunk, false);
			}
			forcedChunks.clear();
		}
	}
}
