package org.unitego.lobecorp.attachment;

import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.util.attachment.LcAttachmentDebugUtil;
import org.unitego.lobecorp.util.attachment.LcAttachmentDebugUtil.Event;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/// 绑定只改变变换来源；解除绑定不移除目标，也不结束目标自己的动画或寿命。
/// 世界引用由来源与目标借出，绑定只负责释放自身引用，不关闭世界。
@SuppressWarnings("resource")
public class LcAttachmentBinding {
	private static final String DIFFERENT_LEVEL = "Attachment anchor and target must belong to the same level";
	private static final String UNAVAILABLE = "Attachment anchor or target is unavailable";
	private static final String RELEASED = "Attachment binding has been released";
	private static final String SINGULAR_TRANSFORM = "Attachment continuity requires invertible transforms";
	private final UUID id = UUID.randomUUID();
	private final Level level;
	private final ILcTransformTarget target;
	private ILcAttachmentAnchor anchor;
	private LcTransform correction = LcTransform.identity();
	private Supplier<LcTransform> trajectory = LcTransform::identity;
	private LcTransform sampledTrajectory = LcTransform.identity();
	private @Nullable LcRenderAnchorReference renderAnchor;
	private LcTransform visualOffset = LcTransform.identity();
	private long revision;
	private boolean preserveWorldTransform;
	private boolean released;

	public LcAttachmentBinding(ILcTransformTarget target, ILcAttachmentAnchor anchor) {
		this.target = Objects.requireNonNull(target);
		this.level = target.level();
		this.anchor = Objects.requireNonNull(anchor);
		requireSource(anchor);
		LcAttachments.register(this);
		LcAttachmentDebugUtil.binding(Event.BOUND, this);
	}

	public UUID id() {
		return id;
	}

	public boolean isReleased() {
		return released;
	}

	public ILcTransformTarget target() {
		return target;
	}

	public ILcAttachmentAnchor anchor() {
		return anchor;
	}

	/// 轨迹由调用方自己的时钟采样，通用绑定不创建或推进动画。
	public void setTrajectory(Supplier<LcTransform> sampler) {
		requireActive();
		trajectory = Objects.requireNonNull(sampler);
		revision++;
	}

	public void setRenderAnchor(@Nullable LcRenderAnchorReference source) {
		setRenderAnchor(source, LcTransform.identity());
	}

	/// 视觉局部偏移相对最终渲染节点，不重复叠加逻辑骨骼或实体锚点偏移。
	public void setRenderAnchor(@Nullable LcRenderAnchorReference source, LcTransform offset) {
		requireActive();
		renderAnchor = source;
		visualOffset = Objects.requireNonNull(offset);
		revision++;
		LcAttachmentDebugUtil.binding(Event.RENDER_SOURCE_CHANGED, this);
	}

	public void switchAnchor(ILcAttachmentAnchor next, boolean preserveWorldTransform) {
		requireActive();
		requireSource(next);
		var nextCorrection = LcTransform.identity();
		if (preserveWorldTransform) {
			var sampled = trajectory.get();
			var nextMatrix = next.resolve().matrix();
			var localMatrix = sampled.matrix();
			if (!Double.isFinite(nextMatrix.determinant()) || nextMatrix.determinant() == 0
					|| !Double.isFinite(localMatrix.determinant()) || localMatrix.determinant() == 0) {
				throw new IllegalArgumentException(SINGULAR_TRANSFORM);
			}

			nextCorrection = LcTransform.fromMatrix(nextMatrix.invert()
					.mul(target.transform().matrix()).mul(localMatrix.invert()));
		}

		correction = nextCorrection;
		anchor = next;
		this.preserveWorldTransform = preserveWorldTransform;
		revision++;
		LcAttachmentDebugUtil.binding(Event.ANCHOR_CHANGED, this);
	}

	public void release() {
		if (released) {
			return;
		}

		released = true;
		LcAttachments.unregister(this);
		LcAttachmentDebugUtil.binding(Event.RELEASED, this);
	}

	public void update() {
		if (released) {
			return;
		}

		if (target.level() != level || anchor.level() != level || !target.isAvailable() || !anchor.isAvailable()) {
			release();
			return;
		}

		var sampled = trajectory.get();
		if (released || !target.isAvailable() || !anchor.isAvailable()
				|| target.level() != level || anchor.level() != level) {
			release();
			return;
		}

		var world = anchor.resolve().compose(correction).compose(sampled);
		if (released) {
			return;
		}

		sampledTrajectory = sampled;
		target.applyTransform(world);
	}

	public LcAttachmentSnapshot snapshot() {
		var offset = anchor.offset().compose(correction).compose(sampledTrajectory);
		var renderOffset = visualOffset.compose(correction).compose(sampledTrajectory);
		return new LcAttachmentSnapshot(id, anchor.reference(), offset, target.transform(), renderAnchor,
				revision, preserveWorldTransform, renderOffset);
	}

	private void requireActive() {
		if (released) {
			throw new IllegalStateException(RELEASED);
		}
	}

	private void requireSource(ILcAttachmentAnchor source) {
		if (source.level() != level || target.level() != level) {
			throw new IllegalArgumentException(DIFFERENT_LEVEL);
		}

		if (!source.isAvailable() || !target.isAvailable()) {
			throw new IllegalArgumentException(UNAVAILABLE);
		}
	}
}
