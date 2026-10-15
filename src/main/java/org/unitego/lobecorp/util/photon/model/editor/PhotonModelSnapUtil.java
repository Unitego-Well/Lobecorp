package org.unitego.lobecorp.util.photon.model.editor;

public class PhotonModelSnapUtil {
	/// 模型平移的三个精度，单位为模型坐标。
	private static final float MODEL_SHIFT_STEP = 0.1f;
	private static final float MODEL_CONTROL_STEP = 0.05f;
	private static final float MODEL_FINE_STEP = 0.01f;
	/// UV 平移的三个精度，单位为纹理像素。
	private static final float PIXEL_SHIFT_STEP = 1f;
	private static final float PIXEL_CONTROL_STEP = 0.5f;
	private static final float PIXEL_FINE_STEP = 0.1f;

	public static float modelStep(boolean shift, boolean control) {
		return shift ? control ? MODEL_FINE_STEP : MODEL_SHIFT_STEP : control ? MODEL_CONTROL_STEP : 0;
	}

	public static float pixelStep(boolean shift, boolean control) {
		return shift ? control ? PIXEL_FINE_STEP : PIXEL_SHIFT_STEP : control ? PIXEL_CONTROL_STEP : 0;
	}

	public static float snap(float value, float step) {
		return step > 0 ? Math.round(value / step) * step : value;
	}
}
