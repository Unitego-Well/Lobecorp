package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectAutomation;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectEditUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;

import java.nio.charset.StandardCharsets;

/// 模型高级制作入口与MCP共用完整批事务；几何/UV/材质/动画原树始终可见并保留未知字段。
public class PhotonModelAuthoringPanel {
	public static ISubscription exports(FXEditor editor) {
		return editor.fileMenu.registerMenuCreator((_, menu) -> menu.branch("ldlib.gui.editor.menu.export", branch -> {
			branch.remove("photon.export_fx");
			branch.remove("photon.export_fxpack");
			branch.remove("photon.manage_fxpack");
			branch.leaf(".mfx", () -> export(editor, "definition", ".mfx"));
			branch.leaf(".mfxpack", () -> export(editor, "pack", ".mfxpack"));
			branch.leaf("OBJ", () -> export(editor, "obj", ".obj"));
			branch.leaf("JSON", () -> export(editor, "json", ".json"));
			branch.leaf("photon.lobecorp.model.editor.model_data", () -> openAdvanced(editor));
		}));
	}

	private static void export(FXEditor editor, String kind, String suffix) {
		var handle = PhotonEffectProjectUtil.projects().stream().filter(value -> value.editor() == editor).findFirst()
				.orElseThrow(() -> new IllegalStateException("当前模型工程已关闭。"));
		Dialog.showFileDialog("ldlib.gui.editor.tips.save_as", PhotonEffectFileUtil.roots().getFirst().toFile(), false,
				Dialog.suffixFilter(suffix), file -> {
					if (file == null) {
						return;
					}
					var request = new JsonObject();
					request.addProperty("path", file.getAbsolutePath());
					request.addProperty("kind", kind);
					PhotonEffectAutomation.exportFromEditor(handle, request, error ->
							Dialog.showNotification("photon.lobecorp.model.project.type", error, null).show(editor));
				}).show(editor);
	}

	public static void open(FXEditor editor) {
		if (editor.getCurrentProject() instanceof PhotonModelProject project) {
			project.showWorkspace(editor);
		}
	}

	public static void openAdvanced(FXEditor editor) {
		if (!(editor.getCurrentProject() instanceof PhotonModelProject project)) {
			return;
		}
		var handle = PhotonEffectProjectUtil.projects().stream().filter(value -> value.editor() == editor && value.project() == project)
				.findFirst().orElseThrow(() -> new IllegalStateException("模型工程不属于当前编辑器。"));
		var revision = PhotonEffectProjectUtil.revision(project);
		var data = project.document();
		String asset = switch (data.get("asset")) {
			case ByteArrayTag bytes -> new String(bytes.getAsByteArray(), StandardCharsets.UTF_8);
			case StringTag(var text) -> text;
			case null, default -> throw new IllegalArgumentException("模型资产缺失。");
		};
		String[] texts = {asset, data.getCompoundOrEmpty("renderer").toString(), project.model().config.clip};
		var config = project.model().config;
		double[] numbers = {config.duration, config.startDelay, config.playbackSpeed, config.animationOffset};
		boolean[] flags = {config.enabled, config.looping, config.paused};
		var dialog = new Dialog().setTitle("photon.lobecorp.model.project.type").width(TaffyDimension.length(850));
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		dialog.addContent(new StringConfigurator("asset", () -> texts[0], value -> texts[0] = value, texts[0], false));
		dialog.addContent(new StringConfigurator("renderer", () -> texts[1], value -> texts[1] = value, texts[1], false));
		dialog.addContent(new StringConfigurator("photon.lobecorp.model.clip", () -> texts[2], value -> texts[2] = value, texts[2], false));
		String[] keys = {"duration", "start_delay", "playback_speed", "animation_offset"};
		for (int index = 0; index < numbers.length; index++) {
			int slot = index;
			dialog.addContent(new NumberConfigurator("photon.lobecorp.model." + keys[index], () -> numbers[slot], value -> numbers[slot] = value.doubleValue(), numbers[slot], false));
		}
		String[] toggles = {"enabled", "looping", "pause"};
		for (int index = 0; index < flags.length; index++) {
			int slot = index;
			dialog.addContent(new BooleanConfigurator("photon.lobecorp.model." + toggles[index], () -> flags[slot], value -> flags[slot] = value, flags[slot], false));
		}
		var status = new TextElement();
		dialog.addContent(status);
		dialog.addButton(new Button().setText("photon.lobecorp.editor.apply").setOnClick(_ -> {
			try {
				var operations = new JsonArray();
				var assetOperation = new JsonObject();
				assetOperation.addProperty("op", "set_model_asset");
				assetOperation.add("asset", PhotonResourceJsonUtil.read(texts[0], PhotonResourceLimits.DEFAULT));
				operations.add(assetOperation);
				var rendererOperation = new JsonObject();
				rendererOperation.addProperty("op", "set_model_renderer");
				rendererOperation.addProperty("snbt", texts[1]);
				operations.add(rendererOperation);
				var values = new JsonObject();
				values.addProperty("enabled", flags[0]);
				values.addProperty("looping", flags[1]);
				values.addProperty("paused", flags[2]);
				values.addProperty("duration", numbers[0]);
				values.addProperty("startDelay", numbers[1]);
				values.addProperty("playbackSpeed", numbers[2]);
				values.addProperty("animationOffset", numbers[3]);
				values.addProperty("clip", texts[2]);
				var playback = new JsonObject();
				playback.addProperty("op", "set_model_playback");
				playback.add("values", values);
				operations.add(playback);
				var request = new JsonObject();
				request.addProperty("projectId", handle.id());
				request.addProperty("expectedRevision", revision);
				request.add("operations", operations);
				PhotonEffectEditUtil.prepare(request).commit();
				dialog.close();
			} catch (Exception invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.close").setOnClick(_ -> dialog.close()));
		dialog.show(editor);
	}
}
