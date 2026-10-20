package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// 制作列表与嵌套轨道的原树事务；路径只在调用方已锁定的工程revision内有效。
public class PhotonNamedTrackAuthoringUtil {
	/// 与外部轨道读取预算一致，限制制作路径的嵌套深度。
	private static final int MAX_DEPTH = 64;

	public static CompoundTag definition(Document source, UUID id, boolean projectTest) {
		if (projectTest) {
			var preview = source.preview();
			if (preview == null || !preview.getStringOr("id", "").equals(id.toString())) {
				throw new IllegalArgumentException("工程测试身份已失效：" + id);
			}

			return preview;
		}

		try {
			return PhotonNamedCodecUtil.read(source.library()).animations().stream().filter(value -> value.id().equals(id))
					.findFirst().orElseThrow(() -> new IllegalArgumentException("命名动画身份已失效：" + id)).source();
		} catch (IOException invalid) {
			throw new IllegalArgumentException("命名制作库无法读取。", invalid);
		}
	}

	/// path由字段名和非负整数下标组成；只允许定义内已存在的列表，不自动创建拼错的容器。
	public static Document edit(Document source, UUID id, boolean projectTest, List<Object> path,
	                            String action, int index, int destination, CompoundTag value) {
		var definition = definition(source, id, projectTest);
		if (path.isEmpty() || path.size() > MAX_DEPTH
				|| !(path.getFirst() instanceof String field) || !List.of("tracks", "parameters", "bindings", "markers").contains(field)) {
			throw new IllegalArgumentException("制作列表路径无效。");
		}

		if (path.size() == 1 && !definition.contains(field)) {
			definition.put(field, new ListTag());
		}

		var list = resolve(definition, path);
		if (action.equals("duplicate") && !field.equals("tracks")) {
			throw new IllegalArgumentException("复制操作只适用于轨道列表。");
		}
		mutate(list, action, index, destination, value);
		if (projectTest) {
			return new Document(source.library(), definition);
		}

		return PhotonNamedAuthoringUtil.replaceDefinition(source, id, definition);
	}

	private static ListTag resolve(CompoundTag definition, List<Object> path) {
		Tag current = definition;
		for (var segment : path) {
			if (segment instanceof String field && current instanceof CompoundTag compound) {
				current = compound.get(field);
			} else if (segment instanceof Integer index && current instanceof ListTag list && index >= 0 && index < list.size()) {
				current = list.get(index);
			} else {
				throw new IllegalArgumentException("制作列表路径不存在：" + path);
			}
		}

		if (!(current instanceof ListTag list)) {
			throw new IllegalArgumentException("制作路径未指向列表：" + path);
		}

		return list;
	}

	private static void mutate(ListTag list, String action, int index, int destination, CompoundTag value) {
		if (index < 0 || index > list.size() || !action.equals("insert") && index == list.size()) {
			throw new IllegalArgumentException("制作列表下标超出范围。");
		}

		switch (action) {
			case "insert" -> list.add(index, value.copy());
			case "remove" -> list.remove(index);
			case "duplicate" -> {
				if (!(list.get(index) instanceof CompoundTag track)) {
					throw new IllegalArgumentException("复制目标必须为轨道复合标签。");
				}
				list.add(index + 1, PhotonTrackCopyUtil.copy(track));
			}
			case "patch" -> {
				if (!(list.get(index) instanceof CompoundTag current)) {
					throw new IllegalArgumentException("局部编辑目标必须为复合标签。");
				}

				if (value.contains("id") && !Objects.equals(current.get("id"), value.get("id"))) {
					throw new IllegalArgumentException("局部编辑不能替换条目身份。");
				}

				list.set(index, merge(current, value));
			}
			case "move" -> {
				if (destination < 0 || destination >= list.size()) {
					throw new IllegalArgumentException("制作排序目标超出范围。");
				}

				var entry = list.remove(index);
				list.add(destination, entry);
			}
			default -> throw new IllegalArgumentException("不支持的制作列表操作：" + action);
		}
	}

	/// 复合字段递归合并，列表显式替换；未给出的字段和NBT数值类型原样保留。
	public static CompoundTag merge(CompoundTag source, CompoundTag patch) {
		var result = source.copy();
		for (var key : patch.keySet()) {
			var value = Objects.requireNonNull(patch.get(key));
			result.put(key, value instanceof CompoundTag child && result.get(key) instanceof CompoundTag previous
					? merge(previous, child) : value.copy());
		}

		return result;
	}
}
