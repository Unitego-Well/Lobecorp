package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import org.joml.Vector3f;

/// 按实际绕序判断单面三角形朝向；调用方先排除双面、线框、位移着色器与奇异变换。
public class PhotonModelCullUtil {
	public static boolean backFacing(PhotonMesh mesh, int triangle, Vector3f localEye, boolean mirrored) {
		var positions = mesh.geometry();
		var indices = mesh.indices();
		var offset = triangle * 3;
		var a = PhotonMesh.geometryOffset(indices[offset]);
		var b = PhotonMesh.geometryOffset(indices[offset + 1]);
		var c = PhotonMesh.geometryOffset(indices[offset + 2]);
		float abX = positions[b] - positions[a];
		float abY = positions[b + 1] - positions[a + 1];
		float abZ = positions[b + 2] - positions[a + 2];
		float acX = positions[c] - positions[a];
		float acY = positions[c + 1] - positions[a + 1];
		float acZ = positions[c + 2] - positions[a + 2];
		float facing = (abY * acZ - abZ * acY) * (localEye.x - positions[a])
				+ (abZ * acX - abX * acZ) * (localEye.y - positions[a + 1])
				+ (abX * acY - abY * acX) * (localEye.z - positions[a + 2]);

		if (!Float.isFinite(facing)) {
			return false;
		}

		return mirrored ? facing >= 0f : facing <= 0f;
	}
}
