package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.data.ToggleGroup;
import org.unitego.lobecorp.util.TranslationKeys;

public class PhotonOcclusionConfig extends ToggleGroup {
	/// 仅接近不透明的像素写入遮挡深度；Alpha 无单位，范围 0 到 1。
	public static final float DEFAULT_ALPHA_CUTOFF = 0.99f;

	@Configurable(name = TranslationKeys.PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY,
			tips = TranslationKeys.PHOTON_OCCLUSION_ALPHA_CUTOFF_TIPS_KEY)
	@ConfigNumber(range = {0, 1})
	protected float alphaCutoff = DEFAULT_ALPHA_CUTOFF;

	public float getAlphaCutoff() {
		return normalizeAlphaCutoff(alphaCutoff);
	}

	public void setAlphaCutoff(float alphaCutoff) {
		this.alphaCutoff = normalizeAlphaCutoff(alphaCutoff);
	}

	@Override
	public void afterDeserialize() {
		setAlphaCutoff(alphaCutoff);
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof PhotonOcclusionConfig that) || isEnable() != that.isEnable()) {
			return false;
		}

		return !isEnable() || Float.compare(getAlphaCutoff(), that.getAlphaCutoff()) == 0;
	}

	@Override
	public int hashCode() {
		return isEnable() ? Float.hashCode(getAlphaCutoff()) : 0;
	}

	private static float normalizeAlphaCutoff(float value) {
		if (!Float.isFinite(value)) {
			return DEFAULT_ALPHA_CUTOFF;
		}

		if (value <= 0f) {
			return 0f;
		}

		return Math.clamp(value, 0f, 1f);
	}
}
