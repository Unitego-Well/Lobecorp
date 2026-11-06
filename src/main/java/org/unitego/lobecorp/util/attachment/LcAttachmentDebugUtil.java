package org.unitego.lobecorp.util.attachment;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;
import org.unitego.lobecorp.util.debug.LcDebugUtil;

/// 通用附着的开发日志只记录显式绑定边沿，不随逐刻位置变化刷屏。
public class LcAttachmentDebugUtil {
	private static final String BINDING_LOG = "[LC Attachment Debug] event={} binding={} client={} anchor={} render={} revision={} released={} world={}";

	@SuppressWarnings("resource")
	public static void binding(Event event, LcAttachmentBinding binding) {
		if (LcDebugUtil.isEnabled(LcDebugUtil.Category.ATTACHMENT)) {
			var snapshot = binding.snapshot();
			Lobecorp.LOGGER.info(BINDING_LOG, event, binding.id(), binding.target().level().isClientSide(),
					snapshot.anchor(), snapshot.renderAnchor(), snapshot.revision(), binding.isReleased(),
					snapshot.worldTransform());
		}
	}

	public enum Event {
		BOUND,
		ANCHOR_CHANGED,
		RENDER_SOURCE_CHANGED,
		RELEASED
	}
}
