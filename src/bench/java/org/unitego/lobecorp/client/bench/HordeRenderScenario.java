package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.*;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeGpuRenderer;
import org.unitego.lobecorp.client.horde.HordeRenderMetrics;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeRoute;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/// 在隔离客户端构造仅用于显示的事件快照；不代表真实参战或网络性能。
public class HordeRenderScenario implements BenchClientScenario {
    private static final UUID EVENT_ID = UUID.fromString("85fb7af6-51fc-4f6e-985f-46409699774d");
	private static final int UNLIMITED_BENCH_COUNT = 10000;
	private int configuredCount;
	private int count;
    private static final int MEASURE_TICKS = 1200;
    private static final String SPARK_START = "sparkc profiler start --timeout 30";
	private static final Pattern SPARK_LINK = Pattern.compile("https://spark\\.lucko\\.me/[A-Za-z0-9]+");
	private Set<String> sparkLinksBefore = Set.of();
	private static final double WIDTH = 512;
    private static final double SPEED = 0.3;
    private static final BenchMetricDescriptor INSTANCES = new BenchMetricDescriptor(
            "lobecorp.horde_render.instances", "count", MetricDirection.NEUTRAL);
    private BenchClientPose originalPose;
    private BenchPoseHold pose;
    private CompletableFuture<Path> screenshot;
    private Path screenshotPath;
    private Vec3 origin;
    private CompoundTag header;
    private long sequence;
    private long baseTick;
    private long firstFrame;
    private int elapsed;
    private int phaseTicks;
    private int previousMaximum;
    private int previousRange;
	private int temporaryHeightSamples;
    private boolean initialized;
    private boolean previousEnabled;
    private boolean profilerStarted;
	private boolean ownsStageCapture;
	private boolean ownsEvent;
	private boolean poseSaved;
	private boolean configurationSaved;
	private UUID gpuCaptureToken;
	private HordeBenchmarkEnvironment environment;
	private long renderFailureBaseline;
	private static final BenchMetricDescriptor VISIBLE_PIXELS = new BenchMetricDescriptor(
			"lobecorp.horde_render.visible_pixels", "count", MetricDirection.NEUTRAL);
	// 与原夹具一致的侧上方视角；锚点必须是实际地面，不能沿用可能悬空的玩家Y。
	private static final int CAMERA_HEIGHT = 64;
	private static final int CAMERA_DISTANCE = 100;
	private double groundY;
	private CompletableFuture<Path> baselineScreenshot;
	private Path baselineScreenshotPath;
	private boolean baselineCaptured;
	private boolean hordeResumed;
	private int visiblePixels = -1;
	private int[] visibleBounds = new int[0];
	private static final BenchMetricDescriptor[] STAGE_CPU = Arrays.stream(HordeRenderMetrics.Stage.values())
			.map(stage -> new BenchMetricDescriptor("lobecorp.horde.stage." + stage.name().toLowerCase(Locale.ROOT) + ".cpu", "ns", MetricDirection.NEUTRAL))
			.toArray(BenchMetricDescriptor[]::new);
	private static final BenchMetricDescriptor DRAW_GPU = new BenchMetricDescriptor(
			"lobecorp.horde.draw_gpu_elapsed", "ns", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor[] STAGE_GPU = Arrays.stream(HordeRenderMetrics.Stage.values())
			.map(stage -> new BenchMetricDescriptor("lobecorp.horde.stage." + stage.name().toLowerCase(Locale.ROOT) + ".gpu_frame_sum", "ns", MetricDirection.NEUTRAL))
			.toArray(BenchMetricDescriptor[]::new);
	private static final BenchMetricDescriptor[] COUNTERS = Arrays.stream(HordeRenderMetrics.Counter.values())
			.map(counter -> new BenchMetricDescriptor("lobecorp.horde." + counter.name().toLowerCase(Locale.ROOT), counter == HordeRenderMetrics.Counter.PENETRATION_MAX ? "microblocks" : counter.name().endsWith("_BYTES") ? "bytes" : "count", MetricDirection.NEUTRAL))
			.toArray(BenchMetricDescriptor[]::new);

    @Override
    public void setup(BenchClientContext context) {
	    renderFailureBaseline = HordeRenderMetrics.failureSequence();
        originalPose = context.automation().pose();
	    poseSaved = true;
        origin = context.player().position();
        previousMaximum = HordeVisualConfig.MAX_VISIBLE.get();
	    configuredCount = previousMaximum;
	    count = configuredCount == -1 ? UNLIMITED_BENCH_COUNT : configuredCount;
	    if (count <= 0) {
		    throw new IllegalArgumentException("浪潮基准需要正的配置数量，-1按10000测量");
	    }
        previousRange = HordeVisualConfig.DISTANCE.get();
        previousEnabled = HordeVisualConfig.ENABLED.get();
	    configurationSaved = true;
	    HordeVisualConfig.MAX_VISIBLE.set(count);
        HordeVisualConfig.DISTANCE.set(-1);
        HordeVisualConfig.ENABLED.set(true);
        GLFW.glfwMaximizeWindow(context.minecraft().getWindow().handle());
	    HordeRenderMetrics.start(context.resultDirectory().resolve("artifacts/stages/horde-stages.jsonl"),
			    true, HordeRenderMetrics.GpuMode.FULL_STAGE_SUM);
	    ownsStageCapture = true;
    }

    @Override
    public BenchClientStepResult stabilize(BenchClientContext context) {
	    HordeRenderMetrics.phase(HordeRenderMetrics.Phase.STABILIZE);
	    requireGpuProgress();
        if (!initialized && !initialize(context)) {
            return BenchClientStepResult.CONTINUE;
        }
        advance();
        return ++phaseTicks >= LobecorpBenchConfig.STABILIZE_TICKS
                && context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)
                ? completePhase() : BenchClientStepResult.CONTINUE;
    }

