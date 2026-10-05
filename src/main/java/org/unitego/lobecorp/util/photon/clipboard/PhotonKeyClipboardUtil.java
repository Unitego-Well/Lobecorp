package org.unitego.lobecorp.util.photon.clipboard;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.joml.Vector2f;
import org.unitego.lobecorp.mixin.photon.editor.client.AnimationTrackUIStateAccessor;
import org.unitego.lobecorp.util.photon.editor.PhotonCurveEditUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashSet;

public class PhotonKeyClipboardUtil {
	/**
	 * 每个编辑器独立保存值与切线副本，不保留源粒子和轨道引用。
	 */
	private static final Map<FXEditor, Clipboard> CLIPBOARDS = new WeakHashMap<>();

	private record Key(int axis, Vector2f point, Vector2f in, Vector2f out) {
	}

	private record Clipboard(String type, String propertyKey, float anchor, List<Key> keys) {
	}

	public static void clear(FXEditor editor) {
		CLIPBOARDS.remove(editor);
	}

	public static boolean systemEnabled(FXEditor editor) {
		return PhotonEditorSettings.of(editor).enabled("system_clipboard");
	}

	public static boolean has(FXEditor editor) {
		return clipboard(editor) != null;
	}

	public static boolean compatible(FXEditor editor, AnimatedProperty property) {
		var clipboard = clipboard(editor);
		return clipboard != null && property != null && clipboard.type().equals(property.type().name())
				&& clipboard.propertyKey().equals(property.type().key());
	}

	public static boolean copy(TimelineContext ctx, AnimationTrackUIState state) {
		var access = (AnimationTrackUIStateAccessor) state;
		var property = access.lobecorp$getSelectedProperty();
		if (property == null || access.lobecorp$getSelectedKeys().isEmpty())
			return false;
		var keys = new ArrayList<Key>();
		float anchor = Float.POSITIVE_INFINITY;
		for (long id : access.lobecorp$getSelectedKeys()) {
			int axis = (int) (id >>> 32), index = (int) id;
			if (axis < 0 || axis >= property.channelCount() || index < 0 || index >= property.keyCount(axis))
				return false;
			var point = property.key(axis, index);
			anchor = Math.min(anchor, point.x);
			keys.add(new Key(axis, new Vector2f(point), clone(property.inHandle(axis, index)), clone(property.outHandle(axis, index))));
		}
		var clipboard = new Clipboard(property.type().name(), property.type().key(), anchor, List.copyOf(keys));
		if (systemEnabled(ctx.editor())) {
			var data = new CompoundTag();
			data.putString("type", clipboard.type());
			data.putString("property", clipboard.propertyKey());
			data.putFloat("anchor", anchor);
			var entries = new ListTag();
			for (var key : keys) {
				var entry = new CompoundTag();
				entry.putInt("axis", key.axis());
				entry.put("point", vector(key.point()));
				if (key.in() != null)
					entry.put("in", vector(key.in()));
				if (key.out() != null)
					entry.put("out", vector(key.out()));
				entries.add(entry);
			}
			data.put("keys", entries);
			PhotonSystemClipboardUtil.write(PhotonSystemClipboardUtil.KEYS, data);
		}
		CLIPBOARDS.put(ctx.editor(), clipboard);
		return true;
	}

	private static CompoundTag vector(Vector2f value) {
		var tag = new CompoundTag();
		tag.putFloat("x", value.x);
		tag.putFloat("y", value.y);
		return tag;
	}

	private static Vector2f vector(CompoundTag value) {
		float x = value.getFloatOr("x", Float.NaN), y = value.getFloatOr("y", Float.NaN);
		if (!Float.isFinite(x) || !Float.isFinite(y))
			throw new IllegalArgumentException();
		return new Vector2f(x, y);
	}

