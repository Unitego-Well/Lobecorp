package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 在隔离导出副本中展开模型材质依赖；保留包内其他条目，不对原件执行GC。
public class PhotonModelPackExportUtil {
	private static final ThreadLocal<Boolean> EXPORTING = ThreadLocal.withInitial(() -> false);

	public static boolean preservingPack() {
		return EXPORTING.get();
	}

	public static void prepare(FX copy) throws IOException {
		for (var object : copy.getFxData().objects()) {
			if (!(object instanceof PhotonModelOutput model)) {
				continue;
			}
			var loaded = PhotonModelSourceUtil.load(model.config);
			var definition = loaded.definition();
			model.config.setAssetSnapshot(loaded.snapshot());
			model.config.source = PhotonModelOutputConfig.Source.EMBEDDED;
			model.config.resource = null;
			model.config.file = "";
			var dependencies = new CompoundTag();
			var textures = new ArrayList<Identifier>();
			for (var entry : definition.materials().entrySet()) {
				if (!entry.getValue().materialData().isEmpty()) {
					dependencies.put(entry.getKey(), PhotonModelAssetUtil.material(entry.getValue().materialData()));
				}
				if (!entry.getValue().texture().isEmpty()) {
					var id = Identifier.tryParse(entry.getValue().texture());
					if (id == null) {
						throw new IOException("模型贴图ID无效：" + entry.getValue().texture());
					}
					textures.add(id);
				}
			}
			model.config.assetMaterialDependencies = dependencies;
			model.config.assetDependencies = textures;
		}
	}

	public static FXPackExporter.Result export(File file, String namespace, String name, FX copy, HolderLookup.Provider provider, boolean modelDefinition) throws IOException {
		boolean previous = EXPORTING.get();
		EXPORTING.set(true);
		try {
			var dependencies = new LinkedHashMap<Identifier, FX>();
			collect(copy.serializeNBT(provider), dependencies, new LinkedHashSet<>(), 0);
			var warnings = new ArrayList<String>();
			for (var entry : dependencies.entrySet()) {
				var dependency = new FX();
				dependency.deserializeNBT(provider, entry.getValue().serializeNBT(provider));
				prepare(dependency);
				var exported = FXPackExporter.exportInto(file, entry.getKey().getNamespace(), entry.getKey().getPath(), dependency, provider);
				warnings.addAll(exported.warnings());
			}
			var result = FXPackExporter.exportInto(file, namespace, name, copy, provider);
			warnings.addAll(result.warnings());
			int fileCount;
			try (var zip = FileSystems.newFileSystem(file.toPath())) {
				if (modelDefinition) {
					var prefix = "assets/" + result.fxId().getNamespace() + "/fx/" + result.fxId().getPath();
					var source = zip.getPath(prefix + ".fx");
					var target = zip.getPath(prefix + ".mfx");
					Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
				}
				try (var files = Files.walk(zip.getPath("assets"))) {
					fileCount = Math.toIntExact(files.filter(Files::isRegularFile).count());
				}
			}
			return new FXPackExporter.Result(result.fxId(), fileCount, List.copyOf(warnings));
		} finally {
			if (previous) {
				EXPORTING.set(true);
			} else {
				EXPORTING.remove();
			}
		}
	}

	private static void collect(Tag tag, Map<Identifier, FX> dependencies, Set<Identifier> active, int depth) throws IOException {
		if (depth > 64 || dependencies.size() > 256) {
			throw new IOException("子特效导出依赖超出预算。");
		}
		if (tag instanceof CompoundTag compound) {
			var text = compound.getStringOr("fxLocation", "");
			if (!text.isEmpty() && compound.getStringOr("source", "FX").equals("FX")) {
				var id = Identifier.tryParse(text);
				if (id == null) {
					throw new IOException("子特效资源ID无效：" + text);
				}
				if (active.contains(id)) {
					throw new IOException("子特效导出依赖循环：" + active + " → " + id);
				}
				if (!dependencies.containsKey(id)) {
					var fx = FXHelper.getFX(id);
					if (fx == null) {
						throw new IOException("子特效导出依赖缺失或受保护：" + id);
					}
					dependencies.put(id, fx);
					active.add(id);
					collect(fx.serializeNBT(Platform.getFrozenRegistry()), dependencies, active, depth + 1);
					active.remove(id);
				}
			}
			for (var child : compound.entrySet()) {
				collect(child.getValue(), dependencies, active, depth + 1);
			}
		} else if (tag instanceof ListTag list) {
			for (var child : list) {
				collect(child, dependencies, active, depth + 1);
			}
		}
	}
}
