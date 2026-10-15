package org.unitego.lobecorp.util.photon.model.editor;

import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Tint;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelMaterialDataUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// 维护模型私有材质槽与面引用，不修改 Photon 的共享资源库。
public class PhotonModelMaterialUtil {
	/// 新建材质槽的稳定名称前缀，重复时追加序号。
	private static final String SLOT_PREFIX = "material";
	/// 每个资产槽独立持有材质实例，避免逐帧反序列化和跨工程共享参数。
	private static final Map<Material, CachedMaterial> MATERIALS = new WeakHashMap<>();

	private record CachedMaterial(String data, String texture, IMaterial value) {
	}

	public record Entry(String id, String texture, String materialData, Tint tint, boolean overlay, long faces) {
	}

	public static List<Entry> entries(PhotonModelAsset asset) {
		return asset.materials.entrySet().stream().map(entry -> {
			var material = entry.getValue();
			return new Entry(entry.getKey(), material.texture, material.materialData, material.tint, material.overlay,
					usage(asset, entry.getKey()));
		}).toList();
	}

	public static long usage(PhotonModelAsset asset, String id) {
		return asset.faces.stream().filter(face -> face.material.equals(id)).count();
	}

	public static long fallbackFaces(PhotonModelAsset asset) {
		return asset.faces.stream().filter(face -> !asset.materials.containsKey(face.material)).count();
	}

	public static Material fromLibrary(IMaterial source) {
		var resolved = source instanceof UIResourceMaterial reference ? reference.getRawMaterial() : source;

		if (resolved == IMaterial.MISSING) {
			throw failure("resources.invalid_material");
		}

		var material = new Material();
		material.texture = (resolved instanceof TextureMaterial texture ? texture : new TextureMaterial()).getTexture().toString();

		if (!(resolved instanceof TextureMaterial)) {
			material.materialData = snapshot(resolved);
		}

		return material;
	}

	public static String snapshot(IMaterial material) {
		if (material == IMaterial.MISSING) {
			throw failure("resources.invalid_material");
		}

		return IMaterial.CODEC.encodeStart(NbtOps.INSTANCE, material).getOrThrow().toString();
	}

	/// 参数控件编辑独立快照；只有会话提交才改变模型资产。
	public static IMaterial editable(Material material) {
		return material.materialData.isEmpty() ? new TextureMaterial(Identifier.parse(material.texture))
				: IMaterial.CODEC.parse(NbtOps.INSTANCE, PhotonModelMaterialDataUtil.parse(material.materialData)).getOrThrow();
	}

	public static IMaterial resolve(Material material) {
		var cached = MATERIALS.get(material);

		if (cached != null && cached.data().equals(material.materialData) && cached.texture().equals(material.texture)) {
			return cached.value();
		}

		IMaterial resolved;

		try {
			resolved = editable(material);
		} catch (RuntimeException _) {
			resolved = IMaterial.MISSING;
		}

		MATERIALS.put(material, new CachedMaterial(material.materialData, material.texture, resolved));
		return resolved;
	}

	public static void replaceMaterial(PhotonModelAsset asset, String id, Material replacement) {
		var material = requireSlot(asset, id);
		material.texture = replacement.texture;
		material.materialData = replacement.materialData;
	}

	public static void updateMaterial(PhotonModelAsset asset, String id, IMaterial edited) {
		var material = requireSlot(asset, id);
		material.materialData = snapshot(edited);

		if (edited instanceof TextureMaterial texture) {
			material.texture = texture.getTexture().toString();
		}
	}

	public static Material defaultMaterial() {
		return fromLibrary(new TextureMaterial());
	}

	public static String nextId(PhotonModelAsset asset) {
		String id = SLOT_PREFIX;

		for (int suffix = 1; reserved(asset, id); suffix++) {
			id = SLOT_PREFIX + "_" + suffix;
		}

		return id;
	}

	public static boolean validName(String name) {
		return name != null && !name.isBlank() && name.chars().noneMatch(Character::isISOControl)
				&& name.indexOf('#') < 0 && name.indexOf('\\') < 0;
	}

	public static void add(PhotonModelAsset asset, String id, Material material) {
		requireName(asset, id);
		asset.materials.put(id, material.copy());
	}

	public static Material requireSlot(PhotonModelAsset asset, String id) {
		var material = asset.materials.get(id);

		if (material == null) {
			throw failure("resources.missing_slot");
		}

		return material;
	}

	public static void replaceTexture(PhotonModelAsset asset, String id, String texture) {
		if (texture == null || Identifier.tryParse(texture) == null) {
			throw failure("resources.invalid_texture");
		}

		var material = requireSlot(asset, id);
		material.texture = texture;
		material.materialData = "";
	}

	public static void rename(PhotonModelAsset asset, String oldId, String newId) {
		if (oldId.equals(newId)) {
			return;
		}

		var material = requireSlot(asset, oldId);
		requireName(asset, newId);

		if (usage(asset, newId) > 0) {
			throw failure("resources.name_exists");
		}

		asset.materials.remove(oldId);
		asset.materials.put(newId, material);
		asset.faces.stream().filter(face -> face.material.equals(oldId)).forEach(face -> face.material = newId);
	}

	public static void delete(PhotonModelAsset asset, String id) {
		requireSlot(asset, id);

		if (usage(asset, id) > 0) {
			throw failure("resources.in_use");
		}

		asset.materials.remove(id);
	}

	public static void assign(PhotonModelAsset asset, String id, Set<Integer> faces) {
		requireSlot(asset, id);

		if (faces.isEmpty()) {
			throw failure("resources.no_faces");
		}

		for (int index : faces) {
			if (index < 0 || index >= asset.faces.size()) {
				throw failure("resources.no_faces");
			}

			asset.faces.get(index).material = id;
		}
	}

	private static void requireName(PhotonModelAsset asset, String name) {
		if (!validName(name)) {
			throw failure("resources.invalid_name");
		}

		if (asset.materials.containsKey(name)) {
			throw failure("resources.name_exists");
		}
	}

	private static boolean reserved(PhotonModelAsset asset, String id) {
		// 自动名称避开普通 OBJ 尚未映射的 usemtl 标签，防止无意改变其材质。
		return asset.materials.containsKey(id) || usage(asset, id) > 0;
	}

	private static IllegalArgumentException failure(String key) {
		return new IllegalArgumentException(Component.translatable(PhotonModelTextUtil.key(key)).getString());
	}
}
