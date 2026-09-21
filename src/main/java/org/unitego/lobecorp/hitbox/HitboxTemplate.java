package org.unitego.lobecorp.hitbox;

import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

/// 判断框的共享只读模板；模板无需注册，也不会参与网络同步。
public class HitboxTemplate {
	private final HitboxSize initialSize;
	private final Predicate<Entity> targetFilter;
	private final IHitboxEffect effect;
	private final HitboxPurpose purpose;

	/// 创建具有固定形状类型的判断框模板。
	///
	/// @param initialSize 实例的初始类型化尺寸
	/// @param targetFilter 每次服务端处理候选目标时执行的默认过滤器
	/// @param effect 候选目标通过几何与遮挡检查后执行的效果
	public HitboxTemplate(HitboxSize initialSize, Predicate<Entity> targetFilter, IHitboxEffect effect) {
		this(initialSize, targetFilter, effect, HitboxPurpose.DAMAGE);
	}

	/// 创建具有固定形状类型和用途的判断框模板。
	public HitboxTemplate(HitboxSize initialSize, Predicate<Entity> targetFilter, IHitboxEffect effect,
			HitboxPurpose purpose) {
		this.initialSize = initialSize;
		this.targetFilter = targetFilter;
		this.effect = effect;
		this.purpose = purpose;
	}

	/// @return 新实例使用的初始尺寸
	public HitboxSize initialSize() {
		return initialSize;
	}

	/// @return 模板固定的形状类型
	public HitboxShapeType shapeType() {
		return initialSize.type();
	}

	/// @return 默认目标过滤器
	public Predicate<Entity> targetFilter() {
		return targetFilter;
	}

	/// @return 服务端命中效果
	public IHitboxEffect effect() {
		return effect;
	}

	/// @return 调试显示和逻辑分类使用的用途
	public HitboxPurpose purpose() {
		return purpose;
	}
}
