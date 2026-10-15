package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.client.gameobject.FXObject;

import java.lang.reflect.Field;
import java.util.function.Supplier;

public class PhotonAnimationNameUtil {
	/// 仅在构造动画模式的原对象检视时锁定名称，嵌套检视恢复上一层范围。
	private static final ThreadLocal<Boolean> LOCK_NAME = ThreadLocal.withInitial(() -> false);

	public static <T> T inspect(Supplier<T> build) {
		var previous = LOCK_NAME.get();
		LOCK_NAME.set(true);

		try {
			return build.get();
		} finally {
			if (previous) {
				LOCK_NAME.set(true);
			} else {
				LOCK_NAME.remove();
			}
		}
	}

	public static boolean locksName(Field field, Object owner) {
		return LOCK_NAME.get() && owner instanceof FXObject
				&& field.getDeclaringClass() == FXObject.class && field.getName().equals("name");
	}
}
