package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.gui.editor.view.FXTimelineView;

import java.lang.reflect.Field;

public class PhotonTimelineStateUtil {
	/// 原生私有枚举无法作为外部 Mixin 字段类型，反射仅限这两个临时预览选项。
	private static final Field END_MODE = field("endMode");
	/// 时间轴回绕行为的配置标识，沿用既有设置序列化键。
	private static final Field WRAP_MODE = field("wrapMode");

	private record Modes(Object end, Object wrap) {
	}

	public static Object capture(FXTimelineView view) {
		try {
			return new Modes(END_MODE.get(view), WRAP_MODE.get(view));
		} catch (IllegalAccessException error) {
			throw new IllegalStateException(error);
		}
	}

	public static void restore(FXTimelineView view, Object state) {
		if (!(state instanceof Modes(Object end, Object wrap))) {
			return;
		}

		try {
			END_MODE.set(view, end);
			WRAP_MODE.set(view, wrap);
		} catch (IllegalAccessException error) {
			throw new IllegalStateException(error);
		}
	}

	private static Field field(String name) {
		try {
			var field = FXTimelineView.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException error) {
			throw new ExceptionInInitializerError(error);
		}
	}
}
