package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.LinkedHashMap;

/// 运行、编辑和导出共用相同来源选择；读取只返回独立快照，不修改配置或原件。
public class PhotonModelSourceUtil {
	/// 仅限制缓存键的保留量；求值器使用弱引用，不延长活实例以外的大模型寿命。
	private static final PreparationCache PREPARED_EMBEDDED = new PreparationCache(16, 16L * 1024 * 1024);

	/// 启动时选择实验路径；未指定时保留逐实例读取，运行中不切换。
	private static final boolean RESOURCE_PREPARATION_CACHE = Boolean.getBoolean("lobecorp.photon.resourcePreparationCache");
	/// 强引用准备结果，按条目数及合并快照字节估算限制保留量；不代表精确堆内存上限。
	private static final ResourcePreparationCache PREPARED_RESOURCES = new ResourcePreparationCache(16, 16L * 1024 * 1024);

	public static PhotonModelPoseSampler prepare(PhotonModelOutputConfig config) throws IOException {
		var snapshot = config.assetSnapshot();

		if (usesSnapshot(config, snapshot)) {
			return PREPARED_EMBEDDED.prepare(snapshot, config.assetMaterialDependencies);
		}

		if (RESOURCE_PREPARATION_CACHE && config.source == PhotonModelOutputConfig.Source.RESOURCE) {
			return PREPARED_RESOURCES.prepare(Minecraft.getInstance().getResourceManager(), config.resource,
					config.flipObjV, PhotonRenderTypes.reloadGeneration()).sampler();
		}

		return new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(load(config).definition()));
	}

	/// 重载 HEAD 先释放旧资产；代次由随后执行的 Photon 原方法推进，不维护平行版本。
	public static void clearResourcePreparationCache() {
		PREPARED_RESOURCES.clear();
	}

	/// 求值器只共享准备数据；采样 scratch 按线程隔离，返回帧不引用可写 scratch。
	public record ResourcePreparation(PhotonModelPoseSampler sampler, String sha256) {
	}

	/// 只缓存实时 RESOURCE；弱身份键不反向持有资源管理器，值不持有世界或实例。
	public static class ResourcePreparationCache {
		private static class Key {
			private final WeakReference<ResourceProvider> provider;
			private final int identity;
			private final int generation;
			private final Identifier resource;
			private final boolean flipV;

			private Key(ResourceProvider provider, int generation, Identifier resource, boolean flipV) {
				this.provider = new WeakReference<>(provider);
				identity = System.identityHashCode(provider);
				this.generation = generation;
				this.resource = resource;
				this.flipV = flipV;
			}

			@Override
			public int hashCode() {
				return Objects.hash(identity, generation, resource, flipV);
			}

			@Override
			public boolean equals(Object other) {
				if (this == other) {
					return true;
				}

				return other instanceof Key key && provider.get() != null && provider.get() == key.provider.get()
						&& generation == key.generation && resource.equals(key.resource) && flipV == key.flipV;
			}
		}

		private record Entry(ResourcePreparation prepared, long bytes) {
		}

		private final int maxEntries;
		private final long maxSourceBytes;
		private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
		private long sourceBytes;

		public ResourcePreparationCache(int maxEntries, long maxSourceBytes) {
			if (maxEntries < 1 || maxSourceBytes < 1) {
				throw new IllegalArgumentException("模型准备缓存预算必须为正。");
			}

			this.maxEntries = maxEntries;
			this.maxSourceBytes = maxSourceBytes;
		}

		/// generation 必须来自当前资源视图的权威代次；同代次内容稳定，重载后才重新读取。
		public synchronized ResourcePreparation prepare(ResourceProvider provider, Identifier resource, boolean flipV,
		                                                int generation) throws IOException {
			Objects.requireNonNull(provider);

			if (resource == null) {
				throw new IOException("模型资源ID为空。");
			}

			var iterator = entries.entrySet().iterator();

			while (iterator.hasNext()) {
				var entry = iterator.next();
				var owner = entry.getKey().provider.get();

				if (owner == null || owner == provider && entry.getKey().generation != generation) {
					sourceBytes -= entry.getValue().bytes();
					iterator.remove();
				}
			}

			var key = new Key(provider, generation, resource, flipV);
			var cached = entries.get(key);

			if (cached != null) {
				return cached.prepared();
			}

			var loaded = loadResource(provider, resource, flipV);
			var prepared = new ResourcePreparation(
					new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(loaded.definition())), loaded.sha256());
			long bytes = loaded.snapshot().sizeInBytes();

			if (bytes <= maxSourceBytes) {
				while (!entries.isEmpty() && (entries.size() >= maxEntries || sourceBytes > maxSourceBytes - bytes)) {
					sourceBytes -= entries.pollFirstEntry().getValue().bytes();
				}

				entries.put(key, new Entry(prepared, bytes));
				sourceBytes += bytes;
			}

			return prepared;
		}

		public synchronized void clear() {
			entries.clear();
			sourceBytes = 0;
		}
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
		if (config.source == PhotonModelOutputConfig.Source.FILE) {
			var document = PhotonResourceReader.read(Path.of(config.file));
			return decode(document, null, config.flipObjV, document.sha256());
		}

		if (config.resource == null) {
			throw new IOException("模型资源ID为空。");
		}

		var provider = resources == null ? Minecraft.getInstance().getResourceManager() : resources;
		return loadResource(provider, config.resource, config.flipObjV);
	}

	private static Loaded loadResource(ResourceProvider provider, Identifier id, boolean flipV) throws IOException {
		var resource = provider.getResource(id).orElseThrow(() -> new IOException("模型资源缺失：" + id));
		PhotonResourceDocument document;

		try (var input = resource.open()) {
			document = PhotonResourceReader.read(resource.sourcePackId() + ":" + id,
					PhotonResourceDocument.Kind.of(id.getPath()), input, PhotonResourceLimits.DEFAULT);
		}

		var companion = document.kind() == PhotonResourceDocument.Kind.OBJ
				? PhotonModelSplitUtil.companionDocument(provider, id) : null;
		return decode(document, companion, flipV, resourceFingerprint(document, companion, flipV));
	}

	/// 指纹区分主资源原字节、伴生缺失或内容和 UV 选项；不把主文件散列冒充组合资产。
	private static String resourceFingerprint(PhotonResourceDocument document, @Nullable PhotonResourceDocument companion,
	                                          boolean flipV) {
		var content = document.sha256() + "\n" + (companion == null ? "absent" : "present:" + companion.sha256())
				+ "\n" + flipV;

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static Loaded decode(PhotonResourceDocument document, @Nullable PhotonResourceDocument companion,
	                             boolean flipV, String sha256) throws IOException {
		if (document.kind() == PhotonResourceDocument.Kind.OBJ) {
			var data = companion == null ? null : PhotonModelSplitUtil.readData(companion.nbt());
			var json = PhotonModelSplitUtil.merge(new String(document.bytes(), StandardCharsets.UTF_8), data, flipV);
			return new Loaded(PhotonModelCodec.read(json), new ByteArrayTag(json.toString().getBytes(StandardCharsets.UTF_8)),
					document.origin(), sha256);
		}

		var models = PhotonModelCodec.readEmbedded(document);

		if (models.size() != 1) {
			throw new IOException("模型来源必须明确包含一个模型，当前数量：" + models.size());
		}

		var definition = models.getFirst().definition();
		return new Loaded(definition, new ByteArrayTag(document.kind() == PhotonResourceDocument.Kind.JSON ? document.bytes()
				: definition.source().toString().getBytes(StandardCharsets.UTF_8)), document.origin(), sha256);
	}
}
