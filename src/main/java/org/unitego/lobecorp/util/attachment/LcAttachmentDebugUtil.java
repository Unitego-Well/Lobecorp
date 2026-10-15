package org.unitego.lobecorp.util.attachment;

import net.neoforged.fml.loading.FMLEnvironment;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;

/// 通用附着的开发日志只记录显式绑定边沿，不随逐刻位置变化刷屏。
public class LcAttachmentDebugUtil {
	/// 仅非生产环境生效；重启后关闭，客户端与服务端 JVM 分别设置。
	public static volatile boolean ENABLED = false;
	private static final String BINDING_LOG = "[LC Attachment Debug] event={} binding={} client={} anchor={} render={} revision={} released={} world={}";

	@SuppressWarnings("resource")
	public static void binding(Event event, LcAttachmentBinding binding) {
		if (ENABLED && !FMLEnvironment.isProduction()) {
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
