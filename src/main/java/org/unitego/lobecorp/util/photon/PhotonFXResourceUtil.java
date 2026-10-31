package org.unitego.lobecorp.util.photon;

import net.minecraft.resources.Identifier;

import java.util.function.Predicate;

/// 读取定义时保持原生 FX 优先；模型定义仅作为相同 ID 的资源包回退。
public class PhotonFXResourceUtil {
	public static Identifier resolve(Identifier nativePath, Predicate<Identifier> exists) {
		var path = nativePath.getPath();
		if (!path.endsWith(".fx") || exists.test(nativePath)) {
			return nativePath;
		}

		return Identifier.fromNamespaceAndPath(nativePath.getNamespace(),
				path.substring(0, path.length() - 3) + ".mfx");
	}
}
