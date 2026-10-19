package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Binding;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Event;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Input;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Marker;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Parameter;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ParameterType;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Rule;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Collections;

/// 只适配当前第六版库，保留完整原树；未知版本和失效身份不能用随机UUID替换。
public class PhotonNamedCodecUtil {
	public static PhotonNamedLibrary read(CompoundTag source) throws IOException {
		try {
			if (!source.isEmpty() && integer(source, "version", -1) != PhotonNamedLibrary.VERSION) {
				throw new IllegalArgumentException("命名动画库版本不支持。");
			}

			var animations = new ArrayList<Definition>();
			var ids = new HashSet<UUID>();
			var names = new HashSet<String>();

			for (var tag : compounds(source, "animations")) {
				var definition = definition(tag);

				if (!ids.add(definition.id()) || !names.add(definition.name())) {
					throw new IllegalArgumentException("命名动画身份或名称重复。");
				}

				animations.add(definition);
			}

			var rules = new ArrayList<Rule>();

			for (var tag : compounds(source, "rules")) {
				double duration = number(tag, "duration", -1);

				if (duration < 0 && duration != -1) {
					throw new IllegalArgumentException("事件调用时长无效。");
				}

				rules.add(new Rule(enumeration(tag, "event", Event.TRIGGER), text(tag, "animation", ""),
						text(tag, "channel", ""), text(tag, "name", ""), text(tag, "target", ""), duration, inputs(tag)));
			}

			return new PhotonNamedLibrary(source, text(source, "defaultAnimation", ""), animations, rules);
		} catch (RuntimeException invalid) {
			throw new IOException("命名动画库无法按当前协议适配。", invalid);
		}
	}

	private static Definition definition(CompoundTag source) {
		var parameters = new ArrayList<Parameter>();
		var ids = new HashSet<UUID>();
		var names = new HashSet<String>();

		for (var tag : compounds(source, "parameters")) {
			var type = enumeration(tag, "type", ParameterType.NUMBER);
			var parameter = new Parameter(uuid(tag, "id"), text(tag, "name", ""), type,
					floats(tag, "defaults", type.channels()), (float) number(tag, "minimum", -Float.MAX_VALUE),
					(float) number(tag, "maximum", Float.MAX_VALUE));

			if (!ids.add(parameter.id()) || !names.add(parameter.name())) {
				throw new IllegalArgumentException("命名动画形参身份或名称重复。");
			}

			parameters.add(parameter);
		}

		var bindings = new ArrayList<Binding>();

		for (var tag : compounds(source, "bindings")) {
			int component = integer(tag, "component", -1);

			if (component < -1 || component > 3) {
				throw new IllegalArgumentException("命名绑定分量无效。");
			}

			bindings.add(new Binding(uuid(tag, "target"), text(tag, "property", ""), enumeration(tag, "blend", Blend.OVERRIDE),
					enumeration(tag, "source", ValueSource.CURVE), text(tag, "parameter", ""), component,
					finiteFloat(tag, "scale", 1), finiteFloat(tag, "offset", 0),
					text(tag, "scaleParameter", ""), text(tag, "offsetParameter", "")));
		}

		var markers = new ArrayList<Marker>();

		for (var tag : compounds(source, "markers")) {
			double time = number(tag, "time", 0);

			if (time < 0) {
				throw new IllegalArgumentException("命名标记时间为负。");
			}

			if (tag.contains("data") && !(tag.get("data") instanceof CompoundTag)) {
				throw new IllegalArgumentException("命名标记附加数据类型无效。");
			}

			markers.add(new Marker(time, text(tag, "name", ""), tag.getCompoundOrEmpty("data")));
		}

		double duration = number(source, "duration", PhotonNamedLibrary.DEFAULT_DURATION);

		if (duration < 0) {
			throw new IllegalArgumentException("命名动画时长为负。");
		}

		return new Definition(source, uuid(source, "id"), text(source, "name", ""), duration,
				bool(source, "autoDuration", true), bool(source, "autoEnableModules", false), bool(source, "loop", false),
				integer(source, "priority", 0), enumeration(source, "completion", Completion.HOLD),
				text(source, "durationParameter", ""), parameters, compounds(source, "tracks"), bindings, markers);
	}

