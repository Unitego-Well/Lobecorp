package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnParameters;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnShape;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

/// 仅复制已核对的自有持久类型；完整 wrapper 每次现编码，扩展类型仍交原生解码。
public class PhotonRuntimeCopyUtil {
	public static @Nullable IFXObject deepCopy(IFXObject source) {
		var encoded = IFXObject.CODEC.encodeStart(NbtOps.INSTANCE, source).result().orElse(null);

		if (encoded == null) {
			return null;
		}

		if (source.getClass() != PhotonChildSpawner.class && source.getClass() != PhotonNamedController.class) {
			return parse(encoded);
		}

		if (!(encoded instanceof CompoundTag wrapper) || !(wrapper.get("data") instanceof CompoundTag data)
				|| !eligibleBase(source)) {
			return parse(encoded);
		}

		if (source.getClass() == PhotonChildSpawner.class) {
			var spawner = (PhotonChildSpawner) source;

			if (!supportsConfig(spawner.config) || !matchesTemplate(spawner.config, data)) {
				return parse(encoded);
			}

			try {
				PhotonSpawnConfigUtil.validate(data);
			} catch (RuntimeException invalid) {
				return null;
			}

			var copy = new PhotonChildSpawner();
			copyConfig(spawner.config, copy.config);
			copyBase(source, copy);
			copy.rememberSource(data);
			return copy;
		}

		if (data.getIntOr("version", 0) != 0 || !(data.get("definition") instanceof CompoundTag definition)) {
			return null;
		}

		try {
			PhotonNamedCodecUtil.read(definition);
		} catch (IOException invalid) {
			return null;
		}

		var copy = new PhotonNamedController();
		copy.definition(definition);
		copyBase(source, copy);
		copy.rememberSource(data);
		return copy;
	}

	private static @Nullable IFXObject parse(Tag encoded) {
		return IFXObject.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
	}

	public static boolean eligibleBase(IFXObject source) {
		var transform = source.transform();
		return source.getName() != null && !source.getName().equals(LDLibExtraCodecs.NULL_STRING)
				&& transform.id() != null && transform.localPosition().isFinite()
				&& transform.localRotation().isFinite() && transform.localScale().isFinite();
	}

	private static boolean matchesTemplate(PhotonSpawnConfig config, CompoundTag data) {
		var template = data.getCompoundOrEmpty("config").getCompoundOrEmpty("template");
		var id = config.template.getTransformId();
		// 空 TransformRef 编码为空 map；unknown 合并可能保留旧 uuid，须让原生读取它。
		return id == null ? !template.contains("uuid") : Objects.equals(id.toString(), template.getString("uuid").orElse(null));
	}

	public static void copyBase(IFXObject source, IFXObject target) {
		target.setName(source.getName());
		var from = source.transform();
		var to = target.transform();
		// set(local=true) 本身会借用向量，随后显式脱离引用；不连接真实 parent/children。
		to.set(from, true);
		to.localPosition(new Vector3f(from.localPosition()));
		// 与原生 ExtraCodecs.QUATERNIONF_COMPONENTS 解码保持相同归一化。
		to.localRotation(new Quaternionf(from.localRotation()).normalize());
		to.localScale(new Vector3f(from.localScale()));
		to._setInternalID(from.id());
		to._setInternalParentID(from._getInternalParentID());
		to._setInternalChildID(from._getInternalChildID());
	}

	/// Curve.copy 丢失 lockControlPoint；其它未核实扩展函数也只能走原生 wrapper parse。
	public static boolean supportsFunction(@Nullable NumberFunction function) {
		if (function == null) {
			return false;
		}

		if (function.getClass() == Constant.class) {
			return immutableNumber(((Constant) function).getNumber());
		}

		if (function.getClass() == Color.class) {
			return ((Color) function).getNumber() instanceof Integer;
		}

		if (function.getClass() == RandomConstant.class) {
			var random = (RandomConstant) function;
			return immutableNumber(random.getA()) && immutableNumber(random.getB());
		}

		if (function.getClass() == Gradient.class) {
			var colors = ((Gradient) function).getGradientColor();
			return colors.getClass() == GradientColor.class
					&& colors.getAP().stream().allMatch(point -> point != null && point.isFinite())
					&& colors.getRgbP().stream().allMatch(point -> point != null && point.isFinite());
		}

		return false;
	}

