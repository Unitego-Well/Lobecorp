package org.unitego.lobecorp.util.photon.model.editor;

import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Tint;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Primitive;

/// 新建模型的默认白模与旧草方块材质构造。
public class PhotonModelDefaultUtil {
	/// Photon 自带的纯白纹理，默认部件只使用此单一纹理槽。
	public static final String WHITE_TEXTURE = "photon:textures/particle/white.png";
	/// 默认草方块的完整边长，单位为模型坐标，对应一个 Minecraft 方块。
	public static final float DEFAULT_BLOCK_SIZE = 1f;
	/// 单位立方体基本体沿前向轴的中心，用于把默认模型移到原点。
	private static final float UNIT_CENTER = 0.5f;
	/// 原立方体基本体侧面的固定面索引。
	private static final int[] SIDE_FACES = {0, 1, 4, 5};
	/// 原立方体基本体底面的固定面索引。
	private static final int BOTTOM_FACE = 2;
	/// 原立方体基本体顶面的固定面索引。
	private static final int TOP_FACE = 3;
	/// 默认资产四种面纹理的稳定材质槽名称。
	private static final String BOTTOM_MATERIAL = "grass_bottom";
	/// 草顶纹理槽的稳定名称。
	private static final String TOP_MATERIAL = "grass_top";
	/// 泥土与草边基础纹理槽的稳定名称。
	private static final String SIDE_MATERIAL = "grass_side";
	/// 侧面绿色草层纹理槽的稳定名称。
	private static final String OVERLAY_MATERIAL = "grass_overlay";
	/// 原版草方块底面的纹理资源。
	private static final String DIRT_TEXTURE = "minecraft:textures/block/dirt.png";
	/// 原版草方块顶面的纹理资源，需乘以草色。
	private static final String TOP_TEXTURE = "minecraft:textures/block/grass_block_top.png";
	/// 原版草方块侧面的基础纹理资源。
	private static final String SIDE_TEXTURE = "minecraft:textures/block/grass_block_side.png";
	/// 原版草方块侧面的透明草层纹理资源，需乘以草色。
	private static final String OVERLAY_TEXTURE = "minecraft:textures/block/grass_block_side_overlay.png";

	public static PhotonModelAsset whiteBlock() {
		var asset = new PhotonModelAsset();
		PhotonModelEditUtil.addPrimitive(asset, Primitive.CUBE);
		asset.parts.removeFirst();
		asset.parts.getFirst().id = PhotonModelAsset.DEFAULT_PART_ID;
		asset.vertices.forEach(vertex -> vertex.z -= UNIT_CENTER);
		asset.faces.forEach(face -> face.partId = PhotonModelAsset.DEFAULT_PART_ID);
		assignWhiteMaterial(asset, PhotonModelAsset.DEFAULT_PART_ID);
		asset.validate();
		return asset;
	}

	public static void assignWhiteMaterial(PhotonModelAsset asset, String partId) {
		String stem = partId + "_material";
		String id = stem;
		int suffix = 1;

		while (asset.materials.containsKey(id)) {
			id = stem + "_" + suffix++;
		}

		material(asset, id, WHITE_TEXTURE, Tint.NONE, false);

		for (var face : asset.faces) {
			if (face.partId.equals(partId)) {
				face.material = id;
			}
		}
	}

	@SuppressWarnings("unused") // 保留旧默认模型的构造入口，已有资产不做自动迁移。
	public static PhotonModelAsset grassBlock() {
		var asset = new PhotonModelAsset();
		PhotonModelEditUtil.addPrimitive(asset, Primitive.CUBE);
		asset.parts.removeFirst();
		asset.parts.getFirst().id = PhotonModelAsset.DEFAULT_PART_ID;
		asset.referenceLength = DEFAULT_BLOCK_SIZE;
		asset.referenceWidth = DEFAULT_BLOCK_SIZE;
		asset.vertices.forEach(vertex -> {
			vertex.z -= UNIT_CENTER;
			vertex.mul(DEFAULT_BLOCK_SIZE);
		});
		asset.faces.forEach(face -> {
			face.partId = PhotonModelAsset.DEFAULT_PART_ID;
			face.material = SIDE_MATERIAL;
		});
		asset.faces.get(BOTTOM_FACE).material = BOTTOM_MATERIAL;
		asset.faces.get(TOP_FACE).material = TOP_MATERIAL;
		material(asset, BOTTOM_MATERIAL, DIRT_TEXTURE, Tint.NONE, false);
		material(asset, TOP_MATERIAL, TOP_TEXTURE, Tint.GRASS, false);
		material(asset, SIDE_MATERIAL, SIDE_TEXTURE, Tint.NONE, false);
		material(asset, OVERLAY_MATERIAL, OVERLAY_TEXTURE, Tint.GRASS, true);

		for (int index : SIDE_FACES) {
			var overlay = asset.faces.get(index).copy();
			overlay.material = OVERLAY_MATERIAL;
			asset.faces.add(overlay);
		}

		asset.validate();
		return asset;
	}

	private static void material(PhotonModelAsset asset, String id, String texture, Tint tint, boolean overlay) {
		var material = new Material();
		material.texture = texture;
		material.tint = tint;
		material.overlay = overlay;
		asset.materials.put(id, material);
	}
}
