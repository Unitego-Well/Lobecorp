package org.unitego.lobecorp.util.photon.model;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;

/// 在资产 JSON 与原生资源扫描器之间传递材质快照，不加载客户端材质类。
public class PhotonModelMaterialDataUtil {
	public static CompoundTag parse(String data) {
		try {
			var result = TagParser.parseCompoundFully(data);

			if (!(result.get("type") instanceof StringTag(var type)) || type.isBlank()
					|| !(result.get("data") instanceof CompoundTag)) {
				throw new IllegalArgumentException(PhotonModelTextUtil.key("resources.invalid_material"));
			}

			return result;
		} catch (CommandSyntaxException error) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("resources.invalid_material"), error);
		}
	}

	public static CompoundTag dependencies(PhotonModelAsset asset) {
		var result = new CompoundTag();
		asset.materials.forEach((id, material) -> {
			if (!material.materialData.isEmpty()) {
				result.put(id, parse(material.materialData));
			}
		});
		return result;
	}

	/// 原生导出会改写结构化快照中的资源路径，载入时必须消费改写后的结果。
	public static void applyDependencies(PhotonModelAsset asset, Tag dependencies) {
		if (!(dependencies instanceof CompoundTag entries)) {
			return;
		}

		asset.materials.forEach((id, material) -> {
			if (!material.materialData.isEmpty() && entries.get(id) instanceof CompoundTag data) {
				material.materialData = parse(data.toString()).toString();
			}
		});
	}
}
