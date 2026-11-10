package org.unitego.lobecorp.registry.translation;

import static org.unitego.lobecorp.registry.translation.TranslationRegistry.register;

public interface ClientConfigTranslationKeys {
	String HUD_STYLE_PREFIX = "lobecorp.configuration.client.hud.style.";
	String CORPSE_BATCH = register("lobecorp.configuration.client.corpseBatch", "Batch frozen corpse models", "批量绘制冻结尸体");
	String HUD_STYLE_DEFAULT = register("lobecorp.configuration.client.hud.style.default", "Default", "默认");
	String HUD_STYLE_VANILLA = register("lobecorp.configuration.client.hud.style.vanilla", "Vanilla", "原版");
	String HUD_STYLE_NEW = register("lobecorp.configuration.client.hud.style.new", "new", "new");
	String HUD = register("lobecorp.configuration.client.hud", "HUD", "界面");
	String HUD_STATUS_BARS = register("lobecorp.configuration.client.hud.statusBars", "Status bars", "状态条");
	String HUD_HEALTH_STYLE = register("lobecorp.configuration.client.hud.healthStyle", "Health bar style", "生命值样式");
	String HUD_RATIONALITY_STYLE = register("lobecorp.configuration.client.hud.rationalityStyle", "Rationality bar style", "理智条样式");
	String HUD_SHIELD_STYLE = register("lobecorp.configuration.client.hud.shieldStyle", "Shield bar style", "护盾条样式");
	String HUD_EVENTS = register("lobecorp.configuration.client.hud.events", "Events", "事件");
	String HUD_EVENTS_MIDNIGHT_CLEANUP = register("lobecorp.configuration.client.hud.events.midnight_cleanup", "Midnight cleanup", "深夜清扫");
	String HUD_TIMER_STYLE = register("lobecorp.configuration.client.hud.timerStyle", "Timer style", "计时器样式");
	String HUD_FILTERS = register("lobecorp.configuration.client.hud.filters", "Screen filters", "屏幕滤镜");
	String HUD_ENABLE_LOW_RATIONALITY_FILTER = register("lobecorp.configuration.client.hud.enable_low_rationality_filter", "Low rationality filter", "低理智滤镜");
	String HUD_ENABLE_LOBOTOMY_CORPORATION_DAMAGE_FILTER = register("lobecorp.configuration.client.hud.enable_lobotomy_corporation_damage_filter", "Damage color filter", "受击四色滤镜");
	String EVENTS = register("lobecorp.configuration.client.events", "Events", "事件");
	String EVENTS_MIDNIGHT_CLEANUP = register("lobecorp.configuration.client.events.midnight_cleanup", "Midnight cleanup", "深夜清扫");
	String EVENTS_MIDNIGHT_CLEANUP_WAVE = register("lobecorp.configuration.client.events.midnight_cleanup.wave", "Wave", "浪潮");
	String HORDE_DISPLAY = register("lobecorp.configuration.client.horde.display", "Display", "显示");
	String HORDE_SOUND = register("lobecorp.configuration.client.horde.sound", "Sound", "声音");
	String HORDE_PARTICLES = register("lobecorp.configuration.client.horde.particles", "Particles", "粒子");
	String HORDE_DEBUG = register("lobecorp.configuration.client.horde.debug", "Debug", "调试");
	String DAMAGE_PARTICLES = register("lobecorp.configuration.client.damageParticles", "Damage particles", "伤害粒子");
	String DAMAGE_PARTICLES_DISPLAY = register("lobecorp.configuration.client.damageParticles.display", "Display", "显示");
	String DAMAGE_PARTICLES_ENABLED = register("lobecorp.configuration.client.damageParticles.enabled", "Enabled", "启用");
	String DAMAGE_PARTICLES_LIFETIME_TICKS = register("lobecorp.configuration.client.damageParticles.lifetimeTicks", "Lifetime in ticks", "显示寿命（刻）");
	String DAMAGE_PARTICLES_MERGE = register("lobecorp.configuration.client.damageParticles.merge", "Merging", "合并");
	String DAMAGE_PARTICLES_MERGE_ENABLED = register("lobecorp.configuration.client.damageParticles.mergeEnabled", "Merge damage", "合并伤害");
	String DAMAGE_PARTICLES_MERGE_RADIUS = register("lobecorp.configuration.client.damageParticles.mergeRadius", "Merge radius", "合并半径");
	String DAMAGE_PARTICLES_CAPACITY = register("lobecorp.configuration.client.damageParticles.capacity", "Capacity", "容量");
	String DAMAGE_PARTICLES_MAX_ACTIVE = register("lobecorp.configuration.client.damageParticles.maxActive", "Maximum active particles", "活跃粒子上限");
	String DAMAGE_PARTICLES_POOL_CAPACITY = register("lobecorp.configuration.client.damageParticles.poolCapacity", "Pool capacity", "空闲池容量");
	String DAMAGE_PARTICLES_APPEARANCE = register("lobecorp.configuration.client.damageParticles.appearance", "Appearance", "外观");
	String DAMAGE_PARTICLES_OUTLINE_COLOR = register("lobecorp.configuration.client.damageParticles.outlineColor", "Outline ARGB color", "描边ARGB颜色");
	String VISUAL_QUALITY = register("lobecorp.configuration.client.visualQuality", "Visual quality", "视觉质量");
	String VISUAL_QUALITY_ANIMATIONS = register("lobecorp.configuration.client.visualQuality.animations", "Animations", "动画");
	String VISUAL_QUALITY_ENABLED = register("lobecorp.configuration.client.visualQuality.enabled", "Enabled", "启用");
	String VISUAL_QUALITY_NEAR_DISTANCE = register("lobecorp.configuration.client.visualQuality.nearDistance", "Near distance", "近距离阈值");
	String VISUAL_QUALITY_FAR_DISTANCE = register("lobecorp.configuration.client.visualQuality.farDistance", "Far distance", "远距离阈值");
	String VISUAL_QUALITY_MEDIUM_SAMPLE_FPS = register("lobecorp.configuration.client.visualQuality.mediumSampleFps", "Medium sample FPS", "中距离采样帧率");
	String VISUAL_QUALITY_FAR_SAMPLE_FPS = register("lobecorp.configuration.client.visualQuality.farSampleFps", "Far sample FPS", "远距离采样帧率");
	String VISUAL_QUALITY_EXEMPT_ENTITIES = register("lobecorp.configuration.client.visualQuality.exemptEntities", "Exempt entities", "排除的实体");
	String VISUAL_QUALITY_EXEMPT_CONTROLLERS = register("lobecorp.configuration.client.visualQuality.exemptControllers", "Exempt controllers", "排除的控制器");
	String VISUAL_QUALITY_MODELS = register("lobecorp.configuration.client.visualQuality.models", "Models", "模型");
	String VISUAL_QUALITY_MEDIUM_CUBE_DETAIL_SIZE = register("lobecorp.configuration.client.visualQuality.mediumCubeDetailSize", "Medium detail size", "中距离细节尺寸");
	String VISUAL_QUALITY_FAR_CUBE_DETAIL_SIZE = register("lobecorp.configuration.client.visualQuality.farCubeDetailSize", "Far detail size", "远距离细节尺寸");
	String VISUAL_QUALITY_MINIMUM_ENTITY_CUBES = register("lobecorp.configuration.client.visualQuality.minimumEntityCubes", "Minimum cube count", "最小立方体数量");
	String VISUAL_QUALITY_EXEMPT_MODELS = register("lobecorp.configuration.client.visualQuality.exemptModels", "Exempt models", "排除的模型");
	String VISUAL_QUALITY_CACHE = register("lobecorp.configuration.client.visualQuality.cache", "Cache", "缓存");
	String VISUAL_QUALITY_CACHE_ENTRIES = register("lobecorp.configuration.client.visualQuality.cacheEntries", "Cache entries", "缓存条目上限");
	String VISUAL_QUALITY_CACHE_MI_B = register("lobecorp.configuration.client.visualQuality.cacheMiB", "Cache budget in MiB", "缓存预算（MiB）");
	String VISUAL_QUALITY_MAX_BAKES_PER_FRAME = register("lobecorp.configuration.client.visualQuality.maxBakesPerFrame", "Bakes per frame", "每帧烘焙上限");
	String VISUAL_QUALITY_DISTANCE_HYSTERESIS = register("lobecorp.configuration.client.visualQuality.distanceHysteresis", "Distance hysteresis", "距离迟滞");

	static String path(String key) {
		return "lobecorp.configuration.client." + key;
	}
}
