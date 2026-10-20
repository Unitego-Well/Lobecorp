package org.unitego.lobecorp.config.photon.playback;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;

import java.util.Objects;

/// 区间两端与单边命令使用同一份协议；没有扩展字段的原生轨道保持原生行为。
public record PhotonActivationOptions(boolean startControl, StartBehavior startBehavior, boolean endControl,
                                      EndBehavior endBehavior, boolean clearOnRestart) {
	public static final String TAG = "lobecorpActivation";
	public static final PhotonActivationOptions NATIVE = new PhotonActivationOptions(true, StartBehavior.CONTINUE, true, EndBehavior.ORIGINAL, true);
	public static final PhotonActivationOptions LEGACY = new PhotonActivationOptions(true, StartBehavior.RESTART, true, EndBehavior.ORIGINAL, true);

	public PhotonActivationOptions {
		Objects.requireNonNull(startBehavior);
		Objects.requireNonNull(endBehavior);
	}

	public enum StartBehavior {RESTART, CONTINUE}

	public enum EndBehavior {ORIGINAL, STOP_EMISSION, CLEAR}

	public boolean pointControl() {
		return startControl != endControl;
	}

	public CompoundTag merge(CompoundTag original) {
		var result = original.copy();
		result.remove("singleFrame");
		result.putBoolean("startControl", startControl);
		result.putString("startBehavior", startBehavior.name());
		result.putBoolean("endControl", endControl);
		result.putString("endBehavior", endBehavior.name());
		result.putBoolean("clearOnRestart", clearOnRestart);
		return result;
	}

	public static PhotonActivationOptions read(CompoundTag source) {
		for (var key : new String[]{"startControl", "endControl", "clearOnRestart", "singleFrame"}) {
			if (source.contains(key) && !(source.get(key) instanceof ByteTag)) {
				throw new IllegalArgumentException("激活开关类型无效：" + key);
			}
		}

		for (var key : new String[]{"startBehavior", "endBehavior"}) {
			if (source.contains(key) && !(source.get(key) instanceof StringTag)) {
				throw new IllegalArgumentException("激活行为类型无效：" + key);
			}
		}

		boolean single = source.getBooleanOr("singleFrame", false);
		return new PhotonActivationOptions(single || source.getBooleanOr("startControl", LEGACY.startControl),
				StartBehavior.valueOf(source.getStringOr("startBehavior", LEGACY.startBehavior.name())),
				!single && source.getBooleanOr("endControl", LEGACY.endControl),
				EndBehavior.valueOf(source.getStringOr("endBehavior", LEGACY.endBehavior.name())),
				source.getBooleanOr("clearOnRestart", LEGACY.clearOnRestart));
	}
}
