package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.NbtIo;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.mixin.photon.editor.client.PhotonEffectEditorAccessor;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.client.photon.model.project.PhotonExportDefinition;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelObjUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.CompoundTag;
import com.lowdragmc.photon.client.fx.FXRuntime;

import java.util.concurrent.Callable;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

public class PhotonEffectFileUtil {
	public static List<Path> roots() {
		return DevelopmentResourceUtil.workspaceRoots(List.of(LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize(),
				FXPacks.getFxPacksDir().toPath().toAbsolutePath().normalize()));
	}

	public record Job(Callable<Prepared> work) {
		public Prepared prepare() throws Exception {
			return work.call();
		}
	}

	public record Prepared(Handle handle, String revision, FXRuntime runtime, Path target, Path temporary,
	                       boolean overwrite,
	                       boolean projectSave, JsonObject exported,
	                       CompoundTag expectedContainer) implements AutoCloseable {
		public JsonObject commit() throws Exception {
			var current = PhotonEffectProjectUtil.require(handle.id());
			if (current.project() != handle.project() || current.editor() != handle.editor()) {
				throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
			}
			if (!revision.equals(PhotonEffectProjectUtil.revision(current.project())) || current.editor().runtime != runtime) {
				throw new PhotonEffectCommandException(Code.REVISION_CONFLICT);
			}
			workspacePath(target.toString());
			if (FXPacks.isFileLocked(target.toFile())) {
				throw new PhotonEffectCommandException(Code.FILE_LOCKED);
			}
			if (projectSave) {
				if (!expectedContainer.equals(PhotonEffectProjectUtil.container(current.project()))) {
					throw new PhotonEffectCommandException(Code.REVISION_CONFLICT, "工程容器或元数据已发生变化。");
				}
			}
			publish(temporary, target, overwrite);

			if (!projectSave) {
				FXHelper.clearCache();
				return exported;
			}


			var accessor = (PhotonEffectEditorAccessor) handle.editor();
			accessor.lobecorp$setProjectFile(target.toFile());
			accessor.lobecorp$recordRecentProject();
			var result = PhotonEffectProjectUtil.describe(handle);
			result.addProperty("savedPath", target.toString());
			return result;
		}

		@Override
		public void close() throws IOException {
			Files.deleteIfExists(temporary);
		}
	}

