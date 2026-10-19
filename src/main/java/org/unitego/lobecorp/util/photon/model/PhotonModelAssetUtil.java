package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.IntTag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;

import java.io.IOException;

/// 打包重写后的材质依赖只应用于运行工作副本，原JSON与原始NBT保持原表示。
public class PhotonModelAssetUtil {
	/// 只在内存工作副本中补齐当前mfx包已保存的渲染设置，不迁移或覆盖原件。
	public static CompoundTag adaptObject(CompoundTag source) throws IOException {
		if (source.contains("version") && (!(source.get("version") instanceof IntTag version) || version.intValue() != 0)) {
			throw new IOException("模型对象版本不支持。");
		}

		if (!(source.get("config") instanceof CompoundTag config)) {
			throw new IOException("模型对象缺少配置。");
		}

		var result = source.copy();

		if (config.get("modelRenderer") instanceof CompoundTag) {
			return result;
		}

		if (config.contains("modelRenderer") || !(config.get("renderer") instanceof CompoundTag renderer)) {
			throw new IOException("模型对象没有有效的渲染设置。");
		}

		var asset = config.get("assetJson");

		if (asset == null) {
			throw new IOException("不能为缺少当前模型快照的对象推导默认渲染设置。");
		}

		read(asset, config.getCompoundOrEmpty("assetMaterialDependencies"));
		result.getCompoundOrEmpty("config").put("modelRenderer", renderer.copy());
		return result;
	}

	public static PhotonModelDefinition read(Tag asset, CompoundTag materialDependencies) throws IOException {
		JsonElement tree = switch (asset) {
			case ByteArrayTag bytes ->
					PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT);
			case StringTag(var text) -> PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT);
			default -> throw new IOException("模型快照类型不支持。");
		};

		if (!tree.isJsonObject()) {
			throw new IOException("模型快照根必须为JSON对象。");
		}

		var effective = tree.getAsJsonObject().deepCopy();
		var materials = effective.getAsJsonObject("materials");

		if (materials != null) {
			for (var entry : materials.entrySet()) {
				var material = entry.getValue().getAsJsonObject();

				if (material.has("materialData") && !material.get("materialData").getAsString().isEmpty()
						&& materialDependencies.get(entry.getKey()) instanceof CompoundTag dependency) {
					validateMaterial(dependency);
					material.addProperty("materialData", dependency.toString());
				}
			}
		}

		return PhotonModelCodec.read(effective);
	}

	public static CompoundTag material(String data) throws IOException {
		try {
			var result = TagParser.parseCompoundFully(data);
			validateMaterial(result);
			return result;
		} catch (CommandSyntaxException failure) {
			throw new IOException("模型材质SNBT无效。", failure);
		}
	}

	private static void validateMaterial(CompoundTag data) throws IOException {
		if (!(data.get("type") instanceof StringTag(var type)) || type.isBlank()
				|| !(data.get("data") instanceof CompoundTag)) {
			throw new IOException("模型材质缺少类型或配置。");
		}
	}
}
