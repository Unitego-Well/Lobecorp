package org.unitego.lobecorp.registry.translation;

import net.neoforged.fml.ModList;
import org.unitego.lobecorp.Lobecorp;

public class TranslationRegistry {
	/// 常量初始化时登记唯一文案；数据生成稍后可导出此前已加载的声明。
	public static String register(String key, String english, String chinese) {
		return LangHandler.creates(Lobecorp.NAMESPACE, key, english, chinese);
	}

	public static void init() {
		var packagePrefix = TranslationRegistry.class.getPackageName() + ".";
		var sources = ModList.get().getModFileById(Lobecorp.NAMESPACE).getFile().getScanResult().getClasses().stream()
				.map(source -> source.clazz().getClassName())
				.filter(name -> name.startsWith(packagePrefix) && name.endsWith("TranslationKeys"))
				.sorted()
				.toList();

		for (var source : sources) {
			try {
				Class.forName(source, true, TranslationRegistry.class.getClassLoader());
			} catch (ClassNotFoundException exception) {
				throw new IllegalStateException("翻译常量类型不可加载：" + source, exception);
			}
		}
	}
}
