package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig.Source;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;
import org.unitego.lobecorp.registry.client.photon.LcPhotonModelOutputTypes;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

/// 完整 wrapper 仍现编码；仅当前模型的规范字段恢复绕过通用字段反射，复杂 renderer 保留原生恢复。
public class PhotonModelRuntimeCopyUtil {
	public static final String NATIVE_MODEL_COPY_PROPERTY = "lobecorp.photon.bench.nativeModelCopy";
	private static final boolean NATIVE_MODEL_COPY = Boolean.getBoolean(NATIVE_MODEL_COPY_PROPERTY);
	private static final String[] BOOLEAN_FIELDS = {"flipObjV", "enabled", "looping", "paused", "useReferenceDimensions", "overrideRenderer"};
	private static final String[] INT_FIELDS = {"duration", "startDelay", "color"};
	private static final String[] FLOAT_FIELDS = {"playbackSpeed", "animationOffset", "length", "width", "opacity"};
	private static final String[] STRING_FIELDS = {"file", "snapshotSource", "clip"};

	public static @Nullable IFXObject deepCopy(PhotonModelOutput source) {
		var encoded = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).result().orElse(null);
		if (encoded == null) {
			return null;
		}

		return nativeModelCopyEnabled() ? IFXObject.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null)
				: copyEncoded(source, encoded);
	}

	public static boolean nativeModelCopyEnabled() {
		return NATIVE_MODEL_COPY;
	}

	/// 微对照强制 typed 候选路径，仍保留所有原生回退；不受整模型 native 控制影响。
	public static @Nullable IFXObject typedCopy(PhotonModelOutput source) {
		var encoded = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).result().orElse(null);
		return encoded == null ? null : copyEncoded(source, encoded);
	}

	/// encoded 必须是 source 当前编码；独立测量恢复阶段，不保留其可变引用。
    public static @Nullable IFXObject copyEncoded(PhotonModelOutput source, Tag encoded) {
        if (!supportsEncoded(source, encoded)) {
            return IFXObject.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
        }

        var data = ((CompoundTag) encoded).getCompoundOrEmpty("data");
        CompoundTag config;

        try {
            config = PhotonModelAssetUtil.borrowCanonicalConfig(data);
        } catch (IOException invalid) {
            return null;
        }

        var copy = new PhotonModelOutput();
        copyConfig(config, copy.config);
        PhotonRuntimeCopyUtil.copyBase(source, copy);
        copy.rememberSource(data);
        return copy;
    }

	/// 只接受当前编码器产出的规范形态；null、扩展类和存在原生规范化差异的值走完整原生路径。
	public static boolean supportsEncoded(PhotonModelOutput source, Tag encoded) {
		if (source.getClass() != PhotonModelOutput.class || source.config.getClass() != PhotonModelOutputConfig.class
				|| !PhotonRuntimeCopyUtil.eligibleBase(source)
				|| !(source.transform().localRotation().lengthSquared() > 0)
				|| !Float.isFinite(source.transform().localRotation().lengthSquared())
				|| !(encoded instanceof CompoundTag wrapper)
				|| !wrapper.getStringOr("type", "").equals(LcPhotonModelOutputTypes.TYPE_NAME)
				|| !(wrapper.get("data") instanceof CompoundTag data)
				|| !(data.get("config") instanceof CompoundTag config)) {
			return false;
		}

		for (var key : BOOLEAN_FIELDS) {
			if (!(config.get(key) instanceof ByteTag)) {
				return false;
			}
		}

		for (var key : INT_FIELDS) {
			if (!(config.get(key) instanceof IntTag)) {
				return false;
			}
		}

		for (var key : FLOAT_FIELDS) {
			if (!(config.get(key) instanceof FloatTag number) || !Float.isFinite(number.floatValue())) {
				return false;
			}
		}

		for (var key : STRING_FIELDS) {
			if (noncanonicalString(config.get(key))) {
				return false;
			}
		}

		return enumName(config.get("source"), Source.values()) && enumName(config.get("anchor"), Anchor.values())
				&& config.getIntOr("duration", 0) > 0 && config.getIntOr("startDelay", -1) >= 0
				&& config.getFloatOr("playbackSpeed", -1) >= 0 && config.getFloatOr("length", -1) >= 0
				&& config.getFloatOr("width", -1) >= 0 && config.getFloatOr("opacity", -1) >= 0
				&& config.getFloatOr("opacity", 2) <= 1
				&& (config.get("assetJson") instanceof ByteArrayTag || config.get("assetJson") instanceof StringTag)
				&& config.get("assetMaterialDependencies") instanceof CompoundTag
				&& config.get("modelRenderer") instanceof CompoundTag && config.get("renderer") instanceof CompoundTag
				&& canonicalResource(config.get("resource")) && canonicalDependencies(config.get("assetDependencies"));
	}

	private static boolean noncanonicalString(@Nullable Tag tag) {
		return !(tag instanceof StringTag(var value)) || value.equals(LDLibExtraCodecs.NULL_STRING);
	}

	private static boolean enumName(@Nullable Tag tag, Enum<?>[] values) {
		if (tag instanceof StringTag(var name)) {
			for (var value : values) {
				if (value.name().equals(name)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean canonicalResource(@Nullable Tag tag) {
		return tag == null || LDLibExtraCodecs.isEmptyOrStringNull(NbtOps.INSTANCE, tag)
				|| Identifier.CODEC.parse(NbtOps.INSTANCE, tag).result().isPresent();
	}

	private static boolean canonicalDependencies(@Nullable Tag tag) {
		if (!(tag instanceof ListTag list)) {
			return false;
		}

		for (var entry : list) {
			if (noncanonicalString(entry) || Identifier.CODEC.parse(NbtOps.INSTANCE, entry).result().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private static void copyConfig(CompoundTag data, PhotonModelOutputConfig target) {
		target.source = Source.valueOf(data.getStringOr("source", ""));
		target.resource = data.contains("resource") && !LDLibExtraCodecs.isEmptyOrStringNull(NbtOps.INSTANCE, data.get("resource"))
				? Identifier.CODEC.parse(NbtOps.INSTANCE, data.get("resource")).getOrThrow() : null;
		target.file = data.getStringOr("file", "");
		target.flipObjV = data.getBooleanOr("flipObjV", false);
		target.enabled = data.getBooleanOr("enabled", true);
		target.duration = data.getIntOr("duration", PhotonModelOutputConfig.DEFAULT_DURATION);
		target.startDelay = data.getIntOr("startDelay", 0);
		target.looping = data.getBooleanOr("looping", true);
		target.paused = data.getBooleanOr("paused", false);
		target.snapshotSource = data.getStringOr("snapshotSource", "");
		target.clip = data.getStringOr("clip", "");
		target.playbackSpeed = data.getFloatOr("playbackSpeed", 1);
		target.animationOffset = data.getFloatOr("animationOffset", 0);
		target.useReferenceDimensions = data.getBooleanOr("useReferenceDimensions", true);
		target.length = data.getFloatOr("length", 1);
		target.width = data.getFloatOr("width", 1);
		target.anchor = Anchor.valueOf(data.getStringOr("anchor", ""));
		target.opacity = data.getFloatOr("opacity", 1);
		target.color = data.getIntOr("color", PhotonModelOutputConfig.DEFAULT_COLOR);
		target.overrideRenderer = data.getBooleanOr("overrideRenderer", false);
        var provider = Platform.getFrozenRegistry();
        // 复杂访问器仍接收独占子树，不把借读的可变 Tag 交给恢复结果。
        PersistedParser.deserializeNBT(data.getCompoundOrEmpty("modelRenderer").copy(), target.modelRenderer, provider);
        PersistedParser.deserializeNBT(data.getCompoundOrEmpty("renderer").copy(), target.renderer, provider);
        // unknown 合并可能保留旧依赖项，必须从合并后的 payload 恢复，不能照搬 source.config。
        target.assetMaterialDependencies = data.getCompoundOrEmpty("assetMaterialDependencies").copy();
		target.assetDependencies.clear();

		for (var entry : (ListTag) Objects.requireNonNull(data.get("assetDependencies"))) {
			target.assetDependencies.add(Identifier.CODEC.parse(NbtOps.INSTANCE, entry).getOrThrow());
		}

		target.setAssetSnapshot(data.get(PhotonModelOutputConfig.ASSET_KEY));
	}
}
