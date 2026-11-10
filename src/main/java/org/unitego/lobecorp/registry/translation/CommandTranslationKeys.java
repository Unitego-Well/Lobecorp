package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface CommandTranslationKeys {
	String COMMANDS_LOBECORP_ANIMATION_EMPTY_ITEM = register("commands.lobecorp.animation.empty_item", "Animation display item must not be empty.", "动画显示物品不能为空。");
}
