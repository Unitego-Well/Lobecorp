package org.unitego.lobecorp.animation.pose;

import java.io.Serial;

public class LcAnimationQueryWriteException extends UnsupportedOperationException {
	@Serial
	private static final long serialVersionUID = 1L;
	private static final String WRITE_DENIED = "当前动画查询上下文不允许写入：";

	public LcAnimationQueryWriteException(String name) {
		super(WRITE_DENIED + name);
	}
}
