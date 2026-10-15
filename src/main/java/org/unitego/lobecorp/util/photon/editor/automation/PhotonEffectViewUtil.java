package org.unitego.lobecorp.util.photon.editor.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException.Code;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil.Handle;

import java.util.List;

// 视角操作参考 photon2mcp（Apache-2.0），已适配当前原生 Scene、多工程身份与嵌入式截图区域。
public class PhotonEffectViewUtil {
	private static final List<String> FIELDS = List.of("center", "zoom", "yaw", "pitch", "orthographic", "orthoRange",
			"bloomEnabled", "effectsEnabled", "shapeVisible", "cullBoxVisible");

	private record Settings(Vector3f center, float zoom, float yaw, float pitch, boolean orthographic, float orthoRange,
	                        boolean bloomEnabled, boolean effectsEnabled, boolean shapeVisible,
	                        boolean cullBoxVisible) {
		private void apply(Handle handle) {
			var view = handle.editor().sceneView;
			var scene = view.sceneEditor.scene;
			scene.setCenter(new Vector3f(center));
			scene.setZoom(zoom);
			scene.setCameraYawAndPitch(yaw, pitch);
			scene.useOrtho(orthographic);
			scene.setOrthoRange(orthoRange);
			view.setBloomEnabled(bloomEnabled);
			view.setEffectsEnabled(effectsEnabled);
			view.setShapeVisible(shapeVisible);
			view.setCullBoxVisible(cullBoxVisible);
		}

		private JsonObject json() {
			var result = new JsonObject();
			var point = new JsonArray();
			point.add(center.x);
			point.add(center.y);
			point.add(center.z);
			result.add("center", point);
			result.addProperty("zoom", zoom);
			result.addProperty("yaw", yaw);
			result.addProperty("pitch", pitch);
			result.addProperty("orthographic", orthographic);
			result.addProperty("orthoRange", orthoRange);
			result.addProperty("bloomEnabled", bloomEnabled);
			result.addProperty("effectsEnabled", effectsEnabled);
			result.addProperty("shapeVisible", shapeVisible);
			result.addProperty("cullBoxVisible", cullBoxVisible);
			return result;
		}
	}

	public static class Prepared {
		private final Handle handle;
		private final Settings before;
		private final Settings after;

		private Prepared(Handle handle, Settings before, Settings after) {
			this.handle = handle;
			this.before = before;
			this.after = after;
		}

		public JsonObject commit() {
			requireCurrent(handle);

			try {
				after.apply(handle);
				return describe(handle);
			} catch (RuntimeException | Error failure) {
				try {
					before.apply(handle);
				} catch (RuntimeException | Error rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}

				throw failure;
			}
		}
	}

	/// 视角属于预览界面状态，不写入工程资产、内容版本或撤销历史。
	public static Prepared prepare(Handle handle, JsonObject request) {
		requireCurrent(handle);
		var before = settings(handle);
		var after = new Settings(center(request, before.center()),
				number(request, "zoom", before.zoom(), true),
				number(request, "yaw", before.yaw(), false),
				number(request, "pitch", before.pitch(), false),
				PhotonEffectJsonUtil.bool(request, "orthographic", before.orthographic()),
				number(request, "orthoRange", before.orthoRange(), true),
				PhotonEffectJsonUtil.bool(request, "bloomEnabled", before.bloomEnabled()),
				PhotonEffectJsonUtil.bool(request, "effectsEnabled", before.effectsEnabled()),
				PhotonEffectJsonUtil.bool(request, "shapeVisible", before.shapeVisible()),
				PhotonEffectJsonUtil.bool(request, "cullBoxVisible", before.cullBoxVisible()));
		return new Prepared(handle, before, after);
	}

