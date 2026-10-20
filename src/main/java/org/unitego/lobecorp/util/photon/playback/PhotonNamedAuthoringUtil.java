package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 对制作原树执行无副作用编辑；工程测试调用与库内名称引用作为同一事务返回。
public class PhotonNamedAuthoringUtil {
	public record Document(CompoundTag library, @Nullable CompoundTag preview) {
		public Document {
			library = library.copy();
			preview = preview == null ? null : preview.copy();
			checked(library);
			if (preview != null) {
				preview = PhotonProjectPreviewUtil.checkedCopy(preview);
			}
		}

		@Override
		public CompoundTag library() {
			return library.copy();
		}

		@Override
		public @Nullable CompoundTag preview() {
			return preview == null ? null : preview.copy();
		}
	}

	public static Document add(Document source, String name) {
		var library = source.library();
		var definition = PhotonProjectPreviewUtil.create();
		definition.putString("name", requireName(name));
		var animations = animations(library);
		animations.add(definition);
		library.putInt("version", PhotonNamedLibrary.VERSION);
		library.put("animations", animations);
		return new Document(library, source.preview());
	}

	public static Document duplicate(Document source, UUID id, String name) {
		var library = source.library();
		var animations = animations(library);
		int index = index(library, id);
		var definition = ((CompoundTag) animations.get(index)).copy();
		definition.putString("id", UUID.randomUUID().toString());
		definition.putString("name", requireName(name));
		animations.add(index + 1, definition);
		return new Document(library, source.preview());
	}

	public static Document move(Document source, UUID id, int destination) {
		var library = source.library();
		var animations = animations(library);
		if (destination < 0 || destination >= animations.size()) {
			throw new IllegalArgumentException("动画排序目标超出范围。");
		}

		var definition = animations.remove(index(library, id));
		animations.add(destination, definition);
		return new Document(library, source.preview());
	}

	/// 属性、参数、绑定和轨道的完整候选原树；重命名必须走引用更新事务。
	public static Document replaceDefinition(Document source, UUID id, CompoundTag replacement) {
		var library = source.library();
		int index = index(library, id);
		var current = (CompoundTag) animations(library).get(index);
		if (!current.getStringOr("id", "").equals(replacement.getStringOr("id", ""))
				|| !current.getStringOr("name", "").equals(replacement.getStringOr("name", ""))) {
			throw new IllegalArgumentException("属性事务不能替换动画身份或绕过重命名。");
		}

		animations(library).set(index, replacement.copy());
		return new Document(library, source.preview());
	}

	public static Document rename(Document source, UUID id, String name) {
		var library = source.library();
		var definition = (CompoundTag) animations(library).get(index(library, id));
		String previous = definition.getStringOr("name", "");
		if (previous.equals(name)) {
			return source;
		}

		definition.putString("name", requireName(name));
		if (previous.isEmpty()) {
			return new Document(library, source.preview());
		}

		if (library.getStringOr("defaultAnimation", "").equals(previous)) {
			library.putString("defaultAnimation", name);
		}

		for (var raw : library.getListOrEmpty("rules")) {
			var rule = (CompoundTag) raw;
			for (var key : List.of("animation", "target")) {
				if (rule.getStringOr(key, "").equals(previous)) {
					rule.putString(key, name);
				}
			}
		}

		var preview = source.preview();
		if (preview != null) {
			visitCalls(preview.getListOrEmpty("tracks"), "preview/tracks", previous, name, new ArrayList<>());
		}

		return new Document(library, preview);
	}

	public static List<String> references(Document source, UUID id) {
		var library = source.library();
		String name = checked(library).animations().get(index(library, id)).name();
		if (name.isEmpty()) {
			return List.of();
		}

		var result = new ArrayList<String>();
		if (library.getStringOr("defaultAnimation", "").equals(name)) {
			result.add("library/defaultAnimation");
		}

		var rules = library.getListOrEmpty("rules");
		for (int i = 0; i < rules.size(); i++) {
			var rule = (CompoundTag) rules.get(i);
			for (var key : List.of("animation", "target")) {
				if (rule.getStringOr(key, "").equals(name)) {
					result.add("library/rules/" + i + "/" + key);
				}
			}
		}

		var preview = source.preview();
		if (preview != null) {
			visitCalls(preview.getListOrEmpty("tracks"), "preview/tracks", name, null, result);
		}

		return List.copyOf(result);
	}

	public static Document remove(Document source, UUID id) {
		var references = references(source, id);
		if (!references.isEmpty()) {
			throw new IllegalArgumentException("动画仍被引用：" + String.join(", ", references));
		}

		var library = source.library();
		animations(library).remove(index(library, id));
		return new Document(library, source.preview());
	}

	private static void visitCalls(ListTag tracks, String path, String name, @Nullable String replacement, List<String> references) {
		for (int i = 0; i < tracks.size(); i++) {
			if (!(tracks.get(i) instanceof CompoundTag track)) {
				throw new IllegalArgumentException("制作轨道必须为复合标签：" + path + "/" + i);
			}

			var data = track.getCompoundOrEmpty("data");
			String current = path + "/" + i + "/data";
			if (track.getStringOr("type", "").equals("lobecorp_animation_call")) {
				var clips = data.getListOrEmpty("clips");
				for (int c = 0; c < clips.size(); c++) {
					if (clips.get(c) instanceof CompoundTag clip && clip.getStringOr("animation", "").equals(name)) {
						references.add(current + "/clips/" + c + "/animation");
						if (replacement != null) {
							clip.putString("animation", replacement);
						}
					}
				}
			} else if (track.getStringOr("type", "").equals("group")) {
				visitCalls(data.getListOrEmpty("children"), current + "/children", name, replacement, references);
			}
		}
	}

	private static int index(CompoundTag library, UUID id) {
		var definitions = checked(library).animations();
		for (int i = 0; i < definitions.size(); i++) {
			if (definitions.get(i).id().equals(id)) {
				return i;
			}
		}

		throw new IllegalArgumentException("制作动画身份已失效：" + id);
	}

	private static ListTag animations(CompoundTag library) {
		if (!library.contains("animations")) {
			library.put("animations", new ListTag());
		}

		return library.getListOrEmpty("animations");
	}

	private static String requireName(String name) {
		if (!name.matches("[a-z_]+")) {
			throw new IllegalArgumentException("新动画名称仅允许小写字母和下划线。");
		}

		return name;
	}

	private static PhotonNamedLibrary checked(CompoundTag library) {
		try {
			return PhotonNamedCodecUtil.read(library);
		} catch (IOException invalid) {
			throw new IllegalArgumentException("命名制作库无法按当前协议读取。", invalid);
		}
	}
}
