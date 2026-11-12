package org.unitego.lobecorp.config.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.registry.translation.ClientConfigTranslationKeys;

/// 屏幕粒子的客户端开关、伤害合并及实例容量；服务端只注册配置，不读取客户端值。
public class DamageParticleConfig {

	public static final ModConfigSpec.BooleanValue ENABLED;
	public static final ModConfigSpec.BooleanValue MERGE_ENABLED;
	public static final ModConfigSpec.DoubleValue MERGE_RADIUS;
	public static final ModConfigSpec.IntValue LIFETIME_TICKS;
	public static final ModConfigSpec.IntValue MAX_ACTIVE;
	public static final ModConfigSpec.IntValue POOL_CAPACITY;
	public static final ModConfigSpec.IntValue OUTLINE_COLOR;
	private static final boolean DEFAULT_ENABLED = true;
	private static final boolean DEFAULT_MERGE_ENABLED = true;
	/// 新命中与实例首次命中位置的最大距离，单位为格。
	private static final double DEFAULT_MERGE_RADIUS = 1;
	/// 新建或续期后的剩余寿命，单位为客户端 tick。
	private static final int DEFAULT_LIFETIME_TICKS = 30;
	/// 同时存活的屏幕粒子上限，超限时优先回收最旧实例。
	private static final int DEFAULT_MAX_ACTIVE = 256;
	/// 最多保留的空闲实例壳数量，不限制活跃实例数量。
	private static final int DEFAULT_POOL_CAPACITY = 256;
	/// 数字的 ARGB 描边颜色，零表示关闭描边。
	private static final int DEFAULT_OUTLINE_COLOR = 0xFF000000;

	static {
		synchronized (ClientConfig.class) {
			var builder = ClientConfig.section("damageParticles", ClientConfigTranslationKeys.DAMAGE_PARTICLES);
			builder.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_DISPLAY).push("display");
			ENABLED = builder.comment("启用屏幕粒子。").translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_ENABLED).define("enabled", DEFAULT_ENABLED);
			LIFETIME_TICKS = builder.comment("新建及每次合并后的剩余寿命，单位为客户端 tick。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_LIFETIME_TICKS).defineInRange("lifetimeTicks", DEFAULT_LIFETIME_TICKS, 1, Integer.MAX_VALUE);
			builder.pop().translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_MERGE).push("merge");
			MERGE_ENABLED = builder.comment("在显示寿命内合并来源、目标、伤害类型相同且位于合并半径内的伤害。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_MERGE_ENABLED).define("mergeEnabled", DEFAULT_MERGE_ENABLED);
			MERGE_RADIUS = builder.comment("与首次命中位置的最大合并距离，单位为格；零仅合并同一点。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_MERGE_RADIUS).defineInRange("mergeRadius", DEFAULT_MERGE_RADIUS, 0, Double.MAX_VALUE);
			builder.pop().translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_CAPACITY).push("capacity");
			MAX_ACTIVE = builder.comment("活跃实例上限；超限回收最旧实例，让新效果可见。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_MAX_ACTIVE).defineInRange("maxActive", DEFAULT_MAX_ACTIVE, 1, Integer.MAX_VALUE);
			POOL_CAPACITY = builder.comment("空闲实例壳的保留上限；零表示不保留。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_POOL_CAPACITY).defineInRange("poolCapacity", DEFAULT_POOL_CAPACITY, 0, Integer.MAX_VALUE);
			builder.pop().translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_APPEARANCE).push("appearance");
			OUTLINE_COLOR = builder.comment("数字描边颜色，使用有符号 ARGB 整数；零关闭描边。")
					.translation(ClientConfigTranslationKeys.DAMAGE_PARTICLES_OUTLINE_COLOR).defineInRange("outlineColor", DEFAULT_OUTLINE_COLOR, Integer.MIN_VALUE, Integer.MAX_VALUE);
			builder.pop(2);

		}

	}

	public static void init() {
	}
}
