package org.unitego.lobecorp.config;

import java.util.Locale;

/// 默认使用A渲染，显式default保留旧A对照；实验项只改变渲染实现，不改变逻辑单位数量。
public enum HordePathVariant {
    DEFAULT, A, A_ADAPTIVE, A_SELECTED, A_DEPTH, A_NEXT, B, NO_HIZ, NO_FACING_CACHE, NO_MOTION_OPT, NO_AVOIDANCE_OPT,
    NO_IRIS_OPT, NO_DETAIL_LOD, NO_HIDDEN_FACES;

    private static final HordePathVariant CURRENT = parse(System.getProperty("lobecorp.horde.variant", "a"));

    private static HordePathVariant parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown lobecorp.horde.variant: " + value
                    + "; allowed: " + java.util.Arrays.toString(values()), exception);
        }
    }

    public static HordePathVariant current() {
        return CURRENT;
    }

    public static boolean optimized() {
        return true;
    }

    public static boolean motionKernel() {
        return CURRENT != DEFAULT && CURRENT != NO_MOTION_OPT;
    }

    public static boolean avoidanceKernel() {
        return CURRENT != DEFAULT && CURRENT != NO_AVOIDANCE_OPT;
    }

    public static boolean irisKernel() {
        return CURRENT != DEFAULT && CURRENT != NO_IRIS_OPT;
    }

    public static boolean facingKernel() {
        return CURRENT != DEFAULT && facingCache();
    }

    public static boolean facingCache() {
        return CURRENT != NO_FACING_CACHE;
    }

    public static boolean detailLod() {
        return CURRENT != NO_DETAIL_LOD;
    }

    public static boolean sceneOcclusion() {
        return detailLod() && CURRENT != NO_HIZ;
    }

    public static boolean layered() {
        return CURRENT == B;
    }

    /// 由最终候选直接散射选中标记，额度统一使用候选key中的距离档。
    public static boolean selectedScatter() {
        return CURRENT == A_SELECTED || CURRENT == A_NEXT;
    }

    /// 复用已初始化的远深度填充区，仅更新实际深度覆盖的矩形。
    public static boolean depthScissor() {
        return CURRENT == A_DEPTH || CURRENT == A_NEXT;
    }

    public static boolean reduceHiddenFaces() {
        return CURRENT != NO_HIDDEN_FACES;
    }
}
