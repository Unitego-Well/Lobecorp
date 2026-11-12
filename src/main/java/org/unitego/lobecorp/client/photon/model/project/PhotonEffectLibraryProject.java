package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEffectLibraryTranslationKeys;
import org.unitego.lobecorp.util.photon.compat.PhotonEffectInventoryUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 特效管理工程：只登记多个特效的引用与共享关系，打开工程不创建任何FX运行态。
///
/// 与FX工程、模型特效工程的区别是它自身不含对象树：预览在选中条目时才按需建立，
/// 因此同时存在的预览实例至多一个，避免打开一个工程就初始化全部内容。
public class PhotonEffectLibraryProject extends FXProject {
	public static final ProjectType TYPE = ProjectType.of(PhotonEffectLibraryTranslationKeys.TYPE, ".mfxlib", PhotonEffectLibraryProject::new);
	/// 条目列表的容器键。
	private static final String ENTRIES_KEY = "entries";
	/// 共享引用的容器键。
	private static final String SHARED_KEY = "shared";
	/// 单条记录的特效ID键。
	private static final String ID_KEY = "id";
	/// 单条记录的相对路径键。
	private static final String PATH_KEY = "path";
	/// 单个管理工程登记的条目上限，防止误选过大目录。
	private static final int MAX_ENTRIES = 512;
	private List<Entry> entries = new ArrayList<>();
	private Set<String> shared = new LinkedHashSet<>();

	public PhotonEffectLibraryProject() {
		super();
	}

	/// 把条目与共享引用包装成可写入ValueOutput的容器；ValueOutput只接受顶层CompoundTag。
	private static CompoundTag wrap(ListTag list, ListTag sharedList) {
		var container = new CompoundTag();
		container.put(ENTRIES_KEY, list);
		container.put(SHARED_KEY, sharedList);
		return container;
	}

	public static String suffix() {
		return TYPE.getSuffix();
	}

	public List<Entry> entries() {
		return List.copyOf(entries);
	}

	public Set<String> shared() {
		return Set.copyOf(shared);
	}

	/// 用扫描结果重建条目；保持顺序并去除重复ID，超出上限时截断而不是抛错。
	public void entries(List<PhotonEffectInventoryUtil.Entry> scanned) {
		List<Entry> next = new ArrayList<>();

		for (var entry : scanned) {
			if (next.size() >= MAX_ENTRIES) {
				break;
			}

			if (next.stream().anyMatch(exist -> exist.id().equals(entry.id()))) {
				continue;
			}

			next.add(new Entry(entry.id(), entry.file() == null ? "" : entry.file().toString()));
		}

		entries = next;
	}

	/// 记录被多个特效共同引用的子特效ID；调用方负责先算好重复项。
	public void shared(Set<String> ids) {
		shared = new LinkedHashSet<>(ids);
	}

	@Override
	public ProjectType getProjectType() {
		return TYPE;
	}

	@Override
	public void serializeProject(ValueOutput output) {
		var list = new ListTag();

		for (var entry : entries) {
			var tag = new CompoundTag();
			tag.putString(ID_KEY, entry.id());
			tag.putString(PATH_KEY, entry.path());
			list.add(tag);
		}

		var sharedList = new ListTag();

		for (var id : shared) {
			sharedList.add(StringTag.valueOf(id));
		}

		output.store("entries", CompoundTag.CODEC, wrap(list, sharedList));
	}

	@Override
	public void deserializeProject(ValueInput input) {
		entries = new ArrayList<>();
		shared = new LinkedHashSet<>();
		var container = input.read("entries", CompoundTag.CODEC).orElse(null);

		if (container == null) {
			return;
		}

		for (var value : container.getListOrEmpty(ENTRIES_KEY)) {
			if (!(value instanceof CompoundTag tag)) {
				continue;
			}

			try {
				entries.add(new Entry(tag.getStringOr(ID_KEY, ""), tag.getStringOr(PATH_KEY, "")));
			} catch (IllegalArgumentException invalid) {
				// 单条无效记录跳过，不影响整个工程的其余条目。
			}
		}

		for (var value : container.getListOrEmpty(SHARED_KEY)) {
			value.asString().filter(id -> !id.isBlank()).ifPresent(shared::add);
		}
	}

	@Override
	public void onLoad(Editor editor) {
		super.onLoad(editor);
	}

	@Override
	public void onClosed(Editor editor) {
		super.onClosed(editor);
	}

	/// 一条被管理的特效记录；只保存引用，不保存对象树副本。
	public record Entry(String id, String path) {
		public Entry {
			if (id == null || id.isBlank()) {
				throw new IllegalArgumentException("特效管理条目缺少ID。");
			}
			path = path == null ? "" : path;
		}
	}
}
