package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.mixin.photon.editor.client.FXTimelineViewAccessor;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelWorkspaceEditUtil;

import java.util.List;
import java.util.function.Consumer;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;

import java.util.ArrayList;
import java.util.Comparator;

import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

/// 和备份一样直接复用原生时间轴外壳，行内容适配当前原树片段，保留关键帧的未知字段。
public class PhotonModelWorkspaceTimeline extends FXTimelineView {
	private final PhotonModelWorkspaceSession session;
	private final Consumer<String> inspect;
	private final FXEditor owner;
	private final UIElement headers;
	private final UIElement lanes;
	private String clipId = "";
	private String selectedKey = "";
	private String dragRevision;
	private String dragPath;
	private float dragTime;
	private String viewRevision = "";
	private final List<Dialog> dialogs = new ArrayList<>();
	private boolean closed;

	public PhotonModelWorkspaceTimeline(FXEditor owner, PhotonModelWorkspaceSession session, Consumer<String> inspect) {
		super(owner);
		this.owner = owner;
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
			if (closed || event.target instanceof TextField) {
				return;
			}
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
		if (!viewRevision.equals(session.revision())) {
			dragPath = null;
			selectedKey = "";
			viewRevision = session.revision();
		}
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
		for (var kind : List.of("boneTracks", "morphTracks", "opacityKeys")) {
			headers.addChild(new Button().setText(PhotonModelTextUtil.key(kind.equals("boneTracks") ? "bone_key"
					: kind.equals("morphTracks") ? "morph_key" : "opacity")).setOnClick(_ -> chooseTrack(kind)));
			lanes.addChild(new UIElement().layout(layout -> layout.height(ROW_HEIGHT)));
		}
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
			if (event.button == 1) {
				String expected = session.revision();
				float time = Math.max(0, (float) xToTick(event.x));
				var menu = TreeBuilder.Menu.start();
				menu.leaf(Component.translatable(PhotonModelTextUtil.key("editor.key_at"), time), () -> insertKey(path, time, expected));
				owner.openMenu(lane, event.x, event.y, menu);
				event.stopPropagation();
				return;
			}
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
					lane.startDrag(this, null);
					event.stopPropagation();
					break;
				}
			}
		});
		lane.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
			if (dragPath != null && !closed) {
				if (!session.revision().equals(dragRevision)) {
					dragPath = null;
					return;
				}
				float duration = session.valueOr(clipPath() + "/duration", new JsonPrimitive(PhotonModelDefinition.DEFAULT_CLIP_TICKS)).getAsFloat();
				dragTime = Math.clamp((float) xToTick(event.x), 0, duration);
			}
		});
		lane.addEventListener(UIEvents.DRAG_END, _ -> {
			if (dragPath != null && !closed) {
				var target = dragPath;
				float time = dragTime;
				dragPath = null;
				session.changeAt(dragRevision, asset -> {
					var values = value(asset, path);
					int index = Integer.parseInt(target.substring(target.lastIndexOf('/') + 1));
					for (int other = 0; other < values.size(); other++) {
						if (other != index && Float.compare(values.get(other).getAsJsonObject().get("time").getAsFloat(), time) == 0) {
							throw new IllegalArgumentException("该时间已有关键帧。");
						}
					}
					values.get(index).getAsJsonObject().addProperty("time", time);
					values.asList().sort(Comparator.comparingDouble(item -> item.getAsJsonObject().get("time").getAsDouble()));
				});
			}
		});
		lanes.addChild(lane);
	}

	private void removeKey() {
		var target = selectedKey;
		var path = target.substring(0, target.lastIndexOf('/'));
		int index = Integer.parseInt(target.substring(target.lastIndexOf('/') + 1));
		session.changeAt(viewRevision, asset -> value(asset, path).remove(index));
		selectedKey = "";
	}

	private void addPartTrack() {
		chooseTrack("partTracks");
	}

	private void chooseTrack(String kind) {
		String path = clipPath();
		if (path.isEmpty()) {
			return;
		}
		String expected = session.revision();
		float time = Math.max(0, owner.sceneView.particleManager.getTime(0));
		if (kind.equals("opacityKeys")) {
			insertKey(path + "/opacityKeys", time, expected);
			return;
		}
		String collection = kind.equals("partTracks") ? "parts" : kind.equals("boneTracks") ? "bones" : "morphs";
		var candidates = session.valueOr("/" + collection, new JsonArray()).getAsJsonArray();
		if (candidates == null || candidates.isEmpty()) {
			return;
		}
		var ids = candidates.asList().stream().map(JsonElement::getAsJsonObject)
				.map(value -> collection.equals("parts") ? PhotonModelWorkspaceEditUtil.partId(value) : value.get("id").getAsString()).toList();
		String[] selected = {ids.getFirst()};
		var selector = new Selector<String>().setCandidates(ids).setSelected(selected[0], false)
				.setCandidateUIProvider(value -> new Label().setText(Component.literal(value)))
				.setOnValueChanged(value -> selected[0] = value);
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key("timeline.tracks"));
		dialog.addContent(selector);
		dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> {
			insertKey(path + "/" + kind + "/" + PhotonModelWorkspaceFields.encode(selected[0]), time, expected);
			dialog.close();
		}));
		dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
		dialogs.add(dialog);
		dialog.setOnClose(() -> dialogs.remove(dialog));
		dialog.show(owner);
	}

	public void close() {
		closed = true;
		dragPath = null;
		for (var dialog : List.copyOf(dialogs)) {
			dialog.close();
		}
		dialogs.clear();
	}

	private void insertKey(String path, float time, String expected) {
		if (closed) {
			return;
		}
		var tokens = path.split("/");
		session.changeAt(expected, asset -> {
			var clip = asset.getAsJsonArray("clips").get(Integer.parseInt(tokens[2])).getAsJsonObject();
			String kind = tokens[3];
			float duration = clip.has("duration") ? clip.get("duration").getAsFloat() : PhotonModelDefinition.DEFAULT_CLIP_TICKS;
			float at = Math.clamp(time, 0, duration);
			boolean transformTrack = kind.equals("partTracks") || kind.equals("boneTracks");
			var key = new JsonObject();
			key.addProperty("time", at);
			JsonArray values;
			if (kind.equals("opacityKeys")) {
				values = clip.has(kind) ? clip.getAsJsonArray(kind) : new JsonArray();
				key.addProperty("value", 1);
				clip.add(kind, values);
			} else {
				String id = tokens[4].replace("~1", "/").replace("~0", "~");
				var tracks = clip.has(kind) ? clip.getAsJsonObject(kind) : new JsonObject();
				values = tracks.has(id) ? tracks.getAsJsonArray(id) : new JsonArray();
				String collection = kind.equals("partTracks") ? "parts" : kind.equals("boneTracks") ? "bones" : "morphs";
				var nodes = kind.equals("partTracks") ? PhotonModelWorkspaceEditUtil.parts(asset) : asset.getAsJsonArray(collection);
				var source = nodes.asList().stream().map(JsonElement::getAsJsonObject)
						.filter(value -> (collection.equals("parts") ? PhotonModelWorkspaceEditUtil.partId(value) : value.get("id").getAsString()).equals(id))
						.findFirst().orElseThrow();
				if (transformTrack) {
					String field = kind.equals("boneTracks") ? "bind" : "transform";
					key.add("transform", source.has(field) ? source.get(field).deepCopy() : new JsonObject());
				} else {
					key.addProperty("value", source.has("weight") ? source.get("weight").getAsFloat() : 0);
				}
				tracks.add(id, values);
				clip.add(kind, tracks);
			}
			var existing = values.asList().stream().map(JsonElement::getAsJsonObject)
					.filter(value -> Float.compare(value.get("time").getAsFloat(), at) == 0).findFirst().orElse(null);
			if (existing == null) {
				values.add(key);
			} else {
				if (transformTrack) {
					var transform = existing.has("transform") ? existing.getAsJsonObject("transform") : new JsonObject();
					var source = key.getAsJsonObject("transform");
					var defaults = JsonParser.parseString("{\"translation\":[0,0,0],\"rotation\":[0,0,0,1],\"scale\":[1,1,1]}").getAsJsonObject();
					for (var field : List.of("translation", "rotation", "scale")) {
						transform.add(field, (source.has(field) ? source.get(field) : defaults.get(field)).deepCopy());
					}
					existing.add("transform", transform);
				} else {
					existing.add("value", key.get("value"));
				}
			}
			values.asList().sort(Comparator.comparingDouble(value -> value.getAsJsonObject().get("time").getAsDouble()));
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
