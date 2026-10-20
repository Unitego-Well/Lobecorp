package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/// 列表编辑只修改会话草稿；显式应用时一次提交库和工程测试引用。
public class PhotonNamedLibraryPanel {
	public static final String TITLE = "photon.lobecorp.animation.outline_panel";
	private final FXEditor editor;
	private final PhotonNamedAuthoringSession session;
	private final UIElement list = new UIElement();
	private final UIElement properties = new UIElement();
	private final TextElement status = new TextElement();
	private @Nullable UUID selected;
	private String name = "animation";

	public PhotonNamedLibraryPanel(FXEditor editor, UUID controller) {
		this.editor = editor;
		session = new PhotonNamedAuthoringSession(editor, controller);
	}

	public static void open(FXEditor editor) {
		try {
			if (!(editor.getCurrentProject() instanceof FXProject project)) {
				throw new IllegalArgumentException("请先打开特效工程。");
			}

			var controllers = project.getFx().getFxData().objects().stream().filter(PhotonNamedController.class::isInstance)
					.map(PhotonNamedController.class::cast).toList();
			if (controllers.size() != 1) {
				throw new IllegalArgumentException("当前制作入口要求工程包含一个命名控制器。");
			}

			new PhotonNamedLibraryPanel(editor, controllers.getFirst().id()).show();
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			Dialog.showNotification(TITLE, invalid.getMessage(), null).show(editor);
		}
	}

	private void show() {
		var dialog = new Dialog().setTitle(TITLE).width(TaffyDimension.length(460));
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		list.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
		properties.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN));
		status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP));
		status.layout(layout -> layout.widthPercent(100).flexShrink(0));
		var scroll = new ScrollerView();
		scroll.layout(layout -> layout.widthPercent(100).height(140));
		scroll.addScrollViewChild(list);
		dialog.addContent(scroll);
		dialog.addContent(properties);
		dialog.addContent(status);
		var actions = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
		actions.addChild(new Button().setText("photon.lobecorp.animation.new").setOnClick(_ -> edit(source -> PhotonNamedAuthoringUtil.add(source, availableName(name)))));
		actions.addChild(new Button().setText("photon.lobecorp.editor.action.duplicate").setOnClick(_ -> edit(source -> PhotonNamedAuthoringUtil.duplicate(source, requireSelected(), availableName(name)))));
		actions.addChild(new Button().setText("photon.lobecorp.animation.rename").setOnClick(_ -> edit(source -> PhotonNamedAuthoringUtil.rename(source, requireSelected(), name))));
		actions.addChild(new Button().setText(Component.literal("↑")).setOnClick(_ -> move(-1)));
		actions.addChild(new Button().setText(Component.literal("↓")).setOnClick(_ -> move(1)));
		actions.addChild(new Button().setText("photon.lobecorp.animation.delete").setOnClick(_ -> edit(source -> PhotonNamedAuthoringUtil.remove(source, requireSelected()))));
		dialog.addContent(actions);
		dialog.addButton(new Button().setText("photon.lobecorp.editor.apply").setOnClick(_ -> {
			try {
				session.commit(session.revision());
				dialog.close();
			} catch (IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.close").setOnClick(_ -> dialog.close()));
		dialog.setOnClose(session::cancel);
		rebuild();
		dialog.show(editor);
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
		list.clearAllChildren();
		var library = library();
		if (selected != null && library.animations().stream().noneMatch(value -> value.id().equals(selected))) {
			selected = null;
		}

		for (var definition : library.animations()) {
			var button = new Button().setText(Component.literal((definition.id().equals(selected) ? "> " : "") + definition.name()));
			button.layout(layout -> layout.widthPercent(100).flexShrink(0));
			button.setOnClick(_ -> {
				selected = definition.id();
				name = definition.name();
				rebuild();
			});
			list.addChild(button);
		}

		properties.clearAllChildren();
		var field = new StringConfigurator("photon.lobecorp.animation.name", () -> name, value -> name = value, name, false);
		field.layout(layout -> layout.widthPercent(100).flexShrink(0));
		properties.addChild(field);
		if (selected != null) {
			addDefinitionProperties(requireSelected());
		}
	}

	private void addDefinitionProperties(UUID id) {
		var definition = library().animations().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
		var duration = new NumberConfigurator("photon.lobecorp.animation.duration", () -> currentDefinition(id).duration(),
				value -> patch(id, tag -> tag.putDouble("duration", value.doubleValue())), definition.duration(), false)
				.setRange(0, Double.MAX_VALUE);
		var automatic = new BooleanConfigurator("photon.lobecorp.animation.auto_duration", () -> currentDefinition(id).autoDuration(),
				value -> patch(id, tag -> tag.putBoolean("autoDuration", value)), definition.autoDuration(), false);
		var loop = new BooleanConfigurator("photon.lobecorp.animation.loop", () -> currentDefinition(id).loop(),
				value -> patch(id, tag -> tag.putBoolean("loop", value)), definition.loop(), false);
		var priority = new NumberConfigurator("photon.lobecorp.animation.priority", () -> currentDefinition(id).priority(),
				value -> patch(id, tag -> tag.putInt("priority", value.intValue())), definition.priority(), false)
				.setRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
		var completion = new SelectorConfigurator<>("photon.lobecorp.animation.completion", () -> currentDefinition(id).completion(),
				value -> patch(id, tag -> tag.putString("completion", value.name())), definition.completion(), false,
				List.of(PhotonNamedLibrary.Completion.values()), value -> Component.translatable("photon.lobecorp.animation.enum." + value.name()).getString());
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

	private void move(int offset) {
		edit(source -> {
			var definitions = library().animations();
			var id = requireSelected();
			for (int i = 0; i < definitions.size(); i++) {
				if (definitions.get(i).id().equals(id)) {
					return PhotonNamedAuthoringUtil.move(source, id, i + offset);
				}
			}

			throw new IllegalStateException("所选动画身份已失效。");
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
