package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface MidnightCleanupTranslationKeys {
	String BANNER_TITLE = register("horde.lobecorp.banner.title", "The Noon of Indigo", "靛蓝色的正午");
	String BANNER_NAME = register("horde.lobecorp.banner.name", "The Sweepers", "清道夫");
	String BANNER_START = register("horde.lobecorp.banner.start", "When night falls, they quietly arrive in the Backstreets...", "夜幕降临之际，他们“悄悄地”来到后巷……");
	String BANNER_END = register("horde.lobecorp.banner.end", "When the sun rises, not a trace will remain...", "旭日东升之时，半点痕迹都不会剩下……");
	String NAME = register("horde.lobecorp.midnight_cleanup", "Midnight Cleanup", "深夜清扫");
}
