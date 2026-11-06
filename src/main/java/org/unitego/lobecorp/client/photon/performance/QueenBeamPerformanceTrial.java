package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.Gson;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/// 仅显式开发启动使用的真实技能采样流程；不反射技能私有数据、不替换时序或伤害。
public class QueenBeamPerformanceTrial {
	public static final String REQUEST_ENV = "LOBECORP_LASER_PROFILE";
	public static final String CAMERA_SCALE_ENV = "LOBECORP_LASER_PROFILE_CAMERA_SCALE";
	public static final String VERIFY_COPY_ENV = "LOBECORP_LASER_PROFILE_VERIFY_COPY";
	public static final String VALIDATION_ONLY_ENV = "LOBECORP_LASER_PROFILE_VALIDATE_ONLY";
	public static final String SPARK_FIRST_ENV = "LOBECORP_LASER_PROFILE_SPARK_FIRST";
	public static final String SINGLE_CAPTURE_ENV = "LOBECORP_LASER_PROFILE_SINGLE_CAPTURE";
	private static final double CAPTURE_SECONDS = 90;
	private static final double SETTLE_SECONDS = 20;
	private static final double LOAD_TIMEOUT_SECONDS = 120;
	// 三次施法在 90 秒采样内的开始时刻（秒）。
	private static final double[] CAST_SECONDS = {4, 31, 58};
	private static final double TARGET_DISTANCE = QueenBeamPerformanceFixture.TARGET_DISTANCE;
	private static final double TARGET_HEALTH = QueenBeamPerformanceFixture.TARGET_HEALTH;
	private static final double CAMERA_X = -7;
	private static final double CAMERA_Z = -8;
	private static final double CAMERA_HEIGHT = 2;
	private static final float CAMERA_YAW = -25;
	private static final float CAMERA_PITCH = 8;
	private static final String WORLD_PREFIX = "laser_profile_";
	private static final String WORLD_SEED = "20261018";
	private static final String PHASE_PARAMETER = "v.beam_phase";
	private static final String SPARK_START = "sparkc profiler start --timeout 90 --save-to-file --thread * --not-combined";
	private static final String SPARK_STOP = "sparkc profiler stop --save-to-file";
	private static final String LOG = "[laser-profile] {}";
	private static final Gson GSON = new Gson();
	private static final ConcurrentLinkedQueue<Mark> MARKS = new ConcurrentLinkedQueue<>();
	private static final Map<UUID, PoseSamples> POSE_SAMPLES = new LinkedHashMap<>();
	private static boolean checked;
	private static State state = State.IDLE;
	private static String label = "";
	private static double cameraScale = 1, originalEntityDistance;
	private static long stateNs;
	private static boolean spark;
	private static boolean sparkRunning;
	private static int capturesCompleted;
	private static boolean verifying;
	private static long captureQueuedNs;
	private static @Nullable UUID lastAnimation;
	private static double lastPhase = Double.NaN;
	private static volatile @Nullable QueenBeamPerformanceFixture fixture;
	private static volatile int queenId = -1;
	private static volatile boolean fixtureReady;
	private static volatile @Nullable String failure;
	private static boolean optionsSaved;
	private static boolean originalGui;

	private enum State {
		IDLE, TITLE, CREATE, LOAD, FIXTURE, SETTLE, CAPTURE, VERIFY, COMPLETE, FAILED
	}

	private record Mark(String event, long nanoTime, String detail) {
	}

	/// 对真实定义比较标准复制和作用域复制的再序列化结果；只在显式开发采样加载阶段执行。
	public static boolean verifyRuntimeCopy(FX fx) {
		if (!SharedConstants.IS_RUNNING_IN_IDE || System.getenv(REQUEST_ENV) == null
				|| !Boolean.parseBoolean(System.getenv(VERIFY_COPY_ENV))) {
			return false;
		}
		var regular = fx.getFxData().copy(true);
		var scoped = fx.getFxData().copy(true);
		var registry = Platform.getFrozenRegistry();
		if (!regular.serializeNBT(registry).equals(scoped.serializeNBT(registry))) {
			failure = "runtime_copy_changed_" + fx.getFxLocation();
			throw new IllegalStateException(failure);
		}
		return true;
	}

