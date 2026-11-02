package org.unitego.lobecorp.client.photon.model;

import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;

/// 单个模型输出独占的紧凑局部姿态；数组不外泄，渲染只通过只读属性读取。
public final class PhotonModelPoseBuffer implements PhotonModelGeometryUtil.LocalPoseView, PhotonModelPoseSampler.IPrimitivePoseSink {
	private static final int STRIDE = 8;

	private PhotonModelTopologyUtil.Prepared prepared;
	private PhotonModelTopologyUtil.Prepared beginPrepared;
	private PhotonModelPoseSampler.Frame frame;
	private float opacity;
	private Vector3 min = Vector3.ZERO;
	private Vector3 max = Vector3.ZERO;
	private PhotonModelPoseSampler.Timings timings;
	private float candidateOpacity;
	private Vector3 candidateMin = Vector3.ZERO;
	private Vector3 candidateMax = Vector3.ZERO;
	private PhotonModelPoseSampler.Timings candidateTimings;
	private float[][] attributes = new float[0][];
	private float[] groupOpacities = new float[0];
	private float[] candidateGroupOpacities = new float[0];
	private int[] cornerCounts = new int[0];
	private int activeGroup = -1;
	private int activeCorner;
	private boolean complete;
	private boolean failed;
	private boolean published;

	public void clear() {
		prepared = null;
		beginPrepared = null;
		frame = null;
		opacity = 0;
		timings = null;
		min = Vector3.ZERO;
		max = Vector3.ZERO;
		candidateOpacity = 0;
		candidateMin = Vector3.ZERO;
		candidateMax = Vector3.ZERO;
		candidateTimings = null;
		activeGroup = -1;
		activeCorner = 0;
		complete = false;
		failed = false;
		published = false;
		Arrays.fill(groupOpacities, 0);
		Arrays.fill(candidateGroupOpacities, 0);
		Arrays.fill(cornerCounts, 0);
	}

	/// 已发布帧的失败回滚不能抹掉仍在使用的上一帧；失败只影响本次候选。
	public void failCandidate() {
		complete = false;
		failed = true;
		published = frame != null && prepared != null;
		beginPrepared = prepared;
		activeGroup = -1;
	}

	@Nullable
	public PhotonModelPoseSampler.Frame frame() {
		if (frame != null) {
			return frame;
		}

		if (!complete || failed || prepared == null) {
			return null;
		}

		var groups = new ArrayList<PhotonModelPoseSampler.Group>(attributes.length);

		for (int group = 0; group < attributes.length; group++) {
			var vertices = new ArrayList<PhotonModelPoseSampler.Vertex>(cornerCounts[group]);
			var source = attributes[group];

			for (int corner = 0; corner < cornerCounts[group]; corner++) {
				int offset = corner * STRIDE;
				vertices.add(new PhotonModelPoseSampler.Vertex(
						new Vector3(source[offset], source[offset + 1], source[offset + 2]),
						new Vector3(source[offset + 3], source[offset + 4], source[offset + 5]),
						new Vector2(source[offset + 6], source[offset + 7])));
			}

			groups.add(new PhotonModelPoseSampler.Group(vertices, groupOpacities[group]));
		}

		frame = new PhotonModelPoseSampler.Frame(prepared, groups, opacity, min, max, timings);
		return frame;
	}

	@Nullable
	public PhotonModelPoseSampler.Timings timings() {
		return timings;
	}

	public PhotonModelTopologyUtil.Prepared prepared() {
		if (prepared == null) {
			throw new IllegalStateException("模型局部姿态尚未采样。");
		}

		return prepared;
	}

	public void capture(PhotonModelPoseSampler.Frame source) {
		var groups = source.groups();
		ensureGroups(groups.size());

		for (int group = 0; group < groups.size(); group++) {
			var vertices = groups.get(group).vertices();
			var target = attributes[group];

			if (target.length != vertices.size() * STRIDE) {
				target = new float[vertices.size() * STRIDE];
				attributes[group] = target;
			}

			for (int corner = 0; corner < vertices.size(); corner++) {
				var vertex = vertices.get(corner);
				int offset = corner * STRIDE;
				target[offset] = vertex.position().x();
				target[offset + 1] = vertex.position().y();
				target[offset + 2] = vertex.position().z();
				target[offset + 3] = vertex.normal().x();
				target[offset + 4] = vertex.normal().y();
				target[offset + 5] = vertex.normal().z();
				target[offset + 6] = vertex.uv().x();
				target[offset + 7] = vertex.uv().y();
			}

			cornerCounts[group] = vertices.size();
			groupOpacities[group] = groups.get(group).opacity();
		}

		prepared = source.prepared();
		frame = source;
		opacity = source.opacity();
		min = source.min();
		max = source.max();
		timings = source.timings();
		complete = true;
		failed = false;
		published = true;
	}