	private static Clipboard clipboard(FXEditor editor) {
		if (!systemEnabled(editor))
			return CLIPBOARDS.get(editor);
		var data = PhotonSystemClipboardUtil.read(PhotonSystemClipboardUtil.KEYS);
		if (data == null)
			return null;
		try {
			float anchor = data.getFloatOr("anchor", Float.NaN);
			var entries = data.getListOrEmpty("keys");
			if (!Float.isFinite(anchor) || anchor < 0 || entries.isEmpty() || entries.size() > PhotonSystemClipboardUtil.MAX_ITEMS)
				return null;
			var keys = new ArrayList<Key>();
			var times = new HashSet<String>();
			for (var entry : entries) {
				if (!(entry instanceof CompoundTag tag))
					return null;
				int axis = tag.getIntOr("axis", -1);
				var point = vector(tag.getCompoundOrEmpty("point"));
				if (axis < 0 || point.x < anchor || !times.add(axis + ":" + point.x))
					return null;
				if (keys.stream().anyMatch(key -> key.axis() == axis && Math.abs(key.point().x - point.x) < PhotonCurveEditUtil.KEY_GAP))
					return null;
				keys.add(new Key(axis, point, tag.contains("in") ? vector(tag.getCompoundOrEmpty("in")) : null,
						tag.contains("out") ? vector(tag.getCompoundOrEmpty("out")) : null));
			}
			return new Clipboard(data.getStringOr("type", ""), data.getStringOr("property", ""), anchor, List.copyOf(keys));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Vector2f clone(Vector2f value) {
		return value == null ? null : new Vector2f(value);
	}

	public static boolean paste(TimelineContext ctx, Track track, AnimationTrackUIState state, double tick) {
		var access = (AnimationTrackUIStateAccessor) state;
		var property = access.lobecorp$getSelectedProperty();
		if (track.lock() || !compatible(ctx.editor(), property) || !Double.isFinite(tick) || tick < 0)
			return false;
		var clipboard = clipboard(ctx.editor());
		if (clipboard == null)
			return false;
		float delta = (float) tick - clipboard.anchor();
		for (var key : clipboard.keys()) {
			float time = key.point().x + delta;
			if (!Float.isFinite(time) || time < 0 || key.axis() >= property.channelCount())
				return false;
			if (key.in() != null && !Float.isFinite(key.in().x + delta) || key.out() != null && !Float.isFinite(key.out().x + delta))
				return false;
			for (int i = 0; i < property.keyCount(key.axis()); i++)
				if (Math.abs(property.key(key.axis(), i).x - time) < PhotonCurveEditUtil.KEY_GAP)
					return false;
		}
		var before = property.copy();
		var selectedBefore = new HashSet<>(access.lobecorp$getSelectedKeys());
		try {
			for (var key : clipboard.keys())
				if (property.addKey(key.axis(), key.point().x + delta, key.point().y) < 0)
					throw new IllegalArgumentException();
			// 插入会调整相邻切线，插入结束后恢复原关键帧，再写入复制的切线。
			for (int axis = 0; axis < before.channelCount(); axis++)
				for (int i = 0; i < before.keyCount(axis); i++)
					handles(property, axis, before.key(axis, i), before.inHandle(axis, i), before.outHandle(axis, i), 0);
			access.lobecorp$getSelectedKeys().clear();
			for (var key : clipboard.keys()) {
				int index = handles(property, key.axis(), key.point(), key.in(), key.out(), delta);
				access.lobecorp$getSelectedKeys().add((long) key.axis() << 32 | index & 0xffffffffL);
			}
		} catch (RuntimeException error) {
			property.restoreFrom(before);
			access.lobecorp$getSelectedKeys().clear();
			access.lobecorp$getSelectedKeys().addAll(selectedBefore);
			return false;
		}
		var after = property.copy();
		ctx.pushApplied("photon.gui.editor.timeline.edit_curve",
				() -> {
					property.restoreFrom(after);
					access.lobecorp$getSelectedKeys().clear();
					ctx.requestRebuild();
					ctx.refreshPreview();
				},
				() -> {
					property.restoreFrom(before);
					access.lobecorp$getSelectedKeys().clear();
					ctx.requestRebuild();
					ctx.refreshPreview();
				});
		access.lobecorp$setPrimaryAxis(-1);
		access.lobecorp$setPrimaryIndex(-1);
		ctx.requestRebuild();
		ctx.refreshPreview();
		return true;
	}

	private static int handles(AnimatedProperty property, int axis, Vector2f point, Vector2f in, Vector2f out, float delta) {
		for (int i = 0; i < property.keyCount(axis); i++) {
			if (property.key(axis, i).x != point.x + delta)
				continue;
			if (in != null)
				property.setInHandle(axis, i, in.x + delta, in.y);
			if (out != null)
				property.setOutHandle(axis, i, out.x + delta, out.y);
			return i;
		}
		throw new IllegalArgumentException();
	}
}