	public static void tick() {
		if (!checked) {
			checked = true;
			String request = System.getenv(REQUEST_ENV);
			if (!SharedConstants.IS_RUNNING_IN_IDE || request == null || request.isBlank()) {
				return;
			}
			if (!request.matches("[a-z0-9_]+")) {
				fail("invalid_profile_label");
				return;
			}
			label = request;
			try {
				cameraScale = Double.parseDouble(System.getenv().getOrDefault(CAMERA_SCALE_ENV, "1"));
				if (!Double.isFinite(cameraScale) || cameraScale < 1 || cameraScale > 16) {
					fail("invalid_camera_scale");
					return;
				}
			} catch (NumberFormatException invalid) {
				fail("invalid_camera_scale");
				return;
			}
			spark = Boolean.parseBoolean(System.getenv(SPARK_FIRST_ENV));
			saveOptions();
			move(State.TITLE);
		}
		if (state == State.IDLE || state == State.COMPLETE || state == State.FAILED) {
			return;
		}
		try {
			if (failure != null) {
				throw new IllegalStateException(failure);
			}
			advance();
		} catch (Exception error) {
			fail(error.toString());
		}
	}

	private static void advance() throws Exception {
		var mc = Minecraft.getInstance();
		if (state != State.CAPTURE && state != State.SETTLE && state != State.VERIFY
				&& elapsed() > LOAD_TIMEOUT_SECONDS) {
			throw new IllegalStateException("profile_stage_timeout_" + state);
		}
		switch (state) {
			case TITLE -> {
				if (mc.screen instanceof TitleScreen && mc.getOverlay() == null && press("Create Test World")) {
					move(State.CREATE);
				}
			}
			case CREATE -> {
				if (mc.screen instanceof CreateWorldScreen screen) {
					screen.getUiState().setName(WORLD_PREFIX + label);
					screen.getUiState().setSeed(WORLD_SEED);
					screen.getUiState().setAllowCommands(true);
					if (press(Component.translatable("selectWorld.create").getString())) {
						move(State.LOAD);
					}
				}
			}
			case LOAD -> {
				if (mc.level != null && mc.player != null && mc.getOverlay() == null) {
					if (Boolean.parseBoolean(System.getenv(VALIDATION_ONLY_ENV))) {
						restoreOptions();
						move(State.COMPLETE);
						return;
					}
					var server = mc.getSingleplayerServer();
					if (server == null) {
						throw new IllegalStateException("integrated_server_required");
					}
					var playerId = mc.player.getUUID();
					server.execute(() -> setup(server, playerId));
					move(State.FIXTURE);
				}
			}
			case FIXTURE -> {
				if (fixtureReady && mc.level != null && mc.level.getEntity(queenId) != null) {
					mc.setScreen(null);
					mc.mouseHandler.releaseMouse();
					mc.options.hideGui = true;
					move(State.SETTLE);
				}
			}
			case SETTLE -> {
				if (elapsed() >= SETTLE_SECONDS) {
					beginCapture();
				}
			}
			case CAPTURE -> {
				markStages();
				if (elapsed() < CAPTURE_SECONDS + 1) {
					return;
				}
				var snapshot = PhotonFrameCapture.snapshot();
				if (!snapshot.get("state").getAsString().equals("RUNNING")) {
					save(snapshot);
					if (!snapshot.get("state").getAsString().equals("DURATION")) {
						throw new IllegalStateException("capture_incomplete_" + snapshot.get("state"));
					}
					sparkRunning = false;
					capturesCompleted++;
					if (capturesCompleted < (Boolean.parseBoolean(System.getenv(SINGLE_CAPTURE_ENV)) ? 1 : 2)) {
						spark = !spark;
						move(State.SETTLE);
					} else {
						verifying = true;
						scheduleCast();
						move(State.VERIFY);
					}
				}
			}
			case VERIFY -> {
				if (elapsed() >= 2 && verifying) {
					verifying = false;
					Screenshot.grab(mc.gameDirectory, label + "_verification.png", mc.getMainRenderTarget(), 1,
							message -> report(message.getString()));
				}
				if (elapsed() >= 5) {
					cleanup();
                    if (Boolean.parseBoolean(System.getenv(PhotonModelBindingsValidation.REQUEST_ENV))) {
                        report(PhotonModelBindingsValidation.verify());
                    }
					restoreOptions();
					move(State.COMPLETE);
				}
			}
			default -> {
			}
		}
	}

	/// 只记录显式开发采样中实际经过的渲染帧；同帧多次骨骼应用不重复计数。
	public static void renderedPose(LcAnimationSnapshot snapshot, double sampleTime, double weight, double renderAge) {
		if (state != State.CAPTURE || snapshot.ownerEntityId() != queenId) {
			return;
		}
		POSE_SAMPLES.computeIfAbsent(snapshot.instanceId(), _ -> new PoseSamples())
				.accept(snapshot.parameters().getOrDefault(PHASE_PARAMETER, 0.0).intValue(),
						sampleTime, weight, renderAge);
	}

