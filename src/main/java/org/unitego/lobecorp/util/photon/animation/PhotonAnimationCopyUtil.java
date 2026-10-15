package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PhotonAnimationCopyUtil {
	public static void remap(IFXObject source, List<IFXObject> copies) {
		if (copies.stream().noneMatch(PhotonAnimationController.class::isInstance)) {
			return;
		}

		var originals = new ArrayList<IFXObject>();
		collect(source, originals);

		if (originals.size() != copies.size()) {
			return;
		}

		var ids = new HashMap<String, String>();

		for (int index = 0; index < originals.size(); index++) {
			ids.put(originals.get(index).id().toString(), copies.get(index).id().toString());
		}

		for (var copy : copies) {
			if (copy instanceof PhotonAnimationController controller) {
				var library = controller.library();
				library.remapTargets(ids);
				controller.library(library);
			}
		}
	}

	private static void collect(IFXObject object, List<IFXObject> result) {
		result.add(object);

		for (var child : object.children()) {
			if (child instanceof IFXObject value) {
				collect(value, result);
			}
		}
	}
}
