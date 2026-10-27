package org.unitego.lobecorp.util.photon.model;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;

/// 运行、编辑和导出共用相同来源选择；读取只返回独立快照，不修改配置或原件。
public class PhotonModelSourceUtil {
	/// 仅限制缓存键的保留量；求值器使用弱引用，不延长活实例以外的大模型寿命。
	private static final PreparationCache PREPARED_EMBEDDED = new PreparationCache(16, 16L * 1024 * 1024);

	/// 只共享内嵌内容的只读准备结果；文件和资源来源仍按原读取路径取得当前内容。
	public static PhotonModelPoseSampler prepare(PhotonModelOutputConfig config) throws IOException {
		var snapshot = config.assetSnapshot();

		if (usesSnapshot(config, snapshot)) {
			return PREPARED_EMBEDDED.prepare(snapshot, config.assetMaterialDependencies);
		}

		return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(load(config).definition()));
	}

	private static boolean usesSnapshot(PhotonModelOutputConfig config, Tag snapshot) {
		boolean empty = snapshot instanceof ByteArrayTag bytes && bytes.getAsByteArray().length == 0
				|| snapshot instanceof StringTag(var text) && text.isBlank();
		var key = config.source.name() + "|" + config.resource + "|" + config.file + "|" + config.flipObjV;
		return !empty && (config.source == PhotonModelOutputConfig.Source.EMBEDDED || key.equals(config.snapshotSource));
	}

	/// 内容键拥有独立 NBT，求值器不含播放时钟、实例槽、材质运行态或可写输出帧。
	public static class PreparationCache {
		private record Key(Tag snapshot, CompoundTag materials) {
		}

		private final int maxEntries;
		private final long maxKeyBytes;
		private final LinkedHashMap<Key, WeakReference<PhotonModelPoseSampler>> entries = new LinkedHashMap<>(16, 0.75f, true);
		private long keyBytes;

		public PreparationCache(int maxEntries, long maxKeyBytes) {
			if (maxEntries < 1 || maxKeyBytes < 1) {
				throw new IllegalArgumentException("模型准备缓存预算必须为正。");
			}

			this.maxEntries = maxEntries;
			this.maxKeyBytes = maxKeyBytes;
		}

		public synchronized PhotonModelPoseSampler prepare(Tag snapshot, CompoundTag materials) throws IOException {
			var iterator = entries.entrySet().iterator();

			while (iterator.hasNext()) {
				var entry = iterator.next();

				if (entry.getValue().get() == null) {
					keyBytes -= size(entry.getKey());
					iterator.remove();
				}
			}

			var lookup = new Key(snapshot, materials);
			var reference = entries.get(lookup);
			var cached = reference == null ? null : reference.get();

			if (cached != null) {
				return cached;
			}

			if (reference != null) {
				entries.remove(lookup);
				keyBytes -= size(lookup);
			}

			var owned = new Key(snapshot.copy(), materials.copy());
			var definition = PhotonModelAssetUtil.read(owned.snapshot(), owned.materials());
			var prepared = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
			long bytes = size(owned);

			if (bytes <= maxKeyBytes) {
				while (!entries.isEmpty() && (entries.size() >= maxEntries || keyBytes > maxKeyBytes - bytes)) {
					keyBytes -= size(entries.pollFirstEntry().getKey());
				}

				entries.put(owned, new WeakReference<>(prepared));
				keyBytes += bytes;
			}

			return prepared;
		}

		private static long size(Key key) {
			return (long) key.snapshot().sizeInBytes() + key.materials().sizeInBytes();
		}
	}

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
		return load(config, null);
	}

	/// 使用同一资源视图读取几何与附加数据；null 使用当前资源管理器，文件和内嵌来源不访问资源提供者。
	public static Loaded load(PhotonModelOutputConfig config, @Nullable ResourceProvider resources) throws IOException {
		var snapshot = config.assetSnapshot();
		if (usesSnapshot(config, snapshot)) {
			return new Loaded(PhotonModelAssetUtil.read(snapshot, config.assetMaterialDependencies), snapshot, "embedded:" + config.snapshotSource, "");
		}
		if (config.source == PhotonModelOutputConfig.Source.EMBEDDED) {
			throw new IOException("模型实例没有内嵌资产。");
		}
		PhotonResourceDocument document;
		CompoundTag companion = null;
		if (config.source == PhotonModelOutputConfig.Source.FILE) {
			document = PhotonResourceReader.read(Path.of(config.file));
		} else {
			if (config.resource == null) {
				throw new IOException("模型资源ID为空。");
			}
			var provider = resources == null ? Minecraft.getInstance().getResourceManager() : resources;
			var resource = provider.getResource(config.resource)
					.orElseThrow(() -> new IOException("模型资源缺失：" + config.resource));
			try (var input = resource.open()) {
				document = PhotonResourceReader.read(resource.sourcePackId() + ":" + config.resource,
						PhotonResourceDocument.Kind.of(config.resource.getPath()), input, PhotonResourceLimits.DEFAULT);
			}
			if (document.kind() == PhotonResourceDocument.Kind.OBJ) {
				companion = PhotonModelSplitUtil.companionData(provider, config.resource);
			}
		}
		if (document.kind() == PhotonResourceDocument.Kind.OBJ) {
			var json = PhotonModelSplitUtil.merge(new String(document.bytes(), StandardCharsets.UTF_8), companion, config.flipObjV);
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
