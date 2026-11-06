package org.unitego.lobecorp.config.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;

public class LobecorpBenchConfig {
	public static final String PROVIDER_ID = "lobecorp";
	public static final String SERVER_SCENARIO = "lobecorp.sweeper-ai";
	public static final String CLIENT_SCENARIO = "lobecorp.sweeper-render";
	public static final String HORDE_SCENARIO = "lobecorp.horde-render";
	public static final String CORPSE_SCENARIO = "lobecorp.corpse-render";
	public static final String SWEEPER30_AI_SCENARIO = "lobecorp.sweeper30-ai";
	public static final String SWEEPER30_NOAI_SCENARIO = "lobecorp.sweeper30-noai";
	public static final String CORPSE_RANDOM100_SCENARIO = "lobecorp.corpse-random100";
	public static final String PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO = "lobecorp.photon-02-first-cast-performance";
	public static final String PHOTON_CONTROL_FX_PROPERTY = "lobecorp.photon.bench.controlFx";
	public static final String PHOTON_CONTROL_FX = PhotonRepeatedEffectConfig.NATIVE_FX;
	public static final String PHOTON_CONTROL_LABEL = "photon-02-native-data-control";
	public static final String PHOTON_REPEAT_PERFORMANCE_SCENARIO = PhotonRepeatedEffectConfig.SCENARIO;
	public static final String PHOTON_CONTROL_FX_SHA256 = PhotonRepeatedEffectConfig.FX_SHA256;
	public static final String PHOTON_CONTROL_MATERIAL_SHA256 = PhotonRepeatedEffectConfig.MATERIAL_SHA256;
	public static final String PHOTON_CONTROL_TEXTURE_SHA256 = PhotonRepeatedEffectConfig.TEXTURE_SHA256;
	public static final int PHOTON_CONTROL_WARMUP_TICKS = 100;
	public static final int PHOTON_CONTROL_MEASURE_SECONDS = 20;
	public static final int GRID_WIDTH = 8;
	public static final int ENTITY_COUNT = GRID_WIDTH * GRID_WIDTH;
	public static final int GRID_SPACING = 3;
	public static final int STABILIZE_TICKS = 40;
	public static final int WARMUP_TICKS = 60;
	public static final int MEASURE_TICKS = 620;
	public static final int STABLE_FRAMES = 20;
	public static final int CORPSE_MEASURE_TICKS = 600;

	public static int hordeWidth() {
		return fileInt("hordeWidth", 512);
	}

	public static int hordeLength() {
		return fileInt("hordeLength", 320);
	}

	public static int hordeMeasureTicks() {
		return fileInt("hordeMeasureTicks", MEASURE_TICKS);
	}

	/// -1 使用基准高空相机；>=0 把玩家放到路线该位置的原地面上，且不飞行。
	public static int hordePlayerStation() {
		return fileInt("hordePlayerStation", -1);
	}

	/// 浪潮推进速度（格/刻）；默认 0.75，是原 0.3 夹具的 2.5 倍速避让夹具。
	public static double hordeSpeed() {
		double speed = fileDouble("hordeSpeed", 0.75);
		if (!Double.isFinite(speed) || speed <= 0.0 || speed > 4.0) {
			throw new IllegalArgumentException("hordeSpeed 必须为 (0,4] 内的有限数");
		}
		return speed;
	}

	/// 30 只真实清道夫的服务端生成数量；有 AI / 无 AI 两个场景共用一个键。
	public static int sweeper30Count() {
		int count = fileInt("sweeper30Count", 30);
		if (count < 1 || count > 256) {
			throw new IllegalArgumentException("sweeper30Count 只能为 1..256");
		}
		return count;
	}

	public static int sweeper30MeasureTicks() {
		int ticks = fileInt("sweeper30MeasureTicks", 600);
		if (ticks < 1) {
			throw new IllegalArgumentException("sweeper30MeasureTicks 必须为正数");
		}
		return ticks;
	}

	/// 随机生物尸体数量（敌对/中立/被动混合）。
	public static int corpseRandomCount() {
		int count = fileInt("corpseRandomCount", 100);
		if (count < 1 || count > 256) {
			throw new IllegalArgumentException("corpseRandomCount 只能为 1..256");
		}
		return count;
	}

	public static int corpseRandomMeasureTicks() {
		int ticks = fileInt("corpseRandomMeasureTicks", 600);
		if (ticks < 1) {
			throw new IllegalArgumentException("corpseRandomMeasureTicks 必须为正数");
		}
		return ticks;
	}

	/// 浪潮路线附近额外生成的真实监守者数量；本轮只允许 0（不加）或 10（矩阵项 6）。
	public static int hordeWardens() {
		int count = fileInt("hordeWardens", 0);
		if (count != 0 && count != 10) {
			throw new IllegalArgumentException("hordeWardens 只能为 0 或 10");
		}
		return count;
	}

	/// 集成服务端实体生成后等待客户端同步的上限 tick（默认 30 秒）；期间服务端会逐 tick 校验并补生成。
	public static int entitySyncTimeoutTicks() {
		int ticks = fileInt("entitySyncTimeoutTicks", 600);
		if (ticks < 20) {
			throw new IllegalArgumentException("entitySyncTimeoutTicks 不得小于 20");
		}
		return ticks;
	}

	/// measure 结束后等待本轮 Spark 上传链接落盘的上限 tick（默认 30 秒；实测 2 分钟采样上传约 10-12 秒）。
	public static int sparkSaveTimeoutTicks() {
		int ticks = fileInt("sparkSaveTimeoutTicks", 600);
		if (ticks < 20) {
			throw new IllegalArgumentException("sparkSaveTimeoutTicks 不得小于 20");
		}
		return ticks;
	}

	public static int corpseRenderCount() {
		int count = Integer.getInteger("lobecorp.bench.corpseRenderCount", 0);
		if (count == 0) {
			count = fileInt("corpseRenderCount", 64);
		}
		if (count != 30 && count != 64 && count != 256) {
			throw new IllegalArgumentException("尸体基准数量只能为30、64或256");
		}
		return count;
	}

	/// 运行目录下的 lobecorp-bench.properties 可切换64/256尸体，不写死默认值。
	private static int fileInt(String key, int fallback) {
		var file = Path.of("lobecorp-bench.properties");
		if (!Files.isRegularFile(file)) {
			return fallback;
		}
		var properties = new Properties();
		try (var reader = Files.newBufferedReader(file)) {
			properties.load(reader);
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
		} catch (IOException | NumberFormatException failure) {
			throw new IllegalArgumentException("无法读取基准属性文件 " + file.toAbsolutePath(), failure);
		}
	}

	private static double fileDouble(String key, double fallback) {
		var file = Path.of("lobecorp-bench.properties");
		if (!Files.isRegularFile(file)) {
			return fallback;
		}
		var properties = new Properties();
		try (var reader = Files.newBufferedReader(file)) {
			properties.load(reader);
			return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
		} catch (IOException | NumberFormatException failure) {
			throw new IllegalArgumentException("无法读取基准属性文件 " + file.toAbsolutePath(), failure);
		}
	}
	public static final int CLIENT_ENTITY_ID_BASE = -1_000_000;
	public static final int CAMERA_HEIGHT = 14;
	public static final int CAMERA_DISTANCE = 24;
	public static final Duration PHASE_TIMEOUT = Duration.ofSeconds(120);
	/// 浪潮测量默认 2400 tick，再加 Spark 上传等待余量，避免阶段超时误判。
	public static final Duration HORDE_PHASE_TIMEOUT = Duration.ofMinutes(6);
}
