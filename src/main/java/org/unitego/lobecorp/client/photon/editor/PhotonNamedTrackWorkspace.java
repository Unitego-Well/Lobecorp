package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedHandle;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedTrackAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys.*;
import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys.TIME;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys.*;

/// 制作游标和测试句柄独立于主场景；关闭只清理本窗口创建的预览贡献。
public class PhotonNamedTrackWorkspace implements AutoCloseable {
	private final FXEditor editor;
	private final PhotonNamedAuthoringSession session;
	private final long expectedRevision;
	private final UUID definitionId;
	private final boolean projectTest;
	private final CompoundTag original;
	private final UIElement rows = new UIElement();
	private final TextElement status = new TextElement();
	private final PhotonNamedTimelineContext context;
	private ListTag tracks;
	private @Nullable PhotonNamedRuntime playback;
	private @Nullable PhotonNamedHandle previewHandle;
	private @Nullable ListTag previewTracks;

	public PhotonNamedTrackWorkspace(FXEditor editor, PhotonNamedAuthoringSession session, UUID definitionId, boolean projectTest) {
		this.editor = editor;
		this.session = session;
		this.definitionId = definitionId;
		this.projectTest = projectTest;
		expectedRevision = session.revision();
		original = PhotonNamedTrackAuthoringUtil.definition(session.document(), definitionId, projectTest);
		tracks = original.getListOrEmpty("tracks").copy();
		context = new PhotonNamedTimelineContext(editor, rows, tracks, this::rebuild,
				value -> tracks = value.copy(), this::refreshPreview, projectTest);
	}

