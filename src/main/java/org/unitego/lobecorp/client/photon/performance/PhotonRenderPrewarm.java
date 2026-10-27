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
import java.util.Deque;
import java.util.List;

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

	private static int prebuildObject(IFXObject object) {
		try {
			if (object instanceof PhotonModelOutput model) {
				return model.prewarmRenderTypes();
			}

			if (object instanceof Emitter emitter) {
				var renderer = emitter.rendererRuntime();
				return prebuild(renderer, emitter.geometryMode()) + prebuildInstanced(renderer, emitter);
			}
		} catch (RuntimeException invalid) {
			Lobecorp.LOGGER.warn("渲染预热失败：{}", object.getName(), invalid);
		}

		return 0;
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

	/// GPU 实例化是独立管线（`#define` 与顶点格式烘进管线），首次实例绘制才会构建；这里按发射器类型补建。
	private static int prebuildInstanced(RendererSetting.Runtime renderer, Emitter emitter) {
		var variants = variants(emitter);

		if (variants.isEmpty()) {
			return 0;
		}

		int built = 0;

		for (var setting : renderer.getMaterials()) {
			var renderType = setting.getRenderType(VertexFormat.Mode.QUADS);

			if (renderType == null) {
				continue;
			}

			var info = PhotonRenderTypes.drawInfo(renderType);

			if (info == null || info.instanced() == null) {
				continue;
			}

			for (var variant : variants) {
				try {
					var pipeline = pipeline(info, variant);

					if (pipeline == null) {
						continue;
					}

					if (RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
						built++;
					} else {
						Lobecorp.LOGGER.warn("实例化管线预编译失败：{} / {}", emitter.getName(), variant);
					}
				} catch (RuntimeException invalid) {
					Lobecorp.LOGGER.warn("实例化管线预热失败：{} / {}", emitter.getName(), variant, invalid);
				}
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

	/// 逐 tick 预算的预热队列：把整棵树拆成“每个对象一次材质预构建 + 每个子发射器一次子模板准备”，
	/// 每 tick 只花固定时间，避免女皇出现时一次冻结数秒；子模板准备完成后把它自己的对象也排进队列。
	public static final class Queue {
		private static final long BUDGET_NANOS = 10_000_000L;
		private final Deque<Runnable> jobs = new ArrayDeque<>();
		private int built;
		private int spawners;
		private int failed;

		/// 插到队列最前面执行；用于必须先完成的壳准备，保证首次施放能采用到预备好的运行态。
		public void prepend(Runnable job) {
			if (!ENABLED) {
				job.run();
				return;
			}

			jobs.addFirst(job);
		}

		/// 把一次“壳准备”登记为队列首个任务：准备子模板与命名运行时，再按深度递归排队。
		public void addShell(FXRuntime runtime, Runnable prepare, int depth) {
			if (!ENABLED) {
				prepare.run();
				return;
			}

			prepend(() -> {
				if (runtime.isDestroyed()) {
					return;
				}

				prepare.run();
				add(runtime, depth);
			});
		}

		/// 只登记任务，不做重活；`depth` 为子模板递归深度。
		public void add(FXRuntime runtime, int depth) {
			if (!ENABLED) {
				return;
			}

			var objects = List.copyOf(runtime.objects.values());

			for (var object : objects) {
				jobs.addLast(() -> {
					if (!runtime.isDestroyed()) {
						built += prebuildObject(object);
					}
				});
			}

			if (depth > 0) {
				for (var object : objects) {
					if (object instanceof PhotonChildSpawner spawner) {
						jobs.addLast(() -> prepare(spawner, depth));
					}
				}
			}
		}

		private void prepare(PhotonChildSpawner spawner, int depth) {
			spawners++;

			try {
				spawner.prepareNext();
			} catch (RuntimeException invalid) {
				failed++;
				Lobecorp.LOGGER.warn("子模板预热失败：{}", spawner.getName(), invalid);
				return;
			}

			var template = spawner.preparedTemplate();

			if (template != null) {
				add(template.runtime(), depth - 1);
			}
		}

		public boolean done() {
			return jobs.isEmpty();
		}

		/// 在预算内尽量推进；返回本轮执行的任务数。
		public int drain() {
			long started = System.nanoTime();
			int ran = 0;

			while (!jobs.isEmpty() && System.nanoTime() - started < BUDGET_NANOS) {
				var job = jobs.pollFirst();
				ran++;

				if (job == null) {
					break;
				}

				try {
					job.run();
				} catch (RuntimeException invalid) {
					failed++;
					Lobecorp.LOGGER.warn("渲染预热任务失败", invalid);
				}
			}

			return ran;
		}

		public void clear() {
			jobs.clear();
		}

		public int built() {
			return built;
		}

		public int spawners() {
			return spawners;
		}

		public int failed() {
			return failed;
		}
	}
}
