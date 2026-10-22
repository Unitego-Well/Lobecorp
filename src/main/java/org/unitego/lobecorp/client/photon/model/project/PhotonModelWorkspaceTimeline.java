package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.mixin.photon.editor.client.FXTimelineViewAccessor;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.List;
import java.util.function.Consumer;

/// 和备份一样直接复用原生时间轴外壳，行内容适配当前原树片段，保留关键帧的未知字段。
public class PhotonModelWorkspaceTimeline extends FXTimelineView {
	private final PhotonModelWorkspaceSession session;
	private final Consumer<String> inspect;
	private final UIElement headers;
	private final UIElement lanes;
	private String clipId = "";
	private String selectedKey = "";
	private String dragRevision;
	private String dragPath;
	private float dragTime;

	public PhotonModelWorkspaceTimeline(FXEditor owner, PhotonModelWorkspaceSession session, Consumer<String> inspect) {
		super(owner);
		this.session = session;
		this.inspect = inspect;
		setId("model.timeline");
		var access = (FXTimelineViewAccessor) this;
		headers = access.lobecorp$headersContainer();
		lanes = access.lobecorp$lanesContainer();
		for (var listener : List.copyOf(getBubbleListeners(UIEvents.TICK))) {
			removeEventListener(UIEvents.TICK, listener);
		}
		for (var listener : List.copyOf(getBubbleListeners(UIEvents.KEY_DOWN))) {
			removeEventListener(UIEvents.KEY_DOWN, listener);
		}
		addEventListener(UIEvents.KEY_DOWN, event -> {
			if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
				dragPath = null;
			} else if (event.keyCode == GLFW.GLFW_KEY_DELETE && !selectedKey.isEmpty()) {
				removeKey();
			}
		});
		refresh();
	}

	public void selectClip(String id) {
		clipId = id;
		selectedKey = "";
		dragPath = null;
		refresh();
	}

	private String clipPath() {
		var clips = session.asset().getAsJsonArray("clips");
		if (clips != null) {
			for (int index = 0; index < clips.size(); index++) {
				if (clips.get(index).getAsJsonObject().get("id").getAsString().equals(clipId)) {
					return "/clips/" + index;
				}
			}
		}
		return "";
	}

	public void refresh() {
		headers.clearAllChildren();
		lanes.clearAllChildren();
		var path = clipPath();
		if (path.isEmpty()) {
			return;
		}
		var clip = session.value(path).getAsJsonObject();
		for (var kind : List.of("partTracks", "boneTracks", "morphTracks")) {
			if (clip.has(kind)) {
				for (var track : clip.getAsJsonObject(kind).entrySet()) {
					row(kind + " / " + track.getKey(), path + "/" + kind + "/" + PhotonModelWorkspaceFields.encode(track.getKey()));
				}
			}
		}
		if (clip.has("opacityKeys")) {
			row(PhotonModelTextUtil.key("opacity"), path + "/opacityKeys");
		}
		headers.addChild(new Button().setText(PhotonModelTextUtil.key("part_key")).setOnClick(_ -> addPartTrack()));
		lanes.addChild(new UIElement().layout(layout -> layout.height(ROW_HEIGHT)));
	}

	private void row(String label, String path) {
		var header = new TextElement().setText(Component.translatable(label));
		header.layout(layout -> layout.height(ROW_HEIGHT).widthPercent(100));
		headers.addChild(header);
		var lane = new UIElement().layout(layout -> layout.height(ROW_HEIGHT).widthPercent(100));
		lane.style(style -> style.backgroundTexture(GuiTexture.of((graphics, x, y, width, height) -> {
			DrawerHelperClient.drawSolidRect(graphics, x, y, width, height, 0xff202020);
			var values = session.valueOr(path, new JsonArray()).getAsJsonArray();
			for (int index = 0; index < values.size(); index++) {
				var keyPath = path + "/" + index;
				float time = keyPath.equals(dragPath) ? dragTime : values.get(index).getAsJsonObject().get("time").getAsFloat();
				DrawerHelperClient.drawSolidRect(graphics, x + tickToLocalX(time) - 2.5f, y + height / 2 - 2.5f,
						5, 5, keyPath.equals(selectedKey) ? 0xffffbb55 : -1);
			}
			drawPlayhead(graphics, x, y, width, height, graphics.partialTick);
		})));
		lane.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button != 0) {
				return;
			}
			var values = session.valueOr(path, new JsonArray()).getAsJsonArray();
			for (int index = 0; index < values.size(); index++) {
				float time = values.get(index).getAsJsonObject().get("time").getAsFloat();
				if (Math.abs(tickToLocalX(time) - (event.x - lane.getContentX())) <= 6) {
					selectedKey = path + "/" + index;
					inspect.accept(selectedKey);
					dragRevision = session.revision();
					dragPath = selectedKey;
					dragTime = time;
					lane.startDrag(null, null);
					event.stopPropagation();
					break;
				}
			}
		});
		lane.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
			if (dragPath != null) {
				dragTime = Math.max(0, (float) xToTick(event.x));
			}
		});
		lane.addEventListener(UIEvents.DRAG_END, _ -> {
			if (dragPath != null) {
				var target = dragPath;
				dragPath = null;
				session.changeAt(dragRevision, asset -> {
					var values = value(asset, path);
					int index = Integer.parseInt(target.substring(target.lastIndexOf('/') + 1));
					values.get(index).getAsJsonObject().addProperty("time", dragTime);
					values.asList().sort(java.util.Comparator.comparingDouble(item -> item.getAsJsonObject().get("time").getAsDouble()));
				});
			}
		});
		lanes.addChild(lane);
	}

	private void removeKey() {
		var target = selectedKey;
		var path = target.substring(0, target.lastIndexOf('/'));
		int index = Integer.parseInt(target.substring(target.lastIndexOf('/') + 1));
		session.change(asset -> value(asset, path).remove(index));
		selectedKey = "";
	}

	private void addPartTrack() {
		var path = clipPath();
		if (path.isEmpty()) {
			return;
		}
		session.change(asset -> {
			var clips = asset.getAsJsonArray("clips");
			int index = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
			var clip = clips.get(index).getAsJsonObject();
			var tracks = clip.has("partTracks") ? clip.getAsJsonObject("partTracks") : new JsonObject();
			var parts = asset.getAsJsonArray("parts");
			if (parts == null || parts.isEmpty()) {
				return;
			}
			var part = parts.get(0).getAsJsonObject();
			var id = part.get("id").getAsString();
			var values = tracks.has(id) ? tracks.getAsJsonArray(id) : new JsonArray();
			var key = new JsonObject();
			key.addProperty("time", 0);
			key.add("transform", part.has("transform") ? part.get("transform").deepCopy() : new JsonObject());
			if (values.isEmpty()) {
				values.add(key);
			}
			tracks.add(id, values);
			clip.add("partTracks", tracks);
		});
	}

	private static JsonArray value(JsonObject root, String pointer) {
		var current = (JsonElement) root;
		for (var token : pointer.substring(1).split("/")) {
			var key = token.replace("~1", "/").replace("~0", "~");
			current = current.isJsonArray() ? current.getAsJsonArray().get(Integer.parseInt(key)) : current.getAsJsonObject().get(key);
		}
		return current.getAsJsonArray();
	}
}
