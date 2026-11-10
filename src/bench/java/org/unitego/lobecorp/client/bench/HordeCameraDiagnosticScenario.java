package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupCinematic;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/// 独立开幕诊断；复用浪潮夹具和采集，但不改旧场景的工作负载与行为。
public class HordeCameraDiagnosticScenario extends HordeRenderScenario {

	public static final String ID = "lobecorp.horde-camera-diagnostic";
	private static final long READY_NS = 10_000_000_000L;
	private long readyAt;
	private boolean initialized, scheduled;
	private long beganTick = -1;
	private int cycle = -1;
	private final JsonArray samples = new JsonArray();
	private final JsonArray markers = new JsonArray();

	@Override
	public void setup(BenchClientContext context) {
		readyAt = 0;
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		if (!initialized) {
			if (context.minecraft().screen != null || context.minecraft().getOverlay() != null) {
				readyAt = 0;
				return BenchClientStepResult.CONTINUE;
			}
			if (readyAt == 0) {
				readyAt = System.nanoTime();
			}
			if (System.nanoTime() - readyAt < READY_NS) {
				return BenchClientStepResult.CONTINUE;
			}
			super.setup(context);
			initialized = true;
		}
		return super.stabilize(context);
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) {
		var result = super.measure(context);
		if (!scheduled) {
			scheduled = true;
			for (int repeat = 0; repeat < 2; repeat++) {
				int current = repeat;
				double at = 1 + repeat * 15;
				PhotonFrameCapture.phase("camera_" + repeat + "_start", at, () -> begin(context, current));
				phase("left", repeat, at + 1.4);
				phase("left_hold", repeat, at + 1.9);
				phase("right", repeat, at + 2.35);
				phase("right_correction", repeat, at + 3.05);
				phase("right_hold", repeat, at + 3.25);
				phase("center", repeat, at + 3.75);
				phase("zoom", repeat, at + 4.2);
				phase("text", repeat, at + 4.8);
				phase("dissolve", repeat, at + 8.4);
				phase("end", repeat, at + 10);
			}
		}

		var camera = context.minecraft().gameRenderer.getMainCamera();
		var sample = new JsonObject();
		sample.addProperty("nano_time", System.nanoTime());
		sample.addProperty("render_frame_id", PhotonFrameCapture.renderFrameId());
		sample.addProperty("game_tick", context.level().getGameTime());
		sample.addProperty("cycle", cycle);
		sample.addProperty("age_tick", beganTick < 0 ? -1 : context.level().getGameTime() - beganTick);
		sample.addProperty("yaw", camera.yRot());
		sample.addProperty("pitch", camera.xRot());
		sample.addProperty("fov", camera.getFov());
		samples.add(sample);
		return result;
	}

	private static void phase(String name, int repeat, double at) {
		PhotonFrameCapture.phase("camera_" + repeat + "_" + name, at, () -> {});
	}

	private void begin(BenchClientContext context, int current) {
		UUID event = HordeClient.events().stream().filter(value -> value.ready && !value.failed)
				.findFirst().orElseThrow().id;
		var body = new CompoundTag();
		body.putBoolean("opening", true);
		body.putBoolean("banner_only", false);
		body.putFloat("target_yaw", context.player().getYRot());
		body.putFloat("target_pitch", 0);
		MidnightCleanupCinematic.receive(event, body);
		beganTick = context.level().getGameTime();
		cycle = current;
		var marker = new JsonObject();
		marker.addProperty("nano_time", System.nanoTime());
		marker.addProperty("game_tick", beganTick);
		marker.addProperty("cycle", current);
		marker.addProperty("event", event.toString());
		markers.add(marker);
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		Path file = context.resultDirectory().resolve("artifacts/camera/opening-phases.json");
		Files.createDirectories(file.getParent());
		var evidence = new JsonObject();
		evidence.addProperty("scope", "client presentation in the stock horde workload; not server opening trigger validation");
		evidence.addProperty("sample_scope", "tick snapshots keyed to render_frame_id; phase boundaries are render-clock requests");
		evidence.add("starts", markers);
		evidence.add("tick_samples", samples);
		Files.writeString(file, evidence.toString());
		context.artifacts().register(file, "application/json");
		super.verify(context);
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		MidnightCleanupCinematic.clear();
		if (initialized) {
			super.teardown(context);
		}
	}
}
