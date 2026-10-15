package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil.ResourcePrepared;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

// 资源枚举与原生编码流程改编自 spawner1145/photon2mcp（Apache-2.0）。
// 本地改动：复用受限文件事务、完整 Tag 往返、PNG 输入与原生缓存更新；来源及许可见第三方声明。
public class PhotonEffectResourceUtil {
	/// 每次浏览最多返回的原生资源和文件条目数，达到上限时返回 truncated。
	private static final int MAX_RESOURCE_ENTRIES = 1000;
	/// 目录浏览最多检查的文件／目录数量及递归深度，非资源文件也计入扫描预算。
	private static final int MAX_SCANNED_FILES = 10000;
	private static final int MAX_BROWSE_DEPTH = 16;
	/// 单份资源编码和读取的 NBT 配额，避免大型资源阻塞客户端线程。
	private static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
	/// PNG 的原始编码上限；Base64 后仍容纳于既有 2 MiB 请求中。
	private static final int MAX_TEXTURE_BYTES = 1024 * 1024;
	/// PNG 单边像素上限，限制 NativeImage 的本地内存分配。
	private static final int MAX_TEXTURE_SIZE = 4096;
	private static final String PNG_SUFFIX = ".png";
	private static final String TEXTURE_DIRECTORY = "textures";

	/// 准备阶段只持有临时文件和 CPU 图像；提交后才发布文件并更新 GPU／原生缓存。
	public static class Prepared implements AutoCloseable {
		private final ResourcePrepared file;
		private final Resource<Object> resource;
		private final Identifier textureId;
		private final JsonObject result;
		private NativeImage image;

		private Prepared(ResourcePrepared file, Resource<Object> resource, Identifier textureId, NativeImage image, JsonObject result) {
			this.file = file;
			this.resource = resource;
			this.textureId = textureId;
			this.image = image;
			this.result = result;
		}

		public JsonObject commit() throws Exception {
			DynamicTexture texture = null;

			try {
				if (image != null) {
					texture = new DynamicTexture(textureId::toString, image);
					image = null;
				}

				file.commit();

				if (texture != null) {
					Minecraft.getInstance().getTextureManager().register(textureId, texture);
					texture = null;
				}

				if (resource != null) {
					invalidate(resource, file.target());
				}

				for (var handle : PhotonEffectProjectUtil.projects()) {
					handle.editor().resourceView.getAssetBrowser().requestGridRebuild();
				}

				return result.deepCopy();
			} finally {
				if (texture != null) {
					texture.close();
				}
			}
		}

		@Override
		public void close() throws IOException {
			try {
				if (image != null) {
					image.close();
					image = null;
				}
			} finally {
				file.close();
			}
		}
	}

	public static JsonObject list(JsonObject request) throws Exception {
		var resources = resources(request);
		var category = PhotonEffectJsonUtil.optionalString(request, "category", "");
		var selected = category.isEmpty() ? resources.resources : List.of(resource(resources, category));
		var categories = new JsonArray();
		var entries = new JsonArray();
		boolean truncated = false;

		for (var resource : selected) {
			var description = new JsonObject();
			description.addProperty("category", resource.getName());
			description.addProperty("suffix", resource.getFileExtension());
			categories.add(description);

			truncated |= entries(resource, entries);
		}

		var directory = PhotonEffectFileUtil.workspacePath(PhotonEffectJsonUtil.optionalString(request, "directory", LDLib2.getAssetsDir().getAbsolutePath()));
		var result = files(directory, selected, category.isEmpty());
		result.add("categories", categories);
		result.add("entries", entries);
		result.addProperty("entriesTruncated", truncated);
		result.addProperty("maxEntries", MAX_RESOURCE_ENTRIES);
		result.addProperty("maxScannedFiles", MAX_SCANNED_FILES);
		result.addProperty("maxBrowseDepth", MAX_BROWSE_DEPTH);
		result.addProperty("maxResourceBytes", MAX_RESOURCE_BYTES);
		result.addProperty("maxTextureBytes", MAX_TEXTURE_BYTES);
		result.addProperty("maxTextureSize", MAX_TEXTURE_SIZE);
		return bounded(result);
	}

