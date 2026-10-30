package org.unitego.lobecorp.client.photon.performance;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.InstancedRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/// 首施法渲染侧预热：把“第一次绘制才会构建”的材质、着色图与管线提前到女皇出现时构建。
///
/// 只走绘制用的同一入口（`MaterialSetting#getRenderType` 与 `PhotonPipelines`），不发射粒子、不推进时间线、
/// 不改变可见性；因此不产生残留粒子或时序偏移。渲染线程调用（客户端 tick 已在该线程）。
/// 默认开启，`-Dlobecorp.photon.prewarmRender=false` 关闭；失败按材质隔离并记录，不阻断首次施放。
public final class PhotonRenderPrewarm {
	private static final boolean ENABLED = !"false".equals(System.getProperty("lobecorp.photon.prewarmRender", "true"));

	private PhotonRenderPrewarm() {
	}

	public static boolean enabled() {
		return ENABLED;
	}

	/// 与 `Emitter#bakeGroup` 相同的材质入口：每个材质按同一状态构建一次渲染类型，含着色图编译与管线预编译。
	public static int prebuild(RendererSetting.Runtime renderer, VertexFormat.Mode mode) {
		if (!ENABLED) {
			return 0;
		}

		int built = 0;

		for (var setting : renderer.getMaterials()) {
			try {
				if (setting.getRenderType(mode) != null) {
					built++;
				}
			} catch (RuntimeException invalid) {
				Lobecorp.LOGGER.warn("材质渲染类型预热失败", invalid);
			}
		}

		return built;
	}

	private static @Nullable RenderPipeline pipeline(PhotonRenderTypes.PhotonDrawInfo info,
	                                                 PhotonPipelines.InstancedVariant variant) {
		var recipe = info.instanced();

		if (recipe == null) {
			return null;
		}

		var graph = info.bindings().graph();

		if (graph != null) {
			return PhotonPipelines.graphShader(graph.compiled(), variant, recipe.key(),
					graph.usedChannelMask(), graph.usesCustomData());
		}

		if (recipe.customShaderKey() != null) {
			return PhotonPipelines.instancedCustomShader(variant, recipe.customShaderKey());
		}

		return PhotonPipelines.instancedHdrParticle(variant, recipe.hdrFragment(), recipe.key());
	}

	/// 该发射器实际会走的实例化变体；关闭 GPU 实例化时为空（走 CPU 批量路径）。
	private static List<PhotonPipelines.InstancedVariant> variants(Emitter emitter) {
		if (!gpuInstanced(emitter)) {
			return List.of();
		}

		var variants = new ArrayList<PhotonPipelines.InstancedVariant>(2);

		switch (emitter) {
			case ParticleEmitter ignored -> {
				variants.add(PhotonPipelines.InstancedVariant.TILE);

				if (emitter.rendererRuntime() instanceof ParticleRendererSetting.Runtime tile
						&& tile.getRenderMode() == ParticleRendererSetting.Mode.Model
						&& tile.getModel() != null) {
					variants.add(tile.isTangent() ? PhotonPipelines.InstancedVariant.MODEL_TANGENT
							: PhotonPipelines.InstancedVariant.MODEL);
				}
			}
			case BeamEmitter ignored -> variants.add(PhotonPipelines.InstancedVariant.BEAM);
			case TrailEmitter ignored -> variants.add(PhotonPipelines.InstancedVariant.TRAIL);
			case AraTrailEmitter ignored -> {
				variants.add(PhotonPipelines.InstancedVariant.ARA);
				variants.add(PhotonPipelines.InstancedVariant.ARA_TUBE);
			}
			default -> {
			}
		}

		return variants;
	}

	private static boolean gpuInstanced(Emitter emitter) {
		return switch (emitter.rendererRuntime()) {
			case ParticleRendererSetting.Runtime tile -> tile.isUseGPUInstance();
			case InstancedRendererSetting.Runtime instanced -> instanced.isUseGPUInstance();
			default -> false;
		};
	}

	/// 生产队列使用的单线程任务调度器；时钟与失败回调可替换，纯 JVM 测试不接触渲染设备。
	public static class WorkQueue<T> {
		private final Deque<Work<T>> pending = new ArrayDeque<>();
		private final LongSupplier clock;
		private final Consumer<RuntimeException> failure;
		private long generation;
		private long executingGeneration = -1;

		public WorkQueue(LongSupplier clock, Consumer<RuntimeException> failure) {
			this.clock = clock;
			this.failure = failure;
		}

		public void addFirst(T type, BooleanSupplier alive, Runnable action) {
			if (accepting()) {
				pending.addFirst(new Work<>(type, alive, action, generation));
			}
		}

		public void addLast(T type, BooleanSupplier alive, Runnable action) {
			if (accepting()) {
				pending.addLast(new Work<>(type, alive, action, generation));
			}
		}

		private boolean accepting() {
			return executingGeneration == -1 || executingGeneration == generation;
		}

