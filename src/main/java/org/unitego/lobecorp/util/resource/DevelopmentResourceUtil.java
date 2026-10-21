package org.unitego.lobecorp.util.resource;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.utils.CustomResourcePack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// 开发环境的手写资源与编辑器本机状态分开存放；发行环境不启用源码目录。
public class DevelopmentResourceUtil {
	/// 可为自定义运行目录指定源码资源根目录，值为包含 assets 的绝对路径。
	public static final String SOURCE_ROOT_PROPERTY = "lobecorp.developmentResources";
	/// 独立包验收设为 true，禁用源码重定向与额外开发资源包；须在资源路径首次初始化前设置。
	public static final String DISABLE_SOURCE_PROPERTY = "lobecorp.disableDevelopmentResources";
	/// 隔离验收时选择唯一完整 FX 包 ID；每次仓库加载重新读取，未设置时保持原生发现。
	public static final String ISOLATED_FX_PACK_PROPERTY = "lobecorp.isolatedFxPack";
	private static final String SOURCE_ROOT_FROM_GAME = "../../src/main/resources";
	private static final String ASSETS_DIRECTORY = "assets";
	private static final String EDITOR_DIRECTORY = "ldlib2";
	private static final String PACKS_DIRECTORY = "fxpacks";
	private static final String PHOTON_DIRECTORY = "photon";
	/// run 目录作为独立资源包加载在源码资源之前，重复资源由 src 覆盖。
	private static final String RUN_PACK_ID = "lobecorp_development_run";
	private static final String INVALID_SOURCE_DIRECTORY = "Invalid development resources directory: ";
	private static final String FX_PACK_ID_PREFIX = "fxpack/";
	private static final String INVALID_ISOLATED_PACK = "Isolated FX pack requires disabled development resources and a complete fxpack/ ID";

	private static final class Paths {
		private static final Path GAME = Platform.getGamePath().toAbsolutePath().normalize();
		private static final @Nullable Path SOURCE = findSourceDirectory();
	}

	private static @Nullable Path findSourceDirectory() {
		if (Boolean.getBoolean(DISABLE_SOURCE_PROPERTY)) {
			return null;
		}

		if (!Platform.isDevEnv()) {
			return null;
		}

		var configured = System.getProperty(SOURCE_ROOT_PROPERTY);
		var source = configured == null
				? Paths.GAME.resolve(SOURCE_ROOT_FROM_GAME).normalize()
				: Path.of(configured).toAbsolutePath().normalize();
		if (!Files.isDirectory(source.resolve(ASSETS_DIRECTORY))) {
			if (configured != null) {
				throw new IllegalStateException(INVALID_SOURCE_DIRECTORY + source);
			}

			return null;
		}

		return source;
	}

	public static File assetsDirectory(File original) {
		return Paths.SOURCE == null ? original : Paths.SOURCE.resolve(ASSETS_DIRECTORY).toFile();
	}

	public static @Nullable File sourceDirectory() {
		return Paths.SOURCE == null ? null : Paths.SOURCE.toFile();
	}

	public static File runDirectory() {
		return Paths.GAME.toFile();
	}

	public static List<Path> workspaceRoots(List<Path> original) {
		if (Paths.SOURCE == null) {
			return original;
		}

		var roots = new ArrayList<>(original);
		roots.add(Paths.GAME.resolve(EDITOR_DIRECTORY).resolve(ASSETS_DIRECTORY));
		roots.add(Paths.GAME.resolve(PHOTON_DIRECTORY).resolve(PACKS_DIRECTORY));
		return roots.stream().distinct().toList();
	}

	public static List<PackResources> runtimePacks(PackType type, List<PackResources> original) {
		var selected = System.getProperty(ISOLATED_FX_PACK_PROPERTY);
		if (type == PackType.CLIENT_RESOURCES && selected != null) {
			return PhotonAcceptancePackResourcesUtil.isolate(original, selected);
		}

		if (Paths.SOURCE == null || type != PackType.CLIENT_RESOURCES) {
			return original;
		}

		for (var pack : original) {
			if (RUN_PACK_ID.equals(pack.packId())) {
				return original;
			}
		}

		for (var index = 0; index < original.size(); index++) {
			var pack = original.get(index);
			if (pack instanceof CustomResourcePack && LDLib2.MOD_ID.equals(pack.packId())) {
				var packs = new ArrayList<>(original);
				var location = new PackLocationInfo(RUN_PACK_ID, Component.literal("run"), PackSource.DEFAULT, Optional.empty());
				packs.add(index, new PathPackResources(location, Paths.GAME.resolve(EDITOR_DIRECTORY)));
				return packs;
			}
		}

		return original;
	}

	public static File packsDirectory(File original) {
		return Paths.SOURCE == null ? original : Paths.SOURCE.resolve(PACKS_DIRECTORY).toFile();
	}

	public static RepositorySource isolatedFxPacks(RepositorySource original) {
		return consumer -> {
			var selected = System.getProperty(ISOLATED_FX_PACK_PROPERTY);
			if (selected == null) {
				original.loadPacks(consumer);
				return;
			}

			if (!Platform.isDevEnv() || !Boolean.getBoolean(DISABLE_SOURCE_PROPERTY) || sourceDirectory() != null
					|| !selected.startsWith(FX_PACK_ID_PREFIX) || selected.substring(FX_PACK_ID_PREFIX.length()).isBlank()) {
				throw new IllegalStateException(INVALID_ISOLATED_PACK);
			}

			original.loadPacks(pack -> {
				if (selected.equals(pack.getId())) {
					consumer.accept(pack);
				}
			});
		};
	}

	public static File packRoot(File original) {
		if (!original.toPath().toAbsolutePath().normalize().equals(Paths.GAME.resolve(EDITOR_DIRECTORY))) {
			return original;
		}

		return Paths.SOURCE == null ? original : Paths.SOURCE.toFile();
	}

	public static File editorStateAssetsDirectory(File original) {
		return Paths.SOURCE == null ? original : Paths.GAME.resolve(EDITOR_DIRECTORY).resolve(ASSETS_DIRECTORY).toFile();
	}

	public static String gameRelativeSourcePath(String original, String rawPath) {
		if (Paths.SOURCE == null) {
			return original;
		}

		try {
			var path = Path.of(rawPath).normalize();
			if (path.isAbsolute() && path.startsWith(Paths.SOURCE.resolve(ASSETS_DIRECTORY))
					&& path.getRoot().equals(Paths.GAME.getRoot())) {
				return "./" + Paths.GAME.relativize(path).toString().replace('\\', '/');
			}
		} catch (InvalidPathException ignored) {
			// 非文件路径沿用 LDLib2 的解析结果。
		}

		return original;
	}
}
