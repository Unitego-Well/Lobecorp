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
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelObjUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

public class PhotonEffectFileUtil {
	public static List<Path> roots() {
		return DevelopmentResourceUtil.workspaceRoots(List.of(LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize(),
				FXPacks.getFxPacksDir().toPath().toAbsolutePath().normalize()));
	}

	/// 已写入临时文件、尚未发布的输出。
	///
	/// `commit` 将临时文件发布到目标；未发布时 `close` 删除临时文件。准备阶段的清理不会覆盖首个失败。
	public record Prepared(Handle handle, Path target, Path temporary, boolean overwrite,
	                       boolean projectSave, JsonObject exported) implements AutoCloseable {
		public JsonObject commit() throws Exception {
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
	/// 编码、重读或验证失败时清理本次临时文件，清理异常作为首个失败的 suppressed 保留。
	public static Prepared prepareSave(Handle handle, JsonObject request) throws Exception {
		PhotonEffectEditUtil.validate(handle.project());
		var target = destination(request, handle.project().getSuffix());
		var copy = PhotonEffectProjectUtil.copy(handle.project());
		Files.createDirectories(target.getParent());
		var temporary = temporary(target, handle.project().getSuffix());

		try {
			copy.getProjectType().saveProjectToFile(copy, temporary.toFile());
			var reloaded = copy.getProjectType().loadProjectFromFile(temporary.toFile());

			if (!PhotonEffectProjectUtil.snapshot(copy).equals(PhotonEffectProjectUtil.snapshot(reloaded))) {
				throw new PhotonEffectCommandException(Code.INVALID_DATA);
			}

			return new Prepared(handle, target, temporary, PhotonEffectJsonUtil.bool(request, "overwrite", false), true, new JsonObject());
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
	}

	/// 导出发布定义或 FXPack；准备失败清理临时文件，提交阶段才替换目标。
	public static Prepared prepareExport(Handle handle, JsonObject request) throws Exception {
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
		var suffix = handle.project() instanceof PhotonModelProject ? pack ? ".mfxpack" : ".mfx" : pack ? FXPacks.SUFFIX : FX.SUFFIX;
		var target = destination(request, suffix);

		if (pack && FXPacks.isFileLocked(target.toFile())) {
			throw new PhotonEffectCommandException(Code.FILE_LOCKED);
		}

		var exportCopy = PhotonEffectProjectUtil.copy(handle.project());
		var rawFx = exportCopy instanceof PhotonModelProject model ? model.exportFX().serializeNBT(Platform.getFrozenRegistry())
				: PhotonEffectProjectUtil.snapshot(exportCopy).getCompoundOrEmpty("fx");
		var fx = new PhotonExportDefinition(rawFx, Platform.getFrozenRegistry());
		PhotonModelPackExportUtil.prepare(fx);
		Files.createDirectories(target.getParent());
		var temporary = temporary(target, suffix);
		var result = new JsonObject();
		var warnings = new JsonArray();

		try {
			if (pack) {
				if (Files.exists(target)) {
					Files.copy(target, temporary);
				}

				var name = target.getFileName().toString();
				var exported = PhotonModelPackExportUtil.export(temporary.toFile(), Lobecorp.NAMESPACE,
						name.substring(0, name.length() - suffix.length()), fx, Platform.getFrozenRegistry(), handle.project() instanceof PhotonModelProject);
				exported.warnings().forEach(warnings::add);
				if (!exported.warnings().isEmpty()) {
					throw new PhotonEffectCommandException(Code.INVALID_DATA, "包依赖未闭合：" + exported.warnings());
				}
				result.addProperty("fxId", exported.fxId().toString());
				result.addProperty("fileCount", exported.fileCount());
			} else {
				var data = fx.serializeNBT(Platform.getFrozenRegistry());
				data.putInt("version", FXProject.VERSION);
				NbtIo.writeCompressed(data, temporary);
			}

			result.addProperty("projectId", handle.id());
			result.addProperty("path", target.toString());
			result.addProperty("kind", kind);
			result.add("warnings", warnings);
			PhotonResourceReader.read(temporary);
			return new Prepared(handle, target, temporary, PhotonEffectJsonUtil.bool(request, "overwrite", false), false, result);
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
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

	private static Prepared prepareModelExchange(Handle handle, JsonObject request, String kind) throws Exception {
		if (!(handle.project() instanceof PhotonModelProject model)) {
			throw new PhotonEffectCommandException(Code.UNSUPPORTED_OPERATION, kind);
		}
		var target = destination(request, "." + kind);
		var value = model.document().get("asset");
		byte[] bytes = switch (value) {
			case ByteArrayTag array -> array.getAsByteArray();
			case StringTag(var text) -> text.getBytes(StandardCharsets.UTF_8);
			case null, default -> throw new PhotonEffectCommandException(Code.INVALID_DATA, "asset");
		};
		if (kind.equals("obj")) {
			var asset = PhotonResourceJsonUtil.read(bytes, PhotonResourceLimits.DEFAULT).getAsJsonObject();
			bytes = PhotonModelObjUtil.write(asset, PhotonEffectJsonUtil.bool(request, "flipV", false)).getBytes(StandardCharsets.UTF_8);
		}
		Files.createDirectories(target.getParent());
		var temporary = temporary(target, "." + kind);
		try {
			Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW);
			var result = new JsonObject();
			result.addProperty("projectId", handle.id());
			result.addProperty("kind", kind);
			result.addProperty("path", target.toString());
			result.addProperty("completeProjectBackup", false);
			return new Prepared(handle, target, temporary, PhotonEffectJsonUtil.bool(request, "overwrite", false), false, result);
		} catch (Exception | Error failure) {
			cleanupTemporary(temporary, failure);
			throw failure;
		}
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
		PhotonEditorProjectGuardUtil.requireSupportedFile(target.toFile());
		if (overwrite) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		} else {
			Files.move(source, target);
		}
	}
}
