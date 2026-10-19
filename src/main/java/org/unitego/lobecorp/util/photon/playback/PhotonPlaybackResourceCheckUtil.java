package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// 开发态只读内存检查；逐项返回失败，不通过受保护文件入口，也不把准备成功算成G1。
public class PhotonPlaybackResourceCheckUtil {
	public record Result(String source, String kind, boolean passed, String detail) {
	}

	public static List<Result> inspect(Path root) throws IOException {
		var results = new ArrayList<Result>();

		for (var folder : List.of("src/main/resources/assets/lobecorp/fx", "src/main/resources/fxpacks")) {
			try (var paths = Files.walk(root.resolve(folder))) {
				for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
					inspect(PhotonResourceReader.read(path), results);
				}
			}
		}

		return List.copyOf(results);
	}

	private static void inspect(PhotonResourceDocument document, List<Result> results) {
		if (document.nbt() != null) {
			inspect(document.nbt(), document.origin(), results);
		}

		for (var child : document.entries()) {
			inspect(child, results);
		}
	}

	private static void inspect(Tag value, String source, List<Result> results) {
		if (value instanceof CompoundTag tag) {
			String type = tag.getStringOr("type", "");

			if (type.equals("lobecorp_emitter_spawner") || type.equals("lobecorp_animation_controller")) {
				try {
					var registered = PhotonRegistries.FX_OBJECTS.get(type);

					if (registered == null) {
						throw new IllegalArgumentException("类型未注册。");
					}

					var raw = tag.getCompoundOrEmpty("data");
					var original = raw.copy();
					var codec = registered.codec();
					var object = codec.parse(NbtOps.INSTANCE, raw).getOrThrow();
					var encoded = codec.encodeStart(NbtOps.INSTANCE, object).getOrThrow();
					requireSubset(original, encoded);

					if (!original.equals(raw)) {
						throw new IllegalStateException("原始NBT被内存解码修改。");
					}

					var copied = object.copy(true);

					if (copied == null || copied == object || !copied.id().equals(object.id())) {
						throw new IllegalStateException("深复制未保持对象身份或未隔离对象。");
					}

					results.add(new Result(source, type, true, "原生类型Codec、逐字段内存往返、深复制身份通过；未发射"));
				} catch (RuntimeException failure) {
					results.add(new Result(source, type, false, failure.toString()));
				}
			}

			if (tag.get("fxObjects") instanceof ListTag objects) {
				inspectPlans(objects, source, results);
			}

			for (var entry : tag.entrySet()) {
				inspect(entry.getValue(), source + "/" + entry.getKey(), results);
			}
		} else if (value instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				inspect(list.get(index), source + "/" + index, results);
			}
		}
	}

	private static void inspectPlans(ListTag objects, String source, List<Result> results) {
		PhotonNamedRuntime playback = null;

		try {
			var parsed = new ArrayList<IFXObject>();

			for (var value : objects) {
				parsed.add(IFXObject.CODEC.parse(NbtOps.INSTANCE, value).getOrThrow());
			}

			var controllers = parsed.stream().filter(PhotonNamedController.class::isInstance).map(PhotonNamedController.class::cast).toList();

			if (controllers.isEmpty()) {
				return;
			}

			if (controllers.size() != 1) {
				throw new IllegalArgumentException("实例控制器数量不为1。");
			}

			var runtime = new FXRuntime(new FXData(parsed, new Timeline()));
			playback = new PhotonNamedRuntime(runtime, controllers.getFirst());
			results.add(new Result(source, "named_plan", true, "对象树、命名计划及原生属性槽准备通过；保留未使用绑定="
					+ playback.unusedBindings() + "；未发射或派发事件"));
		} catch (Exception failure) {
			results.add(new Result(source, "named_plan", false, failure.toString()));
		} finally {
			if (playback != null) {
				playback.close();
			}
		}
	}

	private static void requireSubset(Tag expected, Tag actual) {
		if (expected instanceof CompoundTag source && actual instanceof CompoundTag destination) {
			for (var entry : source.entrySet()) {
				requireSubset(entry.getValue(), destination.get(entry.getKey()));
			}
		} else if (expected instanceof ListTag source && actual instanceof ListTag destination) {
			if (source.size() != destination.size()) {
				throw new IllegalStateException("往返列表长度发生变化。");
			}

			for (int index = 0; index < source.size(); index++) {
				requireSubset(source.get(index), destination.get(index));
			}
		} else if (!expected.equals(actual)) {
			throw new IllegalStateException("往返字段变化：" + expected + " -> " + actual);
		}
	}
}
