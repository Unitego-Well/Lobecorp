package org.unitego.lobecorp.attachment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.attachment.adapter.EntityAnimationTarget;

import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class LcAttachmentAnchors {
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

