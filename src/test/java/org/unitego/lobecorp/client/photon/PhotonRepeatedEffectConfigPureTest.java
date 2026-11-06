package org.unitego.lobecorp.client.photon;

import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;

import java.util.List;

/// 无 Minecraft、JUnit 或 Gradle 依赖的参数审计回归；可单独 javac/java 执行。
public class PhotonRepeatedEffectConfigPureTest {
	@SuppressWarnings({"unused", "ConstantValue", "UnnecessaryModifier"})
	public static void main(String[] args) {
		for (var value : List.of(
				"-DMC_DEBUG_DONT_SAVE_WORLD=true", "-Dminecraft.debug.sync=true",
				"-Dneoforge.enableGameTest=true", "-DmodBench.scenarios=lobecorp.photon-03-repeat-performance",
				"-Dlobecorp.photon.diag.cast=true", "-Dlobecorp.photon.bench.probes=false",
				"-agentlib:jdwp=transport=dt_socket,server=y,address=5005",
				"-XX:+AllowEnhancedClassRedefinition", "-javaagent:C:/tools/hotswap-agent.jar",
				"-XX:StartFlightRecording=name=profile")) {
			require(PhotonRepeatedEffectConfig.isTestVmArgument(value), "未识别测试参数：" + value);
		}

		for (var value : List.of("-Xmx8G", "-Dfile.encoding=UTF-8", "-Dmcp.port=19877",
				"-DlegacyClassPath=C:/mods/photon.jar;C:/mods/spark.jar;C:/mods/gametest.jar",
				"-Djava.library.path=C:/native", "--add-opens=java.base/java.lang=ALL-UNNAMED")) {
			require(!PhotonRepeatedEffectConfig.isTestVmArgument(value), "误判游戏必须参数：" + value);
		}

		require(PhotonRepeatedEffectConfig.COUNT == 3, "三次触发");
		require(PhotonRepeatedEffectConfig.triggerSeconds(0) == 2
				&& PhotonRepeatedEffectConfig.triggerSeconds(1) == 7
				&& PhotonRepeatedEffectConfig.triggerSeconds(2) == 12, "绝对开始时间2/7/12");
		require(PhotonRepeatedEffectConfig.CAPTURE_SECONDS == 40, "主窗口不能截断为20秒");
		for (int index : new int[]{-1, 3}) {
			boolean rejected = false;
			try {
				PhotonRepeatedEffectConfig.triggerSeconds(index);
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}

			require(rejected, "窗口外触发索引必须拒绝：" + index);
		}

		System.out.println("PASS: 16 VM argument cases; 3 absolute offsets; 40s window; index bounds");
	}

	private static void require(boolean value, String message) {
		if (!value) {
			throw new AssertionError(message);
		}
	}
}