	private boolean initialize(BenchClientContext context) {
		// 事件和相机必须以实际地面为锚点：玩家位置可能悬空，旧的 origin.y+64 会把相机抬到地面之上约192格，全部进入雾外。
		int baseX = (int) Math.floor(origin.x);
		int baseZ = (int) Math.floor(origin.z);
		if (!context.level().getChunkSource().hasChunk(baseX >> 4, baseZ >> 4)) {
			return false;
		}
		groundY = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX, baseZ);
		var center = new Vec3(origin.x, groundY, origin.z);
		var anchors = List.of(center.add(160, 0, 0), center.add(-160, 0, 0));
		var route = HordeRoute.create(HordeSamplingUtil.path(anchors));
		var rules = HordeRules.configured().withOverrides(6000, WIDTH);
		if (!route.prepareDeferred(rules, point -> {
			var block = BlockPos.containing(point);
			if (!context.level().getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) {
				temporaryHeightSamples++;
				// 未加载列回退到已确认的中心地面，不能回退到悬空的玩家Y。
				return new Vec3(point.x, groundY, point.z);
			}
			return new Vec3(point.x, context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					block.getX(), block.getZ()), point.z);
		})) {
			return false;
		}
		var nodes = new ListTag();
		for (int index = 0; index < route.size(); index++) {
			nodes.add(route.node(index).save());
		}
		header = new CompoundTag();
		header.putInt("format", 1);
		header.putString("dimension", context.level().dimension().identifier().toString());
		header.put("rules", rules.save());
		header.putLong("stop", 6000);
		header.putDouble("length", route.length());
		header.putDouble("speed", SPEED);
		header.putInt("node_count", route.size());
		var visual = new MidnightCleanup().visuals();
		header.putString("model", visual.model().toString());
		header.putString("animation", visual.animation().toString());
		header.putString("run", visual.runAnimation());
		header.putString("texture", visual.texture().toString());
		header.putDouble("light_probe_height", new MidnightCleanup().dimensions().eyeHeight());
		header.putDouble("entity_height", new MidnightCleanup().dimensions().height());
		baseTick = (long) Math.ceil(route.length() / SPEED) + 40;
		header.putLong("tick", baseTick);
		header.putBoolean("reset", true);
		ownsEvent = true;
		send("header", header);
		header.putBoolean("reset", false);
		var page = new CompoundTag();
		page.putInt("first", 0);
		page.put("nodes", nodes);
		send("route", page);
		var view = BenchClientPose.lookingAt(center.x, center.y + CAMERA_HEIGHT, center.z - CAMERA_DISTANCE,
				center.x, center.y + 1, center.z);
		pose = context.automation().holdPose(view);
		initialized = true;
		return true;
	}

    private void send(String kind, CompoundTag body) {
        var packet = new CompoundTag();
        packet.putString("id", EVENT_ID.toString());
        packet.putString("kind", kind);
        packet.putLong("sequence", ++sequence);
        packet.put("body", body.copy());
        HordeClient.apply(packet);
    }

    private void advance() {
        elapsed++;
        if (elapsed % 20 == 0) {
            header.putLong("tick", baseTick + elapsed);
            send("header", header);
        }
    }

    private BenchClientStepResult completePhase() {
        phaseTicks = 0;
        return BenchClientStepResult.COMPLETE;
    }

	@Override
	public BenchClientStepResult warmup(BenchClientContext context) {
		HordeRenderMetrics.phase(HordeRenderMetrics.Phase.WARMUP);
		requireGpuProgress();
		advance();
		if (++phaseTicks < LobecorpBenchConfig.WARMUP_TICKS) {
			return BenchClientStepResult.CONTINUE;
		}
		// 同机位先关闭浪潮拍对照图；只有差异像素证明浪潮真正进入画面，纯天空截图不能通过。
		if (!baselineCaptured) {
			HordeVisualConfig.ENABLED.set(false);
			baselineScreenshot = context.automation().captureScreenshot("horde-render-off");
			baselineCaptured = true;
			return BenchClientStepResult.CONTINUE;
		}
		if (!baselineScreenshot.isDone() || !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		baselineScreenshotPath = baselineScreenshot.join();
		if (!hordeResumed) {
			HordeVisualConfig.ENABLED.set(true);
			hordeResumed = true;
			return BenchClientStepResult.CONTINUE;
		}
		if (screenshot == null) {
			screenshot = context.automation().captureScreenshot("horde-render");
			return BenchClientStepResult.CONTINUE;
		}
		if (!screenshot.isDone() || !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
			return BenchClientStepResult.CONTINUE;
		}
		screenshotPath = screenshot.join();
		return completePhase();
	}

    @Override
    public BenchClientStepResult measure(BenchClientContext context) {
	    HordeRenderMetrics.phase(HordeRenderMetrics.Phase.MEASURE);
	    requireGpuProgress();
	    drainGpu(context);
	    if (phaseTicks > 0 && context.frames().sampleCount() > firstFrame) {
		    for (var stage : HordeRenderMetrics.Stage.values()) {
			    context.metrics().record(STAGE_CPU[stage.ordinal()], HordeRenderMetrics.cpu(stage));
		    }
		    for (var counter : HordeRenderMetrics.Counter.values()) {
			    long value = HordeRenderMetrics.value(counter);
			    if (value >= 0) {
				    context.metrics().record(COUNTERS[counter.ordinal()], value);
			    }
		    }
	    }
        advance();
        if (phaseTicks == 0) {
            firstFrame = context.frames().sampleCount();
	        if (PhotonFrameCapture.active()) {
		        throw new IllegalStateException("已有GPU捕获正在进行，不能替换其他捕获");
	        }
	        environment = new HordeBenchmarkEnvironment();
	        gpuCaptureToken = PhotonFrameCapture.start("horde_bench_gpu", 30, HordeRenderMetrics::worldGpuFrame);
            sparkLinksBefore = sparkLinks(context);
            profilerStarted = ClientCommandHandler.runCommand(SPARK_START);
            if (!profilerStarted) {
                throw new IllegalStateException("Spark client profiler command is unavailable");
            }
        }
        long count = HordeClient.batches().stream().mapToLong(HordeClient.Batch::count).sum();
        context.metrics().record(INSTANCES, count);
	    if (count != this.count || HordeClient.batches().stream().anyMatch(batch -> batch.event().failed)) {
            throw new AssertionError("GPU horde workload changed: " + count + ", " + HordeGpuRenderer.statistics());
        }
        return ++phaseTicks >= MEASURE_TICKS ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
    }

	@Override
	public void verify(BenchClientContext context) throws Exception {
		drainGpu(context);
		HordeRenderMetrics.phase(HordeRenderMetrics.Phase.VERIFY);
		requireGpuProgress();
		if (context.frames().sampleCount() <= firstFrame || screenshotPath == null || baselineScreenshotPath == null
				|| !Files.isRegularFile(screenshotPath) || !Files.isRegularFile(baselineScreenshotPath)
				|| !HordeGpuRenderer.statistics().contains("instances=" + count)) {
			throw new AssertionError("GPU horde did not produce the expected drawing and contrast screenshots");
		}
		verifyVisible(context);
		saveSpark(context);
		var capture = PhotonFrameCapture.snapshot();
		if (gpuCaptureToken == null || !capture.has("capture_id") || capture.get("capture_id").isJsonNull()
				|| !gpuCaptureToken.toString().equals(capture.get("capture_id").getAsString())) {
			throw new AssertionError("GPU捕获不属于当前基准，不能登记其他捕获的制品");
		}
		if (!capture.get("state").getAsString().equals("DURATION") || capture.get("saved_path").isJsonNull()) {
			throw new AssertionError("GPU timing capture did not finish and save");
		}
		Path gpu = context.resultDirectory().resolve("artifacts/gpu/horde-gpu.json");
		Files.createDirectories(gpu.getParent());
		Files.copy(Path.of(capture.get("saved_path").getAsString()), gpu, StandardCopyOption.REPLACE_EXISTING);
		context.artifacts().register(gpu, "application/json");
	}

	/// 同机位A/B证明浪潮像素真的进入画面；相机悬空、雾外、全黑或光照剔除都会在这里失败。
	private void verifyVisible(BenchClientContext context) throws IOException {
		var pose = context.automation().pose();
		int baseX = (int) Math.floor(origin.x);
		int baseZ = (int) Math.floor(origin.z);
		int currentGround = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX, baseZ);
		double eyeHeight = pose.y() - currentGround;
		if (Math.abs(eyeHeight - CAMERA_HEIGHT) > 8.0 || pose.pitch() < 20.0F || pose.pitch() > 45.0F
				|| Math.abs(pose.z() - (origin.z - CAMERA_DISTANCE)) > 8.0) {
			throw new AssertionError("浪潮基准相机未按地面锚定，不能判定负载可见：" + pose
					+ ", groundY=" + currentGround + ", expectedEyeHeight=" + CAMERA_HEIGHT);
		}
		try (var on = NativeImage.read(Files.newInputStream(screenshotPath));
		     var off = NativeImage.read(Files.newInputStream(baselineScreenshotPath))) {
			int width = on.getWidth();
			int height = on.getHeight();
			if (off.getWidth() != width || off.getHeight() != height) {
				throw new AssertionError("浪潮对照截图尺寸不一致");
			}
			int[] a = on.getPixels();
			int[] b = off.getPixels();
			int minX = width, minY = height, maxX = -1, maxY = -1;
			long diff = 0;
			// 只看地平线以下的3/4，排除云层移动；浪潮必须在这片区域产生成片差异像素。
			for (int y = height / 4; y < height; y++) {
				int row = y * width;
				for (int x = 0; x < width; x++) {
					int p = a[row + x];
					int q = b[row + x];
					int delta = Math.max(Math.abs((p >> 16 & 255) - (q >> 16 & 255)),
							Math.max(Math.abs((p >> 8 & 255) - (q >> 8 & 255)), Math.abs((p & 255) - (q & 255))));
					if (delta <= 16) {
						continue;
					}
					diff++;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
			visiblePixels = (int) diff;
			visibleBounds = maxX < 0 ? new int[0] : new int[]{minX, minY, maxX, maxY};
			long area = (long) width * height * 3 / 4;
			if (diff < Math.max(5000L, area / 200L) || maxX - minX < width / 5 || maxY - minY < height / 20) {
				throw new AssertionError("浪潮未在画面产生足够像素：diff=" + diff + ", bounds=" + Arrays.toString(visibleBounds)
						+ ", pose=" + pose + ", groundY=" + currentGround);
			}
		}
		context.metrics().record(VISIBLE_PIXELS, visiblePixels);
	}


	private void saveSpark(BenchClientContext context) throws Exception {
	    for (String link : sparkLinks(context)) {
		    if (sparkLinksBefore.contains(link)) {
			    continue;
		    }
		    Path file = context.resultDirectory().resolve("artifacts/spark/horde-profile.txt");
		    Files.createDirectories(file.getParent());
		    Files.writeString(file, link + System.lineSeparator());
		    context.artifacts().register(file, "text/plain");
		    return;
	    }
	    throw new AssertionError("Spark did not return a new profile link for this run");
    }

	private Set<String> sparkLinks(BenchClientContext context) {
		try {
			var field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			var links = new LinkedHashSet<String>();
			for (var entry : (List<?>) field.get(context.minecraft().gui.getChat())) {
				if (entry instanceof GuiMessage message) {
					var matcher = SPARK_LINK.matcher(message.content().getString());
					while (matcher.find()) {
						links.add(matcher.group());
					}
                }
            }
	        return links;
        } catch (ReflectiveOperationException exception) {
	        throw new IllegalStateException("Cannot identify Spark profile links for this run", exception);
        }
    }

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		Throwable failure = null;
		if (ownsStageCapture) {
			failure = cleanup(failure, () -> HordeRenderMetrics.phase(HordeRenderMetrics.Phase.TEARDOWN));
		}
		failure = cleanup(failure, () -> saveEnvironment(context));
		failure = cleanup(failure, () -> saveStages(context));
		if (gpuCaptureToken != null) {
			failure = cleanup(failure, () -> {
				PhotonFrameCapture.cancel(gpuCaptureToken);
				gpuCaptureToken = null;
			});
		}
		if (ownsEvent) {
			failure = cleanup(failure, () -> {
				send("remove", new CompoundTag());
				ownsEvent = false;
			});
		}
		if (profilerStarted) {
			failure = cleanup(failure, () -> {
				ClientCommandHandler.runCommand("sparkc profiler stop");
				profilerStarted = false;
			});
		}
		if (screenshot != null && !screenshot.isDone()) {
			failure = cleanup(failure, () -> screenshot.cancel(false));
		}
		if (pose != null) {
			failure = cleanup(failure, () -> {
				pose.close();
				pose = null;
			});
		}
		if (poseSaved) {
			failure = cleanup(failure, () -> context.automation().setPose(originalPose));
		}
		if (configurationSaved) {
			failure = cleanup(failure, () -> HordeVisualConfig.MAX_VISIBLE.set(previousMaximum));
			failure = cleanup(failure, () -> HordeVisualConfig.DISTANCE.set(previousRange));
			failure = cleanup(failure, () -> HordeVisualConfig.ENABLED.set(previousEnabled));
		}
		if (failure instanceof Exception exception) {
			throw exception;
		}
		if (failure instanceof Error error) {
			throw error;
		}
		if (failure != null) {
			throw new IllegalStateException("浪潮基准清理失败", failure);
		}
	}

	/// 每项清理独立执行，后续失败作为suppressed保留，避免阻断其他对象的恢复。
	private static @Nullable Throwable cleanup(@Nullable Throwable previous, Runnable action) {
		try {
			action.run();
		} catch (Throwable failure) {
			if (previous == null) {
				return failure;
			}
			if (previous != failure) {
				previous.addSuppressed(failure);
			}
		}
		return previous;
	}

	private void drainGpu(BenchClientContext context) {
		HordeRenderMetrics.drainGpuSums((stage, nanos) -> context.metrics().record(STAGE_GPU[stage.ordinal()], nanos));
		HordeRenderMetrics.drainDrawTotals(nanos -> context.metrics().record(DRAW_GPU, nanos));
	}

	private void saveEnvironment(BenchClientContext context) {
		if (environment == null) {
			return;
		}
		environment.close();
		var value = environment.snapshot();
		value.add("render_resources", HordeGpuRenderer.resourceStatistics());
		value.addProperty("camera_pose", context.automation().pose().toString());
		value.addProperty("camera_ground_y", groundY);
		value.addProperty("camera_eye_height_above_ground", context.automation().pose().y() - groundY);
		value.addProperty("event_anchor", origin.toString());
		value.addProperty("visible_pixels", visiblePixels);
		value.addProperty("visible_pixel_bounds", Arrays.toString(visibleBounds));
		value.addProperty("contrast_pair", "horde-render.png - horde-render-off.png (lower 3/4, channel delta > 16)");
		value.addProperty("fixture_width", WIDTH);
		value.addProperty("temporary_unloaded_height_samples", temporaryHeightSamples);
		value.addProperty("fixture_instances", count);
		value.addProperty("configured_instances", configuredCount);
		value.addProperty("unlimited_benchmark_fallback", configuredCount == -1);
		value.addProperty("route", "(160,y,0) -> (-160,y,0), relative to benchmark origin");
		value.addProperty("stage_capture_mode", "FULL_STAGE_SUM");
		value.addProperty("gpu_window", "World: 30 seconds; stage sum and Horde draw total: whole capture; three non-nested cohorts share render_frame_id");
		value.addProperty("metric_sample_scope", "CPU and counters: latest completed Horde draw per client tick after MEASURE begins; GPU: every fully covered MEASURE stage row");
		value.addProperty("event_header", header == null ? null : header.toString());
		var output = context.resultDirectory().resolve("artifacts/stages/horde-environment.json");
		try {
			Files.createDirectories(output.getParent());
			Files.writeString(output, new Gson().toJson(value));
			context.artifacts().register(output, "application/json");
			if (!value.get("cleanup_complete").getAsBoolean()) {
				throw new IllegalStateException("基准环境探针清理未完成，详见环境制品中的具体归属记录");
			}
		} catch (IOException failure) {
			throw new IllegalStateException("无法保存浪潮基准环境", failure);
		} finally {
			environment = null;
		}
	}

	private void requireGpuProgress() {
		if (HordeRenderMetrics.failureSequence() != renderFailureBaseline) {
			throw new AssertionError("群体GPU绘制曾失败，不能仅以实例计数判定通过：" + HordeGpuRenderer.statistics());
		}
	}

	private void saveStages(BenchClientContext context) {
		if (!ownsStageCapture) {
			return;
		}
		ownsStageCapture = false;
		try {
			var path = HordeRenderMetrics.finish();
			drainGpu(context);
			context.artifacts().register(path, "application/x-ndjson");
		} catch (IOException exception) {
			throw new IllegalStateException("群体阶段统计保存失败", exception);
		}
    }
}