		/// 预算只限制任务开始；准备模型和设备编译均不能中断。
		public int drain(long budgetNanos) {
			long started = clock.getAsLong();
			int ran = 0;

			while (!pending.isEmpty() && clock.getAsLong() - started < budgetNanos) {
				var work = pending.removeFirst();
				long previousGeneration = executingGeneration;
				executingGeneration = work.generation();
				ran++;

				try {
					if (work.generation() == generation && work.alive().getAsBoolean()) {
						work.action().run();
					}
				} catch (RuntimeException invalid) {
					failure.accept(invalid);
				} finally {
					executingGeneration = previousGeneration;
				}
			}

			return ran;
		}

		public void clear() {
			generation++;
			pending.clear();
		}

		public boolean isEmpty() {
			return pending.isEmpty();
		}

		public int size() {
			return pending.size();
		}

		public long remaining(T type) {
			return pending.stream().filter(work -> work.type().equals(type)).count();
		}

		private record Work<T>(T type, BooleanSupplier alive, Runnable action, long generation) {
		}
	}

	/// 逐 tick 预算的预热队列：模型准备、单材质获取、单实例变体预编译分别排队，
	/// 每 tick 在预算内开始任务，单个任务不可抢占，可能超过预算；子模板准备完成后把它自己的对象也排进队列。
	public static final class Queue {
		private static final long BUDGET_NANOS = 10_000_000L;
		private static final boolean DIAGNOSTICS = PhotonCastProfiler.enabled();

		private enum JobType {
			DEFINE, SHELL, PREPARE, MATERIAL, VARIANT, CHILD
		}

		private final long queuedAt = DIAGNOSTICS ? System.nanoTime() : 0;
		private final long[] jobNanos = DIAGNOSTICS ? new long[JobType.values().length] : null;
		private final long[] jobMaxNanos = DIAGNOSTICS ? new long[JobType.values().length] : null;
		private final int[] jobCounts = DIAGNOSTICS ? new int[JobType.values().length] : null;
		private long drains;
		private long totalWorkNanos;
		private long totalJobCount;
		private int builtIncludingHits;
		private int spawners;
		private int failed;
		private final WorkQueue<JobType> jobs = new WorkQueue<>(System::nanoTime, invalid -> {
			failed++;
			Lobecorp.LOGGER.warn("渲染预热任务失败", invalid);
		});

		/// 插到队列最前面执行；用于必须先完成的壳准备，保证首次施放能采用到预备好的运行态。
		public void prepend(Runnable job) {
			if (!ENABLED) {
				job.run();
				return;
			}

			jobs.addFirst(JobType.DEFINE, () -> true, measured(JobType.DEFINE, job));
		}

		/// 把一次“壳准备”登记为队列首个任务：准备子模板与命名运行时，再按深度递归排队。
		public void addShell(FXRuntime runtime, Runnable prepare, int depth) {
			if (!ENABLED) {
				prepare.run();
				return;
			}

			jobs.addFirst(JobType.SHELL, () -> !runtime.isDestroyed(), measured(JobType.SHELL, () -> {
				prepare.run();
				add(runtime, depth);
			}));
		}

		/// 只登记任务，不做重活；`depth` 为子模板递归深度。
		public void add(FXRuntime runtime, int depth) {
			if (!ENABLED) {
				return;
			}

			add(runtime, depth, () -> true);
		}

		private void add(FXRuntime runtime, int depth, BooleanSupplier parentAlive) {
			BooleanSupplier alive = () -> parentAlive.getAsBoolean() && !runtime.isDestroyed();
			addObjects(List.copyOf(runtime.objects.values()), depth, alive);
		}

		private void addObjects(List<IFXObject> objects, int depth, BooleanSupplier alive) {
			for (var object : objects) {
				enqueue(JobType.PREPARE, alive, () -> {
					if (object instanceof PhotonModelOutput model) {
						model.queuePrewarmRenderTypes(this, alive);
					} else if (object instanceof Emitter emitter) {
						addMaterials(alive, emitter.rendererRuntime(), emitter.geometryMode(),
								variants(emitter), emitter.getName());
					}
				});
			}

			if (depth > 0) {
				// 对象准备先登记其材质，再把子模板任务接到末尾，保持原来的材质优先顺序。
				enqueue(JobType.PREPARE, alive, () -> {
					for (var object : objects) {
						if (object instanceof PhotonChildSpawner spawner) {
							enqueue(JobType.CHILD, alive, () -> prepare(spawner, depth, alive));
						}
					}
				});
			}
		}

		private void enqueue(JobType type, BooleanSupplier alive, Runnable action) {
			jobs.addLast(type, alive, measured(type, action));
		}

		/// 模型准备完成后，按组和材质原顺序登记；这里不获取渲染类型。
		public void addMaterials(BooleanSupplier alive, RendererSetting.Runtime renderer, VertexFormat.Mode mode) {
			addMaterials(alive, renderer, mode, List.of(), "model");
		}

