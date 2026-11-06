package org.unitego.lobecorp.util.photon.editor;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.lang.LangHandler;

/// 特效管理工程及其面板的翻译入口；只登记文案，不参与任何运行态。
public class PhotonEffectLibraryTextUtil {
	/// 特效管理工程的翻译键前缀。
	public static final String PREFIX = "photon.lobecorp.effect.library.";

	/// 工程类型的显示名称，与 `ProjectType` 使用的键一致。
	public static final String TYPE = PREFIX + "type";
	/// 重新扫描按钮。
	public static final String RESCAN = PREFIX + "rescan";
	/// 清空预览按钮。
	public static final String CLEAR = PREFIX + "clear";
	/// 列表汇总行；参数为条目数、对象数、发射器数、解压体积与不可读条目数。
	public static final String SUMMARY = PREFIX + "summary";
	/// 目录为空时的提示。
	public static final String EMPTY = PREFIX + "empty";
	/// 面板打开入口的菜单键。
	public static final String OPEN_MENU = PREFIX + "open";
	/// 打开工程时扫描目录的说明。
	public static final String TIP = PREFIX + "tip";

	public static String key(String name) {
		return PREFIX + name;
	}

	/// 集中登记中英文文案；返回类型键供 `TranslationKeys` 静态初始化使用。
	public static String register() {
		LangHandler.creates(Lobecorp.NAMESPACE, TYPE, "Effect manager project", "特效管理工程");
		LangHandler.creates(Lobecorp.NAMESPACE, RESCAN, "Rescan", "重新扫描");
		LangHandler.creates(Lobecorp.NAMESPACE, CLEAR, "Clear preview", "清空预览");
		LangHandler.creates(Lobecorp.NAMESPACE, SUMMARY, "%s effects | %s objects | %s emitters | %s KB | %s unreadable",
				"%s 个特效 | %s 个对象 | %s 个发射器 | %s KB | %s 个不可读");
		LangHandler.creates(Lobecorp.NAMESPACE, EMPTY, "No effect definitions found in the scanned directory.",
				"扫描目录内没有发现特效定义。");
		LangHandler.creates(Lobecorp.NAMESPACE, OPEN_MENU, "Effect manager", "特效管理器");
		LangHandler.creates(Lobecorp.NAMESPACE, TIP,
				"Lists effect definitions without loading them. Selecting an entry builds exactly one preview runtime; switching entries releases the previous one.",
				"只列出特效定义，不加载它们。选中条目才建立一份预览运行态；切换条目会释放上一份。");
		return TYPE;
	}
}