	private static class PoseSamples {
		private long count, charge, recovery, timeReversals, chargeReversals, recoveryReversals;
		private double lastAge = Double.NEGATIVE_INFINITY, lastTime, lastWeight, maxGap;
		private int lastPhase = -1;

		private void accept(int phase, double time, double weight, double age) {
			if (age <= lastAge) {
				return;
			}
			if (count > 0) {
				maxGap = Math.max(maxGap, age - lastAge);
				if (time + 0.000001 < lastTime) {
					timeReversals++;
				}
				if (phase == lastPhase && phase == 0 && weight + 0.000001 < lastWeight) {
					chargeReversals++;
				}
				if (phase == lastPhase && phase == 2 && weight > lastWeight + 0.000001) {
					recoveryReversals++;
				}
			}
			count++;
			if (phase == 0) {
				charge++;
			} else if (phase == 2) {
				recovery++;
			}
			lastAge = age;
			lastTime = time;
			lastWeight = weight;
			lastPhase = phase;
		}

		private JsonObject snapshot() {
			var result = new JsonObject();
			result.addProperty("render_samples", count);
			result.addProperty("charge_render_samples", charge);
			result.addProperty("recovery_render_samples", recovery);
			result.addProperty("time_reversals", timeReversals);
			result.addProperty("charge_weight_reversals", chargeReversals);
			result.addProperty("recovery_weight_reversals", recoveryReversals);
			result.addProperty("maximum_render_gap_ticks", maxGap);
			return result;
		}
	}

	private static boolean press(String text) {
		var screen = Minecraft.getInstance().screen;
		if (screen == null) {
			return false;
		}
		for (var widget : screen.children()) {
			if (widget instanceof Button button && button.active && button.getMessage().getString().equals(text)) {
				button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
				return true;
			}
		}
		return false;
	}

