package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.NativeImage;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.neoforge.client.*;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.util.Unit;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeGpuRenderer;
import org.unitego.lobecorp.client.horde.HordeRenderMetrics;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeEvents;
import org.unitego.lobecorp.world.horde.HordeRoute;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.HordeStartRequest;
import org.unitego.lobecorp.world.horde.HordeStopMode;
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
	private static final double COMBAT_HEALTH = 100000.0;
	private static final double WIDTH = 512;
    private final double speed = LobecorpBenchConfig.hordeSpeed();
    private static final BenchMetricDescriptor INSTANCES = new BenchMetricDescriptor(
            "lobecorp.horde_render.instances", "count", MetricDirection.NEUTRAL);
    private BenchClientPose originalPose;
    private BenchPoseHold pose;
    private BenchClientPose naturalPose;
    private CompletableFuture<Path> screenshot;
    private Path screenshotPath;
    private Vec3 origin;
    private CompoundTag header;
    private long sequence;
    private long baseTick;
    private long firstFrame;
    private int elapsed;
    private int phaseTicks;
    private int poseLockTicks;
    private String originalGameMode;
	// 监守者轮使用真实 HordeEvent：事件本身生成真实清道夫参与者，与监守者真实战斗。
	private final boolean realEvent = LobecorpBenchConfig.hordeWardens() > 0;
	@Nullable private UUID realEventId;
	private boolean realEventFailed;
	private String realEventFailure = "";
	private int realEventWaitTicks;
    private int previousMaximum;
    private int previousRange;
	private int temporaryHeightSamples;
    private boolean initialized;
    private boolean previousEnabled;
	private boolean ownsStageCapture;
	private boolean ownsEvent;
	private boolean poseSaved;
	private boolean configurationSaved;
	private UUID gpuCaptureToken;
	private HordeBenchmarkEnvironment environment;
	private long renderFailureBaseline;
	private static final BenchMetricDescriptor VISIBLE_PIXELS = new BenchMetricDescriptor(
			"lobecorp.horde_render.visible_pixels", "count", MetricDirection.NEUTRAL);
	private static final BenchMetricDescriptor WARDEN_COUNT = new BenchMetricDescriptor(
			"lobecorp.horde.wardens", "count", MetricDirection.NEUTRAL);
	// 与原夹具一致的侧上方视角；锚点必须是实际地面，不能沿用可能悬空的玩家Y。
	private static final int CAMERA_HEIGHT = 64;
	private static final int CAMERA_DISTANCE = 100;
	private double groundY;
	private CompletableFuture<Path> baselineScreenshot;
	private Path baselineScreenshotPath;
	private boolean baselineCaptured;
	private boolean hordeResumed;
	private final int wardens = LobecorpBenchConfig.hordeWardens();
	private int wardenRepicks;
	private BenchServerWorkload wardenWorkload;
	private CompletableFuture<Path> passAScreenshot;
	private CompletableFuture<Path> passBScreenshot;
	private Path passAPath;
	private Path passBPath;
	private int sparkWaitTicks;
	private boolean measureComplete;
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
	    // -1 表示真正的无限：不改写可见上限，实例数在首帧测量时按实际批次统计。
	    count = configuredCount;
	    if (count == 0) {
		    throw new IllegalArgumentException("浪潮基准数量不能为0；-1表示无限");
	    }
        previousRange = HordeVisualConfig.DISTANCE.get();
        previousEnabled = HordeVisualConfig.ENABLED.get();
	    configurationSaved = true;
	    if (count > 0) {
		    HordeVisualConfig.MAX_VISIBLE.set(count);
	    }
        HordeVisualConfig.DISTANCE.set(-1);
        HordeVisualConfig.ENABLED.set(true);
		var window = context.minecraft().getWindow().handle();
		BenchWindow.reset();
		BenchWindow.ensure(window);
		BenchSpark.start(context);
		setNight(context);
	    HordeRenderMetrics.start(context.resultDirectory().resolve("artifacts/stages/horde-stages.jsonl"),
			    true, HordeRenderMetrics.GpuMode.FULL_STAGE_SUM);
	    ownsStageCapture = true;
    }

    @Override
    public BenchClientStepResult stabilize(BenchClientContext context) {
	    HordeRenderMetrics.phase(HordeRenderMetrics.Phase.STABILIZE);
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		keepPose(context);
	    requireGpuProgress();
        if (!initialized && !initialize(context)) {
            return BenchClientStepResult.CONTINUE;
        }
        if (realEvent) {
            if (realEventFailed) {
                throw new AssertionError("真实浪潮事件启动失败：" + realEventFailure);
            }
            long syncedInstances = HordeClient.batches().stream().mapToLong(HordeClient.Batch::count).sum();
            if (realEventId == null || syncedInstances < 1000) {
                if (++realEventWaitTicks > 600) {
                    throw new AssertionError("真实浪潮事件 600 tick 内未同步出足够虚影实例：event=" + realEventId
                            + ", instances=" + syncedInstances);
                }
                return BenchClientStepResult.CONTINUE;
            }
        }
        if (wardenWorkload != null) {
	        wardenWorkload.audit(context);
	        if (!wardenWorkload.await(context, expectedWorkload(), LobecorpBenchConfig.entitySyncTimeoutTicks(), "浪潮监守者+真实清道夫")) {
		        return BenchClientStepResult.CONTINUE;
	        }
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
		int length = LobecorpBenchConfig.hordeLength();
		var anchors = List.of(center.add(length / 2.0, 0, 0), center.add(-length / 2.0, 0, 0));
		var route = HordeRoute.create(HordeSamplingUtil.path(anchors));
		var rules = HordeRules.configured().withOverrides(6000, LobecorpBenchConfig.hordeWidth());
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
		if (realEvent) {
			// 用户澄清：清道夫由事件本身生成。监守者轮改为启动真实 HordeEvent（事件自己刷真实清道夫参与者），
			// 不再发送伪造客户端快照；客户端通过正常 HordeSync 接收路线/快照并渲染。
			var start = route.point(0, 0);
			var end = route.point(route.length(), 0);
			var server = context.minecraft().getSingleplayerServer();
			if (server == null) {
				throw new IllegalStateException("真实浪潮事件需要集成服务端");
			}
			server.execute(() -> {
				var result = HordeEvents.start(server.overworld(), MidnightCleanup.ID,
						new HordeStartRequest(start, end, List.of(), 6000, LobecorpBenchConfig.hordeWidth()));
				if (result.started()) {
					realEventId = result.eventId();
				} else {
					realEventFailure = result.message();
					realEventFailed = true;
				}
			});
		} else {
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
			header.putDouble("speed", speed);
			header.putInt("node_count", route.size());
			var visual = new MidnightCleanup().visuals();
			header.putString("model", visual.model().toString());
			header.putString("animation", visual.animation().toString());
			header.putString("run", visual.runAnimation());
			header.putString("texture", visual.texture().toString());
			header.putDouble("light_probe_height", new MidnightCleanup().dimensions().eyeHeight());
			header.putDouble("entity_height", new MidnightCleanup().dimensions().height());
			baseTick = (long) Math.ceil(route.length() / speed) + 40;
			header.putLong("tick", baseTick);
			header.putBoolean("reset", true);
			ownsEvent = true;
			send("header", header);
			header.putBoolean("reset", false);
			var page = new CompoundTag();
			page.putInt("first", 0);
			page.put("nodes", nodes);
			send("route", page);
		}
		int station = LobecorpBenchConfig.hordePlayerStation();
		BenchClientPose view;
		if (station >= 0) {
			// 玩家站在路线指定位置的地面上，视角保持人眼高度，不飞到高空。
			// 玩家站在路线中心附近，浪潮从两侧绕过去；地面按中心列采样，避免边缘未加载区块把高度取到minY。
			var centerPoint = route.point(Math.min(station, route.length()), 0);
			var point = route.point(Math.min(station, route.length()), 2.0);
			double ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(int) Math.floor(centerPoint.x), (int) Math.floor(centerPoint.z));
			double eye = ground + 1.62;
			var direction = route.direction(Math.min(station, route.length()));
			// 玩家面向事件的反方向，浪潮从背后推进。
			view = BenchClientPose.lookingAt(point.x, eye, point.z,
					point.x - direction.x * 20, eye, point.z - direction.z * 20);
		} else {
			view = BenchClientPose.lookingAt(center.x, center.y + CAMERA_HEIGHT, center.z - CAMERA_DISTANCE,
					center.x, center.y + 1, center.z);
		}
		pose = context.automation().holdPose(view);
		if (station >= 0) {
			naturalPose = view;
			context.minecraft().player.setNoGravity(true);
			if (wardens > 0) {
				// 用户要求：玩家旁观者模式，真实实体互相打（清道夫×10 对监守者×10）。
				originalGameMode = context.minecraft().player.isCreative() ? "creative"
						: context.minecraft().player.isSpectator() ? "spectator" : "survival";
				switchPlayerGameMode(context, "spectator");
			} else {
				context.minecraft().player.setInvulnerable(true);
			}
		}
		if (wardens > 0) {
			spawnWardens(context, route, station);
		}
		initialized = true;
		return true;
	}

	/// 在玩家站位附近、路线两侧随机位置服务端生成真实监守者；它们会进入客户端区域分割的 target 集合。
	private void spawnWardens(BenchClientContext context, HordeRoute route, int station) {
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("浪潮监守者需要集成服务端");
		}
		// 监守者是 Monster：和平难度下即使 persistenceRequired 也会被 checkDespawn 移除（只在 entity-ticking 区块发生，
		// 所以表现为“个别监守者每次补生成成功后下一 tick 又消失”）。先切普通难度，队列在生成任务之前。
		var difficulty = wardens > 0 ? "normal" : null;
		if (difficulty != null) {
			server.execute(() -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack(), "difficulty " + difficulty));
		}
		var random = new Random(context.seed() * 31L + 0x5EED);
		double center = station >= 0 ? Math.min(station, route.length()) : route.length() / 2;
		double span = Math.min(80, route.length() / 2);
		var specs = new ArrayList<BenchServerWorkload.Spec>(wardens);
		for (int index = 0; index < wardens; index++) {
			float yaw = (float) (random.nextDouble() * 360.0);
			int x = 0;
			int z = 0;
			boolean usable = false;
			// 确定性伪随机重抽：跳过未加载/越界/无有效地面的位置，但不降低 expected。
			for (int attempt = 0; attempt < 20; attempt++) {
				double along = Math.clamp(center + (random.nextDouble() * 2 - 1) * span, 0, route.length());
				double lateral = (random.nextDouble() * 2 - 1) * 60;
				var point = route.point(along, lateral);
				x = (int) Math.floor(point.x);
				z = (int) Math.floor(point.z);
				if (wardenPositionUsable(context, x, z)) {
					usable = true;
					break;
				}
				wardenRepicks++;
			}
			if (!usable) {
				wardenRepicks++;
			}
			var uuid = UUID.nameUUIDFromBytes(("lobecorp-bench:horde-warden:" + context.seed() + ":" + index)
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			int anchorX = x;
			int anchorZ = z;
			specs.add(new BenchServerWorkload.Spec(uuid, anchorX, anchorZ, 32, level -> {
				var warden = new Warden(EntityType.WARDEN, level);
				// 真实战斗模拟：保留监守者全部 AI（不再 setNoAi），用锚点 leash 限制其漫游范围。
				warden.setPersistenceRequired();
				var health = warden.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.setBaseValue(COMBAT_HEALTH);
				}
				warden.setHealth((float) COMBAT_HEALTH);
				warden.setYRot(yaw);
				warden.setYHeadRot(yaw);
				// 程序生成不经过 TRIGGERED 的 finalizeSpawn；显式清掉 emerging/digging 状态，避免一直播放脱离/挖掘动画。
				warden.setPose(Pose.STANDING);
				warden.getBrain().eraseMemory(MemoryModuleType.IS_EMERGING);
				warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 24000L);
				return warden;
			}));
		}
		wardenWorkload = new BenchServerWorkload();
		wardenWorkload.spawn(context, specs);
	}

	/// 监守者场景的实体总数：10 个真实监守者；真实清道夫由真实 HordeEvent 自身生成。
	private int expectedWorkload() {
		return wardens;
	}

	/// 客户端侧可生成性预检：世界边界内、区块已加载、地面高度有效。
	private boolean wardenPositionUsable(BenchClientContext context, int x, int z) {
		if (x < -29_999_000 || z < -29_999_000 || x > 29_999_000 || z > 29_999_000) {
			return false;
		}
		if (!context.level().getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return false;
		}
		int height = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return height > context.level().getMinY() + 1 && height < context.level().getMaxY() - 2;
	}

	/// 服务端执行命令固定时间：客户端时间由服务端同步，避免直接改 Level 时间不同步。
    private void setNight(BenchClientContext context) {
        var server = context.minecraft().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source, "gamerule doDaylightCycle false");
                server.getCommands().performPrefixedCommand(source, "time set midnight");
            });
        }
    }

    /// 每tick重新压住玩家位置/视角，防止重力或碰撞让高度在-60/-62之间抖动。
    private void keepPose(BenchClientContext context) {
        if (naturalPose != null) {
            context.automation().holdPose(naturalPose);
            // AI 实体的推挤/攻击可能让客户端固定姿态漂移：定期在服务端把玩家拉回锚点并清零速度。
            if (++poseLockTicks % 5 == 0) {
                lockPlayerOnServer(context);
            }
        }
    }

    private void lockPlayerOnServer(BenchClientContext context) {
        var server = context.minecraft().getSingleplayerServer();
        if (server == null || naturalPose == null) {
            return;
        }
        var player = context.minecraft().player;
        var uuid = player.getUUID();
        double eyeX = naturalPose.x();
        double eyeY = naturalPose.y();
        double eyeZ = naturalPose.z();
        server.execute(() -> {
            var serverPlayer = server.overworld().getPlayerByUUID(uuid);
            if (serverPlayer == null) {
                return;
            }
            serverPlayer.setPos(eyeX, eyeY - serverPlayer.getEyeHeight(), eyeZ);
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            serverPlayer.setNoGravity(true);
        });
    }

    private void switchPlayerGameMode(BenchClientContext context, String mode) {
        var server = context.minecraft().getSingleplayerServer();
        if (server == null) {
            return;
        }
        var uuid = context.minecraft().player.getUUID();
        server.execute(() -> {
            var serverPlayer = server.overworld().getPlayerByUUID(uuid);
            if (serverPlayer == null) {
                return;
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "gamemode " + mode + " " + serverPlayer.getName().getString());
        });
    }

    private void send(String kind, CompoundTag body) {        var packet = new CompoundTag();
        packet.putString("id", EVENT_ID.toString());
        packet.putString("kind", kind);
        packet.putLong("sequence", ++sequence);
        packet.put("body", body.copy());
        HordeClient.apply(packet);
    }

    private void advance() {
        if (realEvent) {
            return;
        }
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
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		keepPose(context);
		if (wardenWorkload != null) {
			wardenWorkload.audit(context);
		}
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
		BenchWindow.ensure(context.minecraft().getWindow().handle());
		keepPose(context);
		if (wardenWorkload != null) {
			wardenWorkload.audit(context);
		}
	    BenchSpark.capture(context, "horde-profile.txt");
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
            setNight(context);
			// 只统计MEASURE阶段的失焦/恢复，预热阶段的偶发切换不计入验收窗口。
			BenchWindow.reset();
	        if (PhotonFrameCapture.active()) {
		        throw new IllegalStateException("已有GPU捕获正在进行，不能替换其他捕获");
	        }
	        environment = new HordeBenchmarkEnvironment();
	        gpuCaptureToken = PhotonFrameCapture.start("horde_bench_gpu", 30, HordeRenderMetrics::worldGpuFrame);
        }
		if (wardenWorkload != null) {
			context.metrics().record(WARDEN_COUNT, wardenWorkload.synced(context));
		}
        long count = HordeClient.batches().stream().mapToLong(HordeClient.Batch::count).sum();
        // -1 无限模式下事件仍在持续生成，实例数会增长；记录最新值而不是断言工作负载恒定。
        if (configuredCount < 0) {
            this.count = (int) Math.min(Integer.MAX_VALUE, count);
        } else if (count != this.count) {
            throw new AssertionError("GPU horde workload changed: " + count + ", " + HordeGpuRenderer.statistics());
        }
        context.metrics().record(INSTANCES, count);
	    if (HordeClient.batches().stream().anyMatch(batch -> batch.event().failed)) {
            throw new AssertionError("GPU horde workload changed: " + count + ", " + HordeGpuRenderer.statistics());
        }
        int total = LobecorpBenchConfig.hordeMeasureTicks();
        // 避让视觉取证：早期/后期各截一张，沿用同一第一人称位姿，供矩阵轮次对比“两侧绕过、不停顿”。
        if (passAScreenshot == null && phaseTicks == Math.max(1, total / 3)) {
            passAScreenshot = context.automation().captureScreenshot("horde-render-pass-a");
        }
        if (passBScreenshot == null && phaseTicks == Math.max(2, total * 2 / 3)) {
            passBScreenshot = context.automation().captureScreenshot("horde-render-pass-b");
        }
        if (!measureComplete) {
            phaseTicks++;
            if (phaseTicks >= total) {
                measureComplete = true;
                // 整个浪潮场景结束后才关闭 Spark，让采样覆盖完整测量而不是只采样 30 秒。
                BenchSpark.stop();
            }
        }
        if (measureComplete) {
            if (!BenchSpark.saved()) {
                if (++sparkWaitTicks > LobecorpBenchConfig.sparkSaveTimeoutTicks()) {
                    throw new AssertionError("Spark 未在停止后返回上传链接，不能结束客户端");
                }
                return BenchClientStepResult.CONTINUE;
            }
            return BenchClientStepResult.COMPLETE;
        }
        return BenchClientStepResult.CONTINUE;
    }

	@Override
	public void verify(BenchClientContext context) throws Exception {
		drainGpu(context);
		HordeRenderMetrics.phase(HordeRenderMetrics.Phase.VERIFY);
		requireGpuProgress();
		if (!measureComplete || context.frames().sampleCount() <= firstFrame || screenshotPath == null
				|| baselineScreenshotPath == null
				|| !Files.isRegularFile(screenshotPath) || !Files.isRegularFile(baselineScreenshotPath)
				|| !realEvent && !HordeGpuRenderer.statistics().contains("instances=" + count)) {
			throw new AssertionError("GPU horde did not produce the expected drawing and contrast screenshots");
		}
		if (passAScreenshot == null || !passAScreenshot.isDone() || passBScreenshot == null || !passBScreenshot.isDone()) {
			throw new AssertionError("浪潮中途避让截图（pass-a/pass-b）未完成");
		}
		passAPath = passAScreenshot.join();
		passBPath = passBScreenshot.join();
		if (!Files.isRegularFile(passAPath) || !Files.isRegularFile(passBPath)) {
			throw new AssertionError("浪潮中途避让截图文件缺失");
		}
		if (wardenWorkload != null && (wardenWorkload.synced(context) != expectedWorkload()
				|| wardenWorkload.serverPresent() != expectedWorkload())) {
			throw new AssertionError("浪潮监守者/真实清道夫数量在测量后改变：expected=" + expectedWorkload()
					+ ", serverPresent=" + wardenWorkload.serverPresent()
					+ ", clientSynced=" + wardenWorkload.synced(context) + ", "
					+ wardenWorkload.report(context).issues());
		}
		verifyVisible(context);
		// 焦点不再作为验收门禁：后台运行或编辑器抢占焦点都会让失焦成为常态，
		// 只把次数写进报告快照（focus_losses/focus_restores）供分析，不判定整轮失败。
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
		BenchSpark.requireSaved();
		context.artifacts().register(screenshotPath, "image/png");
		context.artifacts().register(baselineScreenshotPath, "image/png");
		context.artifacts().register(passAPath, "image/png");
		context.artifacts().register(passBPath, "image/png");
	}

	/// 同机位A/B证明浪潮像素真的进入画面；相机悬空、雾外、全黑或光照剔除都会在这里失败。
	private void verifyVisible(BenchClientContext context) throws IOException {
		var pose = context.automation().pose();
		int poseX = (int) Math.floor(pose.x());
		int poseZ = (int) Math.floor(pose.z());
		int currentGround = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, poseX, poseZ);
		double eyeHeight = pose.y() - currentGround;
		boolean natural = LobecorpBenchConfig.hordePlayerStation() >= 0;
		double expectedEye = natural ? 1.62 : CAMERA_HEIGHT;
		double tolerance = natural ? 0.9 : 8.0;
		boolean pitchValid = natural ? Math.abs(pose.pitch()) <= 12.0F : pose.pitch() >= 20.0F && pose.pitch() <= 45.0F;
		if (Math.abs(eyeHeight - expectedEye) > tolerance || !pitchValid
				|| !natural && Math.abs(pose.z() - (origin.z - CAMERA_DISTANCE)) > 8.0) {
			throw new AssertionError("浪潮基准相机未按地面锚定，不能判定负载可见：" + pose
					+ ", groundY=" + currentGround + ", expectedEyeHeight=" + expectedEye);
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


	@Override
	public void teardown(BenchClientContext context) throws Exception {
		Throwable failure = null;
		if (ownsStageCapture) {
			failure = cleanup(failure, () -> HordeRenderMetrics.phase(HordeRenderMetrics.Phase.TEARDOWN));
		}
		failure = cleanup(failure, () -> saveEnvironment(context));
		failure = cleanup(failure, () -> saveStages(context));
		failure = cleanup(failure, BenchSpark::stop);
		failure = cleanup(failure, BenchWindow::restore);
		if (wardenWorkload != null) {
			failure = cleanup(failure, () -> wardenWorkload.discard(context));
		}
		if (realEventId != null) {
			var eventId = realEventId;
			realEventId = null;
			failure = cleanup(failure, () -> {
				var server = context.minecraft().getSingleplayerServer();
				if (server != null) {
					server.execute(() -> HordeEvents.stop(server, eventId, HordeStopMode.CLEAR));
				}
			});
		}
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
		if (screenshot != null && !screenshot.isDone()) {
			failure = cleanup(failure, () -> screenshot.cancel(false));
		}
		if (passAScreenshot != null && !passAScreenshot.isDone()) {
			failure = cleanup(failure, () -> passAScreenshot.cancel(false));
		}
		if (passBScreenshot != null && !passBScreenshot.isDone()) {
			failure = cleanup(failure, () -> passBScreenshot.cancel(false));
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
		if (originalGameMode != null) {
			String restore = originalGameMode;
			originalGameMode = null;
			failure = cleanup(failure, () -> switchPlayerGameMode(context, restore));
		}
		if (naturalPose != null) {
			failure = cleanup(failure, () -> {
				context.minecraft().player.setNoGravity(false);
				context.minecraft().player.setInvulnerable(false);
				naturalPose = null;
			});
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
		var window = context.minecraft().getWindow().handle();
		value.addProperty("window_focused_at_end", BenchWindow.focused(window));
		value.addProperty("window_iconified_at_end", BenchWindow.iconified(window));
		value.addProperty("focus_losses", BenchWindow.losses());
		value.addProperty("focus_restores", BenchWindow.restores());
		value.addProperty("event_anchor", origin.toString());
		value.addProperty("visible_pixels", visiblePixels);
		value.addProperty("visible_pixel_bounds", Arrays.toString(visibleBounds));
		value.addProperty("contrast_pair", "horde-render.png - horde-render-off.png (lower 3/4, channel delta > 16)");
		value.addProperty("fixture_width", WIDTH);
		value.addProperty("temporary_unloaded_height_samples", temporaryHeightSamples);
		value.addProperty("fixture_instances", count);
		value.addProperty("configured_instances", configuredCount);
		value.addProperty("horde_wardens", wardens);
		value.addProperty("horde_wardens_synced", wardenWorkload == null ? 0 : wardenWorkload.synced(context));
		if (wardenWorkload != null) {
			var wardenReport = wardenWorkload.report(context);
			value.addProperty("warden_server_present", wardenReport.serverPresent());
			value.addProperty("warden_respawn_count", wardenReport.respawns());
			value.addProperty("warden_spawn_failure_count", wardenReport.spawnFailures());
			value.addProperty("warden_server_difficulty", wardenReport.difficulty());
			value.addProperty("warden_previous_difficulty", wardenReport.previousDifficulty());
			value.addProperty("warden_repicks", wardenRepicks);
			value.addProperty("warden_difficulty_command", wardens > 0 ? "normal" : "none");
			value.addProperty("warden_client_missing_recreate_count", wardenReport.recreated());
			value.addProperty("gamerule_spawn_mobs_disabled", wardenReport.gameruleDisabled());
			value.addProperty("gamerule_previous_spawn_mobs", wardenReport.previousSpawnMobs());
			value.addProperty("gamerule_previous_spawn_monsters", wardenReport.previousSpawnMonsters());
			value.add("warden_workload_issues", new Gson().toJsonTree(wardenReport.issues()));
		}
		value.addProperty("horde_wardens_role",
				"Real integrated-server wardens with full AI retained + 10 real AI sweepers spawned as opponents; player is spectator, both sides fight each other (real-combat simulation), anchors leashed to 32");
		value.addProperty("horde_wardens_ai", wardens > 0 ? "all_ai_retained" : "none");
		value.addProperty("horde_wardens_leash_radius", 32);
		value.addProperty("horde_combat_sweepers", 0);
		value.addProperty("horde_combat_health", COMBAT_HEALTH);
		value.addProperty("horde_real_event", realEvent);
		value.addProperty("horde_real_event_id", realEventId == null ? null : realEventId.toString());
		value.addProperty("horde_player_mode", wardens > 0 ? "spectator" : "fixed");
		value.addProperty("horde_workload_expected", expectedWorkload());
		value.addProperty("measure_complete", measureComplete);
		value.addProperty("spark_artifact", "artifacts/spark/horde-profile.txt");
		value.addProperty("avoidance_pass_a", passAPath == null ? null : passAPath.toString());
		value.addProperty("avoidance_pass_b", passBPath == null ? null : passBPath.toString());
		if (wardenWorkload != null) {
			var wardenList = new JsonArray();
			for (var uuid : wardenWorkload.uuids()) {
				var warden = new com.google.gson.JsonObject();
				warden.addProperty("uuid", uuid.toString());
				var entity = context.level().getEntity(uuid);
				warden.addProperty("client_present", entity != null);
				if (entity != null) {
					warden.addProperty("type", entity.getType().toString());
					warden.addProperty("x", entity.getX());
					warden.addProperty("y", entity.getY());
					warden.addProperty("z", entity.getZ());
				}
				wardenList.add(warden);
			}
			value.add("wardens", wardenList);
		}
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