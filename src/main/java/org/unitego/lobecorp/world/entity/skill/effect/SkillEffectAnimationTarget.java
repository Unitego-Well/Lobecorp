package org.unitego.lobecorp.world.entity.skill.effect;

import net.minecraft.world.level.Level;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.Objects;

/// 只驱动轻量效果已有的位置，实例寿命、来源和技能绑定仍由效果系统维护。
public class SkillEffectAnimationTarget implements ILcTransformTarget {
	private final EntitySkillEffect effect;

	public SkillEffectAnimationTarget(EntitySkillEffect effect) {
		this.effect = Objects.requireNonNull(effect);
	}

	@Override
	public Object identity() {
		return effect;
	}

	@Override
	public Level level() {
		return effect.level();
	}

	@Override
	public LcTransform transform() {
		var identity = LcTransform.identity();
		return new LcTransform(effect.position(), identity.rotation(), identity.scale());
	}

	@Override
	public void applyTransform(LcTransform transform) {
		effect.setPosition(transform.position());
	}

	@Override
	public boolean isAvailable() {
		return !effect.isRemoved();
	}
}