	private static boolean immutableNumber(@Nullable Number value) {
		return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
				|| value instanceof Float || value instanceof Double;
	}

	public static boolean supportsConfig(PhotonSpawnConfig source) {
		var parameters = source.parameters;
		return source.getClass() == PhotonSpawnConfig.class && source.shape.getClass() == PhotonSpawnShape.class
				&& parameters.getClass() == PhotonSpawnParameters.class && source.template.getClass() == TransformRef.class
				&& source.source != null && source.mode != null && source.endBehavior != null && source.shape.type != null
				&& source.shape.size != null && source.shape.size.isFinite()
				&& source.shape.offset != null && source.shape.offset.isFinite()
				&& parameters.targetName != null && !parameters.targetName.equals(LDLibExtraCodecs.NULL_STRING)
				&& supportsFunction(source.count) && supportsFunction(source.rate) && supportsFunction(source.probability)
				&& supportsFunction(source.launchSpeed) && supportsFunction(parameters.lifetime)
				&& parameters.size != null && supportsFunction(parameters.size.x)
				&& supportsFunction(parameters.size.y) && supportsFunction(parameters.size.z)
				&& supportsFunction(parameters.color) && supportsFunction(parameters.speed)
				&& supportsFunction(parameters.beamLength) && supportsFunction(parameters.beamWidth);
	}

	/// 调用方先确认 supportsConfig；所有可变配置独占，不读取或复制运行覆盖槽。
	public static void copyConfig(PhotonSpawnConfig source, PhotonSpawnConfig target) {
		if (!supportsConfig(source) || source == target) {
			throw new IllegalArgumentException("配置不属于已核实的独占复制范围。");
		}

		target.enabled = source.enabled;
		target.source = source.source;
		target.template.setTransformId(source.template.getTransformId());
		target.fxLocation = source.fxLocation;
		target.includeChildren = source.includeChildren;
		target.duration = source.duration;
		target.looping = source.looping;
		target.startDelay = source.startDelay;
		target.mode = source.mode;
		target.interval = source.interval;
		target.count = source.count.copy();
		target.rate = source.rate.copy();
		target.probability = source.probability.copy();
		target.maxInstances = source.maxInstances;
		target.maxTotal = source.maxTotal;
		target.maxPerTick = source.maxPerTick;
		target.maxDepth = source.maxDepth;
		target.instanceLifetime = source.instanceLifetime;
		target.endBehavior = source.endBehavior;
		target.clearOnRestart = source.clearOnRestart;
		target.follow = source.follow;
		target.inheritRotation = source.inheritRotation;
		target.inheritScale = source.inheritScale;
		target.inheritColor = source.inheritColor;
		target.inheritVelocity = source.inheritVelocity;
		target.launchSpeed = source.launchSpeed.copy();
		target.shape.type = source.shape.type;
		target.shape.size = new Vector3f(source.shape.size);
		target.shape.offset = new Vector3f(source.shape.offset);
		copyParameters(source, target);
	}

	private static void copyParameters(PhotonSpawnConfig source, PhotonSpawnConfig target) {
		var from = source.parameters;
		var to = target.parameters;
		to.targetName = from.targetName;
		to.overrideLifetime = from.overrideLifetime;
		to.lifetime = from.lifetime.copy();
		to.overrideSize = from.overrideSize;
		to.size = new NumberFunction3(from.size.x.copy(), from.size.y.copy(), from.size.z.copy());
		to.overrideColor = from.overrideColor;
		to.color = from.color.copy();
		to.overrideSpeed = from.overrideSpeed;
		to.speed = from.speed.copy();
		to.overrideBeamLength = from.overrideBeamLength;
		to.beamLength = from.beamLength.copy();
		to.overrideBeamWidth = from.overrideBeamWidth;
		to.beamWidth = from.beamWidth.copy();
		to.playbackSpeed = from.playbackSpeed;
	}
}
