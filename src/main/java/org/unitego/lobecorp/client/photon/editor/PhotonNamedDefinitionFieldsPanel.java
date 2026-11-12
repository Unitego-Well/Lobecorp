package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedTrackAuthoringUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys.INSPECTOR_PANEL;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys.APPLY;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys.CLOSE;

/// 原树高级字段编辑不通过摘要重建条目；应用时与同一制作会话版本一起提交。
public class PhotonNamedDefinitionFieldsPanel {
	public static void openLibrary(FXEditor editor, PhotonNamedAuthoringSession session) {
		long revision = session.revision();
		var library = session.document().library();
		var wrapper = new CompoundTag();
		wrapper.put("rules", library.getListOrEmpty("rules").copy());
		String[] values = {library.getStringOr("defaultAnimation", ""), wrapper.toString()};
		var dialog = new PhotonEditorDockedPanel(PhotonNamedLibraryPanel.TITLE, PhotonEditorDockedPanel.Slot.RIGHT);
		dialog.setId("named.rules");
		dialog.group(session, false);
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		dialog.addContent(new StringConfigurator("defaultAnimation", () -> values[0], value -> values[0] = value, values[0], false));
		dialog.addContent(new StringConfigurator("rules", () -> values[1], value -> values[1] = value, values[1], false));
		var status = new TextElement();
		dialog.addContent(status);
		dialog.addButton(new Button().setText(APPLY).setOnClick(_ -> {
			try {
				var parsed = TagParser.parseCompoundFully(values[1]);
				if (parsed.size() != 1 || !(parsed.get("rules") instanceof ListTag rules)) {
					throw new IllegalArgumentException("规则必须为完整rules列表。");
				}
				session.edit(revision, source -> PhotonNamedAuthoringUtil.rules(PhotonNamedAuthoringUtil.defaultAnimation(source, values[0]), rules));
				dialog.close();
			} catch (CommandSyntaxException | IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText(CLOSE).setOnClick(_ -> dialog.close()));
		dialog.show(editor);
	}

	public static void open(FXEditor editor, PhotonNamedAuthoringSession session, UUID definitionId, boolean projectTest) {
		long revision = session.revision();
		var source = PhotonNamedTrackAuthoringUtil.definition(session.document(), definitionId, projectTest);
		var values = new LinkedHashMap<String, String>();
		String[] durationParameter = {source.getStringOr("durationParameter", "")};
		boolean[] enableModules = {source.getBooleanOr("autoEnableModules", false)};
		var dialog = new PhotonEditorDockedPanel(INSPECTOR_PANEL, PhotonEditorDockedPanel.Slot.RIGHT);
		dialog.setId("named.fields." + definitionId);
		dialog.group(session, false);
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		dialog.addContent(new StringConfigurator("durationParameter", () -> durationParameter[0], value -> durationParameter[0] = value, durationParameter[0], false));
		dialog.addContent(new BooleanConfigurator("autoEnableModules", () -> enableModules[0], value -> enableModules[0] = value, enableModules[0], false));
		for (var key : List.of("parameters", "bindings", "markers")) {
			var wrapper = new CompoundTag();
			wrapper.put(key, source.getListOrEmpty(key).copy());
			values.put(key, wrapper.toString());
			var field = new StringConfigurator(key, () -> values.get(key), value -> values.put(key, value), values.get(key), false);
			field.layout(layout -> layout.widthPercent(100).flexShrink(0));
			dialog.addContent(field);
		}

		var status = new TextElement();
		dialog.addContent(status);
		dialog.addButton(new Button().setText(APPLY).setOnClick(_ -> {
			try {
				var patch = new CompoundTag();
				patch.putString("durationParameter", durationParameter[0]);
				patch.putBoolean("autoEnableModules", enableModules[0]);
				for (var entry : values.entrySet()) {
					var parsed = TagParser.parseCompoundFully(entry.getValue());
					if (parsed.size() != 1 || !(parsed.get(entry.getKey()) instanceof ListTag list)) {
						throw new IllegalArgumentException("字段必须保留对应的完整列表：" + entry.getKey());
					}
					patch.put(entry.getKey(), list.copy());
				}

				session.edit(revision, document -> projectTest
						? PhotonNamedAuthoringUtil.patchPreview(document, definitionId, patch)
						: PhotonNamedAuthoringUtil.patchDefinition(document, definitionId, patch));
				dialog.close();
			} catch (CommandSyntaxException | IllegalArgumentException | IllegalStateException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText(CLOSE).setOnClick(_ -> dialog.close()));
		dialog.show(editor);
	}
}