		private void addMaterials(BooleanSupplier alive, RendererSetting.Runtime renderer, VertexFormat.Mode mode,
		                          List<PhotonPipelines.InstancedVariant> variants, String name) {
			if (!ENABLED || !alive.getAsBoolean()) {
				return;
			}

			var variantJobs = new ArrayList<PendingVariant>();

			for (var setting : renderer.getMaterials()) {
				var info = new PhotonRenderTypes.PhotonDrawInfo[1];
				enqueue(JobType.MATERIAL, alive, () -> {
					var renderType = setting.getRenderType(mode);

					if (renderType != null) {
						builtIncludingHits++;

						if (mode == VertexFormat.Mode.QUADS && !variants.isEmpty()) {
							info[0] = PhotonRenderTypes.drawInfo(renderType);
						}
					}
				});

				if (variants.isEmpty()) {
					continue;
				}

				// 非 QUADS 基础几何与实例化有不同的材质键，必须各取一次，不能复用错误模式。
				if (mode != VertexFormat.Mode.QUADS) {
					variantJobs.add(new PendingVariant(JobType.MATERIAL, () -> {
						var renderType = setting.getRenderType(VertexFormat.Mode.QUADS);
						info[0] = renderType == null ? null : PhotonRenderTypes.drawInfo(renderType);
					}));
				}

				for (var variant : variants) {
					variantJobs.add(new PendingVariant(JobType.VARIANT, () -> {
						if (info[0] == null || info[0].instanced() == null) {
							return;
						}

						var pipeline = pipeline(info[0], variant);

						if (pipeline == null) {
							return;
						}

						if (RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
							builtIncludingHits++;
						} else {
							failed++;
							Lobecorp.LOGGER.warn("实例化管线预编译失败：{} / {}", name, variant);
						}
					}));
				}
			}

			// 所有基础材质先排完，再按材质逐个排实例化变体。
			for (var job : variantJobs) {
				enqueue(job.type(), alive, job.action());
			}
		}

		private record PendingVariant(JobType type, Runnable action) {
		}

		private void prepare(PhotonChildSpawner spawner, int depth, BooleanSupplier alive) {
			spawners++;
			spawner.prepareNext();
			var template = spawner.preparedTemplate();

			if (template != null) {
				add(template.runtime(), depth - 1, alive);
			}
		}

		public boolean done() {
			return jobs.isEmpty();
		}

		private Runnable measured(JobType type, Runnable job) {
			if (!DIAGNOSTICS) {
				return job;
			}

			return () -> {
				long started = System.nanoTime();
				try {
					job.run();
				} finally {
					long elapsed = System.nanoTime() - started;
					int index = type.ordinal();
					jobCounts[index]++;
					jobNanos[index] += elapsed;
					totalWorkNanos += elapsed;
					totalJobCount++;
					jobMaxNanos[index] = Math.max(jobMaxNanos[index], elapsed);
				}
			};
		}

		/// 在预算内尽量推进；单个任务可以超出预算，返回本轮执行的任务数。
		public int drain() {
			return drain(-1);
		}

		public int drain(long tick) {
			if (DIAGNOSTICS) {
				Arrays.fill(jobNanos, 0);
				Arrays.fill(jobMaxNanos, 0);
				Arrays.fill(jobCounts, 0);
			}

			long started = System.nanoTime();
			int ran = jobs.drain(BUDGET_NANOS);

			if (DIAGNOSTICS) {
				logDrain(tick, started, ran);
			}

			return ran;
		}

		private void logDrain(long tick, long started, int ran) {
			long ended = System.nanoTime();
			var breakdown = new StringBuilder();
			for (var type : JobType.values()) {
				int index = type.ordinal();
				breakdown.append(type).append("[count=").append(jobCounts[index])
						.append(",us=").append(jobNanos[index] / 1_000)
						.append(",maxUs=").append(jobMaxNanos[index] / 1_000)
						.append(",maxNs=").append(jobMaxNanos[index])
						.append(",remaining=").append(jobs.remaining(type)).append("] ");
			}

			Lobecorp.LOGGER.info("CASTPREWARM queue tick={}, drain={}, ran={}, remaining={}, drainUs={}, elapsedUs={}, done={}, budgetUs={}, builtIncludingHits={}, failed={}, totalWorkNs={}, totalJobs={}, jobs={}, startNs={}, endNs={}",
					tick, ++drains, ran, jobs.size(), (ended - started) / 1_000, (ended - queuedAt) / 1_000,
					jobs.isEmpty(), BUDGET_NANOS / 1_000, builtIncludingHits, failed, totalWorkNanos, totalJobCount, breakdown, started, ended);
		}

		public void clear() {
			jobs.clear();
		}

		/// 成功返回的基础渲染类型与有效实例化管线数，包含缓存命中，不代表新增编译数。
		public int built() {
			return builtIncludingHits;
		}

		public int spawners() {
			return spawners;
		}

		public int failed() {
			return failed;
		}
	}
}
