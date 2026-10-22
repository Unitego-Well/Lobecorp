package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperVariant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// 客户端本地实体仅用于隔离渲染成本，不代表服务端 AI 或网络同步负载。
public class SweeperRenderScenario implements BenchClientScenario {
	private static final BenchMetricDescriptor LOADED_ENTITIES = new BenchMetricDescriptor(
			"lobecorp.sweeper_render.loaded_entities", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor FRAME_SAMPLES = new BenchMetricDescriptor(
			"lobecorp.sweeper_render.frame_samples", "count", MetricDirection.NEUTRAL);
	private final List<Sweeper> entities = new ArrayList<>();
	private BlockPos origin;
	private BenchClientPose originalPose;
	private BenchPoseHold poseHold;
	private CompletableFuture<Path> screenshot;
	private Path screenshotPath;
	private int stabilizeTicks;
	private int warmupTicks;
	private int captureRecoveryTicks;
	private int measureTicks;
	private long firstFrameCount;
	private long measuredFrames;

	@Override
	public void setup(BenchClientContext context) {
		originalPose = context.automation().pose();
		int halfGrid = LobecorpBenchConfig.GRID_WIDTH * LobecorpBenchConfig.GRID_SPACING / 2;
		origin = context.player().blockPosition().offset(-halfGrid, 0, -halfGrid);
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		if (entities.isEmpty()) {
			for (int index = 0; index < LobecorpBenchConfig.ENTITY_COUNT; index++) {
				var position = position(index);
				if (!context.level().getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
					return BenchClientStepResult.CONTINUE;
				}
			}
			createEntities(context);
		}

		requireOwnedEntities(context);
		return ++stabilizeTicks >= LobecorpBenchConfig.STABILIZE_TICKS
				&& context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	private BlockPos position(int index) {
		return origin.offset(
				index % LobecorpBenchConfig.GRID_WIDTH * LobecorpBenchConfig.GRID_SPACING, 0,
				index / LobecorpBenchConfig.GRID_WIDTH * LobecorpBenchConfig.GRID_SPACING);
	}

	private void createEntities(BenchClientContext context) {
		for (int index = 0; index < LobecorpBenchConfig.ENTITY_COUNT; index++) {
			int id = LobecorpBenchConfig.CLIENT_ENTITY_ID_BASE - index;
			if (context.level().getEntity(id) != null) {
				throw new IllegalStateException("The benchmark client entity ID is already occupied");
			}

			var position = position(index);
			var sweeper = new Sweeper(OrdealEntityTypes.SWEEPER.get(), context.level());
			entities.add(sweeper);
			sweeper.setId(id);
			sweeper.setVariant(SweeperVariant.values()[index % SweeperVariant.values().length]);
			sweeper.setNoAi(true);
			sweeper.setNoGravity(true);
			sweeper.setInvulnerable(true);
			sweeper.setPos(position.getX() + 0.5,
					context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							position.getX(), position.getZ()), position.getZ() + 0.5);
			sweeper.setYRot(180.0F);
			context.level().addEntity(sweeper);
		}

		double halfGrid = (LobecorpBenchConfig.GRID_WIDTH - 1) * LobecorpBenchConfig.GRID_SPACING / 2.0;
		double centerX = origin.getX() + halfGrid + 0.5;
		double centerZ = origin.getZ() + halfGrid + 0.5;
		double groundY = entities.getFirst().getY();
		var pose = BenchClientPose.lookingAt(centerX, groundY + LobecorpBenchConfig.CAMERA_HEIGHT,
				origin.getZ() - LobecorpBenchConfig.CAMERA_DISTANCE, centerX, groundY + 1, centerZ);
		poseHold = context.automation().holdPose(pose);
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		requireOwnedEntities(context);
		if (++warmupTicks < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}

		if (screenshot == null) {
			screenshot = context.automation().captureScreenshot("sweeper-render");
			return BenchClientStepResult.CONTINUE;
		}

		if (!screenshot.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}

		screenshotPath = screenshot.getNow(null);
		return ++captureRecoveryTicks >= LobecorpBenchConfig.STABLE_FRAMES
				&& context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		requireOwnedEntities(context);
		if (measureTicks == 0) {
			firstFrameCount = context.frames().sampleCount();
		}

		context.metrics().record(LOADED_ENTITIES, entities.size());
		if (++measureTicks < LobecorpBenchConfig.MEASURE_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}

		measuredFrames = context.frames().sampleCount() - firstFrameCount;
		context.metrics().record(FRAME_SAMPLES, measuredFrames);
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		requireOwnedEntities(context);
		if (measureTicks != LobecorpBenchConfig.MEASURE_TICKS || measuredFrames <= 0) {
			throw new AssertionError("The client render measurement did not produce frame samples");
		}

		if (screenshotPath == null || !Files.isRegularFile(screenshotPath) || Files.size(screenshotPath) == 0) {
			throw new AssertionError("The sweeper render screenshot is missing");
		}
	}

	private void requireOwnedEntities(BenchClientContext context) {
		if (entities.size() != LobecorpBenchConfig.ENTITY_COUNT
				|| entities.stream().anyMatch(entity -> entity.isRemoved() || context.level().getEntity(entity.getId()) != entity)) {
			throw new AssertionError("The client sweeper workload changed during the benchmark");
		}
	}

	@Override
	public void teardown(BenchClientContext context) {
		try {
			for (var entity : entities) {
				entity.discard();
			}
		} finally {
			entities.clear();
			if (poseHold != null) {
				poseHold.close();
			}
			if (originalPose != null) {
				context.automation().setPose(originalPose);
			}
			if (screenshot != null && !screenshot.isDone()) {
				screenshot.cancel(false);
			}
		}
	}
}
