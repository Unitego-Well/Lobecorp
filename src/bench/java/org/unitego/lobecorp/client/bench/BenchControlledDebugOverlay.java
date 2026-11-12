package org.unitego.lobecorp.client.bench;

import com.mojang.logging.LogUtils;

/// 独立基准固定调试覆盖层；只拒绝该实例的开启请求，不读取或操作实体输入。
public class BenchControlledDebugOverlay {
    private static final boolean CONTROLLED = Boolean.getBoolean("lobecorp.horde.bench.controlledDebugOverlay")
            && HordeEncounterPerformanceScenario.ID.equals(System.getProperty("modBench.scenarios"));
    private static int blockedRequests;

    public static boolean rejectOpening(boolean visible) {
        if (!CONTROLLED || !visible) {
            return false;
        }
        blockedRequests++;
        if (blockedRequests <= 4) {
            LogUtils.getLogger().info("event=benchmark-debug-overlay-opening-blocked count={}", blockedRequests,
                    new Throwable("基准调试覆盖层开启来源"));
        }
        return true;
    }

    public static int blockedRequests() {
        return blockedRequests;
    }
}
