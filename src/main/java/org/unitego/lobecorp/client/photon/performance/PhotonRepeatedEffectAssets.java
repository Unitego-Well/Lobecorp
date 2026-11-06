package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffect;
import org.unitego.lobecorp.config.photon.PhotonRepeatedEffectConfig;
import org.unitego.lobecorp.util.photon.compat.PhotonInactiveLegacyFieldUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceInspection;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class PhotonRepeatedEffectAssets {
	public static FX loadNative(JsonObject report, JsonArray hashes) throws Exception {
		long start = System.nanoTime();
		var fx = FXHelper.getFX(Identifier.parse(PhotonRepeatedEffectConfig.NATIVE_FX));
		report.addProperty("fx_helper_started_ns", start);
		report.addProperty("fx_helper_ended_ns", System.nanoTime());
		if (fx == null || fx.getFxData().objects().size() != 1
				|| !fx.getFxData().objects().getFirst().name().equals("particle_emitter")) {
			throw new IllegalStateException("repeat_native_object_mismatch");
		}

		// 审计在真实首次 getFX 之后；不提前读取被测资源。
		var raw = resource(FXHelper.fileLocation(Identifier.parse(PhotonRepeatedEffectConfig.NATIVE_FX)),
				PhotonRepeatedEffectConfig.FX_SHA256, hashes);
		var directory = Minecraft.getInstance().gameDirectory.toPath();
		file(directory.resolve(PhotonRepeatedEffectConfig.FX_FILE), PhotonRepeatedEffectConfig.FX_SHA256, hashes);
		file(directory.resolve(PhotonRepeatedEffectConfig.MATERIAL_FILE), PhotonRepeatedEffectConfig.MATERIAL_SHA256, hashes);
		resource(Identifier.parse(PhotonRepeatedEffectConfig.TEXTURE), PhotonRepeatedEffectConfig.TEXTURE_SHA256, hashes);
		var tag = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
		int inactive = inspect(tag);
		var data = tag.getCompoundOrEmpty("fxData");
		var objects = data.getListOrEmpty("fxObjects");
		if (inactive != PhotonRepeatedEffectConfig.INACTIVE_EXTENSION_FIELDS || objects.size() != 1
				|| !objects.getCompoundOrEmpty(0).getStringOr("type", "").equals("particle_emitter") || data.contains("timeline")) {
			throw new IllegalStateException("repeat_native_structure_mismatch");
		}

		report.addProperty("inactive_extension_fields", inactive);
		report.addProperty("strict_zero_extension_keys", false);
		return fx;
	}

	private static int inspect(Tag tag) {
		int count = 0;
		if (tag instanceof CompoundTag compound) {
			var type = compound.getStringOr("type", "");
			if (PhotonResourceInspection.isLegacyType(type) || type.startsWith("lobecorp")) {
				throw new IllegalStateException("repeat_native_extension_type:" + type);
			}

			for (var entry : compound.entrySet()) {
				if (PhotonResourceInspection.isLegacyField(entry.getKey())) {
					if (!PhotonInactiveLegacyFieldUtil.allowed(compound, entry.getKey(), entry.getValue())) {
						throw new IllegalStateException("repeat_native_active_extension:" + entry.getKey());
					}

					count++;
				}

				count += inspect(entry.getValue());
			}
		} else if (tag instanceof ListTag list) {
			for (var item : list) {
				count += inspect(item);
			}
		}

		return count;
	}

	public static void verifyMaterial(PhotonGeoEffect effect) {
		var emitters = effect.runtime().objects.values().stream().filter(ParticleEmitter.class::isInstance)
				.map(ParticleEmitter.class::cast).toList();
		if (emitters.size() != 1) {
			throw new IllegalStateException("repeat_native_runtime_type");
		}

		var materials = emitters.getFirst().config.renderer.getMaterials();
		if (materials.size() != 1 || !(materials.getFirst().getMaterial() instanceof UIResourceMaterial reference)
				|| !(reference.getRawMaterial() instanceof TextureMaterial texture)
				|| !Identifier.parse(PhotonRepeatedEffectConfig.TEXTURE).equals(texture.getTexture())) {
			throw new IllegalStateException("repeat_native_material_resolution");
		}
	}

	public static byte[] resource(Identifier id, String expected, JsonArray hashes) throws Exception {
		var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow();
		try (var input = resource.open()) {
			var raw = input.readAllBytes();
			record(raw, expected, id.toString(), resource.sourcePackId(), hashes);
			return raw;
		}
	}

	public static void file(Path path, String expected, JsonArray hashes) throws Exception {
		record(Files.readAllBytes(path), expected, path.toString(), "file", hashes);
	}

	public static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	public static void record(byte[] bytes, String expected, String source, String pack, JsonArray hashes) throws Exception {
		var item = new JsonObject();
		var actual = sha256(bytes);
		item.addProperty("source", source);
		item.addProperty("source_pack", pack);
		item.addProperty("sha256", actual);
		item.addProperty("bytes", bytes.length);
		item.addProperty("expected_sha256", expected);
		hashes.add(item);
		if (expected != null && !expected.equals(actual)) {
			throw new IllegalStateException("repeat_hash_mismatch:" + source);
		}
	}
}
