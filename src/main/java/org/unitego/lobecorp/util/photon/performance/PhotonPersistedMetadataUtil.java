package org.unitego.lobecorp.util.photon.performance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/// 仅保存当前声明类的反射数组；借给原 parser 使用，调用方不得改写数组内容或顺序。
public class PhotonPersistedMetadataUtil {
	private static final ClassValue<Cell<Method[]>> METHODS = new ClassValue<>() {
		@Override
		protected Cell<Method[]> computeValue(@NotNull Class<?> type) {
			return new Cell<>();
		}
	};
	private static final ClassValue<Cell<Field[]>> FIELDS = new ClassValue<>() {
		@Override
		protected Cell<Field[]> computeValue(@NotNull Class<?> type) {
			return new Cell<>();
		}
	};

	public static Method[] declaredMethods(Class<?> type, Operation<Method[]> original) {
		return METHODS.get(type).get(type, original);
	}

	public static Field[] declaredFields(Class<?> type, Operation<Field[]> original) {
		return FIELDS.get(type).get(type, original);
	}

	private static class Cell<T> {
		private volatile T value;

		private T get(Class<?> type, Operation<T> original) {
			var cached = value;
			if (cached != null) {
				return cached;
			}

			synchronized (this) {
				cached = value;
				if (cached == null) {
					// 仅成功后发布；不持有加载器，失败由下一次调用使用其自身的 Operation 重试。
					cached = original.call(type);
					value = cached;
				}

				return cached;
			}
		}
	}
}
