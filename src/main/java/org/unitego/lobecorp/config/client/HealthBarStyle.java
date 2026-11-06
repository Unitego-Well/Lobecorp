package org.unitego.lobecorp.config.client;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/// 生命值样式：默认底部条、原版心形或 NEW 状态面板，三者互斥。
@NullMarked
public enum HealthBarStyle implements TranslatableEnum {
    DEFAULT,
    VANILLA,
    NEW;

    @Override
    public Component getTranslatedName() {
        return Component.translatable(PlayerHudConfig.PLAYER_HUD_STYLE_TRANSLATION
                + name().toLowerCase(Locale.ROOT));
    }
}
