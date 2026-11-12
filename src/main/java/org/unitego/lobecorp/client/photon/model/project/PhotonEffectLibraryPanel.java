package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorDockedPanel;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEffectLibraryTranslationKeys;
import org.unitego.lobecorp.util.photon.compat.PhotonEffectInventoryUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 特效管理工程的停靠面板：列出目录内全部特效与它们的静态开销，按需切换预览，同一时刻只保留一份运行态。
///
/// 面板本身不创建FX运行态；列表数据来自只读NBT索引，点选条目才调用编辑器工具挂一份预览。
public class PhotonEffectLibraryPanel {
	/// 面板标题的翻译键。
	private static final String TITLE = PhotonEffectLibraryTranslationKeys.TYPE;
	/// 列表区的最大可见行数，超出滚动。
	private static final int MAX_ROWS = 64;
	private final PhotonEffectLibraryProject project;
	private final FXEditor editor;
	private final PhotonEditorDockedPanel panel;
	private final UIElement rows = new UIElement();
	private final TextElement summary = new TextElement();
	private final Map<String, Button> buttons = new LinkedHashMap<>();
	private List<PhotonEffectInventoryUtil.Entry> scanned = List.of();
	private @Nullable Path root;
	private @Nullable String selected;

	private PhotonEffectLibraryPanel(FXEditor editor, PhotonEffectLibraryProject project) {
		this.editor = editor;
		this.project = project;
		panel = new PhotonEditorDockedPanel(TITLE, PhotonEditorDockedPanel.Slot.RIGHT);
		panel.addContent(summary);
		rows.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN));
		panel.addContent(rows);
		panel.addButton(new Button().setText(Component.translatable(PhotonEffectLibraryTranslationKeys.RESCAN))
				.setOnClick(_ -> rescan()));
		panel.addButton(new Button().setText(Component.translatable(PhotonEffectLibraryTranslationKeys.CLEAR))
				.setOnClick(_ -> select(null)));
		panel.setOnClose(this::close);
	}

	/// 菜单入口：当前工程是特效管理工程时打开面板，否则提示先打开或新建该类型工程。
	public static void open(FXEditor editor) {
		if (editor.getCurrentProject() instanceof PhotonEffectLibraryProject project) {
			open(editor, project);
			return;
		}

		Dialog.showNotification(PhotonEffectLibraryTranslationKeys.TYPE, PhotonEffectLibraryTranslationKeys.TIP, null).show(editor);
	}

	/// 打开或切换到当前工程的面板；已存在同工程面板时复用，不重复扫描。
	public static void open(FXEditor editor, PhotonEffectLibraryProject project) {
		var panel = new PhotonEffectLibraryPanel(editor, project);
		panel.rescan();
		panel.panel.show(editor);
	}

	/// 被两个以上特效共同引用的子特效ID；共享引用优化优先处理这些。
	private static java.util.Set<String> sharedIds(List<PhotonEffectInventoryUtil.Entry> entries) {
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (var entry : entries) {
			for (var link : entry.linkedIds()) {
				counts.merge(link, 1, Integer::sum);
			}
		}

		var shared = new java.util.LinkedHashSet<String>();
		counts.forEach((id, count) -> {
			if (count > 1) {
				shared.add(id);
			}
		});
		return shared;
	}

	/// 单行的展示文本：ID、对象数、发射器数、估算渲染类型、子特效链接与深度。
	private static String label(PhotonEffectInventoryUtil.Entry entry) {
		if (!entry.readable()) {
			return entry.id() + "  ✗ " + entry.failure();
		}

		return String.format(java.util.Locale.ROOT, "%s  obj=%d emit=%d spawn=%d rt=%d link=%d d=%d  %dKB",
				entry.id(), entry.objects(), entry.emitters(), entry.spawners(), entry.estimatedRenderTypes(),
				entry.linkedEffects(), entry.linkDepth(), entry.decodedBytes() / 1024);
	}

	/// 重新扫描资源根并把结果写回工程条目；单文件失败只体现在该行的failure上。
	private void rescan() {
		List<PhotonEffectInventoryUtil.Entry> found = new ArrayList<>();
		Path detected = detectRoot();

		if (detected != null) {
			try {
				found.addAll(PhotonEffectInventoryUtil.scan(detected));
			} catch (IOException unreadable) {
				Lobecorp.LOGGER.warn("特效管理扫描失败：{}", detected, unreadable);
			}
		}

		root = detected;
		scanned = found.stream()
				.sorted((left, right) -> Long.compare(right.cost(), left.cost()))
				.limit(MAX_ROWS)
				.toList();
		project.entries(scanned);
		project.shared(sharedIds(scanned));
		render();
	}

	/// 优先取工程条目已经指向的目录；没有条目时退回资源根的第一项。
	private @Nullable Path detectRoot() {
		for (var entry : project.entries()) {
			if (entry.path() == null || entry.path().isEmpty()) {
				continue;
			}

			try {
				var file = Path.of(entry.path());
				var parent = file.getParent();
				if (parent != null) {
					return parent;
				}
			} catch (RuntimeException invalid) {
				// 路径无效的条目跳过，继续找下一条。
			}
		}

		var roots = PhotonEffectFileUtil.roots();
		return roots.isEmpty() ? null : roots.getFirst();
	}

	private void render() {
		rows.clearAllChildren();
		buttons.clear();

		long totalObjects = 0;
		long totalEmitters = 0;
		long totalBytes = 0;
		int unreadable = 0;

		for (var entry : scanned) {
			totalObjects += entry.objects();
			totalEmitters += entry.emitters();
			totalBytes += entry.decodedBytes();

			if (!entry.readable()) {
				unreadable++;
			}
		}

		summary.setText(Component.translatable(PhotonEffectLibraryTranslationKeys.SUMMARY,
				scanned.size(), totalObjects, totalEmitters, totalBytes / 1024, unreadable));

		for (var entry : scanned) {
			var row = new Button().setText(Component.literal(label(entry)));
			row.layout(layout -> layout.widthPercent(100));
			row.setOnClick(_ -> select(entry));
			buttons.put(entry.id(), row);
			rows.addChild(row);
		}

		if (scanned.isEmpty()) {
			rows.addChild(new TextElement().setText(Component.translatable(PhotonEffectLibraryTranslationKeys.EMPTY)));
		}
	}

	/// 选中一行：同一时刻只保留一份运行态，切换时先释放旧的。
	private void select(@Nullable PhotonEffectInventoryUtil.Entry entry) {
		var tools = PhotonEditorTools.of(editor);

		if (tools == null) {
			return;
		}

		if (entry == null) {
			tools.clearEffectPreview();
			selected = null;
			highlight();
			return;
		}

		var id = Identifier.tryParse(entry.id().contains(":") ? entry.id() : "lobecorp:" + entry.id());

		if (id == null) {
			Lobecorp.LOGGER.warn("特效管理预览失败，ID 无法解析：{}", entry.id());
			return;
		}

		if (tools.previewEffect(id)) {
			selected = entry.id();
		}

		highlight();
	}

	/// 选中行加粗显示，其余还原，避免再引入额外的选中状态控件。
	private void highlight() {
		for (var item : buttons.entrySet()) {
			item.getValue().setText(Component.literal((item.getKey().equals(selected) ? "▶ " : "  ") + labelOf(item.getKey())));
		}
	}

	private String labelOf(String id) {
		for (var entry : scanned) {
			if (entry.id().equals(id)) {
				return label(entry);
			}
		}

		return id;
	}

	private void close() {
		var tools = PhotonEditorTools.of(editor);

		if (tools != null) {
			tools.clearEffectPreview();
		}

		selected = null;
		buttons.clear();
		scanned = List.of();
	}
}