	private static void setup(MinecraftServer server, UUID playerId) {
		try {
			var player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				throw new IllegalStateException("profile_player_missing");
			}
			var level = player.level();
			int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
			var candidate = QueenBeamPerformanceFixture.create(server, playerId, new Vec3(0, height, 0), 1);
			fixture = candidate;
			queenId = candidate.members().getFirst().queenId();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(level, CAMERA_X * cameraScale, height + CAMERA_HEIGHT, CAMERA_Z * cameraScale,
					Set.of(), CAMERA_YAW, CAMERA_PITCH, true);
			fixtureReady = true;
			report("fixture_ready");
		} catch (Exception error) {
			failure = error.toString();
		}
	}

	private static void beginCapture() {
		var mc = Minecraft.getInstance();
		MARKS.clear();
		POSE_SAMPLES.clear();
		lastAnimation = null;
		lastPhase = Double.NaN;
		if (spark && !ClientCommandHandler.runCommand(SPARK_START)) {
			throw new IllegalStateException("spark_client_command_unavailable");
		}
		sparkRunning = spark;
		QueenBeamAllocationCapture.start();
		captureQueuedNs = System.nanoTime();
		PhotonFrameCapture.start(label + (spark ? "_spark" : "_frames"), CAPTURE_SECONDS);
		PhotonFrameCapture.phase("empty", 0, () -> {
		});
		for (int index = 0; index < CAST_SECONDS.length; index++) {
			PhotonFrameCapture.phase("cast_" + index, CAST_SECONDS[index], QueenBeamPerformanceTrial::scheduleCast);
		}
		move(State.CAPTURE);
	}

	private static void scheduleCast() {
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			failure = "profile_server_missing";
			return;
		}
		server.execute(() -> cast(server));
	}

	private static void cast(MinecraftServer server) {
		var current = fixture;

		if (current == null) {
			failure = "profile_fixture_removed";
			return;
		}

		try {
			for (var result : current.cast(server)) {
				MARKS.add(new Mark("server_cast", System.nanoTime(), result.status()));
		report(result.status());

		if (!result.started()) {
			failure = "profile_cast_" + result.status();
		}
	}
		} catch (RuntimeException invalid) {
			failure = invalid.getMessage() == null ? invalid.toString() : invalid.getMessage();
		}
	}

	private static void markStages() {
		boolean present = false;
		for (var snapshot : LcClientAnimations.snapshots()) {
			if (snapshot.ownerEntityId() != queenId) {
				continue;
			}
			present = true;
			double phase = snapshot.parameters().getOrDefault(PHASE_PARAMETER, -1.0);
			if (!snapshot.instanceId().equals(lastAnimation) || phase != lastPhase) {
				lastAnimation = snapshot.instanceId();
				lastPhase = phase;
				long phaseNs = System.nanoTime();
				MARKS.add(new Mark("client_phase", phaseNs, snapshot.instanceId() + ":" + phase));
				QueenBeamAllocationCapture.phase(snapshot.instanceId(), phase, phaseNs);
			}
		}
		if (!present && lastAnimation != null) {
			MARKS.add(new Mark("client_removed", System.nanoTime(), lastAnimation.toString()));
			lastAnimation = null;
		}
	}

	private static void save(JsonObject capture) throws Exception {
		var result = summary(capture);
		result.add("allocation_capture", QueenBeamAllocationCapture.finish(label));
		var path = Minecraft.getInstance().gameDirectory.toPath().resolve("performance")
				.resolve(label + (spark ? "_spark" : "_frames") + "_marks_" + System.nanoTime() + ".json");
		Files.writeString(path, GSON.toJson(result), StandardOpenOption.CREATE_NEW);
		report(path);

		long outputs = MARKS.stream().filter(mark -> mark.event().equals("client_phase") && mark.detail().endsWith(":1.0")).count();
		if (outputs != CAST_SECONDS.length) {
			throw new IllegalStateException("missing_output_phases_" + outputs);
		}
	}

	private static JsonObject summary(JsonObject capture) {
		var result = new JsonObject();
		result.addProperty("label", label);
		result.addProperty("spark", spark);
		result.addProperty("capture_index", capturesCompleted);
		result.addProperty("capture_queued_ns", captureQueuedNs);
		result.addProperty("queen_id", queenId);
		result.addProperty("target_distance", TARGET_DISTANCE);
		result.addProperty("target_health", TARGET_HEALTH);
		result.addProperty("camera_x", CAMERA_X * cameraScale);
		result.addProperty("camera_distance_scale", cameraScale);
		result.addProperty("entity_distance_scaling", Minecraft.getInstance().options.entityDistanceScaling().get());
		result.addProperty("camera_z", CAMERA_Z * cameraScale);
		result.addProperty("camera_height_above_ground", CAMERA_HEIGHT);
		result.addProperty("camera_yaw", CAMERA_YAW);
		result.addProperty("camera_pitch", CAMERA_PITCH);
		result.addProperty("particle_random_seed_fixed", false);
		result.add("capture", capture.deepCopy());
		result.addProperty("shader_pipeline_cache_available", false);
		result.addProperty("prepared_first_use_available", false);
		result.addProperty("effect_runtime_available", false);
		var poses = new JsonObject();
		POSE_SAMPLES.forEach((id, samples) -> poses.add(id.toString(), samples.snapshot()));
		result.add("code_animation_render_samples", poses);
		var marks = new JsonArray();
		for (var mark : MARKS) {
			var item = new JsonObject();
			item.addProperty("event", mark.event());
			item.addProperty("nano_time", mark.nanoTime());
			item.addProperty("detail", mark.detail());
			marks.add(item);
		}
		result.add("marks", marks);
		return result;
	}

	// 采样期间固定窗口与帧率设置，结束后由 restoreOptions 恢复保存值。
	private static void saveOptions() {
		var mc = Minecraft.getInstance();
		originalGui = mc.options.hideGui;
		originalEntityDistance = mc.options.entityDistanceScaling().get();
		// 放大相机距离时同步提高实体距离下限，避免目标提前消失。
		optionsSaved = true;
		if (cameraScale > 1) {
			mc.options.entityDistanceScaling().set(Math.max(2, originalEntityDistance));
		}
	}

	private static void restoreOptions() {
		var mc = Minecraft.getInstance();
        if (optionsSaved) {
            optionsSaved = false;
			mc.options.hideGui = originalGui;
			mc.options.entityDistanceScaling().set(originalEntityDistance);
		}
		mc.mouseHandler.releaseMouse();
	}

	private static void cleanup() {
		var server = Minecraft.getInstance().getSingleplayerServer();
		var current = fixture;
		fixture = null;

		if (server != null && current != null) {
			server.execute(() -> current.clear(server));
		}
	}

	private static double elapsed() {
		return (System.nanoTime() - stateNs) / 1_000_000_000.0;
	}

	private static void report(Object message) {
		Lobecorp.LOGGER.info(LOG, message);
	}

	private static void move(State next) {
		state = next;
		stateNs = System.nanoTime();
		report(next);
	}

	private static void fail(String message) {
		failure = message;
		PhotonFrameCapture.stop();
		if (sparkRunning) {
			ClientCommandHandler.runCommand(SPARK_STOP);
		}
		cleanup();
		restoreOptions();
		move(State.FAILED);
		Lobecorp.LOGGER.error(LOG, message);
	}
}
