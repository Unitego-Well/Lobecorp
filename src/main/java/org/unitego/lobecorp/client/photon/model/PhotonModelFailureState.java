package org.unitego.lobecorp.client.photon.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 保存模型最近一次可恢复失败的阶段与消息，用于统一状态展示和日志去重。
///
/// 本类只记录观察结果，不停止时钟、不清理缓存，也不决定绘制或资源生命周期。
@NullMarked
public class PhotonModelFailureState {
	public record Snapshot(String operation, String message) {
		public Snapshot {
			Objects.requireNonNull(operation);
			Objects.requireNonNull(message);
		}

		/// 状态栏使用的单行说明；阶段沿用调用方提供的中文操作名。
		public String detail() {
			return operation + "：" + message;
		}
	}

	private @Nullable Snapshot current;

	/// 记录失败并返回状态是否变化；相同阶段和消息不会重复触发日志。
	public boolean record(String operation, Throwable error) {
		var next = new Snapshot(operation, message(error));

		if (next.equals(current)) {
			return false;
		}

		current = next;
		return true;
	}

	public void clear() {
		current = null;
	}

	/// 仅清理指定阶段，避免采样成功掩盖仍在生效的准备失败。
	public void clear(String operation) {
		if (current != null && current.operation().equals(operation)) {
			current = null;
		}
	}

	public @Nullable Snapshot current() {
		return current;
	}

	public @Nullable String message() {
		return current == null ? null : current.message();
	}

	private static String message(Throwable error) {
		var message = error.getMessage();

		if (message != null && !message.isBlank()) {
			return message;
		}

		var type = error.getClass().getSimpleName();
		return type.isBlank() ? error.getClass().getName() : type;
	}
}