	public static JsonObject read(JsonObject request) throws Exception {
		var resource = resource(resources(request), PhotonEffectJsonUtil.string(request, "category"));
		var reference = PhotonEffectJsonUtil.string(request, "path");
		var data = readData(resource, reference);
		var result = describe(resource, data);
		result.addProperty("resourcePath", reference);
		return bounded(result);
	}

	public static Prepared prepareWrite(JsonObject request) throws Exception {
		if (request.has("snbt") == request.has("source")) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		var resource = resource(resources(request), PhotonEffectJsonUtil.string(request, "category"));
		var data = request.has("snbt") ? TagParser.create(NbtOps.INSTANCE).parseFully(PhotonEffectJsonUtil.string(request, "snbt"))
				: readData(resource, PhotonEffectJsonUtil.string(request, "source"));
		var decoded = resource.deserializeResource(data.copy(), Platform.getFrozenRegistry());
		var serialized = decoded == null ? null : resource.serializeResource(decoded, Platform.getFrozenRegistry());

		if (!data.equals(serialized)) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		var target = PhotonEffectFileUtil.workspacePath(PhotonEffectJsonUtil.string(request, "path"));

		if (!target.getFileName().toString().endsWith(resource.getFileExtension())) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, resource.getFileExtension());
		}

		var wrapper = new CompoundTag();
		wrapper.putString("type", resource.getName());
		wrapper.put("data", serialized);
		var bytes = encode(wrapper);
		var result = describe(resource, serialized);
		result.addProperty("path", target.toString());
		result.addProperty("resourcePath", new FilePath(target.toFile()).getPathWithType());
		result.addProperty("bytes", bytes.length);
		bounded(result);
		var file = PhotonEffectFileUtil.prepareResource(target, bytes, PhotonEffectJsonUtil.bool(request, "overwrite", false));
		return new Prepared(file, resource, null, null, result);
	}

	public static Prepared prepareTexture(JsonObject request) throws Exception {
		var target = PhotonEffectFileUtil.workspacePath(PhotonEffectJsonUtil.string(request, "path"));
		var assets = LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize();

		if (!target.startsWith(assets)) {
			throw new PhotonEffectCommandException(Code.PATH_FORBIDDEN);
		}

		var relative = assets.relativize(target);

		if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals(TEXTURE_DIRECTORY)
				|| !target.getFileName().toString().endsWith(PNG_SUFFIX)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		var texturePath = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
		var id = Identifier.fromNamespaceAndPath(relative.getName(0).toString(), texturePath);
		var bytes = Base64.getDecoder().decode(PhotonEffectJsonUtil.string(request, "base64"));
		validatePng(bytes);
		var image = NativeImage.read(bytes);

		try {
			var result = new JsonObject();
			result.addProperty("path", target.toString());
			result.addProperty("textureId", id.toString());
			result.addProperty("resourcePath", new FilePath(target.toFile()).getPathWithType());
			result.addProperty("bytes", bytes.length);
			result.addProperty("width", image.getWidth());
			result.addProperty("height", image.getHeight());
			var file = PhotonEffectFileUtil.prepareResource(target, bytes, PhotonEffectJsonUtil.bool(request, "overwrite", false));
			return new Prepared(file, null, id, image, result);
		} catch (Exception | Error failure) {
			image.close();
			throw failure;
		}
	}

	private static Resources resources(JsonObject request) {
		return request.has("projectId") ? PhotonEffectProjectUtil.require(PhotonEffectJsonUtil.string(request, "projectId")).project().getResources()
				: new FXProject().getResources();
	}

	@SuppressWarnings("unchecked")
	private static Resource<Object> resource(Resources resources, String category) {
		return (Resource<Object>) resources.resources.stream().filter(resource -> resource.getName().equals(category)).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.INVALID_ARGUMENT, category));
	}

	private static Tag readData(Resource<Object> resource, String reference) throws Exception {
		var nativePath = IResourcePath.parse(reference);

		if (nativePath != null) {
			if (nativePath instanceof FilePath file) {
				var diskPath = fileReference(file);

				if (diskPath != null) {
					return readFile(resource, diskPath.toString());
				}

				var id = file.getLocation();

				if (id == null || !id.getPath().endsWith(resource.getFileExtension())) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, resource.getFileExtension());
				}

				var packed = Minecraft.getInstance().getResourceManager().getResource(id)
						.orElseThrow(() -> new PhotonEffectCommandException(Code.FILE_NOT_FOUND, id.toString()));

				try (var input = new DataInputStream(packed.open())) {
					return readWrapper(resource, input);
				}
			}

			var value = resource.getResourceInstance().getResource(nativePath);
			var data = value == null ? null : resource.serializeResource(value, Platform.getFrozenRegistry());

			if (data == null || encodeTag(data).length > MAX_RESOURCE_BYTES) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}

			return data.copy();
		}

		return readFile(resource, reference);
	}

	private static Tag readFile(Resource<?> resource, String reference) throws IOException {
		var sourceRequest = new JsonObject();
		sourceRequest.addProperty("path", reference);
		var file = PhotonEffectFileUtil.source(sourceRequest, resource.getFileExtension());

		if (Files.size(file) > MAX_RESOURCE_BYTES) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		try (var input = new DataInputStream(Files.newInputStream(file))) {
			return readWrapper(resource, input);
		}
	}

	private static Tag readWrapper(Resource<?> resource, DataInputStream input) throws IOException {
		var wrapper = NbtIo.read(input, NbtAccounter.create(MAX_RESOURCE_BYTES));
		var data = wrapper.get("data");

		if (!wrapper.getStringOr("type", "").equals(resource.getName()) || data == null) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		return data.copy();
	}

	private static Path fileReference(IResourcePath reference) throws IOException {
		if (!(reference instanceof FilePath file)) {
			return null;
		}

		if (file.getPath().startsWith("assets/") && file.getLocation() != null) {
			var id = file.getLocation();

			if (!file.getPath().equals("assets/" + id.getNamespace() + "/" + id.getPath())
					|| Arrays.stream(id.getPath().split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
				throw new PhotonEffectCommandException(Code.PATH_FORBIDDEN);
			}

			// 此形式只允许 ResourceManager 的包资源，读取时不交给磁盘／自定义 provider。
			return null;
		}

		return PhotonEffectFileUtil.workspacePath(file.getFile().getAbsolutePath());
	}

	private static boolean forbidden(IResourcePath path) throws IOException {
		try {
			fileReference(path);
			return false;
		} catch (PhotonEffectCommandException failure) {
			if (!failure.code().equals(Code.PATH_FORBIDDEN.name())) {
				throw failure;
			}

			return true;
		}
	}

	private static JsonObject files(Path directory, List<? extends Resource<?>> resources, boolean textures) throws Exception {
		var result = new JsonObject();
		var files = new JsonArray();
		result.addProperty("directory", directory.toString());

		if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
			throw new PhotonEffectCommandException(Code.FILE_NOT_FOUND, directory.toString());
		}

		boolean truncated = false;

		try (var paths = Files.walk(directory, MAX_BROWSE_DEPTH)) {
			var iterator = paths.iterator();
			int scanned = 0;

			while (iterator.hasNext()) {
				if (++scanned > MAX_SCANNED_FILES) {
					truncated = true;
					break;
				}

				var path = iterator.next();

				if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && directory.relativize(path).getNameCount() >= MAX_BROWSE_DEPTH) {
					truncated = true;
				}

				if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					continue;
				}

				var name = path.getFileName().toString();
				var category = resources.stream().filter(resource -> name.endsWith(resource.getFileExtension())).findFirst();

				if (category.isEmpty() && (!textures || !name.endsWith(PNG_SUFFIX))) {
					continue;
				}

				if (files.size() >= MAX_RESOURCE_ENTRIES) {
					truncated = true;
					break;
				}

				PhotonEffectFileUtil.workspacePath(path.toString());
				var entry = new JsonObject();
				entry.addProperty("path", path.toString());
				entry.addProperty("category", category.map(Resource::getName).orElse("texture"));
				entry.addProperty("resourcePath", new FilePath(path.toFile()).getPathWithType());
				entry.addProperty("bytes", Files.size(path));
				files.add(entry);
			}
		}

		result.add("files", files);
		result.addProperty("filesTruncated", truncated);
		return result;
	}

	private static JsonObject describe(Resource<?> resource, Tag data) {
		var result = new JsonObject();
		result.addProperty("category", resource.getName());
		result.addProperty("snbt", data.toString());
		result.add("data", PhotonEffectJsonUtil.json(data));
		result.add("schema", PhotonEffectJsonUtil.schema(data));
		return bounded(result);
	}

	private static JsonObject bounded(JsonObject result) {
		if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RESOURCE_BYTES) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		return result;
	}

	private static byte[] encode(CompoundTag wrapper) throws IOException {
		var output = new ByteArrayOutputStream();

		try (var data = new DataOutputStream(output)) {
			NbtIo.write(wrapper, data);
		}

		if (output.size() > MAX_RESOURCE_BYTES) {
			throw new PhotonEffectCommandException(Code.INVALID_DATA);
		}

		return output.toByteArray();
	}

	private static byte[] encodeTag(Tag tag) throws IOException {
		var wrapper = new CompoundTag();
		wrapper.put("data", tag);
		return encode(wrapper);
	}

	private static void validatePng(byte[] bytes) throws IOException {
		if (bytes.length == 0 || bytes.length > MAX_TEXTURE_BYTES) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);

			if (!readers.hasNext()) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}

			var reader = readers.next();

			try {
				if (!reader.getFormatName().equalsIgnoreCase("png")) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA);
				}

				reader.setInput(input);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);

				if (width < 1 || height < 1 || width > MAX_TEXTURE_SIZE || height > MAX_TEXTURE_SIZE) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
				}
			} finally {
				reader.dispose();
			}
		}
	}

	private static void invalidate(Resource<?> resource, Path target) {
		var instance = resource.getResourceInstance();
		var path = new FilePath(target.toFile());

		for (var candidate : providers(instance)) {
			if (candidate instanceof FileResourceProvider<?> provider && provider.supportResourcePath(path)) {
				provider.checkAndUpdateResourceProvider();
				// 相同时间戳的覆盖也必须丢弃旧对象，不能只依赖文件扫描的时间戳判定。
				provider.getContents().remove(path);
			}
		}

		instance.clearCache();
	}

	private static List<IResourceProvider<?>> providers(ResourceInstance<?> instance) {
		var providers = new ArrayList<IResourceProvider<?>>();
		instance.getBuiltinProviders().values().forEach(providers::addAll);
		instance.getCustomProviders().values().forEach(providers::addAll);
		return providers;
	}

	private static boolean entries(Resource<?> resource, JsonArray entries) throws IOException {
		int scanned = 0;

		for (var provider : providers(resource.getResourceInstance())) {
			if (provider instanceof FileResourceProvider<?> fileProvider
					&& (fileProvider.resourceLocation == null || forbidden(new FilePath(fileProvider.resourceLocation)))) {
				continue;
			}

			for (var entry : provider) {
				if (++scanned > MAX_SCANNED_FILES || entries.size() >= MAX_RESOURCE_ENTRIES) {
					return true;
				}

				if (forbidden(entry.getKey())) {
					continue;
				}

				var item = new JsonObject();
				item.addProperty("category", resource.getName());
				item.addProperty("provider", provider.getName());
				item.addProperty("resourcePath", entry.getKey().getPathWithType());
				entries.add(item);
			}
		}

		return false;
	}
}
