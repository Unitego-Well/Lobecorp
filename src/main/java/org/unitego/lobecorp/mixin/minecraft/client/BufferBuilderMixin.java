package org.unitego.lobecorp.mixin.minecraft.client;

import com.lowdragmc.photon.client.render.PhotonPipelines;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.model.IPhotonPackedVertexSink;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements IPhotonPackedVertexSink {
	/// 仅包含 Photon 原生位置、颜色、UV0、UV2 与法线元素，排除额外顶点属性。
	@Unique
	private static final int LOBECORP_PHOTON_ELEMENTS_MASK = 0x37;
	@Shadow
	@Final
	private ByteBufferBuilder buffer;
	@Shadow
	private long vertexPointer;
	@Shadow
	private int vertices;
	@Shadow
	@Final
	private VertexFormat format;
	@Shadow
	@Final
	private VertexFormat.Mode mode;
	@Shadow
	@Final
	private int vertexSize;
	@Shadow
	@Final
	private int initialElementsToFill;
	@Shadow
	@Final
	private int[] offsetsByElement;
	@Shadow
	private int elementsToFill;
	@Shadow
	private boolean building;

	// Mixin 将本方法合并到目标类；源码类型不能代表运行时接收者。
	@Override
	@Unique
	@SuppressWarnings("ConstantValue")
	public boolean lobecorp$tryAppendPhotonSnapshot(byte[] packed, int vertexCount) {
		if (!Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY)
				|| ((Object) this).getClass() != BufferBuilder.class
				|| !building || vertices != 0 || vertexPointer != -1L || elementsToFill != 0
				|| mode != VertexFormat.Mode.QUADS || format != PhotonPipelines.PARTICLE_FORMAT
				|| format.getElementsMask() != LOBECORP_PHOTON_ELEMENTS_MASK
				|| !PhotonPackedVertexUtil.supportsLayout(vertexSize, initialElementsToFill, offsetsByElement)
				|| !PhotonPackedVertexUtil.validPayload(packed, vertexCount)) {
			return false;
		}

		long pointer = buffer.reserve(packed.length);
		MemoryUtil.memCopy(packed, pointer);
		vertices = vertexCount;
		vertexPointer = pointer + packed.length - vertexSize;
		elementsToFill = 0;
		return true;
	}
}
