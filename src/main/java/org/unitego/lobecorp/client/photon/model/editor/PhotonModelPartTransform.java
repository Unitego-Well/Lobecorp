package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.math.ITransform;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

/// 将部件轴心变换接入原生 Gizmo，父矩阵保留非均匀缩放产生的剪切，旋转独立沿父级四元数链换算。
public class PhotonModelPartTransform implements ITransform {

	private final Vector3f pivot;
	private final Vector3f localPosition;
	private final Quaternionf localRotation;
	private final Vector3f localScale;
	private final Matrix4f parentToWorld;
	private final Matrix4f worldToParent;
	private final Quaternionf parentRotation;
	private final Quaternionf inverseParentRotation;

	public PhotonModelPartTransform(PhotonModelAsset.Part part, Matrix4f parentToWorld, Quaternionf parentRotation) {
		pivot = new Vector3f(part.pivot);
		localPosition = new Vector3f(pivot).add(part.transform.translation);
		localRotation = new Quaternionf(part.transform.rotation);
		localScale = new Vector3f(part.transform.scale);
		this.parentToWorld = new Matrix4f(parentToWorld);
		worldToParent = new Matrix4f(parentToWorld).invert();

		if (!worldToParent.isFinite()) {
			throw new IllegalArgumentException(Component.translatable(PhotonModelTextUtil.key("editor.singular_part_transform")).getString());
		}

		this.parentRotation = new Quaternionf(parentRotation).normalize();
		inverseParentRotation = new Quaternionf(this.parentRotation).invert();
	}

	@Override
	public Vector3f localPosition() {
		return localPosition;
	}

	@Override
	public Quaternionf localRotation() {
		return localRotation;
	}

	@Override
	public Vector3f localScale() {
		return localScale;
	}

	@Override
	public void localPosition(Vector3f localPosition) {
		this.localPosition.set(localPosition);
	}

	@Override
	public void localRotation(Quaternionf localRotation) {
		this.localRotation.set(localRotation);
	}

	@Override
	public void localScale(Vector3f localScale) {
		this.localScale.set(localScale);
	}

	@Override
	public Vector3f position() {
		return parentToWorld.transformPosition(new Vector3f(localPosition));
	}

	@Override
	public Quaternionf rotation() {
		return new Quaternionf(parentRotation).mul(localRotation).normalize();
	}

	@Override
	public void position(Vector3f position) {
		worldToParent.transformPosition(position, localPosition);
	}

	@Override
	public void rotation(Quaternionf rotation) {
		inverseParentRotation.mul(rotation, localRotation).normalize();
	}

	/// 轴心在一次拖动中保持固定，只回写部件 TRS，不烘焙顶点或改变父级数据。
	public void applyTo(PhotonModelAsset.Part part) {
		part.transform.translation.set(localPosition).sub(pivot);
		part.transform.rotation.set(localRotation).normalize();
		part.transform.scale.set(localScale);
	}
}
