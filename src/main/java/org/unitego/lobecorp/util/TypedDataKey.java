package org.unitego.lobecorp.util;

/// 用对象身份区分值的类型安全临时数据键。
///
/// @param <T> 键对应的值类型
public final class TypedDataKey<T> {
	private TypedDataKey() {
	}

	/// 创建互不共享的新数据键。
	///
	/// @param <T> 键对应的值类型
	/// @return 仅与本次调用返回对象匹配的数据键
	public static <T> TypedDataKey<T> create() {
		return new TypedDataKey<>();
	}
}
