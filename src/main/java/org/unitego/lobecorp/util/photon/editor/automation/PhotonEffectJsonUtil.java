package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

public class PhotonEffectJsonUtil {
	public static JsonObject success(JsonObject data) {
		var result = new JsonObject();
		result.addProperty("ok", true);
		result.add("data", data);
		result.add("error", JsonNull.INSTANCE);
		return result;
	}

	public static JsonObject failure(Throwable failure) {
		var error = new JsonObject();
		error.addProperty("code", failure instanceof PhotonEffectCommandException command ? command.code() : Code.INTERNAL_ERROR.name());
		error.addProperty("message", String.valueOf(failure.getMessage()));
		var result = new JsonObject();
		result.addProperty("ok", false);
		result.add("data", JsonNull.INSTANCE);
		result.add("error", error);
		return result;
	}

	public static String string(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return object.get(key).getAsString();
	}

	public static String optionalString(JsonObject object, String key, String fallback) {
		return object.has(key) ? string(object, key) : fallback;
	}

	public static boolean bool(JsonObject object, String key, boolean fallback) {
		if (!object.has(key)) {
			return fallback;
		}

		if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return object.get(key).getAsBoolean();
	}

	public static JsonObject object(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonObject()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return object.getAsJsonObject(key);
	}

	public static JsonArray array(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonArray()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return object.getAsJsonArray(key);
	}

	public static double number(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		double value = object.get(key).getAsDouble();

		if (!Double.isFinite(value)) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return value;
	}

	public static JsonElement json(Tag value) {
		return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value);
	}

	/// 沿用已有字段的 NBT 数值和数组类型，避免 JSON 将浮点、UUID 或资产字节数组改成其他格式。
	public static Tag tag(JsonElement value, Tag template) {
		if (value == null || value.isJsonNull()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		return switch (template) {
			case CompoundTag compound -> compound(value, compound);
			case ListTag list -> list(value, list);
			case ByteArrayTag _ -> new ByteArrayTag(bytes(value));
			case IntArrayTag _ -> new IntArrayTag(ints(value));
			case LongArrayTag _ -> new LongArrayTag(longs(value));
			case ByteTag _ -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
					? ByteTag.valueOf(value.getAsBoolean()) : ByteTag.valueOf((byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE));
			case ShortTag _ -> ShortTag.valueOf((short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE));
			case IntTag _ -> IntTag.valueOf((int) integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
			case LongTag _ -> LongTag.valueOf(integral(value, Long.MIN_VALUE, Long.MAX_VALUE));
			case FloatTag _ -> FloatTag.valueOf(finiteFloat(value));
			case DoubleTag _ -> DoubleTag.valueOf(finiteDouble(value));
			case StringTag _ -> {
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
					throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
				}

				yield StringTag.valueOf(value.getAsString());
			}
			case null -> JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value);
			default -> throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		};
	}

	private static CompoundTag compound(JsonElement value, CompoundTag template) {
		if (!value.isJsonObject()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		var result = new CompoundTag();

		for (var entry : value.getAsJsonObject().entrySet()) {
			result.put(entry.getKey(), tag(entry.getValue(), template.get(entry.getKey())));
		}

		return result;
	}

	private static ListTag list(JsonElement value, ListTag template) {
		if (!value.isJsonArray()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		var result = new ListTag();
		var values = value.getAsJsonArray();

		for (int i = 0; i < values.size(); i++) {
			result.add(tag(values.get(i), i < template.size() ? template.get(i) : template.isEmpty() ? null : template.getFirst()));
		}

		return result;
	}

	private static byte[] bytes(JsonElement value) {
		var values = value.getAsJsonArray();
		var result = new byte[values.size()];

		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) integral(values.get(i), Byte.MIN_VALUE, Byte.MAX_VALUE);
		}

		return result;
	}

	private static int[] ints(JsonElement value) {
		var values = value.getAsJsonArray();
		var result = new int[values.size()];

		for (int i = 0; i < result.length; i++) {
			result[i] = (int) integral(values.get(i), Integer.MIN_VALUE, Integer.MAX_VALUE);
		}

		return result;
	}

	private static long[] longs(JsonElement value) {
		var values = value.getAsJsonArray();
		var result = new long[values.size()];

		for (int i = 0; i < result.length; i++) {
			result[i] = integral(values.get(i), Long.MIN_VALUE, Long.MAX_VALUE);
		}

		return result;
	}

	private static long integral(JsonElement value, long min, long max) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		try {
			long number = new BigDecimal(value.getAsString()).longValueExact();

			if (number < min || number > max) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
			}

			return number;
		} catch (ArithmeticException | NumberFormatException failure) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}
	}

	private static double finiteDouble(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		return value.getAsDouble();
	}

	private static float finiteFloat(JsonElement value) {
		double number = finiteDouble(value);

		if (Math.abs(number) > Float.MAX_VALUE) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT);
		}

		return (float) number;
	}

	public static void patch(JsonElement root, String pointer, JsonElement value, boolean existingOnly) {
		var parts = pointer(pointer);
		JsonElement parent = root;

		for (var part : parts.subList(0, parts.size() - 1)) {
			parent = child(parent, part);
		}

		var key = parts.getLast();

		if (parent.isJsonObject()) {
			var object = parent.getAsJsonObject();

			if (existingOnly && !object.has(key)) {
				throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, pointer);
			}

			if (value.isJsonNull()) {
				object.remove(key);
			} else {
				object.add(key, value.deepCopy());
			}

			return;
		}

		var array = parent.getAsJsonArray();

		if ("-".equals(key) && !existingOnly) {
			array.add(value.deepCopy());
			return;
		}

		int index = Integer.parseInt(key);

		if (value.isJsonNull()) {
			array.remove(index);
		} else {
			array.set(index, value.deepCopy());
		}
	}

	private static JsonElement child(JsonElement parent, String key) {
		var value = parent.isJsonObject() ? parent.getAsJsonObject().get(key) : parent.getAsJsonArray().get(Integer.parseInt(key));

		if (value == null) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return value;
	}

	private static List<String> pointer(String pointer) {
		if (!pointer.startsWith("/")) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, pointer);
		}

		return Arrays.stream(pointer.substring(1).split("/", -1)).map(part -> part.replace("~1", "/").replace("~0", "~")).toList();
	}

	public static JsonObject schema(Tag value) {
		var result = new JsonObject();
		result.addProperty("nbtType", value.getType().getName());

		if (value instanceof CompoundTag compound) {
			var fields = new JsonObject();
			compound.entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> fields.add(entry.getKey(), schema(entry.getValue())));
			result.add("properties", fields);
		} else if (value instanceof ListTag list && !list.isEmpty()) {
			result.add("items", schema(list.getFirst()));
		} else {
			result.add("value", json(value));
		}

		return result;
	}
}
