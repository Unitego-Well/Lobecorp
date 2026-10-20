package org.unitego.lobecorp.util.photon.model;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/// 运行、编辑和导出共用相同来源选择；读取只返回独立快照，不修改配置或原件。
public class PhotonModelSourceUtil {
	public record Loaded(PhotonModelDefinition definition, Tag snapshot, String origin, String sha256) {
		public Loaded {
			snapshot = snapshot.copy();
		}

		@Override
		public Tag snapshot() {
			return snapshot.copy();
		}
	}

	public static Loaded load(PhotonModelOutputConfig config) throws IOException {
		var snapshot = config.assetSnapshot();
		boolean empty = snapshot instanceof ByteArrayTag bytes && bytes.getAsByteArray().length == 0
				|| snapshot instanceof StringTag(var text) && text.isBlank();
		var key = config.source.name() + "|" + config.resource + "|" + config.file + "|" + config.flipObjV;
		if (!empty && (config.source == PhotonModelOutputConfig.Source.EMBEDDED || key.equals(config.snapshotSource))) {
			return new Loaded(PhotonModelAssetUtil.read(snapshot, config.assetMaterialDependencies), snapshot, "embedded:" + config.snapshotSource, "");
		}
		if (config.source == PhotonModelOutputConfig.Source.EMBEDDED) {
			throw new IOException("模型实例没有内嵌资产。");
		}
		PhotonResourceDocument document;
		if (config.source == PhotonModelOutputConfig.Source.FILE) {
			document = PhotonResourceReader.read(Path.of(config.file));
		} else {
			if (config.resource == null) {
				throw new IOException("模型资源ID为空。");
			}
			var resource = Minecraft.getInstance().getResourceManager().getResource(config.resource)
					.orElseThrow(() -> new IOException("模型资源缺失：" + config.resource));
			try (var input = resource.open()) {
				document = PhotonResourceReader.read(resource.sourcePackId() + ":" + config.resource,
						PhotonResourceDocument.Kind.of(config.resource.getPath()), input, PhotonResourceLimits.DEFAULT);
			}
		}
		if (document.kind() == PhotonResourceDocument.Kind.OBJ) {
			var json = PhotonModelObjUtil.read(new String(document.bytes(), StandardCharsets.UTF_8), config.flipObjV);
			return new Loaded(PhotonModelCodec.read(json), new ByteArrayTag(json.toString().getBytes(StandardCharsets.UTF_8)), document.origin(), document.sha256());
		}
		var models = PhotonModelCodec.readEmbedded(document);
		if (models.size() != 1) {
			throw new IOException("模型来源必须明确包含一个模型，当前数量：" + models.size());
		}
		var definition = models.getFirst().definition();
		return new Loaded(definition, new ByteArrayTag(document.kind() == PhotonResourceDocument.Kind.JSON ? document.bytes()
				: definition.source().toString().getBytes(StandardCharsets.UTF_8)), document.origin(), document.sha256());
	}
}