	public void show() {
		var dialog = new PhotonEditorDockedPanel(projectTest ? PhotonProjectPreviewPanel.TITLE : TIMELINE_PANEL, PhotonEditorDockedPanel.Slot.BOTTOM);
		dialog.setId("named.timeline." + definitionId);
		dialog.setName("named.timeline." + definitionId);
		dialog.setDynamicName(() -> Component.translatable(projectTest ? PhotonProjectPreviewPanel.TITLE : TIMELINE_PANEL)
				.append(Component.literal(" / " + original.getStringOr("name", ""))));
		dialog.group(session, false);
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		var toolbar = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(3));
		toolbar.addChild(new TextElement().setText(Component.literal(original.getStringOr("name", "") + " / " + definitionId)));
		toolbar.addChild(new Button().setText("+").setOnClick(event -> {
			var menu = TreeBuilder.Menu.start();
			for (var type : PhotonRegistries.TIMELINE_TRACKS) {
				menu.leaf(Component.translatable("photon.timeline_track." + type.name()), () -> attempt(() -> context.addTrack(type.create())));
			}
			context.openMenu(event.x, event.y, menu);
		}));
		toolbar.addChild(new Button().setText("ldlib.gui.editor.menu.undo").setOnClick(_ -> attempt(context::undo)));
		toolbar.addChild(new Button().setText("ldlib.gui.editor.menu.redo").setOnClick(_ -> attempt(context::redo)));
		toolbar.addChild(new Button().setText(DELETE).setOnClick(_ -> attempt(context::deleteSelection)));
		toolbar.addChild(new NumberConfigurator(TIME, context::currentTimeTicks,
				value -> attempt(() -> context.time(value.longValue())), 0, false).setRange(0, Long.MAX_VALUE));
		toolbar.addChild(new Button().setText(PLAY).setOnClick(_ -> attempt(this::startPreview)));
		toolbar.addChild(new Button().setText(STOP).setOnClick(_ -> stopPreview()));
		dialog.addContent(toolbar);
		var rulerRow = new UIElement().layout(layout -> layout.widthPercent(100).height(18).flexDirection(FlexDirection.ROW));
		rulerRow.addChild(new UIElement().layout(layout -> layout.width(260).flexShrink(0)));
		var ruler = new UIElement().layout(layout -> layout.flex(1).heightPercent(100));
		context.origin(ruler);
		ruler.style(style -> style.backgroundTexture(GuiTexture.of((graphics, x, y, width, height) -> {
			DrawerHelperClient.drawSolidRect(graphics, x, y, width, height, 0xff202020);
			double step = context.majorTickInterval();
			double end = context.scrollTicks() + width / context.scale();
			for (double tick = Math.ceil(context.scrollTicks() / step) * step; tick <= end; tick += step) {
				DrawerHelperClient.drawSolidRect(graphics, x + context.tickToLocalX(tick), y + height - 7, 1, 7, 0xff888888);
			}
			context.drawPlayhead(graphics, x, y, width, height, graphics.partialTick);
		})));
		ruler.addEventListener(UIEvents.MOUSE_WHEEL, context::zoom);
		ruler.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button == 0) {
				attempt(() -> context.time(Math.max(0, Math.round(context.xToTick(event.x)))));
				ruler.startDrag(null, null);
				event.stopPropagation();
			}
		});
		ruler.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> attempt(() -> context.time(Math.max(0, Math.round(context.xToTick(event.x))))));
		rulerRow.addChild(ruler);
		dialog.addContent(rulerRow);
		rows.layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
		var scroll = new ScrollerView();
		scroll.layout(layout -> layout.widthPercent(100));
		scroll.layout(layout -> layout.flex(1).minHeight(0));
		scroll.scrollerStyle(style -> style.adaptiveHeight(false));
		scroll.addScrollViewChild(rows);
		dialog.addContent(scroll);
		dialog.addContent(status);
		dialog.addButton(new Button().setText(APPLY).setOnClick(_ -> attempt(() -> {
			var patch = new CompoundTag();
			patch.put("tracks", context.tracks());
			session.edit(expectedRevision, source -> projectTest
					? PhotonNamedAuthoringUtil.patchPreview(source, definitionId, patch)
					: PhotonNamedAuthoringUtil.patchDefinition(source, definitionId, patch));
			dialog.close();
		})));
		dialog.addButton(new Button().setText(CLOSE).setOnClick(_ -> dialog.close()));
		dialog.setOnClose(this::close);
		dialog.addEventListener(UIEvents.KEY_DOWN, event -> {
			if (event.isCtrlDown() && event.keyCode == GLFW.GLFW_KEY_Z) {
				attempt(event.isShiftDown() ? context::redo : context::undo);
				event.stopPropagation();
			} else if (event.isCtrlDown() && event.keyCode == GLFW.GLFW_KEY_Y) {
				attempt(context::redo);
				event.stopPropagation();
			}
		});
		dialog.addEventListener(UIEvents.TICK, _ -> attempt(context::pollRecording, false));
		rebuild();
		dialog.show(editor);
	}

	private void rebuild() {
		rows.clearAllChildren();
		for (var track : context.draft().timeline().tracks()) {
			row(track, 0);
		}
	}

	private void row(Track track, int depth) {
		var nativeEditor = context.lobecorp$trackEditor(track);
		var state = context.state(track);
		var row = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW));
		var headerColumn = new UIElement().layout(layout -> layout.width(260).flexShrink(0).flexDirection(FlexDirection.ROW).paddingLeft(depth * 12));
		var header = nativeEditor.buildHeaderContent(context, track, state);
		header.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button == 0) {
				context.selectTrack(track);
			}
		});
		header.layout(layout -> layout.flex(1).height(TimelineContext.ROW_HEIGHT));
		headerColumn.addChild(header);
		var controls = nativeEditor.buildHeaderControls(context, track, state);
		if (controls != null) {
			headerColumn.addChild(controls);
		}
		headerColumn.addChild(new Button().setText("…").setOnClick(event -> {
			var menu = TreeBuilder.Menu.start();
			nativeEditor.buildTrackMenu(menu, context, track);
			menu.leaf(ACTION_DUPLICATE, () -> attempt(() -> context.duplicate(track)));
			menu.leaf("↑", () -> attempt(() -> context.move(track, -1)));
			menu.leaf("↓", () -> attempt(() -> context.move(track, 1)));
			context.openMenu(event.x, event.y, menu);
		}));
		row.addChild(headerColumn);
		var lane = nativeEditor.buildLane(context, track, state);
		lane.layout(layout -> layout.flex(1).minWidth(200));
		row.addChild(lane);
		rows.addChild(row);
		if (nativeEditor.isExpandable()) {
			var left = nativeEditor.buildExpandedLeft(context, track, state);
			var right = nativeEditor.buildExpandedRight(context, track, state);
			var expanded = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW).height(120));
			if (left != null) {
				left.layout(layout -> layout.width(260).flexShrink(0));
				expanded.addChild(left);
			}
			if (right != null) {
				right.layout(layout -> layout.flex(1));
				expanded.addChild(right);
			}
			rows.addChild(expanded);
		}
		if (track instanceof TrackGroup group) {
			for (var child : group.children()) {
				row(child, depth + 1);
			}
		}
	}

	private void startPreview() {
		requireRuntime();
		var runtime = ((PhotonNamedAccess) Objects.requireNonNull(context.runtime())).lobecorp$namedRuntime();
		if (runtime == null || runtime.closed()) {
			throw new IllegalStateException("命名运行实例不可用。");
		}
		var source = original.copy();
		source.put("tracks", context.tracks());
		try {
			var definition = PhotonProjectPreviewUtil.read(source);
			var next = runtime.previewAuthoring(definition, context.currentTimeTicks());
			stopPreview();
			playback = runtime;
			previewHandle = next;
			previewTracks = context.tracks();
		} catch (IOException invalid) {
			throw new IllegalArgumentException("制作预览定义无效。", invalid);
		}
	}

	private void refreshPreview() {
		requireRuntime();
		if (playback != null && previewHandle != null && playback.ownsAuthoring(previewHandle)) {
			if (!Objects.equals(context.tracks(), previewTracks)) {
				startPreview();
			} else {
				playback.seekAuthoring(previewHandle, context.currentTimeTicks());
			}
		}
	}

	private void requireRuntime() {
		if (editor.runtime != context.runtime()) {
			throw new IllegalStateException("场景已重建，请重新打开工作区。");
		}
	}

	private void stopPreview() {
		if (playback != null && previewHandle != null && playback.ownsAuthoring(previewHandle)) {
			playback.stop(previewHandle);
		}
		previewHandle = null;
		playback = null;
		previewTracks = null;
	}

	private void attempt(Runnable action) {
		attempt(action, true);
	}

	private void attempt(Runnable action, boolean clearStatus) {
		try {
			action.run();
			if (clearStatus) {
				status.setText(Component.empty());
			}
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			status.setText(Component.literal(invalid.getMessage()));
		}
	}

	@Override
	public void close() {
		try {
			context.close();
		} finally {
			stopPreview();
		}
	}
}