	/// 耗时编码只写本次临时文件，进入提交阶段后才发布到正式路径。
	public static Job prepareSave(Handle handle, JsonObject request) throws Exception {
		PhotonEffectEditUtil.validate(handle.project());
		var target = destination(request, handle.project().getSuffix());
		var container = PhotonEffectProjectUtil.container(handle.project());
		PhotonEffectProjectUtil.verifyContainer(handle.project(), container);
		var revision = PhotonEffectProjectUtil.revision(handle.project());
		var runtime = handle.editor().runtime;
		boolean overwrite = PhotonEffectJsonUtil.bool(request, "overwrite", false);
		var suffix = handle.project().getSuffix();
		return new Job(() -> {
			workspacePath(target.toString());
		Files.createDirectories(target.getParent());
			var temporary = temporary(target, suffix);
		try {
				NbtIo.write(container, temporary);
				var document = PhotonResourceReader.read(temporary);
				var actual = document.nbt();
				if (!Objects.equals(container, actual)) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA, "完整工程容器磁盘往返不一致。");
			}
				return new Prepared(handle, revision, runtime, target, temporary, overwrite, true, new JsonObject(), container);
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
		});
	}

	public static Job prepareExport(Handle handle, JsonObject request) throws Exception {
		PhotonEffectEditUtil.validate(handle.project());
		var kind = PhotonEffectJsonUtil.string(request, "kind");
		if (kind.equals("obj") || kind.equals("json")) {
			return prepareModelExchange(handle, request, kind);
		}
		boolean pack = switch (kind) {
			case "definition" -> false;
			case "pack" -> true;
			default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, kind);
		};
		boolean modelDefinition = handle.project() instanceof PhotonModelProject;
		var suffix = modelDefinition ? pack ? ".mfxpack" : ".mfx" : pack ? FXPacks.SUFFIX : FX.SUFFIX;
		var target = destination(request, suffix);
		if (pack && FXPacks.isFileLocked(target.toFile())) {
			throw new PhotonEffectCommandException(Code.FILE_LOCKED);
		}
		var exportCopy = PhotonEffectProjectUtil.copy(handle.project());
		var rawFx = exportCopy instanceof PhotonModelProject model ? model.exportFX().serializeNBT(Platform.getFrozenRegistry())
				: PhotonEffectProjectUtil.snapshot(exportCopy).getCompoundOrEmpty("fx");
		var fx = new PhotonExportDefinition(rawFx, Platform.getFrozenRegistry());
		PhotonModelPackExportUtil.prepare(fx);
		var name = target.getFileName().toString();
		var archive = pack ? PhotonModelPackExportUtil.capture(Lobecorp.NAMESPACE,
				name.substring(0, name.length() - suffix.length()), fx, Platform.getFrozenRegistry(), modelDefinition) : null;
		var definition = fx.serializeNBT(Platform.getFrozenRegistry());
		definition.putInt("version", FXProject.VERSION);
		var revision = PhotonEffectProjectUtil.revision(handle.project());
		var runtime = handle.editor().runtime;
		boolean overwrite = PhotonEffectJsonUtil.bool(request, "overwrite", false);
		return new Job(() -> {
			workspacePath(target.toString());
		Files.createDirectories(target.getParent());
		var temporary = temporary(target, suffix);
		try {
				var result = new JsonObject();
				PhotonResourceDocument originalPack = null;
				if (archive != null) {
				if (Files.exists(target)) {
					Files.copy(target, temporary);
						originalPack = PhotonResourceReader.read(temporary);
				}
					archive.write(temporary);
					result.addProperty("fxId", archive.fxId().toString());
			} else {
					NbtIo.writeCompressed(definition, temporary);
				}
				var document = PhotonResourceReader.read(temporary);
				var actual = document.nbt();
				if (archive == null && (!Objects.equals(definition, actual))) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA, "完整定义磁盘往返不一致。");
				}
				if (archive != null) {
					archive.verify(document, originalPack);
					result.addProperty("fileCount", document.entries().size());
			}
			result.addProperty("projectId", handle.id());
			result.addProperty("path", target.toString());
			result.addProperty("kind", kind);
				result.add("warnings", new JsonArray());
				return new Prepared(handle, revision, runtime, target, temporary, overwrite, false, result, null);
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
		});
	}

	public static Path workspacePath(String value) throws IOException {
		var path = Path.of(value);
		var root = roots().getFirst();
		var target = (path.isAbsolute() ? path : root.resolve(path)).toAbsolutePath().normalize();

		var allowed = roots().stream().filter(target::startsWith).findFirst()
				.orElseThrow(() -> new PhotonEffectCommandException(Code.PATH_FORBIDDEN));

		for (var parent = target; parent != null && parent.startsWith(allowed); parent = parent.getParent()) {
			if (Files.isSymbolicLink(parent)) {
				throw new PhotonEffectCommandException(Code.PATH_FORBIDDEN);
			}

			if (Files.exists(parent) && Files.exists(allowed) && !parent.toRealPath().startsWith(allowed.toRealPath())) {
				throw new PhotonEffectCommandException(Code.PATH_FORBIDDEN);
			}
		}

		return target;
	}

	private static Job prepareModelExchange(Handle handle, JsonObject request, String kind) throws Exception {
		if (!(handle.project() instanceof PhotonModelProject model)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, kind);
		}
		var target = destination(request, "." + kind);
		var value = model.document().get("asset");
		var revision = PhotonEffectProjectUtil.revision(handle.project());
		var runtime = handle.editor().runtime;
		boolean overwrite = PhotonEffectJsonUtil.bool(request, "overwrite", false);
		boolean flipV = PhotonEffectJsonUtil.bool(request, "flipV", false);
		return new Job(() -> {
		byte[] bytes = switch (value) {
			case ByteArrayTag array -> array.getAsByteArray();
			case StringTag(var text) -> text.getBytes(StandardCharsets.UTF_8);
			case null, default -> throw new PhotonEffectCommandException(Code.INVALID_DATA, "asset");
		};
		if (kind.equals("obj")) {
			var asset = PhotonResourceJsonUtil.read(bytes, PhotonResourceLimits.DEFAULT).getAsJsonObject();
				bytes = PhotonModelObjUtil.write(asset, flipV).getBytes(StandardCharsets.UTF_8);
		}
			workspacePath(target.toString());
		Files.createDirectories(target.getParent());
		var temporary = temporary(target, "." + kind);
		try {
			Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW);
			var result = new JsonObject();
			result.addProperty("projectId", handle.id());
			result.addProperty("kind", kind);
			result.addProperty("path", target.toString());
			result.addProperty("completeProjectBackup", false);
				return new Prepared(handle, revision, runtime, target, temporary, overwrite, false, result, null);
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
		});
	}

	public static Path source(JsonObject request, String suffix) throws IOException {
		var target = workspacePath(PhotonEffectJsonUtil.string(request, "path"));

		if (!target.getFileName().toString().endsWith(suffix)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, suffix);
		}

		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new PhotonEffectCommandException(Code.FILE_NOT_FOUND, target.toString());
		}

		return target;
	}

	public static Path destination(JsonObject request, String suffix) throws Exception {
		var target = workspacePath(PhotonEffectJsonUtil.string(request, "path"));

		if (!target.getFileName().toString().endsWith(suffix)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, suffix);
		}

		if (Files.exists(target) && (!PhotonEffectJsonUtil.bool(request, "overwrite", false) || Files.isDirectory(target))) {
			throw new PhotonEffectCommandException(Code.FILE_EXISTS, target.toString());
		}

		PhotonEditorProjectGuardUtil.requireSupportedFile(target.toFile());
		return target;
	}

	/// 资源写入沿用工程的准备／提交边界，不属于 FX 撤销历史。
	public record ResourcePrepared(Path target, Path temporary, boolean overwrite) implements AutoCloseable {
		public void commit() throws Exception {
			workspacePath(target.toString());
			checkResourceTarget(target, overwrite);
			publish(temporary, target, overwrite);
		}

		@Override
		public void close() throws IOException {
			Files.deleteIfExists(temporary);
		}
	}

	/// 资源字节先写临时文件；提交阶段重新校验目标并发布，准备失败清理临时文件。
	public static ResourcePrepared prepareResource(Path target, byte[] bytes, boolean overwrite) throws IOException {
		var destination = workspacePath(target.toString());
		checkResourceTarget(destination, overwrite);
		Files.createDirectories(destination.getParent());
		var temporary = temporary(destination, ".tmp");

		try {
			Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return new ResourcePrepared(destination, temporary, overwrite);
		} catch (IOException | RuntimeException | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
	}

	private static void checkResourceTarget(Path target, boolean overwrite) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(target.toFile());
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
				&& (!overwrite || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
			throw new PhotonEffectCommandException(Code.FILE_EXISTS, target.toString());
		}
	}

	/// 清理准备失败留下的临时文件。
	/// 删除失败时附加到首个失败上，保持调用方看到原始异常；发布、覆盖和重读逻辑不在此处理。
	private static void cleanupTemporary(Path temporary, Throwable failure) {
		try {
			Files.deleteIfExists(temporary);
		} catch (IOException | RuntimeException cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
	}

	private static Path temporary(Path target, String suffix) {
		return target.resolveSibling(".mcp-" + UUID.randomUUID() + suffix);
	}

	private static void publish(Path source, Path target, boolean overwrite) throws Exception {
		workspacePath(target.toString());
		PhotonEditorProjectGuardUtil.requireSupportedFile(target.toFile());
		if (overwrite) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} else {
			Files.move(source, target);
		}
	}
}
