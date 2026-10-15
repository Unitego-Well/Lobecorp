package org.unitego.lobecorp.util.photon.model.editor;

import com.lowdragmc.lowdraglib2.client.scene.SceneRenderContext;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GrassColor;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampledModel;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.MaterialGroup;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Tint;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// 编辑视口复用 Photon 原生材质的绘制管线；不修改共享材质或发射器运行状态。
public class PhotonModelPreviewMaterialUtil {
	/// 模型编辑预览采用全亮光照，与世界光照变化隔离。
	private static final int PREVIEW_LIGHT = 0x00F000F0;

	@Nullable
	public static Identifier getTexture(PhotonModelEmitter emitter) {
		var setting = textureSetting(emitter);
		return setting == null ? null : ((TextureMaterial) setting.getMaterial()).getTexture();
	}

	/// UV 使用所选面的独立纹理；普通 OBJ 或无面选择仍使用共享材质。
	@Nullable
	public static Identifier getTexture(PhotonModelAsset asset, PhotonModelEmitter emitter, int faceIndex) {
		if (faceIndex >= 0 && faceIndex < asset.faces.size()) {
			var material = asset.materials.get(asset.faces.get(faceIndex).material);

			if (material != null) {
				if (material.materialData.isEmpty()) {
					return Identifier.tryParse(material.texture);
				}

				return PhotonModelMaterialUtil.resolve(material) instanceof TextureMaterial texture ? texture.getTexture() : null;
			}
		}

		return getTexture(emitter);
	}

	/// 从模型私有材质复制管线设置，禁止修改来源发射器的共享配置。
	public static MaterialSetting materialSetting(MaterialSetting source, @Nullable Material material) {
		if (material == null) {
			return source;
		}

		if (!material.materialData.isEmpty()) {
			return copySetting(source, PhotonModelMaterialUtil.resolve(material))
					.setDepthMask(!material.overlay && source.isDepthMask());
		}

		if (!(source.getMaterial() instanceof TextureMaterial texture)) {
			return source;
		}

		var copy = (TextureMaterial) texture.copy();
		copy.setTexture(Identifier.parse(material.texture));
		return copySetting(source, copy).setDepthMask(!material.overlay && source.isDepthMask());
	}

	/// 无纹理覆盖时也复制管线设置，避免部件双面开关改写共享材质配置。
	public static MaterialSetting materialSetting(MaterialSetting source, @Nullable Material material, boolean doubleSided) {
		var result = materialSetting(source, material);

		if (result == source) {
			result = copySetting(source, source.getMaterial());
		}

		return result.setCull(!doubleSided);
	}

	private static MaterialSetting copySetting(MaterialSetting source, IMaterial material) {
		var result = new MaterialSetting(material).setCull(source.isCull()).setDepthTest(source.isDepthTest())
				.setDepthMask(source.isDepthMask());
		var blend = source.getBlendMode();
		var targetBlend = result.getBlendMode();
		targetBlend.setEnableBlend(blend.isEnableBlend());
		targetBlend.setSrcColorFactor(blend.getSrcColorFactor());
		targetBlend.setDstColorFactor(blend.getDstColorFactor());
		targetBlend.setSrcAlphaFactor(blend.getSrcAlphaFactor());
		targetBlend.setDstAlphaFactor(blend.getDstAlphaFactor());
		targetBlend.setBlendFunc(blend.getBlendFunc());
		return result;
	}

	public static int tint(int color, @Nullable Material material) {
		return material != null && material.tint == Tint.GRASS
				? ARGB.multiply(color, ARGB.opaque(GrassColor.getDefaultColor())) : color;
	}

	/// 覆盖面稳定放在基础面后，编辑拓扑或导入 JSON 后也保持相同顺序。
	public static List<Map.Entry<MaterialGroup, PhotonMesh>> orderedGroups(SampledModel model, PhotonModelAsset asset) {
		return model.materialMeshes().entrySet().stream().sorted(Comparator.comparing(entry -> {
			var material = asset.materials.get(entry.getKey().materialId());
			return material != null && material.overlay;
		})).toList();
	}

	@Nullable
	private static MaterialSetting textureSetting(PhotonModelEmitter emitter) {
		for (var setting : emitter.config.renderer().getMaterials()) {
			if (setting.getMaterial() instanceof TextureMaterial) {
				return setting;
			}
		}

		return null;
	}

	/// 兼容原发射器预览入口；会话编辑使用显式传入资产的重载。
	@SuppressWarnings("unused")
	public static boolean draw(SceneRenderContext context, SampledModel model, PhotonModelEmitter emitter) {
		return draw(context, model, emitter, emitter.getAsset());
	}

	/// 使用会话资产绘制，未提交到来源发射器的材质修改也立即进入预览。
	public static boolean draw(SceneRenderContext context, SampledModel model, PhotonModelEmitter emitter, PhotonModelAsset asset) {
		var materials = emitter.config.renderer().getMaterials();

		if (materials.isEmpty()) {
			return false;
		}

		var setting = materials.getFirst();
		boolean drawn = false;

		if (asset.materials.isEmpty() || model.materialMeshes().isEmpty()) {
			for (var entry : model.partMeshes().entrySet()) {
				drawn |= drawMesh(context, model, emitter, asset, entry.getKey(), entry.getValue(), setting, null);
			}
		} else {
			for (var entry : orderedGroups(model, asset)) {
				var group = entry.getKey();
				var material = asset.materials.get(group.materialId());
				drawn |= drawMesh(context, model, emitter, asset, group.partId(), entry.getValue(), setting, material);
			}
		}

		return drawn;
	}

	private static boolean drawMesh(SceneRenderContext context, SampledModel model, PhotonModelEmitter emitter,
	                                PhotonModelAsset asset, String partId, PhotonMesh mesh, MaterialSetting setting,
	                                @Nullable Material material) {
		boolean doubleSided = asset.parts.stream().filter(part -> part.id.equals(partId))
				.findFirst().map(part -> part.doubleSided).orElse(true);
		var previewSetting = materialSetting(setting, material, doubleSided);
		var renderType = previewSetting.getMaterial().getRenderType(previewSetting, VertexFormat.Mode.QUADS);

		if (renderType == null) {
			return false;
		}

		var buffer = context.bufferSource().getBuffer(renderType);
		var pose = context.poseStack().last().pose();
		var positions = mesh.geometry();
		var attributes = mesh.attributes();
		var indices = mesh.indices();
		float opacity = model.opacity() * model.partOpacity().getOrDefault(partId, 1f);
		int alpha = Math.round((emitter.config.color >>> 24) * Math.clamp(opacity, 0f, 1f));
		int color = tint(emitter.config.color & 0x00FFFFFF | alpha << 24, material);

		int triangle = 0;

		while (triangle < mesh.triangleCount()) {
			var indexOffset = triangle * 3;
			var paired = mesh.quadPaired(triangle);

			// QUADS 缓冲保留原生四边面；独立三角面重复末角。
			for (int corner = 0; corner < 4; corner++) {
				var vertex = indices[indexOffset + (corner < 3 ? corner : paired ? 4 : 2)];
				var offset = PhotonMesh.geometryOffset(vertex);
				var attributeOffset = PhotonMesh.attributeOffset(vertex);
				buffer.addVertex(pose, positions[offset], positions[offset + 1], positions[offset + 2])
						.setUv(attributes[attributeOffset], attributes[attributeOffset + 1])
						.setColor(color)
						.setLight(PREVIEW_LIGHT);
			}

			triangle += paired ? 2 : 1;
		}

		context.bufferSource().endBatch(renderType);
		return true;
	}
}