	public static JsonObject describe(Handle handle) {
		requireCurrent(handle);
		var editor = handle.editor();
		var window = editor.getWindow();
		boolean active = window != null && window.getCurrentEditor() == editor;
		var minecraft = Minecraft.getInstance();
		boolean embedded = window != null && !window.isInOsWindow()
				&& minecraft.screen instanceof ModularUIScreen screen && containsUi(screen.modularUI, window.getModularUI());
		var result = new JsonObject();
		result.addProperty("projectId", handle.id());
		result.addProperty("kind", handle.project() instanceof PhotonModelProject ? "model" : "fx");
		result.add("view", settings(handle).json());
		var supported = new JsonArray();
		FIELDS.forEach(supported::add);
		result.add("supportedFields", supported);
		result.addProperty("active", active);
		result.addProperty("embedded", embedded);
		result.addProperty("guiScale", minecraft.getWindow().getGuiScale());
		var regions = new JsonObject();
		regions.add("scene", active && embedded ? region(editor.sceneView.sceneEditor.scene) : JsonNull.INSTANCE);
		regions.add("editor", active && embedded ? region(editor) : JsonNull.INSTANCE);
		result.add("captureRegions", regions);
		return result;
	}

	private static Settings settings(Handle handle) {
		var view = handle.editor().sceneView;
		var scene = view.sceneEditor.scene;
		return new Settings(new Vector3f(scene.getCenter()), scene.getZoom(), scene.getRotationYaw(), scene.getRotationPitch(),
				scene.isUseOrtho(), scene.getRange(), view.isBloomEnabled(), view.isEffectsEnabled(),
				view.isShapeVisible(), view.isCullBoxVisible());
	}

	private static void requireCurrent(Handle handle) {
		if (handle.editor().getCurrentProject() != handle.project()) {
			throw new PhotonEffectCommandException(Code.PROJECT_CLOSED);
		}
	}

	private static float number(JsonObject request, String key, float fallback, boolean positive) {
		if (!request.has(key)) {
			return fallback;
		}

		double value = PhotonEffectJsonUtil.number(request, key);

		if (Math.abs(value) > Float.MAX_VALUE || positive && value <= 0 || positive && (float) value == 0) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, key);
		}

		return (float) value;
	}

	private static Vector3f center(JsonObject request, Vector3f fallback) {
		if (!request.has("center")) {
			return new Vector3f(fallback);
		}

		var values = PhotonEffectJsonUtil.array(request, "center");

		if (values.size() != 3) {
			throw new PhotonEffectCommandException(Code.INVALID_ARGUMENT, "center");
		}

		var point = new JsonObject();

		for (int index = 0; index < values.size(); index++) {
			point.add(Integer.toString(index), values.get(index));
		}

		return new Vector3f(number(point, "0", 0, false), number(point, "1", 0, false), number(point, "2", 0, false));
	}

	private static boolean containsUi(ModularUI root, ModularUI target) {
		if (target == null) {
			return false;
		}

		if (root == target) {
			return true;
		}

		for (var embedded : root.getEmbeddedUIs()) {
			if (containsUi(embedded, target)) {
				return true;
			}
		}

		return false;
	}

	/// 隐藏面板或脱离当前游戏界面的视图不提供截图区域，防止截取另一工程。
	private static JsonElement region(UIElement element) {
		if (!(Minecraft.getInstance().screen instanceof ModularUIScreen screen)
				|| !containsUi(screen.modularUI, element.getModularUI())) {
			return JsonNull.INSTANCE;
		}

		for (var current = element; current != null; current = current.getParent()) {
			if (!current.isDisplayed() || !current.isVisible()) {
				return JsonNull.INSTANCE;
			}
		}

		float x = element.getPositionX();
		float y = element.getPositionY();
		float width = element.getSizeWidth();
		float height = element.getSizeHeight();
		var corners = List.of(element.getWorldMouse(x, y), element.getWorldMouse(x + width, y),
				element.getWorldMouse(x, y + height), element.getWorldMouse(x + width, y + height));
		float left = Float.POSITIVE_INFINITY;
		float top = Float.POSITIVE_INFINITY;
		float right = Float.NEGATIVE_INFINITY;
		float bottom = Float.NEGATIVE_INFINITY;

		for (var corner : corners) {
			left = Math.min(left, corner.x);
			top = Math.min(top, corner.y);
			right = Math.max(right, corner.x);
			bottom = Math.max(bottom, corner.y);
		}

		if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)
				|| width <= 0 || height <= 0 || right <= left || bottom <= top) {
			return JsonNull.INSTANCE;
		}

		var result = new JsonObject();
		result.addProperty("x", left);
		result.addProperty("y", top);
		result.addProperty("width", right - left);
		result.addProperty("height", bottom - top);
		return result;
	}
}
