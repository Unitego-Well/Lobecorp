package org.unitego.lobecorp.api;

/// Mixin 未实现接口默认方法时抛出，用于区分缺失注入与业务异常。
public class NoMixinException extends IllegalStateException {
	public NoMixinException() {
		super("A mixin should have implemented this method!");
	}
}
