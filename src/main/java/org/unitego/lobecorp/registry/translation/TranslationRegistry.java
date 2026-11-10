package org.unitego.lobecorp.registry.translation;

import org.unitego.lobecorp.Lobecorp;
import java.util.LinkedHashMap;
import java.util.Map;

public class TranslationRegistry {
	private static final Map<String, Entry> entries = new LinkedHashMap<>();

	/// 常量初始化时登记唯一文案；数据生成稍后可导出此前已加载的声明。
	public static String register(String key, String english, String chinese) {
		entries.put(key, new Entry(english, chinese));
		return LangHandler.creates(Lobecorp.NAMESPACE, key, english, chinese);
	}

	public static void export() {
		TranslationSources.load();
		entries.forEach((key, value) -> LangHandler.creates(Lobecorp.NAMESPACE, key, value.english(), value.chinese()));
	}

	private record Entry(String english, String chinese) {
	}
}
