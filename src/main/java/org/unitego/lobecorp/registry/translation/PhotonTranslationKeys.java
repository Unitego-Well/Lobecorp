package org.unitego.lobecorp.registry.translation;

import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslations;
import org.unitego.lobecorp.registry.translation.photon.performance.PhotonPerformanceTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEmitterSpawnerTranslations;
import org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEffectLibraryTranslations;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;

public interface PhotonTranslationKeys {
	String PHOTON_MODEL_ENABLED_KEY = "photon.lobecorp.model.enabled";
	String PHOTON_MODEL_DURATION_KEY = "photon.lobecorp.model.duration";
	String PHOTON_MODEL_START_DELAY_KEY = "photon.lobecorp.model.start_delay";
	String PHOTON_MODEL_LOOPING_KEY = "photon.lobecorp.model.looping";
	String PHOTON_MODEL_PAUSE_KEY = "photon.lobecorp.model.pause";
	String PHOTON_MODEL_CLIP_KEY = "photon.lobecorp.model.clip";
	String PHOTON_MODEL_PLAYBACK_SPEED_KEY = "photon.lobecorp.model.playback_speed";
	String PHOTON_MODEL_ANIMATION_OFFSET_KEY = "photon.lobecorp.model.animation_offset";
	String PHOTON_MODEL_USE_REFERENCE_DIMENSIONS_KEY = "photon.lobecorp.model.use_reference_dimensions";
	String PHOTON_MODEL_LENGTH_KEY = "photon.lobecorp.model.length";
	String PHOTON_MODEL_WIDTH_KEY = "photon.lobecorp.model.width";
	String PHOTON_MODEL_ANCHOR_KEY = "photon.lobecorp.model.anchor";
	String PHOTON_MODEL_OPACITY_KEY = "photon.lobecorp.model.opacity";
	String PHOTON_MODEL_OVERRIDE_RENDERER_KEY = "photon.lobecorp.model.override_renderer";
	public String PHOTON_ANIMATION_TEXT = PhotonAnimationTranslations.PREFIX;
	String PHOTON_EDITOR_TEXT = PhotonEditorTranslations.PREFIX;
	String PHOTON_PERFORMANCE_TEXT = PhotonPerformanceTranslations.PREFIX;
	String PHOTON_EMITTER_SPAWNER_TEXT = PhotonEmitterSpawnerTranslations.PREFIX;
	public String PHOTON_MODEL_TEXT = PhotonModelTranslations.PREFIX;
	public String PHOTON_EFFECT_LIBRARY_TEXT = PhotonEffectLibraryTranslations.TYPE;
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_AXES_KEY = "photon.lobecorp.cycle_time_offset.axes";
	String PHOTON_CYCLE_TIME_OFFSET_AXES = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_AXES_KEY, "Time offset (ticks)", "时间偏移（tick）");
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY = "photon.lobecorp.cycle_time_offset.rotation";
	String PHOTON_CYCLE_TIME_OFFSET_ROTATION = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_ROTATION_KEY, "Rotation time offset (ticks)", "旋转时间偏移（tick）");
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY = "photon.lobecorp.cycle_time_offset.linear";
	String PHOTON_CYCLE_TIME_OFFSET_LINEAR = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY, "Linear time offset (ticks)", "线性时间偏移（tick）");
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY = "photon.lobecorp.cycle_time_offset.orbital";
	String PHOTON_CYCLE_TIME_OFFSET_ORBITAL = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY, "Orbital time offset (ticks)", "轨道时间偏移（tick）");
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY = "photon.lobecorp.cycle_time_offset.center";
	String PHOTON_CYCLE_TIME_OFFSET_CENTER = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY, "Orbital center time offset (ticks)", "轨道中心时间偏移（tick）");
	/// 循环采样的时间偏移；单位 tick。
	String PHOTON_CYCLE_TIME_OFFSET_UV_KEY = "photon.lobecorp.cycle_time_offset.uv";
	String PHOTON_CYCLE_TIME_OFFSET_UV = TranslationRegistry.register(PHOTON_CYCLE_TIME_OFFSET_UV_KEY, "UV time offset (ticks)", "UV 时间偏移（tick）");
	/// 循环采样的 X 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_X_KEY = "photon.lobecorp.cycle_offset.x";
	String PHOTON_CYCLE_OFFSET_X = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_X_KEY, "X time offset (ticks)", "X 时间偏移（tick）");
	/// 循环采样的 Y 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_Y_KEY = "photon.lobecorp.cycle_offset.y";
	String PHOTON_CYCLE_OFFSET_Y = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_Y_KEY, "Y time offset (ticks)", "Y 时间偏移（tick）");
	/// 循环采样的 Z 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_Z_KEY = "photon.lobecorp.cycle_offset.z";
	String PHOTON_CYCLE_OFFSET_Z = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_Z_KEY, "Z time offset (ticks)", "Z 时间偏移（tick）");
	/// 循环采样的 翻滚时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_ROLL_KEY = "photon.lobecorp.cycle_offset.roll";
	String PHOTON_CYCLE_OFFSET_ROLL = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_ROLL_KEY, "Roll time offset (ticks)", "翻滚时间偏移（tick）");
	/// 循环采样的 俯仰时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_PITCH_KEY = "photon.lobecorp.cycle_offset.pitch";
	String PHOTON_CYCLE_OFFSET_PITCH = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_PITCH_KEY, "Pitch time offset (ticks)", "俯仰时间偏移（tick）");
	/// 循环采样的 偏航时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_YAW_KEY = "photon.lobecorp.cycle_offset.yaw";
	String PHOTON_CYCLE_OFFSET_YAW = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_YAW_KEY, "Yaw time offset (ticks)", "偏航时间偏移（tick）");
	/// 循环采样的 轨道 X 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY = "photon.lobecorp.cycle_offset.orbital_x";
	String PHOTON_CYCLE_OFFSET_ORBITAL_X = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_ORBITAL_X_KEY, "Orbital X time offset (ticks)", "轨道 X 时间偏移（tick）");
	/// 循环采样的 轨道 Y 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY = "photon.lobecorp.cycle_offset.orbital_y";
	String PHOTON_CYCLE_OFFSET_ORBITAL_Y = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_ORBITAL_Y_KEY, "Orbital Y time offset (ticks)", "轨道 Y 时间偏移（tick）");
	/// 循环采样的 轨道 Z 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY = "photon.lobecorp.cycle_offset.orbital_z";
	String PHOTON_CYCLE_OFFSET_ORBITAL_Z = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_ORBITAL_Z_KEY, "Orbital Z time offset (ticks)", "轨道 Z 时间偏移（tick）");
	/// 循环采样的 轨道中心 X 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_CENTER_X_KEY = "photon.lobecorp.cycle_offset.center_x";
	String PHOTON_CYCLE_OFFSET_CENTER_X = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_CENTER_X_KEY, "Orbital center X time offset (ticks)", "轨道中心 X 时间偏移（tick）");
	/// 循环采样的 轨道中心 Y 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_CENTER_Y_KEY = "photon.lobecorp.cycle_offset.center_y";
	String PHOTON_CYCLE_OFFSET_CENTER_Y = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_CENTER_Y_KEY, "Orbital center Y time offset (ticks)", "轨道中心 Y 时间偏移（tick）");
	/// 循环采样的 轨道中心 Z 时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_CENTER_Z_KEY = "photon.lobecorp.cycle_offset.center_z";
	String PHOTON_CYCLE_OFFSET_CENTER_Z = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_CENTER_Z_KEY, "Orbital center Z time offset (ticks)", "轨道中心 Z 时间偏移（tick）");
	/// 循环采样的 径向时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_RADIAL_KEY = "photon.lobecorp.cycle_offset.radial";
	String PHOTON_CYCLE_OFFSET_RADIAL = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_RADIAL_KEY, "Radial time offset (ticks)", "径向时间偏移（tick）");
	/// 循环采样的 速度倍率时间偏移 的配置标签。
	String PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY = "photon.lobecorp.cycle_offset.multiplier";
	String PHOTON_CYCLE_OFFSET_MULTIPLIER = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY, "Speed multiplier time offset (ticks)", "速度倍率时间偏移（tick）");
	/// 循环采样的 红色时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_RED_KEY = "photon.lobecorp.cycle_offset.red";
	String PHOTON_CYCLE_OFFSET_RED = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_RED_KEY, "Red time offset (ticks)", "红色时间偏移（tick）");
	/// 循环采样的 绿色时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_GREEN_KEY = "photon.lobecorp.cycle_offset.green";
	String PHOTON_CYCLE_OFFSET_GREEN = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_GREEN_KEY, "Green time offset (ticks)", "绿色时间偏移（tick）");
	/// 循环采样的 蓝色时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_BLUE_KEY = "photon.lobecorp.cycle_offset.blue";
	String PHOTON_CYCLE_OFFSET_BLUE = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_BLUE_KEY, "Blue time offset (ticks)", "蓝色时间偏移（tick）");
	/// 循环采样的 透明度时间偏移（tick） 的配置标签。
	String PHOTON_CYCLE_OFFSET_ALPHA_KEY = "photon.lobecorp.cycle_offset.alpha";
	String PHOTON_CYCLE_OFFSET_ALPHA = TranslationRegistry.register(PHOTON_CYCLE_OFFSET_ALPHA_KEY, "Alpha time offset (ticks)", "透明度时间偏移（tick）");
	String PHOTON_FIXED_RENDER_KEY = "photon.lobecorp.renderer.fixed";
	String PHOTON_FIXED_RENDER = TranslationRegistry.register(PHOTON_FIXED_RENDER_KEY, "Fixed rendering", "修复渲染");
	String PHOTON_QUALITY_EXEMPT_KEY = "photon.lobecorp.quality.exempt";
	String PHOTON_QUALITY_EXEMPT = TranslationRegistry.register(PHOTON_QUALITY_EXEMPT_KEY, "Keep full detail", "始终保持完整精度");
	String PHOTON_PLANAR_LOD_KEY = "photon.lobecorp.quality.planar";
	String PHOTON_PLANAR_LOD = TranslationRegistry.register(PHOTON_PLANAR_LOD_KEY, "Allow planar distant LOD", "允许远处平面简化");
	/// 发射器之间按视线深度重排的原生编辑器开关。
	String PHOTON_VIEW_DEPTH_SORT_KEY = "photon.lobecorp.renderer.view_depth_sort";
	/// 说明新排序模式的启用范围与限制。
	String PHOTON_VIEW_DEPTH_SORT_TIPS_KEY = "photon.lobecorp.renderer.view_depth_sort.tips";
	String PHOTON_VIEW_DEPTH_SORT = TranslationRegistry.register(PHOTON_VIEW_DEPTH_SORT_KEY, "View depth sorting", "视线深度排序");
	String PHOTON_VIEW_DEPTH_SORT_TIPS = TranslationRegistry.register(PHOTON_VIEW_DEPTH_SORT_TIPS_KEY, "Sort enabled emitters with the same Order by camera view depth. Enable on all overlapping emitters. Does not resolve intersecting geometry.", "相同 Order 中开启此选项的发射器按视线深度排序。请在需要互相叠加的发射器上同时开启，无法解决面片相交。");
	/// 渲染器可选的不透明像素深度遮挡组。
	String PHOTON_OCCLUSION_KEY = "photon.lobecorp.renderer.opaque_occlusion";
	/// 原生遮挡组的悬浮说明键。
	String PHOTON_OCCLUSION_TIPS_KEY = PHOTON_OCCLUSION_KEY + ".tooltip";
	/// 参与深度遮挡的有效 Alpha 下限。
	String PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY = PHOTON_OCCLUSION_KEY + ".alpha_cutoff";
	/// 有效 Alpha 遮挡阈值的悬浮说明键。
	String PHOTON_OCCLUSION_ALPHA_CUTOFF_TIPS_KEY = PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY + ".tooltip";
	String PHOTON_OCCLUSION = TranslationRegistry.register(PHOTON_OCCLUSION_KEY, "Opaque pixel occlusion", "不透明像素遮挡");
	String PHOTON_OCCLUSION_ALPHA_CUTOFF = TranslationRegistry.register(PHOTON_OCCLUSION_ALPHA_CUTOFF_KEY, "Occlusion alpha threshold", "遮挡 Alpha 阈值");
	/// 颜色、大小、速度和受力模块的循环开关，与粒子寿命独立。
	String PHOTON_LIFETIME_CYCLE_ENABLED_KEY = "photon.lobecorp.lifetime_cycle.enabled";
	/// 对应模块的曲线完整循环一次的 tick 数。
	String PHOTON_LIFETIME_CYCLE_TICKS_KEY = "photon.lobecorp.lifetime_cycle.ticks";
	String PHOTON_LIFETIME_CYCLE_ENABLED = TranslationRegistry.register(PHOTON_LIFETIME_CYCLE_ENABLED_KEY, "Loop curves", "循环曲线");
	String PHOTON_LIFETIME_CYCLE_TICKS = TranslationRegistry.register(PHOTON_LIFETIME_CYCLE_TICKS_KEY, "Curve cycle (ticks)", "曲线周期（tick）");
	/// 循环采样旋转曲线的编辑器开关，与粒子寿命独立。
	String PHOTON_ROTATION_CYCLE_ENABLED_KEY = "photon.lobecorp.rotation_cycle.enabled";
	/// 旋转曲线完整循环一次的 tick 数。
	String PHOTON_ROTATION_CYCLE_TICKS_KEY = "photon.lobecorp.rotation_cycle.ticks";
	String PHOTON_ROTATION_CYCLE_ENABLED = TranslationRegistry.register(PHOTON_ROTATION_CYCLE_ENABLED_KEY, "Loop rotation", "循环旋转");
	String PHOTON_ROTATION_CYCLE_TICKS = TranslationRegistry.register(PHOTON_ROTATION_CYCLE_TICKS_KEY, "Rotation cycle (ticks)", "旋转周期（tick）");
}