	@Override
	public void beginFrame(PhotonModelTopologyUtil.Prepared prepared, float opacity, int groupCount) {
		// 候选写入期间保留上一份完整帧；只有 endFrame 成功后才替换公开快照。
		complete = false;
		failed = false;
		beginPrepared = prepared;
		candidateOpacity = opacity;
		candidateMin = Vector3.ZERO;
		candidateMax = Vector3.ZERO;
		candidateTimings = null;
		this.activeGroup = -1;
		this.activeCorner = 0;
		ensureGroups(groupCount);
		Arrays.fill(candidateGroupOpacities, 0, groupCount, 0);
		Arrays.fill(cornerCounts, 0);
	}

	@Override
	public void beginGroup(int groupIndex, int vertexCount, float opacity) {
		if (groupIndex < 0 || groupIndex >= attributes.length) {
			fail();
			throw new IllegalArgumentException("模型姿态组索引越界：" + groupIndex);
		}

		var target = attributes[groupIndex];
		int required = vertexCount * STRIDE;

		if (target.length < required) {
			target = new float[required];
			attributes[groupIndex] = target;
		}

		activeGroup = groupIndex;
		activeCorner = 0;
		cornerCounts[groupIndex] = vertexCount;
		candidateGroupOpacities[groupIndex] = opacity;
	}

	@Override
	public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
		if (activeGroup < 0 || activeCorner >= cornerCounts[activeGroup]) {
			fail();
			throw new IllegalStateException("模型姿态角点写入越界。");
		}

		int offset = activeCorner++ * STRIDE;
		var target = attributes[activeGroup];
		target[offset] = x;
		target[offset + 1] = y;
		target[offset + 2] = z;
		target[offset + 3] = nx;
		target[offset + 4] = ny;
		target[offset + 5] = nz;
		target[offset + 6] = u;
		target[offset + 7] = v;
	}

	@Override
	public void endGroup() {
		if (activeGroup < 0 || activeCorner != cornerCounts[activeGroup]) {
			fail();
			throw new IllegalStateException("模型姿态组角点数量不完整。");
		}

		activeGroup = -1;
	}

	@Override
	public void endFrame(Vector3 min, Vector3 max, @Nullable PhotonModelPoseSampler.Timings timings) {
		if (beginPrepared == null || activeGroup >= 0) {
			fail();
			throw new IllegalStateException("模型姿态帧未完整结束。");
		}

		this.candidateMin = min;
		this.candidateMax = max;
		this.candidateTimings = timings;
		var previousGroupOpacities = groupOpacities;
		groupOpacities = candidateGroupOpacities;
		candidateGroupOpacities = previousGroupOpacities;
		this.complete = true;
		this.failed = false;
		this.prepared = beginPrepared;
		this.published = true;
		this.frame = null;
		this.min = candidateMin;
		this.max = candidateMax;
		this.opacity = candidateOpacity;
		this.timings = candidateTimings;
	}

	/// 只允许输出成功结束后物化；异常/取消留下失败标记，下一次同 Input 不得命中。
	public boolean complete() {
		return published && !failed;
	}

	/// 公开 Frame 已从当前成功姿态独立物化；清除这里的物化缓存不会改变姿态值。
	public void invalidateFrameSnapshot() {
		frame = null;
	}

	public void fail() {
		complete = false;
		failed = true;
		published = frame != null && prepared != null;
		beginPrepared = prepared;
		activeGroup = -1;
	}

	private void ensureGroups(int count) {
		ensureGroupOpacityCapacity(count);

		if (attributes.length == count) {
			return;
		}

		attributes = Arrays.copyOf(attributes, count);
		cornerCounts = Arrays.copyOf(cornerCounts, count);

		for (int index = 0; index < count; index++) {
			if (attributes[index] == null) {
				attributes[index] = new float[0];
			}
		}
	}

	private void ensureGroupOpacityCapacity(int count) {
		if (groupOpacities.length >= count && candidateGroupOpacities.length >= count) {
			return;
		}

		int capacity = Math.max(count, Math.max(groupOpacities.length, candidateGroupOpacities.length));
		if (groupOpacities.length < capacity) {
			groupOpacities = Arrays.copyOf(groupOpacities, capacity);
		}
		if (candidateGroupOpacities.length < capacity) {
			candidateGroupOpacities = Arrays.copyOf(candidateGroupOpacities, capacity);
		}
	}

	@Override
	public float opacity() {
		return opacity;
	}

	@Override
	public float groupOpacity(int groupIndex) {
		return groupOpacities[groupIndex];
	}

	@Override
	public float attribute(int groupIndex, int cornerIndex, int attribute) {
		return attributes[groupIndex][cornerIndex * STRIDE + attribute];
	}
}
