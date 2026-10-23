package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.client.fx.FX;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.client.BenchPoseHold;
import net.minecraft.nbt.StringTag;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// 固定几何和相机的诊断负载，静止与动画只切换同一资产的片段；不替代真实光束验收。
public class PhotonModelRenderScenario implements BenchClientScenario {
	private static final int GRID = 16;
	private static final int WARMUP = 100;
	private static final int MEASURE = 300;
	private static final int MODEL_GRID = 4;
	private static final float SPACING = 2.5f;
	private static final BenchMetricDescriptor MODELS = new BenchMetricDescriptor(
			"lobecorp.photon.loaded_models", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor STARTUP = new BenchMetricDescriptor(
			"lobecorp.photon.create_emit", "ns", MetricDirection.LOWER_IS_BETTER);
	private final String label;
	private final int count;
	private final boolean animated;
	private final boolean cold;
	private final List<PhotonGeoEffect> effects = new ArrayList<>();
	private final List<PhotonModelOutput> models = new ArrayList<>();
	private BenchPoseHold poseHold;
	private BenchClientPose originalPose;
	private CompletableFuture<Path> screenshot;
	private Vector3f origin;
	private String assetJson;
	private int screenshotRecovery;
	private int ticks;
	private long createNanos;
	private long firstFrame;
	private boolean created;

	public PhotonModelRenderScenario(String label, int count, boolean animated, boolean cold) {
		this.label = label;
		this.count = count;
		this.animated = animated;
		this.cold = cold;
	}

	@Override
	public void setup(BenchClientContext context) {
		if (!ModList.get().isLoaded("sodium")) {
			throw new IllegalStateException("本轮模型基准要求加载 Sodium。");
		}
		var window = context.minecraft().getWindow();
		GLFW.glfwMaximizeWindow(window.handle());
		GLFW.glfwShowWindow(window.handle());
		GLFW.glfwFocusWindow(window.handle());
		originalPose = context.automation().pose();
		var player = context.player();
		origin = new Vector3f((float) player.getX(), (float) player.getY(), (float) player.getZ());
		assetJson = asset().toString();
		poseHold = context.automation().holdPose(BenchClientPose.lookingAt(
				player.getX(), player.getY() + 7, player.getZ() - 10,
				player.getX(), player.getY() + 2, player.getZ() + 5));
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		if (++ticks < LobecorpBenchConfig.STABILIZE_TICKS
				|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		ticks = 0;
		if (!cold) {
			create(context);
		}
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		if (++ticks < WARMUP) {
			return BenchClientStepResult.CONTINUE;
		}
		if (!cold && screenshot == null) {
			screenshot = context.automation().captureScreenshot(label);
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot != null && !screenshot.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot != null) {
			screenshot.join();
			if (++screenshotRecovery < LobecorpBenchConfig.STABLE_FRAMES
					|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
				return BenchClientStepResult.CONTINUE;
			}
		}
		ticks = 0;
		return BenchClientStepResult.COMPLETE;
	}

	private void create(BenchClientContext context) {
		long started = System.nanoTime();
		for (int index = 0; index < count; index++) {
			var fx = new FX();
			var model = new PhotonModelOutput();
			model.config.setAssetSnapshot(StringTag.valueOf(assetJson));
			model.config.clip = animated ? "turn" : "";
			model.config.useReferenceDimensions = false;
			fx.getFxData().objects().add(model);
			var effect = new PhotonGeoEffect(fx, context.level());
			effects.add(effect);
			var runtimeModel = (PhotonModelOutput) effect.runtime().objects.get(model.id());
			models.add(runtimeModel);
			float x = count == 1 ? 0 : (index % MODEL_GRID - (MODEL_GRID - 1) / 2f) * SPACING;
			int row = index / MODEL_GRID;
			float z = count == 1 ? 0 : (row - (MODEL_GRID - 1) / 2f) * SPACING;
			effect.anchor(new Vector3f(origin.x + x, origin.y + 2, origin.z + 5 + z), new Quaternionf(), new Vector3f(2));
			effect.start();
		}
		createNanos = System.nanoTime() - started;
		created = true;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		if (ticks == 0) {
			firstFrame = context.frames().sampleCount();
			if (!created) {
				create(context);
			}
			context.metrics().record(STARTUP, createNanos);
		}
		for (var model : models) {
			if (model.failure() != null || model.getParticleAmount() == 0) {
				throw new IllegalStateException("模型负载没有持续参与原生输出：" + model.failure());
			}
		}
		context.metrics().record(MODELS, models.size());
		return ++ticks >= MEASURE ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		if (models.size() != count || context.frames().sampleCount() <= firstFrame
				|| effects.stream().anyMatch(effect -> !effect.isAlive())) {
			throw new AssertionError("模型基准负载或帧样本不完整。");
		}
		for (var model : models) {
			var frame = model.sampledFrame();
			if (frame == null || frame.groups().isEmpty()) {
				throw new AssertionError("模型没有产生实际姿态采样。");
			}
		}
		var report = new JsonObject();
		report.addProperty("label", label);
		report.addProperty("synthetic", true);
		report.addProperty("models", count);
		report.addProperty("animated", animated);
		report.addProperty("cold_first_emit", cold);
		report.addProperty("vertices_per_model", (GRID + 1) * (GRID + 1));
		report.addProperty("faces_per_model", GRID * GRID);
		report.addProperty("create_emit_ns", createNanos);
		report.addProperty("measured_frames", context.frames().sampleCount() - firstFrame);
		report.addProperty("sodium", ModList.get().getModContainerById("sodium").orElseThrow()
				.getModInfo().getVersion().toString());
		context.artifacts().write(label + "-workload.json", "application/json", report.toString());
	}

	@Override
	public void teardown(BenchClientContext context) {
		try {
			effects.forEach(PhotonGeoEffect::close);
		} finally {
			effects.clear();
			models.clear();
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

	private static JsonObject asset() {
		var asset = new JsonObject();
		asset.addProperty("version", 1);
		var vertices = new JsonArray();
		var faces = new JsonArray();
		var weights = new JsonObject();
		for (int y = 0; y <= GRID; y++) {
			for (int x = 0; x <= GRID; x++) {
				var vertex = new JsonArray();
				vertex.add((float) x / GRID - 0.5f);
				vertex.add((float) y / GRID - 0.5f);
				vertex.add(0);
				weights.add(Integer.toString(vertices.size()), JsonParser.parseString("[{\"boneId\":\"root\",\"value\":1}]"));
				vertices.add(vertex);
			}
		}
		for (int y = 0; y < GRID; y++) {
			for (int x = 0; x < GRID; x++) {
				int first = y * (GRID + 1) + x;
				var face = new JsonObject();
				var indices = new JsonArray();
				for (int index : new int[]{first, first + 1, first + GRID + 2, first + GRID + 1}) {
					indices.add(index);
				}
				face.add("vertices", indices);
				face.add("uv", JsonParser.parseString("[[0,0],[1,0],[1,1],[0,1]]"));
				faces.add(face);
			}
		}
		asset.add("vertices", vertices);
		asset.add("faces", faces);
		asset.add("weights", weights);
		asset.add("bones", JsonParser.parseString("[{\"id\":\"root\"}]"));
		asset.add("clips", JsonParser.parseString("""
				[{"id":"turn","duration":40,"loop":true,"boneTracks":{"root":[
				{"time":0,"transform":{}},{"time":20,"transform":{"rotation":[0,0.38268343,0,0.9238795]}},
				{"time":40,"transform":{}}]}}]
				"""));
		return asset;
	}
}
