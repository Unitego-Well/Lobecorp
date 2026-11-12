package org.unitego.lobecorp.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;

/// 服务端资源实际改变后发出；SPIRIT 表示理智，THE_SOUL 表示灵魂。上限变化或直接设置时 source 可为空。
public class LivingResourceChangeEvent extends LivingEvent {
	private final LcDamageType damageType;
	private final float oldValue;
	private final float newValue;
	@Nullable
	private final DamageSource source;

	public LivingResourceChangeEvent(LivingEntity entity, LcDamageType damageType, float oldValue,
	                                 float newValue, @Nullable DamageSource source) {
		super(entity);
		this.damageType = damageType;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.source = source;
	}

	public LcDamageType getDamageType() {
		return damageType;
	}

	public float getOldValue() {
		return oldValue;
	}

	public float getNewValue() {
		return newValue;
	}

	@Nullable
	public DamageSource getSource() {
		return source;
	}
}
