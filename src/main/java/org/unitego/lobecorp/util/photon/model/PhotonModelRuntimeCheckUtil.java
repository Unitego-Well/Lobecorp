package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.PhotonRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.registry.photon.client.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// 在已启动客户端中核对真实模型对象与原生注册/复制链，不保存或覆盖原件。
public class PhotonModelRuntimeCheckUtil {
	public record Result(String source, boolean passed, String detail) {
	}

	public static List<Result> inspect(Path projectRoot) throws IOException {
		var results = new ArrayList<Result>();

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks")) {
			try (var paths = Files.walk(projectRoot.resolve(folder))) {
				for (var file : paths.filter(Files::isRegularFile).sorted().toList()) {
					var original = Files.readAllBytes(file);
					inspect(PhotonResourceReader.read(file), results);

					if (!Arrays.equals(original, Files.readAllBytes(file))) {
						throw new IOException("只读检查中来源发生外部变化：" + file);
					}
				}
			}
		}

		return List.copyOf(results);
	}

	private static void inspect(PhotonResourceDocument document, List<Result> results) {
		var tag = document.nbt();

		if (tag != null) {
			inspect(tag, document.origin(), results);
		}

		for (var child : document.entries()) {
			inspect(child, results);
		}
	}

	private static void inspect(Tag tag, String source, List<Result> results) {
		if (tag instanceof CompoundTag compound) {
			if (compound.getStringOr("type", "").equals(LcPhotonModelOutputTypes.TYPE_NAME)) {
				try {
					var type = PhotonRegistries.FX_OBJECTS.get(LcPhotonModelOutputTypes.TYPE_NAME);

					if (type != LcPhotonModelOutputTypes.MODEL) {
						throw new IllegalStateException("原生注册表没有绑定当前模型适配器。");
					}

					// 文件保护尚未放行；只读验收显式调用已登记类型的内存编解码，不触碰文件入口。
					var decoded = type.codec().parse(NbtOps.INSTANCE, compound.getCompoundOrEmpty("data")).getOrThrow();

					if (!(decoded instanceof PhotonModelOutput model)) {
						throw new IllegalStateException("原生注册表没有产生模型输出对象。");
					}

					model.prepare();
					model.reset();

					if (model.failure() != null) {
						throw new IllegalStateException(model.failure());
					}

					var encoded = model.serializeWrapper();

					if (encoded == null) {
						throw new IllegalStateException("模型对象没有可编码结果。");
					}

					var before = compound.getCompoundOrEmpty("data").getCompoundOrEmpty("config");
					var after = encoded.getCompoundOrEmpty("data").getCompoundOrEmpty("config");

					if (!Objects.equals(before.get("assetJson"), after.get("assetJson"))) {
						throw new IllegalStateException("内存往返改变了模型快照的值或表示。");
					}

					var copied = (PhotonModelOutput) model.copy(false);
					copied.reset();

					if (copied.failure() != null || copied.config == model.config) {
						throw new IllegalStateException("实例复制失败或共享可变配置。");
					}

					model.updateTick(1);

					if (copied.getAgeF() != 0 || copied.slot("length") == model.slot("length")) {
						throw new IllegalStateException("模型时钟或运行属性串实例。");
					}

					model.remove(true);
					copied.remove(true);
					results.add(new Result(source + "/" + model.getName(), true, "原生注册、准备、内存快照往返、实例隔离通过；尚非视觉验收"));
				} catch (RuntimeException | IOException failure) {
					results.add(new Result(source, false, failure.toString()));
				}
			}

			for (var entry : compound.entrySet()) {
				inspect(entry.getValue(), source + "/" + entry.getKey(), results);
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				inspect(list.get(index), source + "/" + index, results);
			}
		}
	}
}
