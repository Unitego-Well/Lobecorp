package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.RotationOverLifetimeSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonRotationCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.util.TranslationKeys;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;

@Mixin(RotationOverLifetimeSetting.class)
public abstract class RotationOverLifetimeSettingMixin implements PhotonRotationCycleAccess, PhotonCycleOffsetAccess, IToggleConfigurable {
	/**
	 * 默认一个游戏秒完成一次曲线；单位 tick，可在编辑器调整。
	 */
	@Unique
	private static final int DEFAULT_ROTATION_CYCLE_TICKS = 20;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_ROTATION_CYCLE_ENABLED_KEY)
	private boolean lobecorp$rotationCycleEnabled;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_ROTATION_CYCLE_TICKS_KEY)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	private int lobecorp$rotationCycleTicks = DEFAULT_ROTATION_CYCLE_TICKS;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetRoll;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetPitch;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetYaw;

	@Override
	public boolean lobecorp$isRotationCycleEnabled() {
		return this.lobecorp$rotationCycleEnabled;
	}

	@Override
	public int lobecorp$getRotationCycleTicks() {
		return this.lobecorp$rotationCycleTicks;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		IToggleConfigurable.super.buildConfigurator(father);
		father.addConfigurators(PhotonLifetimeCycleUtil.offsetConfigurator(TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY,
				() -> new NumberFunction3(this.lobecorp$cycleOffsetYaw, this.lobecorp$cycleOffsetPitch, this.lobecorp$cycleOffsetRoll),
				value -> {
					this.lobecorp$cycleOffsetYaw = value.x.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetPitch = value.y.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetRoll = value.z.get(0, () -> 0f).floatValue();
				}, this));
	}

	@Override
	public float lobecorp$getCycleOffset(Component component) {
		return switch (component) {
			case ROLL -> this.lobecorp$cycleOffsetRoll;
			case PITCH -> this.lobecorp$cycleOffsetPitch;
			case YAW -> this.lobecorp$cycleOffsetYaw;
			default -> 0;
		};
	}
}
