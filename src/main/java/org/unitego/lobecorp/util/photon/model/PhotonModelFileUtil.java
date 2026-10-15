package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Path;

/// 模型文件与普通特效分开保存；包内定义仍使用原生资源目录和无后缀 ID。
public class PhotonModelFileUtil {

	public static final String PROJECT_SUFFIX = ".mfxproj";
	public static final String DEFINITION_SUFFIX = ".mfx";
	public static final String PACK_SUFFIX = ".mfxpack";
	public static final String FX_SUFFIX = FX.SUFFIX;
	public static final String FXPACK_SUFFIX = FXPacks.SUFFIX;

	public static boolean isModelPack(File file) {
		return file.getName().endsWith(PACK_SUFFIX);
	}

	public static boolean isModelPack(Path path) {
		return path.toString().endsWith(PACK_SUFFIX);
	}

	public static boolean isModelPack(FileSystem fileSystem) {
		for (var root : fileSystem.getRootDirectories()) {
			if (root.toUri().getSchemeSpecificPart().endsWith(PACK_SUFFIX + "!/")) {
				return true;
			}
		}

		return false;
	}

	public static String definitionPath(FileSystem fileSystem, String nativePath) {
		if (!isModelPack(fileSystem) || !nativePath.endsWith(FX_SUFFIX)) {
			return nativePath;
		}

		return nativePath.substring(0, nativePath.length() - FX_SUFFIX.length()) + DEFINITION_SUFFIX;
	}
}
