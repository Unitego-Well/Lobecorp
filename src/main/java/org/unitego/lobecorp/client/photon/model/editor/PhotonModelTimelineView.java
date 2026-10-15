package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import org.unitego.lobecorp.mixin.photon.editor.client.FXTimelineViewAccessor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;

import java.util.Locale;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineEditAccess;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.TransformKey;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.ValueKey;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/// 直接继承原生时间轴组件，只适配版本 1 模型轨道、关键帧和播放时钟。
public class PhotonModelTimelineView extends FXTimelineView {
	/// 小数 tick 输入和滚轮步长。
	private static final float TIME_STEP = 0.1f;
	/// 原生时间轴关键帧方点边长，单位为 GUI 像素。
	private static final float KEY_SIZE = 5;
	/// 原生时间输入使用秒，模型采样保存小数 tick。
	private static final float TICKS_PER_SECOND = 20f;
	private final PhotonModelEditSession session;
	private final PhotonModelSceneController scene;
	private final Runnable activateAnimation;
	private final UIElement headerRows;
	private final UIElement laneRows;
	private final List<Track> tracks = new ArrayList<>();
	private long displayedRevision = -1;
	private String displayedClip = "";
	private @Nullable Track selectedTrack;
	private @Nullable Float selectedKey;
	private @Nullable KeyDrag drag;

	private enum Kind {
		PART, BONE, MORPH, OPACITY
	}

	private record Track(Kind kind, String id) {
	}

	private record ViewState(Object nativeState, @Nullable Track track, @Nullable Float key) {
	}

	private static class KeyDrag {
		private final Track track;
		private final float grabOffset;
		private float current;
		private boolean edited;

		private KeyDrag(Track track, float time, float grabOffset) {
			this.track = track;
			current = time;
			this.grabOffset = grabOffset;
		}
	}

	public PhotonModelTimelineView(PhotonModelEditSession session, PhotonModelSceneController scene, Runnable activateAnimation) {
		super(scene.owner());
		this.session = session;
		this.scene = scene;
		this.activateAnimation = activateAnimation;
		setId("model.timeline");
		var access = (FXTimelineViewAccessor) this;
		headerRows = access.lobecorp$headersContainer();
		laneRows = access.lobecorp$lanesContainer();
		configureNativeControls(this);
		// 模型播放与删除由原生 Keymap 分发，移除继承控件固定的 Space/Delete 监听。
		clearListeners(this, UIEvents.KEY_DOWN);
		addEventListener(UIEvents.EXECUTE_COMMAND, this::modelCommand, true);
		addEventListener(UIEvents.TICK, _ -> refresh());
		refresh();
	}

	/// 保留原生控件实例和布局，仅替换模型时间输入、循环与标尺拖动的行为。
	private void configureNativeControls(UIElement element) {
		if ("timeline.time".equals(element.getId()) && element instanceof TextField time) {
			clearListeners(time, UIEvents.TICK);
			clearListeners(time, UIEvents.FOCUS_OUT);
			time.addEventListener(UIEvents.TICK, _ -> {
				if (!time.isFocused()) {
					time.setText(String.format(Locale.ROOT, "%.3f", displayTime() / TICKS_PER_SECOND), false);
				}
			});
			time.addEventListener(UIEvents.FOCUS_OUT, _ -> {
				try {
					seekPreview(Float.parseFloat(time.getText()) * TICKS_PER_SECOND);
				} catch (NumberFormatException _) {
					// 输入未完成时保留当前播放位置。
				}
			});
		} else if ("timeline.ruler".equals(element.getId())) {
			clearListeners(element, UIEvents.MOUSE_DOWN);
			clearListeners(element, UIEvents.DRAG_SOURCE_UPDATE);
			element.addEventListener(UIEvents.MOUSE_DOWN, event -> {
				if (event.button == 0) {
					element.startDrag(null, null);
					scrub(event);
				}
			});
			element.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::scrub);
		} else if ("timeline.wrapMode".equals(element.getId()) && element instanceof Toggle toggle) {
			toggle.setOnToggleChanged(on -> {
				if (scene.activeClip() != null) {
					session.change("loop", asset -> findClip(asset).loop = on);
				}
			});
			toggle.addEventListener(UIEvents.TICK, _ -> {
				var clip = scene.activeClip();
				toggle.setOn(clip != null && clip.loop, false);
			});
		} else if ("timeline.endMode".equals(element.getId()) || "timeline.markerToggle".equals(element.getId())) {
			// 模型动画按片段长度结束；版本 1 没有事件标记，不开放无法保存的特效标记编辑。
			element.setDisplay(false);
		}

