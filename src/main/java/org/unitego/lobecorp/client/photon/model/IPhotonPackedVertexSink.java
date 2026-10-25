package org.unitego.lobecorp.client.photon.model;

/// 向当前 Photon 作业借读已打包的顶点数组；实现不得保存数组或接管其生命周期。
public interface IPhotonPackedVertexSink {
	/// 返回 false 时尚未预留或写入目标缓冲，可安全改走逐顶点提交。
	/// 预留开始后的异常直接传播，调用方不得在同一作业继续回退写入。
	@SuppressWarnings("UnnecessaryModifier")
	public boolean lobecorp$tryAppendPhotonSnapshot(byte[] packed, int vertexCount);
}
