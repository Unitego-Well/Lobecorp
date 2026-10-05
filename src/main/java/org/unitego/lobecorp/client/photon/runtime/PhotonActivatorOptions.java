package org.unitego.lobecorp.client.photon.runtime;

import net.minecraft.nbt.CompoundTag;

public record PhotonActivatorOptions(boolean startControl, StartBehavior startBehavior, boolean endControl,
                                     EndBehavior endBehavior, boolean clearOnRestart) {
	/**
	 * 激活轨道默认进入时重启并清理粒子、离开时停止更新并隐藏。
	 */
	public static final PhotonActivatorOptions DEFAULT = new PhotonActivatorOptions(true, StartBehavior.RESTART, true, EndBehavior.ORIGINAL, true);
	/**
	 * 激活轨道的扩展数据协议键。
	 */
	public static final String TAG = "lobecorpActivation";

	public enum StartBehavior {RESTART, CONTINUE}

	public enum EndBehavior {ORIGINAL, STOP_EMISSION, CLEAR}

	public CompoundTag write() {
		var tag = new CompoundTag();
		tag.putBoolean("startControl", startControl);
		tag.putString("startBehavior", startBehavior.name());
		tag.putBoolean("endControl", endControl);
		tag.putString("endBehavior", endBehavior.name());
		tag.putBoolean("clearOnRestart", clearOnRestart);
		return tag;
	}

	public static PhotonActivatorOptions read(CompoundTag tag) {
		return new PhotonActivatorOptions(tag.getBooleanOr("startControl", DEFAULT.startControl),
				readEnum(StartBehavior.class, tag.getStringOr("startBehavior", ""), DEFAULT.startBehavior),
				tag.getBooleanOr("endControl", DEFAULT.endControl),
				readEnum(EndBehavior.class, tag.getStringOr("endBehavior", ""), DEFAULT.endBehavior),
				tag.getBooleanOr("clearOnRestart", DEFAULT.clearOnRestart));
	}

	private static <T extends Enum<T>> T readEnum(Class<T> type, String name, T fallback) {
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException exception) {
			return fallback;
		}
	}
}
