package org.unitego.lobecorp.client.photon.model;

import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/// 实例材质槽属于客户端；静态几何与姿态采样器不访问材质注册表或GPU。
public class PhotonModelMaterials {
	private record GroupBlueprint(List<MaterialSetting> materials) {
		private GroupBlueprint {
			materials = List.copyOf(materials);
		}
	}

	private final RendererSetting.Runtime base;
	private final List<RendererSetting.Runtime> groups;
	private final List<GroupBlueprint> blueprints;
	private List<MaterialSetting> sourceMaterials;

	public PhotonModelMaterials(RendererSetting config, RendererSetting.Runtime runtime, PhotonModelTopologyUtil.Prepared prepared) throws IOException {
		base = runtime;
		groups = new ArrayList<>();
		blueprints = new ArrayList<>();
		var definitions = prepared.definition();
		var resolved = new HashMap<String, IMaterial>();

		for (var entry : definitions.materials().entrySet()) {
			var material = entry.getValue();

			if (!material.materialData().isEmpty()) {
				var data = prepared.materialData(entry.getKey());
				var parsed = IMaterial.CODEC.parse(NbtOps.INSTANCE, data).getOrThrow();

				if (parsed == IMaterial.MISSING) {
					throw new IOException("模型材质类型缺失：" + entry.getKey());
				}

				resolved.put(entry.getKey(), parsed);
			}
		}

		sourceMaterials = base.getMaterials();

		for (var group : prepared.groups()) {
			var material = definitions.materials().get(group.material());
			var part = definitions.parts().get(group.part());
			var settings = new ArrayList<MaterialSetting>();

			for (var source : sourceMaterials) {
				var value = resolved.get(group.material());
				IMaterial effective = value == null ? source.getMaterial() : value;

				if (value == null && material != null && effective instanceof TextureMaterial texture) {
					var copy = (TextureMaterial) texture.copy();

					if (!material.texture().isEmpty()) {
						copy.setTexture(Identifier.parse(material.texture()));
					}

					effective = copy;
				}

				var setting = copy(source, effective).setCull(!part.doubleSided());

				if (material != null && material.overlay()) {
					setting.setDepthMask(false);
				}

				settings.add(setting);
			}

			var groupRuntime = new RendererSetting.Runtime(config) {
			};
			groupRuntime.materials.set(List.copyOf(settings));
			groups.add(groupRuntime);
			blueprints.add(new GroupBlueprint(settings));
		}
	}

	public RendererSetting.Runtime base() {
		return base;
	}

	public boolean materialsChanged() {
		return sourceMaterials != base.getMaterials();
	}

	public RendererSetting.Runtime group(int index) {
		var runtime = groups.get(index);
		runtime.materials.set(blueprints.get(index).materials());
		runtime.layer.set(base.getLayer());
		runtime.orderInLayer.set(base.getOrderInLayer());
		runtime.vertexSortingMode.set(base.getVertexSortingMode());
		runtime.compositeMode.set(base.getCompositeMode());
		runtime.writeCustomMask.set(base.isWriteCustomMask());
		runtime.maskGroup.set(base.getMaskGroup());
		runtime.maskAlphaCutoff.set(base.getMaskAlphaCutoff());
		return runtime;
	}

	private static MaterialSetting copy(MaterialSetting source, IMaterial material) {
		var result = new MaterialSetting(material).setCull(source.isCull())
				.setDepthMask(source.isDepthMask()).setDepthTest(source.isDepthTest())
				.setRenderStateSource(source.getRenderStateSource());
		var from = source.getBlendMode();
		var to = result.getBlendMode();
        to.setEnableBlend(from.isEnableBlend());
        to.setBlendFunc(from.getBlendFunc());
        to.setSrcColorFactor(from.getSrcColorFactor());
        to.setDstColorFactor(from.getDstColorFactor());
        to.setSrcAlphaFactor(from.getSrcAlphaFactor());
        to.setDstAlphaFactor(from.getDstAlphaFactor());
		return result;
	}
}