	public static List<Input> inputs(CompoundTag source) {
		var result = new ArrayList<Input>();

		for (var tag : compounds(source, "inputs")) {
			result.add(new Input(text(tag, "target", ""), text(tag, "source", ""), floats(tag, "value", 1),
					bool(tag, "follow", false)));
		}

		return List.copyOf(result);
	}

	private static UUID uuid(CompoundTag source, String key) {
		var value = text(source, key, "");
		var id = UUID.fromString(value);

		if (!id.toString().equalsIgnoreCase(value)) {
			throw new IllegalArgumentException("命名数据UUID不是完整表示：" + key);
		}

		return id;
	}

	private static List<CompoundTag> compounds(CompoundTag source, String key) {
		if (!source.contains(key)) {
			return List.of();
		}

		if (!(source.get(key) instanceof ListTag list)) {
			throw new IllegalArgumentException("命名数据列表类型无效：" + key);
		}

		var result = new ArrayList<CompoundTag>();

		for (var value : list) {
			if (!(value instanceof CompoundTag compound)) {
				throw new IllegalArgumentException("命名数据列表包含非复合值：" + key);
			}

			result.add(compound.copy());
		}

		return List.copyOf(result);
	}

	private static List<Float> floats(CompoundTag source, String key, int defaultChannels) {
		if (!source.contains(key)) {
			return Collections.nCopies(defaultChannels, key.equals("value") ? 1f : 0f);
		}

		if (!(source.get(key) instanceof ListTag list)) {
			throw new IllegalArgumentException("命名数据向量类型无效：" + key);
		}

		var result = new ArrayList<Float>();

		for (var value : list) {
			if (!(value instanceof NumericTag number) || !Float.isFinite(number.floatValue())) {
				throw new IllegalArgumentException("命名数据向量含无效值：" + key);
			}

			result.add(number.floatValue());
		}

		return List.copyOf(result);
	}

	private static String text(CompoundTag source, String key, String fallback) {
		if (!source.contains(key)) {
			return fallback;
		}

		if (!(source.get(key) instanceof StringTag(var text))) {
			throw new IllegalArgumentException("命名数据文本类型无效：" + key);
		}

		return text;
	}

	private static boolean bool(CompoundTag source, String key, boolean fallback) {
		if (!source.contains(key)) {
			return fallback;
		}

		if (!(source.get(key) instanceof ByteTag value) || value.byteValue() != 0 && value.byteValue() != 1) {
			throw new IllegalArgumentException("命名数据布尔值无效：" + key);
		}

		return value.byteValue() != 0;
	}

	private static double number(CompoundTag source, String key, double fallback) {
		if (!source.contains(key)) {
			return fallback;
		}

		if (!(source.get(key) instanceof NumericTag number) || !Double.isFinite(number.doubleValue())) {
			throw new IllegalArgumentException("命名数据数值无效：" + key);
		}

		return number.doubleValue();
	}

	private static float finiteFloat(CompoundTag source, String key, float fallback) {
		float value = (float) number(source, key, fallback);

		if (!Float.isFinite(value)) {
			throw new IllegalArgumentException("命名数据数值超出当前精度：" + key);
		}

		return value;
	}

	private static int integer(CompoundTag source, String key, int fallback) {
		double value = number(source, key, fallback);

		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || Math.rint(value) != value) {
			throw new IllegalArgumentException("命名数据整数无效：" + key);
		}

		return (int) value;
	}

	private static <E extends Enum<E>> E enumeration(CompoundTag source, String key, E fallback) {
		return Enum.valueOf(fallback.getDeclaringClass(), text(source, key, fallback.name()));
	}
}
