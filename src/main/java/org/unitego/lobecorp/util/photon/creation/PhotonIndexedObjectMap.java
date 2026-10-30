package org.unitego.lobecorp.util.photon.creation;

import com.lowdragmc.photon.client.gameobject.IFXObject;

/// Photon 实例只保存对象数组与 presence，共享不可变 UUID 布局。
public class PhotonIndexedObjectMap extends PhotonIndexedMap<IFXObject> {
	public PhotonIndexedObjectMap(Layout layout) {
		super(layout, IFXObject[]::new);
	}
}
