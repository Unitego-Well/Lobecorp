package org.unitego.lobecorp.config.client;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/// 二选一 HUD 样式；NEW 使用对应 newhud 表现，具体生效范围由所属组件处理。
@NullMarked
public enum HudStyle implements TranslatableEnum {
	DEFAULT,
	NEW;

	@Override
	public Component getTranslatedName() {
		return Component.translatable(PlayerHudConfig.PLAYER_HUD_STYLE_TRANSLATION
				+ name().toLowerCase(Locale.ROOT));
	}
}