		for (var child : element.getChildren()) {
			configureNativeControls(child);
		}
	}

	private void clearListeners(UIElement element, String event) {
		for (var listener : List.copyOf(element.getBubbleListeners(event))) {
			element.removeEventListener(event, listener);
		}
	}

	/// 特效轨道剪贴板不得进入模型预览运行时；模型撤销只通过当前会话处理。
	private void modelCommand(UIEvent event) {
		if (event.target instanceof TextField
				|| event.target.getFirstAncestorOfType(TextField.class) != null) {
			return;
		}

		if (CommandEvents.UNDO.equals(event.command)) {
			session.undo();
		} else if (CommandEvents.REDO.equals(event.command)) {
			session.redo();
		} else if (!CommandEvents.COPY.equals(event.command) && !CommandEvents.PASTE.equals(event.command)) {
			return;
		}
		event.stopImmediatePropagation();
	}

	public void toggleModelPlayback() {
		if (session.playing) {
			scene.pause();
		} else {
			var clip = scene.activeClip();

			if (clip != null && !clip.loop && session.time >= clip.duration) {
				scene.seek(0);
			}
			session.clearPose();
			scene.preview().previewMorph("");
			scene.play();
		}
	}

	public void stopModelPlayback() {
		scene.stop();
	}

	@Override
	public boolean isPlaying() {
		return session != null && session.playing;
	}

	@Override
	public void stepFrames(int delta) {
		seekPreview(displayTime() + delta);
	}

	/// 原生末尾按钮保留模型片段的小数 tick，不取整到粒子模拟时间。
	public void jumpToModelEnd() {
		var clip = scene.activeClip();

		if (clip == null) {
			return;
		}

		seekPreview(clip.duration);
	}

	/// 未选中动画时没有内容终点，预览时钟可持续推进。
	public double modelDuration() {
		var clip = scene == null ? null : scene.activeClip();
		return clip == null ? 0 : clip.duration;
	}

	public void openModelTrackMenu(float x, float y) {
		if (scene.activeClip() == null) {
			return;
		}

		var menu = TreeBuilder.Menu.start();
		for (var track : tracks) {
			menu.leaf(trackName(track), () -> {
				selectTrack(track);
				safe(this::recordSelected);
			});
		}
		openMenu(x, y, menu);
	}

	@Override
	public void rebuild() {
		if (session != null) {
			displayedRevision = -1;
			refresh();
		}
	}

	@Override
	public void drawPlayhead(GUIContext graphics, float x, float y, float width, float height, float partialTick) {
		float cursor = originX() + (displayTime() - scrollTicks()) * scale();
		if (cursor >= x && cursor <= x + width) {
			DrawerHelperClient.drawSolidRect(graphics, cursor, y, 1, height, ColorPattern.RED.color);
		}
	}

	public void refresh() {
		var clip = scene.activeClip();
		var clipId = clip == null ? "" : clip.id;

		if (drag != null || displayedRevision == session.revision() && displayedClip.equals(clipId)) {
			return;
		}

		if (!displayedClip.equals(clipId)) {
			selectedTrack = null;
			selectedKey = null;
		}

		displayedRevision = session.revision();
		displayedClip = clipId;
		tracks.clear();
		headerRows.clearAllChildren();
		laneRows.clearAllChildren();

		if (clip == null) {
			selectedTrack = null;
			selectedKey = null;
			var empty = new Label().setText(PhotonModelTextUtil.key("timeline.empty"));
			TimelineContext.styleLabel(empty);
			empty.layout(layout -> layout.widthPercent(100).height(trackHeight()));
			headerRows.addChild(empty);
			return;
		}

		session.asset().parts.forEach(part -> tracks.add(new Track(Kind.PART, part.id)));
		session.asset().bones.forEach(bone -> tracks.add(new Track(Kind.BONE, bone.id)));
		session.asset().morphs.forEach(morph -> tracks.add(new Track(Kind.MORPH, morph.id)));
		tracks.add(new Track(Kind.OPACITY, ""));

		if (!tracks.contains(selectedTrack)) {
			selectedTrack = null;
			selectedKey = null;
		}

		for (var track : tracks) {
			addTrack(track);
		}
	}

	private void addTrack(Track track) {
		var title = new Label().setText(trackName(track));
		TimelineContext.styleLabel(title);
		title.layout(layout -> layout.widthPercent(100).height(trackHeight()));
		title.style(style -> style.backgroundTexture(GuiTexture.of((graphics, x, y, width, height) ->
				DrawerHelperClient.drawSolidRect(graphics, x, y, width, height,
						(track.equals(selectedTrack) ? ColorPattern.T_BLUE : ColorPattern.T_GRAY).color))));
		title.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			selectTrack(track);

			if (event.button == 1) {
				openTrackMenu(event, track);
			}
		});
		headerRows.addChild(title);
		var lane = new UIElement().layout(layout -> layout.widthPercent(100).height(trackHeight()));
		lane.setOverflowVisible(false).style(style -> style.backgroundTexture(GuiTexture.of(
				(graphics, x, y, width, height) -> drawLane(graphics, x, y, width, height, track))));
		lane.addEventListener(UIEvents.MOUSE_DOWN, event -> laneDown(event, track, lane));
		lane.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::moveDrag);
		lane.addEventListener(UIEvents.DRAG_END, _ -> safe(this::finishDrag));
		lane.addEventListener(UIEvents.MOUSE_WHEEL, this::zoom);
		laneRows.addChild(lane);
	}

	private Component trackName(Track track) {
		String key = switch (track.kind) {
			case PART -> "timeline.part_track";
			case BONE -> "timeline.bone_track";
			case MORPH -> "timeline.morph_track";
			case OPACITY -> "timeline.opacity_track";
		};
		return Component.translatable(PhotonModelTextUtil.key(key), track.id);
	}

	private int trackHeight() {
		return ((FXTimelineViewAccessor) this).lobecorp$rowHeight();
	}

	private void selectTrack(Track track) {
		if (scene.activeClip() == null || !tracks.contains(track)) {
			return;
		}

		activateAnimation.run();
		if (!track.equals(selectedTrack)) {
			selectedKey = null;
		}

		selectedTrack = track;

		switch (track.kind) {
			case PART -> session.selectedPart = track.id;
			case BONE -> session.selectedBone = track.id;
			case MORPH -> session.selectedMorph = track.id;
			case OPACITY -> {
			}
		}

		session.selectionChanged();
		focus();
	}

	private float displayTime() {
		float time = scene.time();
		var clip = scene.activeClip();
		return session.playing && clip != null && clip.loop ? time % clip.duration : time;
	}

	private float modelTick(float x) {
		return (float) Math.max(0, xToTick(x));
	}

	private void scrub(UIEvent event) {
		seekPreview(modelTick(event.x));
		event.stopPropagation();
	}

	private void seekPreview(float time) {
		scene.seek(time);
		session.clearPose();
		scene.preview().previewMorph("");
	}

	private void drawLane(GUIContext graphics, float x, float y, float width, float height, Track track) {
		DrawerHelperClient.drawSolidRect(graphics, x, y, width, height,
				(track.equals(selectedTrack) ? ColorPattern.T_BLUE : ColorPattern.T_GRAY).color);
		double interval = majorTickInterval();

		for (double tick = Math.ceil(scrollTicks() / interval) * interval; tick <= scrollTicks() + width / scale(); tick += interval) {
			DrawerHelperClient.drawSolidRect(graphics, x + (float) (tick - scrollTicks()) * scale(), y,
					1, height, ColorPattern.T_WHITE.color);
		}

		var clip = scene.activeClip();

		if (clip == null) {
			return;
		}

		for (float time : keyTimes(clip, track)) {
			float position = x + (time - scrollTicks()) * scale();

			if (position < x - KEY_SIZE || position > x + width + KEY_SIZE) {
				continue;
			}

			boolean selected = track.equals(selectedTrack) && selectedKey != null && Float.compare(time, selectedKey) == 0;
			DrawerHelperClient.drawSolidRect(graphics, position - KEY_SIZE / 2, y + (height - KEY_SIZE) / 2,
					KEY_SIZE, KEY_SIZE, (selected ? ColorPattern.WHITE : ColorPattern.PURPLE).color);
		}
	}

	private List<Float> keyTimes(Clip clip, Track track) {
		return switch (track.kind) {
			case PART, BONE -> transforms(clip, track).stream().map(key -> key.time).toList();
			case MORPH, OPACITY -> values(clip, track).stream().map(key -> key.time).toList();
		};
	}

	private List<TransformKey> transforms(Clip clip, Track track) {
		return (track.kind == Kind.BONE ? clip.boneTracks : clip.partTracks).getOrDefault(track.id, List.of());
	}

	private List<ValueKey> values(Clip clip, Track track) {
		return track.kind == Kind.OPACITY ? clip.opacityKeys : clip.morphTracks.getOrDefault(track.id, List.of());
	}

	private @Nullable Float keyAt(float x, Track track) {
		var clip = scene.activeClip();

		if (clip == null) {
			return null;
		}

		Float nearest = null;
		float distance = TimelineContext.KEY_HIT_PX;

		for (float time : keyTimes(clip, track)) {
			float delta = Math.abs(originX() + (time - scrollTicks()) * scale() - x);

			if (delta <= distance) {
				nearest = time;
				distance = delta;
			}
		}

		return nearest;
	}

	private void laneDown(UIEvent event, Track track, UIElement lane) {
		if (scene.activeClip() == null) {
			return;
		}

		selectTrack(track);
		selectedKey = keyAt(event.x, track);

		if (event.button == 1) {
			openTrackMenu(event, track);
			return;
		}

		if (event.button != 0) {
			return;
		}

		if (selectedKey == null) {
			seekPreview(modelTick(event.x));
		} else {
			drag = new KeyDrag(track, selectedKey, modelTick(event.x) - selectedKey);
			seekPreview(selectedKey);
			lane.startDrag(drag, null);
		}

		event.stopPropagation();
	}

	private void moveDrag(UIEvent event) {
		var current = drag;
		var clip = session.clip();

		if (!scene.isAnimationMode() || current == null || clip == null) {
			return;
		}

		float time = modelTick(event.x) - current.grabOffset;
		float lower = 0;
		float upper = Float.MAX_VALUE;

		for (float other : keyTimes(clip, current.track)) {
			if (other < current.current) {
				lower = Math.max(lower, Math.nextUp(other));
			} else if (other > current.current) {
				upper = Math.min(upper, Math.nextDown(other));
			}
		}

		time = Math.clamp(time, lower, upper);

		if (Float.compare(time, current.current) == 0) {
			return;
		}

		float target = time;
		session.liveEdit(asset -> moveKey(findClip(asset), current.track, current.current, target));
		current.edited = true;
		current.current = time;
		selectedKey = time;
		scene.refresh();
		scene.seek(time);
		event.stopPropagation();
	}

	private void moveKey(Clip clip, Track track, float from, float to) {
		if (track.kind == Kind.PART || track.kind == Kind.BONE) {
			transforms(clip, track).stream().filter(key -> Float.compare(key.time, from) == 0)
					.findFirst().orElseThrow().time = to;
		} else {
			values(clip, track).stream().filter(key -> Float.compare(key.time, from) == 0)
					.findFirst().orElseThrow().time = to;
		}

		clip.duration = Math.max(clip.duration, to);
	}

	private void finishDrag() {
		var current = drag;
		drag = null;

		if (current != null && current.edited) {
			session.endLiveEdit("timeline.move_key");
		}

		refresh();
	}

	private Clip findClip(PhotonModelAsset asset) {
		return asset.clips.stream().filter(clip -> clip.id.equals(session.selectedClip)).findFirst().orElseThrow();
	}

	private void recordSelected() {
		if (scene.activeClip() == null || selectedTrack == null) {
			return;
		}

		scene.seek(displayTime());
		activateAnimation.run();
		var track = selectedTrack;

		switch (track.kind) {
			case PART -> {
				session.selectedPart = track.id;
				session.recordTransform(false);
			}
			case BONE -> {
				session.selectedBone = track.id;
				session.recordTransform(true);
			}
			case MORPH -> {
				session.selectedMorph = track.id;
				session.recordMorph();
			}
			case OPACITY -> recordOpacity();
		}

		selectedKey = session.time;
		refresh();
	}

	private void recordOpacity() {
		float time = session.time;
		float value = PhotonModelSamplerUtil.sampleValue(session.clip().opacityKeys, time, 1);
		session.change("editor.record_key", asset -> {
			var clip = findClip(asset);
			clip.opacityKeys.removeIf(key -> Float.compare(key.time, time) == 0);
			var key = new ValueKey();
			key.time = time;
			key.value = value;
			clip.opacityKeys.add(key);
			clip.opacityKeys.sort(Comparator.comparingDouble(frame -> frame.time));
			clip.duration = Math.max(clip.duration, time);
		});
	}

	private void deleteSelectedKey() {
		var track = selectedTrack;
		var time = selectedKey;
		var clip = scene.activeClip();

		if (clip == null || track == null || time == null || !keyTimes(clip, track).contains(time)) {
			return;
		}

		session.change("editor.delete_key", asset -> {
			var target = findClip(asset);

			if (track.kind == Kind.PART || track.kind == Kind.BONE) {
				transforms(target, track).removeIf(key -> Float.compare(key.time, time) == 0);
			} else {
				values(target, track).removeIf(key -> Float.compare(key.time, time) == 0);
			}
		});
		selectedKey = null;
		refresh();
	}

	private void openTrackMenu(UIEvent event, Track track) {
		if (scene.activeClip() == null) {
			return;
		}

		var editor = getEditor();

		if (editor == null) {
			return;
		}

		var menu = TreeBuilder.Menu.start().leaf(PhotonModelTextUtil.key("editor.record_key"), () -> safe(this::recordSelected));

		var time = selectedKey;

		if (time != null) {
			menu.leaf(PhotonModelTextUtil.key("timeline.key_time"), () -> editTime(track, time));
			menu.leaf(PhotonModelTextUtil.key("editor.delete_key"), () -> safe(this::deleteSelectedKey));

			if (track.kind == Kind.MORPH || track.kind == Kind.OPACITY) {
				menu.leaf(PhotonModelTextUtil.key("timeline.key_value"), () -> editValue(track, time));
			}
		}

		editor.openMenu(this, event.x, event.y, menu);
		event.stopPropagation();
	}

	private void editTime(Track track, float original) {
		numberDialog("timeline.key_time", original, value -> {
			var clip = scene.activeClip();

			if (clip == null || value < 0 || keyTimes(clip, track).stream()
					.anyMatch(time -> Float.compare(time, value) == 0 && Float.compare(time, original) != 0)) {
				return;
			}

			session.change("timeline.move_key", asset -> {
				var target = findClip(asset);
				moveKey(target, track, original, value);

				if (track.kind == Kind.PART || track.kind == Kind.BONE) {
					transforms(target, track).sort(Comparator.comparingDouble(key -> key.time));
				} else {
					values(target, track).sort(Comparator.comparingDouble(key -> key.time));
				}
			});
			selectedKey = value;
			scene.seek(value);
		});
	}

	private void editValue(Track track, float time) {
		var clip = scene.activeClip();

		if (clip == null) {
			return;
		}

		values(clip, track).stream().filter(value -> Float.compare(value.time, time) == 0).findFirst().ifPresent(key ->
				numberDialog("timeline.key_value", key.value, value -> {
					if (scene.activeClip() == null) {
						return;
					}

					session.change("editor.record_key", asset ->
							values(findClip(asset), track).stream().filter(frame -> Float.compare(frame.time, time) == 0)
									.findFirst().orElseThrow().value = track.kind == Kind.OPACITY ? Math.clamp(value, 0, 1) : value);
				}));
	}

	private void numberDialog(String key, float initial, Consumer<Float> consumer) {
		var ui = getModularUI();

		if (ui == null) {
			return;
		}

		float[] value = {initial};
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key(key));
		dialog.addContent(new NumberConfigurator("", () -> value[0], number -> value[0] = number.floatValue(),
				initial, false).setWheel(TIME_STEP));
		dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> safe(() -> {
			if (Float.isFinite(value[0])) {
				consumer.accept(value[0]);
				dialog.close();
			}
		})));
		dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
		dialog.show(ui);
	}

	public void deleteSelectedKeyAction() {
		safe(this::deleteSelectedKey);
	}

	private void safe(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException failure) {
			var ui = getModularUI();

			if (ui != null) {
				Dialog.showNotification(PhotonModelTextUtil.key("editor"), Component.translatable(
						PhotonModelTextUtil.key("error"), String.valueOf(failure.getMessage())).getString(), () -> {
				}).show(ui);
			}
		}
	}

	public void close() {
		finishEditing();
		tracks.clear();
		clearAllChildren();
	}

	/// 捕获前提交模型关键帧拖动，快照仅保存当前工作区的临时视图与选择。
	public Object captureViewState() {
		finishEditing();
		return new ViewState(((PhotonTimelineEditAccess) this).lobecorp$captureViewState(), selectedTrack, selectedKey);
	}

	/// 原生恢复会重建轨道，模型选择必须在重建后按当前片段重新校验。
	public void restoreViewState(Object snapshot) {
		if (!(snapshot instanceof ViewState(var nativeState, var track, var key))) {
			return;
		}

		((PhotonTimelineEditAccess) this).lobecorp$restoreViewState(nativeState);
		var clip = scene.activeClip();
		selectedTrack = clip != null && tracks.contains(track) ? track : null;
		selectedKey = clip != null && selectedTrack != null && key != null
				&& keyTimes(clip, selectedTrack).contains(key) ? key : null;
	}

	/// 切换片段或关闭工作区前结束拖动，避免跨片段共享实时编辑快照。
	public void finishEditing() {
		safe(this::finishDrag);
	}
}
