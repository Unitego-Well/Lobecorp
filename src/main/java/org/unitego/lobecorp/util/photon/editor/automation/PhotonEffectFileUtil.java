package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.NbtIo;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.mixin.photon.editor.client.PhotonEffectEditorAccessor;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;

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
		return List.of(LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize(),
				FXPacks.getFxPacksDir().toPath().toAbsolutePath().normalize());
	}

	public record Prepared(Handle handle, Path target, Path temporary, boolean overwrite,
	                       boolean projectSave, JsonObject exported) implements AutoCloseable {
		public JsonObject commit() throws Exception {
			publish(temporary, target, overwrite);

			if (!projectSave) {
				FXHelper.clearCache();
				return exported;
			}

			if (handle.project() instanceof PhotonModelProject model) {
				model.updateModelName(target.toFile());
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
			Files.deleteIfExists(temporary);
			throw failure;
		}
	}

	public static Prepared prepareExport(Handle handle, JsonObject request) throws Exception {
		PhotonEffectEditUtil.validate(handle.project());
		boolean model = handle.project() instanceof PhotonModelProject;
		var kind = PhotonEffectJsonUtil.string(request, "kind");
		boolean pack = switch (kind) {
			case "definition" -> false;
			case "pack" -> true;
			default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, kind);
		};
		var suffix = pack ? model ? PhotonModelFileUtil.PACK_SUFFIX : FXPacks.SUFFIX
				: model ? PhotonModelFileUtil.DEFINITION_SUFFIX : FX.SUFFIX;
		var target = destination(request, suffix);

		if (pack && FXPacks.isFileLocked(target.toFile())) {
			throw new PhotonEffectCommandException(Code.FILE_LOCKED);
		}

		var fx = handle.project() instanceof PhotonModelProject project ? project.exportProject().getFx()
				: ((FXProject) handle.project()).getFx();
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
				var exported = FXPackExporter.exportInto(temporary.toFile(), Lobecorp.NAMESPACE,
						name.substring(0, name.length() - suffix.length()), fx, Platform.getFrozenRegistry());
				exported.warnings().forEach(warnings::add);
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
			return new Prepared(handle, target, temporary, PhotonEffectJsonUtil.bool(request, "overwrite", false), false, result);
		} catch (Exception | Error failure) {
			Files.deleteIfExists(temporary);
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

	public static ResourcePrepared prepareResource(Path target, byte[] bytes, boolean overwrite) throws IOException {
		var destination = workspacePath(target.toString());
		checkResourceTarget(destination, overwrite);
		Files.createDirectories(destination.getParent());
		var temporary = temporary(destination, ".tmp");

		try {
			Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return new ResourcePrepared(destination, temporary, overwrite);
		} catch (IOException | RuntimeException | Error failure) {
			Files.deleteIfExists(temporary);
			throw failure;
		}
	}

	private static void checkResourceTarget(Path target, boolean overwrite) {
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
				&& (!overwrite || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
			throw new PhotonEffectCommandException(Code.FILE_EXISTS, target.toString());
		}
	}

	private static Path temporary(Path target, String suffix) {
		return target.resolveSibling(".mcp-" + UUID.randomUUID() + suffix);
	}

	private static void publish(Path source, Path target, boolean overwrite) throws Exception {
		if (overwrite) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		} else {
			Files.move(source, target);
		}
	}
}
