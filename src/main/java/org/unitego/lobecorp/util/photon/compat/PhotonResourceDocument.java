package org.unitego.lobecorp.util.photon.compat;

import com.google.gson.JsonElement;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// 保留原始容器与未知数据；解码出口返回副本，读取成功不代表运行兼容通过。
public record PhotonResourceDocument(String origin, Kind kind, String sha256, byte[] bytes,
                                     @Nullable CompoundTag nbt, @Nullable JsonElement json,
                                     List<PhotonResourceDocument> entries) {
	public PhotonResourceDocument {
		bytes = bytes.clone();
		nbt = nbt == null ? null : nbt.copy();
		json = json == null ? null : json.deepCopy();
		entries = List.copyOf(entries);
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}

	public int sourceBytes() {
		return bytes.length;
	}

	@Override
	public @Nullable CompoundTag nbt() {
		return nbt == null ? null : nbt.copy();
	}

	@Override
	public @Nullable JsonElement json() {
		return json == null ? null : json.deepCopy();
	}

	public enum Kind {
		FX_PROJECT, FX, MODEL_PROJECT, MODEL_FX, FX_PACK, MODEL_PACK, JSON, OBJ, NBT, BINARY;

		public static Kind of(String name) {
			if (name.endsWith(".mfxproj")) {
				return MODEL_PROJECT;
			}

			if (name.endsWith(".fxproj")) {
				return FX_PROJECT;
			}

			if (name.endsWith(".mfxpack")) {
				return MODEL_PACK;
			}

			if (name.endsWith(".fxpack")) {
				return FX_PACK;
			}

			if (name.endsWith(".mfx")) {
				return MODEL_FX;
			}

			if (name.endsWith(".fx")) {
				return FX;
			}

			if (name.endsWith(".json") || name.endsWith(".mcmeta")) {
				return JSON;
			}

			if (name.endsWith(".obj")) {
				return OBJ;
			}

			return name.endsWith(".nbt") ? NBT : BINARY;
		}
	}
}
