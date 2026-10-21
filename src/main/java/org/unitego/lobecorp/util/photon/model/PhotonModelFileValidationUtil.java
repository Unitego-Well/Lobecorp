package org.unitego.lobecorp.util.photon.model;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
		if (config.contains("source") && (!(config.get("source") instanceof StringTag(
				var source
		)) || !source.equals("EMBEDDED"))) {
			throw new IOException("文件或资源来源模型仍需来源闭合验收，请保留原始数据。");
		}
		if (!(data.get("transform") instanceof CompoundTag transform) || !validIdentity(transform)) {
			throw new IOException("模型对象身份缺失或无效。");
		}

		var definition = definition(config.get("assetJson"), config.getCompoundOrEmpty("assetMaterialDependencies"));
		playback(config, definition);
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

	private static void playback(CompoundTag data, PhotonModelDefinition definition) throws IOException {
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
		if (!clip.isEmpty() && definition.clips().stream().noneMatch(value -> value.id().equals(clip))) {
			throw new IOException("模型片段引用不存在：" + clip);
		}
	}
}
