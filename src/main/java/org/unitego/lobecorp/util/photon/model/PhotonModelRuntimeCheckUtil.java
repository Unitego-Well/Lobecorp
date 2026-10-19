package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClip;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
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

	/// 客户端内存夹具覆盖原生回调、循环末姿态、属性槽及贡献释放，不写入资源文件。
	public static Result inspectTimelineFixture() {
		try {
			var model = new PhotonModelOutput();
			model.config.setAssetSnapshot(StringTag.valueOf("""
					{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
					"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
					"morphs":[{"id":"rise","deltas":{"0":[0,0,1]}}],
					"clips":[{"id":"grow","duration":10,"loop":true,
					"partTracks":{"main":[{"time":0,"transform":{}},{"time":10,"transform":{"translation":[0,2,0],"scale":[3,3,3]}}]},
					"morphTracks":{"rise":[{"time":0,"value":0},{"time":10,"value":1}]}}]}
					"""));
			model.prepare();
			model.reset();
			model.measure(true);
			var staticFrame = model.sampleLocal(0);
			model.updateTick(1);

			if (staticFrame != model.sampleLocal(0.5f) || model.sampledFrame() != staticFrame) {
				throw new IllegalStateException("无片段静态几何因时钟推进重复求值。");
			}

			var timeline = new Timeline();
			var track = (PhotonModelClipTrack) LcPhotonModelOutputTypes.MODEL_ANIMATION.create();
			track.targetId(model.id());
			var clip = new PhotonModelClip(0, 10, 1);
			clip.animation = "grow";
			clip.loop = true;
			clip.completion = PhotonModelClip.Completion.HOLD;
			track.clips().add(clip);
			timeline.tracks().add(track);
			var runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(model)), timeline));
			runtime.timelinePlayer.setAudioDispatch(false);
			runtime.timelinePlayer.setSignalDispatch(false);
			runtime.timelinePlayer.begin(new FXProjectEffectExecutor(new TrackedDummyWorld()));
			runtime.timelinePlayer.tick(5);
			runtime.timelinePlayer.tick(0);
			requirePoint(model, 0, 1, 1);
			var position = ConfigPropertyType.fromBinding(Objects.requireNonNull(model.dynamicBinding("parts.main.position")));
			position.apply(model, new float[]{5, 0, 0});
			requirePoint(model, 5, 0, 1);
			model.slot("parts.main.position").clear();
			var morph = ConfigPropertyType.fromBinding(Objects.requireNonNull(model.dynamicBinding("morphs.rise.weight")));
			morph.apply(model, new float[]{0});
			requirePoint(model, 0, 1, 0);
			model.slot("morphs.rise.weight").clear();
			runtime.timelinePlayer.tick(5);
			runtime.timelinePlayer.tick(0);
			requirePoint(model, 0, 2, 3);
			var owner = new Object();
			model.playback(owner, new PhotonModelClip.Sample("grow", 0, false, false, 1, 1));
			requirePoint(model, 0, 0, 0);
			model.playback(owner, null);
			requirePoint(model, 0, 2, 3);
			runtime.timelinePlayer.stop();
			requirePoint(model, 0, 0, 0);
			runtime.destroy(true);
			return new Result("runtime_model_clip_fixture", true, "原生轨道回调、TRS/Morph槽、HOLD末姿态、独立贡献释放与静态缓存通过；未计作可见画面验收");
		} catch (IOException | RuntimeException failure) {
			return new Result("runtime_model_clip_fixture", false, failure.toString());
		}
	}

	private static void requirePoint(PhotonModelOutput model, float x, float y, float z) {
		var frame = Objects.requireNonNull(model.sampleLocal(0));
		var point = frame.groups().getFirst().vertices().getFirst().position();

		if (point.x() != x || point.y() != y || point.z() != z) {
			throw new IllegalStateException("模型局部帧不符：" + point + "，期望[" + x + "," + y + "," + z + "]");
		}
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
