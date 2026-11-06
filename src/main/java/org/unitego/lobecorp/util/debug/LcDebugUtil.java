package org.unitego.lobecorp.util.debug;

import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/// 非 Photon 游戏调试日志的唯一类别开关；默认关闭，生产环境始终关闭。
public class LcDebugUtil {
	/// 可独立开启的调试日志类别。
	public enum Category {
		SKILL,
		ANIMATION,
		ATTACHMENT
	}

	private static final String GLOBAL_PROPERTY = "lobecorp.debug";
	private static final String SKILL_PROPERTY = "lobecorp.debug.skill";
	private static final String ANIMATION_PROPERTY = "lobecorp.debug.animation";
	private static final String ATTACHMENT_PROPERTY = "lobecorp.debug.attachment";

	private static volatile Set<Category> enabled = readProperties();

	private LcDebugUtil() {
	}

	/// 类别是否启用；生产环境忽略所有运行期开关。
	public static boolean isEnabled(Category category) {
		return !FMLEnvironment.isProduction() && enabled.contains(category);
	}

	/// 覆盖当前 JVM 的单个类别开关，不写入配置。
	public static synchronized void setEnabled(Category category, boolean value) {
		var next = EnumSet.noneOf(Category.class);
		next.addAll(enabled);
		if (value) {
			next.add(category);
		} else {
			next.remove(category);
		}

		enabled = Collections.unmodifiableSet(next);
	}

	/// 覆盖当前 JVM 的全部类别开关，不写入配置。
	public static synchronized void setAllEnabled(boolean value) {
		enabled = value ? Collections.unmodifiableSet(EnumSet.allOf(Category.class)) : Collections.emptySet();
	}

	/// 读取当前 JVM 的类别快照，供命令反馈使用。
	public static Set<Category> snapshot() {
		return enabled;
	}

	/// 启动时按属性解析初始类别集合。
	private static Set<Category> readProperties() {
		boolean global = parseBoolean(GLOBAL_PROPERTY, false);
		var result = EnumSet.noneOf(Category.class);
		if (parseBoolean(SKILL_PROPERTY, global)) {
			result.add(Category.SKILL);
		}

		if (parseBoolean(ANIMATION_PROPERTY, global)) {
			result.add(Category.ANIMATION);
		}

		if (parseBoolean(ATTACHMENT_PROPERTY, global)) {
			result.add(Category.ATTACHMENT);
		}

		return Collections.unmodifiableSet(result);
	}

	/// 标准 Boolean.parseBoolean 语义：属性存在时空串和非 true 文本均为 false。
	private static boolean parseBoolean(String property, boolean fallback) {
		String value = System.getProperty(property);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}
}
