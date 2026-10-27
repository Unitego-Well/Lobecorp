package org.unitego.lobecorp.client.photon.performance;

import org.unitego.lobecorp.Lobecorp;

/// 仅在显式诊断属性开启时记录一次施法准备的分段耗时；默认为空操作，不产生日志或分配。
public final class PhotonCastProfiler {
    private static final boolean ENABLED = Boolean.getBoolean("lobecorp.photon.diag.cast");
    private static final StringBuilder MARKS = new StringBuilder();
    private static long origin;
    private static long last;
    private static boolean active;

    private PhotonCastProfiler() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void begin(String label) {
        if (!ENABLED) {
            return;
        }

        origin = System.nanoTime();
        last = origin;
        active = true;
        MARKS.setLength(0);
        mark("begin:" + label);
    }

    public static void mark(String label) {
        if (!ENABLED || !active) {
            return;
        }

        long now = System.nanoTime();
        MARKS.append(label).append('=')
                .append(String.format(java.util.Locale.ROOT, "%.3f", (now - origin) / 1_000_000d))
                .append("ms(+")
                .append(String.format(java.util.Locale.ROOT, "%.3f", (now - last) / 1_000_000d))
                .append(") ");
        last = now;
    }

    public static void end(String label) {
        if (!ENABLED || !active) {
            return;
        }

        long now = System.nanoTime();
        MARKS.append("end:").append(label).append('=')
                .append(String.format(java.util.Locale.ROOT, "%.3f", (now - origin) / 1_000_000d))
                .append("ms(+")
                .append(String.format(java.util.Locale.ROOT, "%.3f", (now - last) / 1_000_000d))
                .append(") ");
        active = false;
        try {
            Lobecorp.LOGGER.info("CASTPROF {}", MARKS);
        } finally {
            MARKS.setLength(0);
        }
    }
}
