package org.unitego.lobecorp.client.photon.editor;

import org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations;

import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectEditUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.ArrayList;

import org.unitego.lobecorp.client.photon.model.project.PhotonModelWorkspaceScene;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.AUTO_DURATION;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.COMPLETION;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.DURATION;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.INSPECTOR_PANEL;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.LOOP;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.NAME;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.NEW;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.OUTLINE_PANEL;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.PRIORITY;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.RENAME;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.TIMELINE_PANEL;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslations.TITLE;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslations.APPLY;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslations.CLOSE;

/// 列表编辑只修改会话草稿；显式应用时一次提交库和工程测试引用。
public class PhotonNamedLibraryPanel {
	public static final String TITLE = OUTLINE_PANEL;
	private final FXEditor editor;
	private final PhotonNamedAuthoringSession session;
	private final PhotonNamedOutline list;
	private PhotonModelWorkspaceScene animationScene;
	private final UIElement properties = new UIElement();
	private PhotonNamedInspectorPages inspectorPages;
	private final TextElement status = new TextElement();
	private @Nullable UUID selected;
	private String name = "animation";
	private final List<Dialog> ownedDialogs = new ArrayList<>();

	public PhotonNamedLibraryPanel(FXEditor editor, UUID controller) {
		this.editor = editor;
		session = new PhotonNamedAuthoringSession(editor, controller);
		list = new PhotonNamedOutline(editor, this::select,
				this::create,
				() -> edit(source -> PhotonNamedAuthoringUtil.duplicate(source, requireSelected(), availableName(name))),
				this::rename, () -> edit(source -> PhotonNamedAuthoringUtil.remove(source, requireSelected())), this::reorder);
		var definitions = library().animations();
		if (!definitions.isEmpty()) {
			selected = definitions.getFirst().id();
			name = definitions.getFirst().name();
		}
	}

	public static void open(FXEditor editor) {
		try {
			if (!(editor.getCurrentProject() instanceof FXProject project)) {
				throw new IllegalArgumentException("请先打开特效工程。");
			}

			var controllers = project.getFx().getFxData().objects().stream().filter(PhotonNamedController.class::isInstance)
					.map(PhotonNamedController.class::cast).toList();
			if (controllers.isEmpty()) {
				var handle = PhotonEffectProjectUtil.projects().stream().filter(value -> value.editor() == editor && value.project() == project).findFirst()
						.orElseThrow(() -> new IllegalStateException("当前特效工程已关闭。"));
				var operation = new JsonObject();
				operation.addProperty("op", "add_emitter");
				operation.addProperty("type", "lobecorp_animation_controller");
				var operations = new JsonArray();
				operations.add(operation);
				var request = new JsonObject();
				request.addProperty("projectId", handle.id());
				request.addProperty("expectedRevision", PhotonEffectProjectUtil.revision(project));
				request.add("operations", operations);
				try {
					PhotonEffectEditUtil.prepare(request).commit();
				} catch (Exception invalid) {
					throw new IllegalArgumentException("命名控制器创建失败。", invalid);
				}
				controllers = project.getFx().getFxData().objects().stream().filter(PhotonNamedController.class::isInstance).map(PhotonNamedController.class::cast).toList();
			}
			if (controllers.size() != 1) {
				throw new IllegalArgumentException("当前制作入口要求工程包含一个命名控制器。");
			}

			new PhotonNamedLibraryPanel(editor, controllers.getFirst().id()).show();
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			Dialog.showNotification(TITLE, invalid.getMessage(), null).show(editor);
		}
	}

