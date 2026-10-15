package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.EnumAccessor;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3Config;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;

import java.util.Arrays;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelPlaybackConfig;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class PhotonEffectSchemaUtil {
	/// 元数据只沿已注册对象的声明类型展开，不访问实例私有字段；限制递归防止类型自引用。
	private static final int MAX_DEPTH = 8;

	public static JsonObject describe(Class<?> type, Tag data) {
		return describe(type, data, "", new HashSet<>(), 0);
	}

	private static JsonObject describe(Class<?> type, Tag data, String path, Set<Class<?>> parents, int depth) {
		var result = base(type, data, path);

		if (type.isEnum()) {
			var values = new JsonArray();

			for (var constant : type.getEnumConstants()) {
				values.add(EnumAccessor.getEnumName((Enum<?>) constant));
			}

			result.add("enum", values);
			return result;
		}

		if (depth >= MAX_DEPTH || type.isPrimitive() || type == String.class || !parents.add(type)) {
			return result;
		}

		var fields = new JsonObject();

		for (var owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
			for (var field : owner.getDeclaredFields()) {
				var key = PersistedParser.persistedKey(field);

				if (Modifier.isStatic(field.getModifiers()) || key == null) {
					continue;
				}

				boolean flat = PersistedParser.isPersistedFlat(field);
				var child = flat ? data : data instanceof CompoundTag compound ? compound.get(key) : null;
				var childPath = flat ? path : path + "/" + key.replace("~", "~0").replace("/", "~1");
				var metadata = describe(field.getType(), child, childPath, new HashSet<>(parents), depth + 1);
				metadata.addProperty("field", field.getName());
				metadata.addProperty("flattened", flat);
				metadata.addProperty("editable", child != null && !"id".equals(field.getName()));
				annotate(metadata, field, child);
				fields.add(key, metadata);
			}
		}

		if (type == PhotonModelEmitterConfig.class || type == PhotonModelPlaybackConfig.class) {
			modelBounds(fields);
		}

		result.add("properties", fields);
		return result;
	}

	private static JsonObject base(Class<?> type, Tag data, String path) {
		var result = new JsonObject();
		result.addProperty("javaType", type.getTypeName());
		result.addProperty("path", path);
		result.addProperty("editable", data != null);
		result.add("nativeType", data == null ? JsonNull.INSTANCE : new JsonPrimitive(data.getType().getName()));
		result.add("value", data == null ? JsonNull.INSTANCE : PhotonEffectJsonUtil.json(data));
		result.add("min", JsonNull.INSTANCE);
		result.add("max", JsonNull.INSTANCE);
		result.add("unit", JsonNull.INSTANCE);
		return result;
	}

	private static void annotate(JsonObject metadata, Field field, Tag data) {
		var configurable = field.getAnnotation(Configurable.class);

		if (configurable != null) {
			metadata.addProperty("labelKey", configurable.name());
			metadata.addProperty("label", Component.translatable(configurable.name()).getString());
			var tips = new JsonArray();

			for (var tip : configurable.tips()) {
				tips.add(Component.translatable(tip).getString());
			}

			metadata.add("tips", tips);
		}

		var number = field.getAnnotation(ConfigNumber.class);

		if (number != null && number.range().length == 2) {
			metadata.addProperty("min", number.range()[0]);
			metadata.addProperty("max", number.range()[1]);
			metadata.addProperty("wheel", number.wheel());
			metadata.addProperty("constraintSource", ConfigNumber.class.getSimpleName());
		}

		var function = field.getAnnotation(NumberFunctionConfig.class);

		if (function != null) {
			numberFunction(metadata, data, function);
		}

		var vector = field.getAnnotation(NumberFunction3Config.class);

		if (vector != null && data instanceof CompoundTag axes) {
			var properties = new JsonObject();
			var names = List.of("x", "y", "z");

			for (int i = 0; i < names.size(); i++) {
				var name = names.get(i);
				var axis = base(NumberFunction.class, axes.get(name), metadata.get("path").getAsString() + "/" + name);
				numberFunction(axis, axes.get(name), vector.xyz().length == names.size() ? vector.xyz()[i] : vector.common());
				properties.add(name, axis);
			}

			metadata.add("properties", properties);
		}

		var selector = field.getAnnotation(ConfigSelector.class);

		if (selector != null && selector.candidate().length > 0) {
			var candidates = new JsonArray();

			for (var candidate : selector.candidate()) {
				if (!metadata.has("enum") || metadata.getAsJsonArray("enum").contains(new JsonPrimitive(candidate))) {
					candidates.add(candidate);
				}
			}

			metadata.add("enum", candidates);
		}
	}

	private static void numberFunction(JsonObject metadata, Tag data, NumberFunctionConfig config) {
		var allowed = new JsonArray();
		Arrays.stream(config.types()).forEach(type -> allowed.add(type.getName()));
		metadata.add("allowedFunctionClasses", allowed);
		var allowedTypes = new JsonArray();

		for (var entry : PhotonRegistries.NUMBER_FUNCTIONS.entries()) {
			if (Arrays.stream(config.types()).anyMatch(type -> type.isAssignableFrom(entry.getValue().clazz()))) {
				allowedTypes.add(entry.getKey());
			}
		}

		metadata.add("allowedFunctionTypes", allowedTypes);

		if (!(data instanceof CompoundTag wrapper)) {
			return;
		}

		var function = NumberFunction.deserializeWrapper(wrapper);

		if (function == null) {
			metadata.addProperty("functionAllowed", false);
			return;
		}

		metadata.addProperty("functionAllowed", Arrays.stream(config.types()).anyMatch(type -> type.isInstance(function)));
		var path = metadata.get("path").getAsString() + "/data";
		var nested = describe(function.getClass(), wrapper.getCompoundOrEmpty("data"), path, new HashSet<>(), 0);
		var properties = new JsonObject();
		properties.add("data", nested);
		var functionType = base(String.class, wrapper.get("type"), metadata.get("path").getAsString() + "/type");
		functionType.add("enum", allowedTypes.deepCopy());
		properties.add("type", functionType);
		metadata.add("properties", properties);

		if (function instanceof Constant || function instanceof RandomConstant) {
			for (var name : List.of("number", "a", "b")) {
				if (!nested.getAsJsonObject("properties").has(name)) {
					continue;
				}

				var value = nested.getAsJsonObject("properties").getAsJsonObject(name);
				value.addProperty("min", config.min());
				value.addProperty("max", config.max());
				value.addProperty("wheel", config.wheelDur());
				value.addProperty("numberType", config.numberType().name());
				value.addProperty("constraintSource", NumberFunctionConfig.class.getSimpleName());
			}
		}
	}

	/// 自有配置的范围来自其真实手写配置器，不在 MCP 层复制业务数值。
	private static void modelBounds(JsonObject fields) {
		var group = new ConfiguratorGroup();
		new PhotonModelEmitterConfig().buildConfigurator(group);
		var labels = Map.of("duration", "duration", "startDelay", "start_delay", "playbackSpeed", "playback_speed",
				"animationOffset", "animation_offset", "length", "length", "width", "width", "opacity", "opacity");

		for (var entry : labels.entrySet()) {
			if (!fields.has(entry.getKey())) {
				continue;
			}

			var metadata = fields.getAsJsonObject(entry.getKey());
			var label = Component.translatable(PhotonModelTextUtil.key(entry.getValue())).getString();
			applyBounds(group, label, metadata);
		}

		for (var key : List.of("duration", "startDelay", "animationOffset")) {
			if (fields.has(key)) {
				fields.getAsJsonObject(key).addProperty("unit", "tick");
			}
		}

		if (fields.has("playbackSpeed")) {
			fields.getAsJsonObject("playbackSpeed").addProperty("unit", "multiplier");
		}
	}

	private static boolean applyBounds(ConfiguratorGroup group, String label, JsonObject metadata) {
		for (var configurator : group.getConfigurators()) {
			if (configurator instanceof NumberConfigurator number && configurator.getLabel().getString().equals(label)) {
				metadata.add("min", number.getMin() == null ? JsonNull.INSTANCE : new JsonPrimitive(number.getMin()));
				metadata.add("max", number.getMax() == null ? JsonNull.INSTANCE : new JsonPrimitive(number.getMax()));
				metadata.add("wheel", number.getWheel() == null ? JsonNull.INSTANCE : new JsonPrimitive(number.getWheel()));
				metadata.addProperty("constraintSource", PhotonModelEmitterConfig.class.getSimpleName());
				return true;
			}

			if (configurator instanceof ConfiguratorGroup child && applyBounds(child, label, metadata)) {
				return true;
			}
		}

		return false;
	}

	public static void validate(Class<?> type, Tag data) {
		validateMetadata(describe(type, data));
	}

	private static void validateMetadata(JsonObject metadata) {
		if (metadata.has("functionAllowed") && !metadata.get("functionAllowed").getAsBoolean()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, metadata.get("path").getAsString());
		}

		var value = metadata.get("value");

		if (value != null && !value.isJsonNull()) {
			if (metadata.has("enum") && value.isJsonPrimitive() && !metadata.getAsJsonArray("enum").contains(value)) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, metadata.get("path").getAsString());
			}

			if (!metadata.get("min").isJsonNull() || !metadata.get("max").isJsonNull()) {
				validateNumbers(value, metadata);
			}
		}

		if (metadata.has("properties")) {
			for (var field : metadata.getAsJsonObject("properties").entrySet()) {
				validateMetadata(field.getValue().getAsJsonObject());
			}
		}
	}

	private static void validateNumbers(JsonElement value, JsonObject metadata) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			double number = value.getAsDouble();

			if (!Double.isFinite(number) || !metadata.get("min").isJsonNull() && number < metadata.get("min").getAsDouble()
					|| !metadata.get("max").isJsonNull() && number > metadata.get("max").getAsDouble()) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, metadata.get("path").getAsString());
			}
		} else if (value.isJsonArray()) {
			value.getAsJsonArray().forEach(child -> validateNumbers(child, metadata));
		} else if (value.isJsonObject()) {
			value.getAsJsonObject().entrySet().forEach(entry -> validateNumbers(entry.getValue(), metadata));
		}
	}

}
