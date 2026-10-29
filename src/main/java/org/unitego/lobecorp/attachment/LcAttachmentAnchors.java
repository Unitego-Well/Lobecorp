package org.unitego.lobecorp.attachment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.attachment.adapter.EntityAnimationTarget;

import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class LcAttachmentAnchors {
	/// 绝对世界锚点：直接使用给定世界变换，不依赖实体或动画实例。
	public static ILcAttachmentAnchor world(Level level, LcTransform transform) {
		return custom(level, () -> transform, () -> true);
	}

	/// 自定义来源的代码只在创建侧执行，远端使用同步的最终世界变换。
	public static ILcAttachmentAnchor custom(Level level, Supplier<LcTransform> sampler, BooleanSupplier available) {
		Objects.requireNonNull(level);
		Objects.requireNonNull(sampler);
		Objects.requireNonNull(available);
		return new ILcAttachmentAnchor() {
			@Override
			public Level level() {
				return level;
			}

			@Override
			public LcTransform resolve() {
				return sampler.get();
			}

			@Override
			public LcAnchorReference reference() {
				return new LcAnchorReference(LcAnchorReference.Kind.WORLD, null, 0, null, null);
			}

			@Override
			public boolean isAvailable() {
				return available.getAsBoolean();
			}
		};
	}

	/// 实体局部锚点：采样实体当前姿态并叠加局部偏移；引用携带 UUID 与数字 ID 供远端校验。
	public static ILcAttachmentAnchor entity(Entity entity, LcTransform offset) {
		var source = new EntityAnimationTarget(entity, Set.of());
		return new ILcAttachmentAnchor() {
			@Override
			public Level level() {
				return source.level();
			}

			@Override
			public LcTransform resolve() {
				return source.transform().compose(offset);
			}

			@Override
			public LcAnchorReference reference() {
				return new LcAnchorReference(LcAnchorReference.Kind.ENTITY, entity.getUUID(), entity.getId(), null, null);
			}

			@Override
			public LcTransform offset() {
				return offset;
			}

			@Override
			public boolean isAvailable() {
				return source.isAvailable();
			}
		};
	}
}

