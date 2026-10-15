package org.unitego.lobecorp.animation.attachment;

import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.attachment.ILcAttachmentAnchor;
import org.unitego.lobecorp.attachment.LcAnchorReference;
import org.unitego.lobecorp.attachment.LcTransform;

/// 动画只是通用附着的一种来源；播放结束后该来源失效。
public record LcAnimationAnchor(LcAnimationInstance instance, String bone, @Nullable String locator,
                                LcTransform offset) implements ILcAttachmentAnchor {
	public static LcAnimationAnchor bone(LcAnimationInstance instance, String bone, LcTransform offset) {
		return new LcAnimationAnchor(instance, bone, null, offset);
	}

	public static LcAnimationAnchor locator(LcAnimationInstance instance, String bone, String locator, LcTransform offset) {
		return new LcAnimationAnchor(instance, bone, locator, offset);
	}

	@Override
	public Level level() {
		return instance.level();
	}

	@Override
	public LcTransform resolve() {
		var transform = locator == null ? instance.boneTransform(bone) : instance.locatorTransform(bone, locator);
		return transform.compose(offset);
	}

	@Override
	public LcAnchorReference reference() {
		return new LcAnchorReference(LcAnchorReference.Kind.ANIMATION, instance.id(), 0, bone, locator);
	}

	@Override
	public boolean isAvailable() {
		return !instance.isStopped() && instance.ownerTarget().isAvailable();
	}
}
