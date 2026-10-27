package org.unitego.lobecorp.util.photon.model;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/// 当前内嵌模型文件的结构入口；来源未闭合或字段不受支持时拒绝，不代表整批G1已通过。
public class PhotonModelFileValidationUtil {
	/// 已适配的Photon发布容器版本；不读取会初始化客户端编辑器的FXProject静态字段。
	public static final int DEFINITION_VERSION = 5;

	public static void projectData(CompoundTag data) throws IOException {
		if (data.contains("playback") && !(data.get("playback") instanceof CompoundTag)
				|| data.contains("renderer") && !(data.get("renderer") instanceof CompoundTag)) {
			throw new IOException("模型工程播放或渲染配置类型无效。");
		}

		var definition = definition(data.get("asset"), new CompoundTag());
		playback(data.getCompoundOrEmpty("playback"), definition);
		if (data.contains("emitterData")) {
			if (!(data.get("emitterData") instanceof CompoundTag emitter)) {
				throw new IOException("模型工程对象数据类型无效。");
			}
			emitterData(emitter);
		}
	}

	public static void emitterData(CompoundTag data) throws IOException {
		var adapted = PhotonModelAssetUtil.adaptObject(data);
		var config = adapted.getCompoundOrEmpty("config");
		var source = config.getStringOr("source", "EMBEDDED");

		// 文件来源仍禁止：外部绝对路径无法随资源包分发，保持原验收边界。
		if (source.equals("FILE")) {
			throw new IOException("文件来源模型仍需来源闭合验收，请保留原始数据。");
		}

		if (!source.equals("EMBEDDED") && !source.equals("RESOURCE")) {
			throw new IOException("模型来源不受支持：" + source);
		}

		if (!(data.get("transform") instanceof CompoundTag transform) || !validIdentity(transform)) {
			throw new IOException("模型对象身份缺失或无效。");
		}

		if (source.equals("RESOURCE")) {
			// 资源来源的几何、骨骼与片段都在外部文件，加载期不解析；这里只确认标识存在，
			// 片段引用与实际几何在首次使用时由来源读取路径校验，避免打开工程就同步读盘。
			if (!(config.get("resource") instanceof StringTag(var resource)) || resource.isBlank()) {
				throw new IOException("资源来源模型缺少资源标识。");
			}

			playback(config, null);
			return;
		}

		var definition = definition(config.get("assetJson"), config.getCompoundOrEmpty("assetMaterialDependencies"));
		playback(config, definition);
	}

	/// 旧工程首次加载只允许增加本次生成的对象身份，其他原始字段必须逐项完全相等。
	public static void requireGeneratedEmitterData(CompoundTag original, CompoundTag normalized, CompoundTag generated, UUID objectId) throws IOException {
		if (original.contains("emitterData") || !original.contains("asset")
				|| !(normalized.get("emitterData") instanceof CompoundTag added) || !added.equals(generated)) {
			throw new IOException("模型工程不符合首次生成对象身份的条件。");
		}
		var withoutIdentity = normalized.copy();
		withoutIdentity.remove("emitterData");
		if (!original.equals(withoutIdentity)) {
			throw new IOException("生成模型对象身份时改变了原始工程字段。");
		}

		emitterData(added);
		var config = added.getCompoundOrEmpty("config");
		var transform = added.getCompoundOrEmpty("transform");
		var identity = transform.get("id") instanceof StringTag(var text)
				? UUID.fromString(text) : transform.read("id", UUIDUtil.CODEC).orElseThrow();
		if (!identity.equals(objectId) || !config.getStringOr("source", "").equals("EMBEDDED")
				|| !Objects.equals(original.get("asset"), config.get("assetJson"))) {
			throw new IOException("生成模型对象的身份或内嵌资产与本次加载不一致。");
		}
	}

	private static PhotonModelDefinition definition(Tag asset, CompoundTag dependencies) throws IOException {
		if (asset == null) {
			throw new IOException("模型快照缺失。");
		}
		var definition = PhotonModelAssetUtil.read(asset, dependencies);
		PhotonModelTopologyUtil.prepare(definition);
		return definition;
	}

	private static boolean validIdentity(CompoundTag transform) {
		if (transform.get("id") instanceof StringTag(var text)) {
			try {
				return UUID.fromString(text).toString().equalsIgnoreCase(text);
			} catch (IllegalArgumentException invalid) {
				return false;
			}
		}
		return transform.read("id", UUIDUtil.CODEC).isPresent();
	}

	private static void playback(CompoundTag data, @Nullable PhotonModelDefinition definition) throws IOException {
		for (var key : List.of("enabled", "looping", "paused")) {
			if (data.contains(key) && (!(data.get(key) instanceof ByteTag value) || value.byteValue() < 0 || value.byteValue() > 1)) {
				throw new IOException("模型布尔字段类型无效：" + key);
			}
		}
		for (var key : List.of("duration", "startDelay")) {
			if (data.contains(key) && (!(data.get(key) instanceof IntTag value) || value.intValue() < (key.equals("duration") ? 1 : 0))) {
				throw new IOException("模型整数播放字段无效：" + key);
			}
		}
		for (var key : List.of("playbackSpeed", "animationOffset")) {
			if (data.contains(key) && (!(data.get(key) instanceof NumericTag value) || !Double.isFinite(value.doubleValue())
					|| key.equals("playbackSpeed") && value.doubleValue() < 0)) {
				throw new IOException("模型数值播放字段无效：" + key);
			}
		}
		if (data.contains("clip") && !(data.get("clip") instanceof StringTag)) {
			throw new IOException("模型片段标识类型无效。");
		}
		var clip = data.getStringOr("clip", "");
		if (!clip.isEmpty() && definition != null
				&& definition.clips().stream().noneMatch(value -> value.id().equals(clip))) {
			throw new IOException("模型片段引用不存在：" + clip);
		}
	}
}
