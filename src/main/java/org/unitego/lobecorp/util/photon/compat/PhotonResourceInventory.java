package org.unitego.lobecorp.util.photon.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// 冻结调用方指定来源的读取清单；来源顺序由调用方声明，不替代游戏内资源优先级验收。
public class PhotonResourceInventory {
	public record Root(String name, Path directory) {
		public Root {
			directory = directory.toAbsolutePath().normalize();
		}
	}

	public record Source(String root, String path, PhotonResourceInspection.Report report) {
	}

	public record Failure(String root, String path, String reason) {
	}

	public record Manifest(List<Root> roots, List<Source> sources, List<Failure> failures) {
		public Manifest {
			roots = List.copyOf(roots);
			sources = List.copyOf(sources);
			failures = List.copyOf(failures);
		}
	}

	public static Manifest scan(List<Root> roots, PhotonResourceLimits limits) throws IOException {
		var sources = new ArrayList<Source>();
		var failures = new ArrayList<Failure>();
		int files = 0;

		for (var root : List.copyOf(roots)) {
			if (!Files.isDirectory(root.directory(), LinkOption.NOFOLLOW_LINKS)) {
				failures.add(new Failure(root.name(), "", "来源目录不存在或为符号链接。"));
				continue;
			}

			try (var paths = Files.walk(root.directory())) {
				var iterator = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().iterator();

				while (iterator.hasNext()) {
					var path = iterator.next();

					if (++files > limits.entries()) {
						throw new IOException("资源清单超出源文件数量预算。");
					}

					var relative = root.directory().relativize(path).toString().replace('\\', '/');

					try {
						var document = PhotonResourceReader.read(path, limits);
						sources.add(new Source(root.name(), relative, PhotonResourceInspection.inspect(document, limits)));
					} catch (IOException failure) {
						if (Thread.currentThread().isInterrupted()) {
							throw failure;
						}

						failures.add(new Failure(root.name(), relative, failure.toString()));
					}
				}
			}
		}

		return new Manifest(roots, sources, failures);
	}
}
