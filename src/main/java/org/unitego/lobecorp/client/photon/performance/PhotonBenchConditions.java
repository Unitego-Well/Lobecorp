package org.unitego.lobecorp.client.photon.performance;

import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.SharedConstants;

/// 显式 Photon 基准拥有的种子序列；普通游戏不启用，不写入资源定义。
public class PhotonBenchConditions {
    private static boolean active;
    private static long seed;
    private static long sequence;

    public static void begin(long value) {
        if (!SharedConstants.IS_RUNNING_IN_IDE || !System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-")) {
            throw new IllegalStateException("固定随机输入只允许用于显式 Photon 开发基准。");
        }

        seed = value;
        sequence = 0;
        active = true;
    }

    public static void seed(FXObject object) {
        if (!active) {
            return;
        }

        // SplitMix64 的混合避免相邻发射器种子相关；消费顺序由原生发射过程保持。
        long value = seed + 0x9E3779B97F4A7C15L * ++sequence;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        object.setRandomSeed(value ^ value >>> 31);
    }

    public static long seededObjects() {
        return sequence;
    }

    public static void end() {
        active = false;
    }
}
