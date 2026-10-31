package org.unitego.lobecorp.util.resource;

import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// 仅独立验收会话隐藏基础包中的已声明特效资源，不改变资源包优先级或普通运行路径。
@NullMarked
public class PhotonAcceptancePackResourcesUtil {
	private static final String GAME_NAME = "g1_resource_acceptance_20261021";
	private static final String SOURCE_BASELINE = "g1_source_baseline.fxpack";
	private static volatile @Nullable Scope latest;

	public record Scope(String selectedPackId, List<String> origins, Set<Identifier> resourceIds,
						Set<Identifier> hiddenIds) {
		public Scope {
			origins = List.copyOf(origins);
			resourceIds = Set.copyOf(resourceIds);
			hiddenIds = Set.copyOf(hiddenIds);
		}
	}

	public static @Nullable Scope scope() {
		return latest;
	}

	public static List<PackResources> isolate(List<PackResources> original, String selected) {
		var game = Platform.getGamePath().toAbsolutePath().normalize();
		if (!Platform.isDevEnv() || !game.getFileName().toString().equals(GAME_NAME)
				|| !Boolean.getBoolean(DevelopmentResourceUtil.DISABLE_SOURCE_PROPERTY)
				|| DevelopmentResourceUtil.sourceDirectory() != null || !selected.startsWith("fxpack/")
				|| selected.substring("fxpack/".length()).isBlank()) {
			throw new IllegalStateException("资源屏蔽仅允许在明确隔离的特效验收目录启用。");
		}

		var captured = capture(game.resolve("photon/fxpacks"), selected);
		var result = new ArrayList<PackResources>();
		for (var pack : original) {
			result.add(selected.equals(pack.packId()) ? pack : hideResources(pack, captured.hiddenIds()));
		}
		latest = captured;
		return List.copyOf(result);
	}

	private static Scope capture(Path root, String selected) {
		var resources = new LinkedHashSet<Identifier>();
		var origins = new ArrayList<String>();
		long sourceBytes = 0;
		long expandedBytes = 0;
		var limits = PhotonResourceLimits.DEFAULT;
		try (var paths = Files.list(root)) {
			for (var file : paths.filter(PhotonAcceptancePackResourcesUtil::candidate).sorted().toList()) {
				try {
					if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
						throw new IOException("验收来源不是普通包文件。");
					}
					sourceBytes = Math.addExact(sourceBytes, Files.size(file));
					if (sourceBytes > limits.sourceBytes()) {
						throw new IOException("验收包集合超过读取预算。");
					}
					var document = PhotonResourceReader.read(file, limits);
					origins.add(file + "#" + document.sha256());
                    for (var entry : document.entries()) {
                        expandedBytes = Math.addExact(expandedBytes, entry.sourceBytes());
                        if (expandedBytes > limits.unpackedBytes()) {
                            throw new IOException("验收包集合超过展开预算。");
                        }
                        var name = entry.origin().substring(document.origin().length() + 2);
                        if (name.startsWith("assets/")) {
                            int separator = name.indexOf('/', "assets/".length());
                            if (separator < 0) {
                                throw new IOException("验收包资源缺少命名空间。");
                            }
                            resources.add(Identifier.fromNamespaceAndPath(name.substring("assets/".length(), separator), name.substring(separator + 1)));
                            if (resources.size() > limits.entries()) {
                                throw new IOException("验收包集合超过展开预算。");
                            }
                        }
                    }
				} catch (IOException | RuntimeException invalid) {
					throw new IllegalStateException("无法读取验收屏蔽来源：" + file, invalid);
				}
			}
		} catch (IOException invalid) {
			throw new IllegalStateException("无法枚举验收包目录：" + root, invalid);
		}

		var hidden = new LinkedHashSet<>(resources);
		resources.stream().filter(id -> !id.getPath().endsWith(".mcmeta"))
				.map(id -> id.withPath(id.getPath() + ".mcmeta")).forEach(hidden::add);
		return new Scope(selected, origins, resources, hidden);
	}

	private static boolean candidate(Path path) {
		var name = path.getFileName().toString();
		return !name.equals(SOURCE_BASELINE) && (name.startsWith("source_packs_") || name.startsWith("pack_cache_") || name.startsWith("g1_"))
				&& (name.endsWith(".fxpack") || name.endsWith(".mfxpack"));
	}

	public static PackResources hideResources(PackResources original, Set<Identifier> hidden) {
		return new Filtered(original, Set.copyOf(hidden));
	}

	private record Filtered(PackResources original, Set<Identifier> hidden) implements PackResources {
		@Override
		public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
			return original.getRootResource(path);
		}

		@Override
		public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
			return type == PackType.CLIENT_RESOURCES && hidden.contains(location) ? null : original.getResource(type, location);
		}

		@Override
		public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
			original.listResources(type, namespace, directory, (id, supplier) -> {
				if (type != PackType.CLIENT_RESOURCES || !hidden.contains(id)) {
					output.accept(id, supplier);
				}
			});
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return original.getNamespaces(type);
		}

		@Override
		public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) throws IOException {
			return original.getMetadataSection(type);
		}

		@Override
		public PackLocationInfo location() {
			return original.location();
		}

		@Override
		public String packId() {
			return original.packId();
		}

		@Override
		public Optional<KnownPack> knownPackInfo() {
			return original.knownPackInfo();
		}

		@Override
		public boolean isHidden() {
			return original.isHidden();
		}

		@Override
		public void close() {
			original.close();
		}
	}
}
