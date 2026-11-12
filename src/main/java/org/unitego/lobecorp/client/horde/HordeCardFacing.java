package org.unitego.lobecorp.client.horde;

/// 每视图重建的选向缓存契约；GPU资源生命周期和pass由Renderer管理。
public class HordeCardFacing {
    /// 每实例四个RGBA8原始浮点字，共16字节。
    public static final int WORDS_PER_INSTANCE = 4;
    public static final String SAMPLER = "HordeCardFacing";
    public static final String FRAGMENT_SHADER = "core/horde_card_facing";

    /// 索引必须与当前HordeMotion一致，不能使用LOD压缩后的槽位。
    public static int firstWord(int motionInstance) {
        return Math.multiplyExact(motionInstance, WORDS_PER_INSTANCE);
    }
}
