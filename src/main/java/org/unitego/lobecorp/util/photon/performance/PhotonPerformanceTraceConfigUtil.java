package org.unitego.lobecorp.util.photon.performance;

import org.jetbrains.annotations.Nullable;

/// 仅解析诊断节点、聚合与计数器容量，不读取 JVM 属性、不分配采集缓冲。
public class PhotonPerformanceTraceConfigUtil {
	public static final String MAX_NODES_PROPERTY = "lobecorp.photon.trace.maxNodes";
	private static final int DEFAULT_MAX_NODES = 4096;
	private static final int UPPER_MAX_NODES = 16384;
    public static final String MAX_AGGREGATES_PROPERTY = "lobecorp.photon.trace.maxAggregatesPerThread";
    private static final int DEFAULT_MAX_AGGREGATES_PER_THREAD = 4096;
    // 诊断覆盖最多保留每线程 65536 个聚合键，不扩大其他预算。
    private static final int UPPER_MAX_AGGREGATES_PER_THREAD = 65536;

    public static final String MAX_COUNTERS_PROPERTY = "lobecorp.photon.trace.maxCountersPerThread";
    private static final int DEFAULT_MAX_COUNTERS_PER_THREAD = 4096;
    // 每线程计数器键的独立上限，不扩大聚合或原始事件预算。
    private static final int UPPER_MAX_COUNTERS_PER_THREAD = 16384;

    /// 十进制整数钳制到 4096..16384；未设置、非法或整数溢出时使用默认 4096。
    public static int maxNodes(@Nullable String value) {
        if (value == null) {
            return DEFAULT_MAX_NODES;
        }

        try {
            return Math.clamp(Integer.parseInt(value), DEFAULT_MAX_NODES, UPPER_MAX_NODES);
        } catch (NumberFormatException _) {
            return DEFAULT_MAX_NODES;
        }
    }

    /// 十进制整数钳制到 4096..16384；未设置、非法或整数溢出时使用默认 4096。
    public static int maxCountersPerThread(@Nullable String value) {
        if (value == null) {
            return DEFAULT_MAX_COUNTERS_PER_THREAD;
        }

        try {
            return Math.clamp(Integer.parseInt(value), DEFAULT_MAX_COUNTERS_PER_THREAD, UPPER_MAX_COUNTERS_PER_THREAD);
        } catch (NumberFormatException _) {
            return DEFAULT_MAX_COUNTERS_PER_THREAD;
        }
    }

    /// 十进制整数钳制到 4096..65536；未设置、非法或整数溢出时使用默认 4096。
    public static int maxAggregatesPerThread(@Nullable String value) {
        if (value == null) {
            return DEFAULT_MAX_AGGREGATES_PER_THREAD;
        }

        try {
            return Math.clamp(Integer.parseInt(value), DEFAULT_MAX_AGGREGATES_PER_THREAD, UPPER_MAX_AGGREGATES_PER_THREAD);
        } catch (NumberFormatException _) {
            return DEFAULT_MAX_AGGREGATES_PER_THREAD;
        }
    }
}