	private void show() {
		var dialog = new PhotonEditorDockedPanel(TITLE, PhotonEditorDockedPanel.Slot.LEFT);
		dialog.group(session, true);
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		list.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
		properties.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN));
		status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP));
		status.layout(layout -> layout.widthPercent(100).flexShrink(0));
		dialog.addContent(list);
		inspectorPages = new PhotonNamedInspectorPages(session, () -> selected, properties,
				value -> status.setText(Component.literal(value)), () -> PhotonNamedDefinitionFieldsPanel.openLibrary(editor, session));
		dialog.inspector(INSPECTOR_PANEL, inspectorPages);
		dialog.addContent(status);
		dialog.addContent(new Button().setText(TIMELINE_PANEL).setOnClick(_ -> openTracks(false)));
		dialog.addContent(new Button().setText(PhotonProjectPreviewPanel.TITLE).setOnClick(_ -> openTracks(true)));
		dialog.addButton(new Button().setText(APPLY).setOnClick(_ -> {
			try {
				if (selected != null && !currentDefinition(selected).name().equals(name)) {
					session.edit(session.revision(), source -> PhotonNamedAuthoringUtil.rename(source, selected, name));
				}
				session.commit(session.revision());
				dialog.close();
			} catch (IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText(CLOSE).setOnClick(_ -> dialog.close()));
		animationScene = new PhotonModelWorkspaceScene(editor, () -> {
			animationScene.showScene(true);
			dialog.activate(true);
		}, () -> {
			animationScene.showScene(false);
			dialog.activate(false);
		});
		animationScene.setName(TITLE);
		animationScene.setId("named.animation_scene");
		animationScene.setOnRemove(dialog::close);
		dialog.companion(animationScene, PhotonEditorDockedPanel.Slot.CENTER);
		dialog.setCanRemove(false);
		dialog.setOnClose(() -> {
			for (var pending : List.copyOf(ownedDialogs)) {
				pending.close();
			}
			animationScene.close();
			session.cancel();
		});
		rebuild();
		dialog.show(editor);
	}

	@SuppressWarnings("resource") // 对话框关闭回调接管工作区生命周期，不在show返回时提前释放。
	private void openTracks(boolean projectTest) {
		try {
			UUID id;
			if (projectTest) {
				var preview = session.document().preview();
				if (preview == null) {
					preview = PhotonProjectPreviewUtil.create();
					var created = preview;
					session.edit(session.revision(), source -> new Document(source.library(), created));
				}
				id = UUID.fromString(preview.getStringOr("id", ""));
			} else {
				id = requireSelected();
			}

			new PhotonNamedTrackWorkspace(editor, session, id, projectTest).show();
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			status.setText(Component.literal(invalid.getMessage()));
		}
	}

	private void edit(UnaryOperator<Document> operation) {
		try {
			session.edit(session.revision(), operation);
			status.setText(Component.empty());
			rebuild();
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			status.setText(Component.literal(invalid.getMessage()));
		}
	}

	private void rebuild() {
		var library = library();
		if (selected != null && library.animations().stream().noneMatch(value -> value.id().equals(selected))) {
			selected = null;
		}

		list.rebuild(library.animations(), selected);

		properties.clearAllChildren();
		var field = new StringConfigurator(NAME, () -> name, value -> name = value, name, false);
		field.layout(layout -> layout.widthPercent(100).flexShrink(0));
		properties.addChild(field);
		properties.addChild(new Button().setText(INSPECTOR_PANEL).setOnClick(_ -> {
			try {
				PhotonNamedDefinitionFieldsPanel.open(editor, session, requireSelected(), false);
			} catch (IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		if (selected != null) {
			addDefinitionProperties(requireSelected());
		}
		if (inspectorPages != null) {
			inspectorPages.refresh();
		}
	}

	private void addDefinitionProperties(UUID id) {
		var definition = library().animations().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
		var duration = new NumberConfigurator(DURATION, () -> currentDefinition(id).duration(),
				value -> patch(id, tag -> tag.putDouble("duration", value.doubleValue())), definition.duration(), false)
				.setRange(0, Double.MAX_VALUE);
		var automatic = new BooleanConfigurator(AUTO_DURATION, () -> currentDefinition(id).autoDuration(),
				value -> patch(id, tag -> tag.putBoolean("autoDuration", value)), definition.autoDuration(), false);
		var loop = new BooleanConfigurator(LOOP, () -> currentDefinition(id).loop(),
				value -> patch(id, tag -> tag.putBoolean("loop", value)), definition.loop(), false);
		var priority = new NumberConfigurator(PRIORITY, () -> currentDefinition(id).priority(),
				value -> patch(id, tag -> tag.putInt("priority", value.intValue())), definition.priority(), false)
				.setRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
		var completion = new SelectorConfigurator<>(COMPLETION, () -> currentDefinition(id).completion(),
				value -> patch(id, tag -> tag.putString("completion", value.name())), definition.completion(), false,
				List.of(PhotonNamedLibrary.Completion.values()), value -> Component.translatable(PhotonAnimationTranslations.key("enum." + value.name())).getString());
		for (var property : List.of(duration, automatic, loop, priority, completion)) {
			property.layout(layout -> layout.widthPercent(100).flexShrink(0));
			properties.addChild(property);
		}
	}

	private PhotonNamedLibrary.Definition currentDefinition(UUID id) {
		return library().animations().stream().filter(value -> value.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("制作动画身份已失效。"));
	}

	/// 字段回调只改变草稿，不重建输入控件或提前提交历史。
	private void patch(UUID id, Consumer<CompoundTag> operation) {
		try {
			var patch = new CompoundTag();
			operation.accept(patch);
			session.edit(session.revision(), source -> PhotonNamedAuthoringUtil.patchDefinition(source, id, patch));
			status.setText(Component.empty());
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			status.setText(Component.literal(invalid.getMessage()));
		}
	}

	private void select(UUID id) {
		if (Objects.equals(selected, id)) {
			return;
		}
		selected = id;
		if (id != null) {
			name = currentDefinition(id).name();
		}
		rebuild();
	}

	private void own(Dialog dialog) {
		ownedDialogs.add(dialog);
		dialog.setOnClose(() -> ownedDialogs.remove(dialog));
		dialog.show(editor);
	}

	private void create() {
		long revision = session.revision();
		own(Dialog.stringEditorDialog(NEW, availableName("animation"), value -> value.matches("[a-z_]+"), value -> {
			try {
				session.edit(revision, source -> PhotonNamedAuthoringUtil.add(source, value));
				rebuild();
			} catch (IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
	}

	private void rename() {
		try {
			UUID id = requireSelected();
			long revision = session.revision();
			own(Dialog.stringEditorDialog(RENAME, currentDefinition(id).name(),
					value -> value.matches("[a-z_]+"), value -> {
						try {
							session.edit(revision, source -> PhotonNamedAuthoringUtil.rename(source, id, value));
							name = value;
							rebuild();
						} catch (IllegalArgumentException | IllegalStateException invalid) {
							status.setText(Component.literal(invalid.getMessage()));
						}
					}));
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			status.setText(Component.literal(invalid.getMessage()));
		}
	}

	private void reorder(List<UUID> ordered) {
		edit(source -> {
			var current = library().animations().stream().map(PhotonNamedLibrary.Definition::id).toList();
			if (ordered.size() != current.size() || !new HashSet<>(ordered).equals(new HashSet<>(current))) {
				throw new IllegalArgumentException("排序目标已经改变。");
			}
			var result = source;
			for (int index = 0; index < ordered.size(); index++) {
				result = PhotonNamedAuthoringUtil.move(result, ordered.get(index), index);
			}
			return result;
		});
	}

	private UUID requireSelected() {
		if (selected == null) {
			throw new IllegalArgumentException("请先选择命名动画。");
		}

		return selected;
	}

	private String availableName(String base) {
		String candidate = base;
		var names = library().animations().stream().map(PhotonNamedLibrary.Definition::name).toList();
		while (names.contains(candidate)) {
			candidate += "_copy";
		}

		return candidate;
	}

	private PhotonNamedLibrary library() {
		try {
			return PhotonNamedCodecUtil.read(session.document().library());
		} catch (IOException invalid) {
			throw new IllegalArgumentException("命名制作库无法读取。", invalid);
		}
	}
}
