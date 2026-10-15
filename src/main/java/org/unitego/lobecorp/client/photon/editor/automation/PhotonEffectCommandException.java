package org.unitego.lobecorp.client.photon.editor.automation;

public class PhotonEffectCommandException extends RuntimeException {
	public enum Code {
		INVALID_ARGUMENT("参数类型、取值或路径无效。"),
		NO_PROJECT("没有可用的特效工程，请先创建工程或打开编辑器。"),
		WORLD_REQUIRED("请先进入 Minecraft 世界，再创建特效工程。"),
		PROJECT_CLOSED("工程已关闭或被替换，请重新读取状态。"),
		REVISION_CONFLICT("工程已发生变化，请读取最新状态后重试。"),
		UNSUPPORTED_OPERATION("此工程类型不支持该操作。"),
		INVALID_DATA("数据无法按当前原生格式完整读取。"),
		PATH_FORBIDDEN("目标必须位于允许的特效资源目录内。"),
		FILE_EXISTS("目标文件已存在，请明确指定 overwrite。"),
		FILE_NOT_FOUND("资源路径不是现有的普通文件。"),
		FILE_LOCKED("目标包正在被资源管理器使用，请释放后重试。"),
		CLIENT_TIMEOUT("客户端未及时执行，排队命令已取消。"),
		CONTROL_REQUIRED("写入操作需要保持 MCP 接管模式。"),
		INTERNAL_ERROR("特效命令执行失败。");

		private final String message;

		@SuppressWarnings("UnnecessaryModifier")
		private Code(String message) {
			this.message = message;
		}
	}

	private final Code code;

	public PhotonEffectCommandException(Code code) {
		super(code.message);
		this.code = code;
	}

	public PhotonEffectCommandException(Code code, String detail) {
		super(code.message + " " + detail);
		this.code = code;
	}

	public String code() {
		return code.name();
	}
}
