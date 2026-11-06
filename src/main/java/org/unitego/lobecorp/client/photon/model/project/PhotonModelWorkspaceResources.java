package org.unitego.lobecorp.client.photon.model.project;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceFileImport;
import com.lowdragmc.lowdraglib2.editor.ui.browser.AssetBrowser.DraggedAssets;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

/// 材质编辑只提交独占副本，PNG沿用原生导入与资源引用流程。
public class PhotonModelWorkspaceResources implements AutoCloseable {
	private final FXEditor editor;
	private final PhotonModelWorkspaceSession session;
	private final List<Dialog> dialogs = new ArrayList<>();
	private boolean closed;

	public PhotonModelWorkspaceResources(FXEditor editor, PhotonModelWorkspaceSession session) {
		this.editor = editor;
		this.session = session;
	}

	public void actions(UIElement parent, String target) {
		var content = new UIElement();
		parent.addChild(content);
		button(content, "resources.from_library", () -> library(target));
		button(content, "resources.import_png", () -> png(target));
		button(content, "resources.add_graph", () -> material(target, new ShaderGraphMaterial()));
		button(content, "resources.add_custom", () -> material(target, new CustomShaderMaterial()));
		if (!target.isEmpty()) {
			button(content, "resources.edit_shader", () -> edit(target));
		}
		content.addEventListener(UIEvents.DRAG_PERFORM, event -> {
			if (event.dragHandler == null) {
				return;
			}
			var value = event.dragHandler.draggingObject;
			if (value instanceof IMaterial material) {
				material(target, material);
				event.stopPropagation();
			} else if (value instanceof DraggedAssets(var files) && !files.isEmpty()) {
				importPng(target, files.getFirst());
				event.stopPropagation();
			}
		});
		content.addEventListener(UIEvents.FILE_DROP, event -> {
			if (event.droppedFiles != null && !event.droppedFiles.isEmpty()) {
				importPng(target, event.droppedFiles.getFirst());
				event.stopPropagation();
			}
		});
	}

	private static void button(UIElement parent, String key, Runnable action) {
		parent.addChild(new Button().setText(PhotonModelTextUtil.key(key)).setOnClick(_ -> action.run()));
	}

	private void show(Dialog dialog) {
		dialogs.add(dialog);
		dialog.setOnClose(() -> dialogs.remove(dialog));
		dialog.show(editor);
	}

	private void library(String target) {
		var selected = new AtomicReference<IMaterial>();
		String expected = session.revision();
		var dialog = MaterialResource.INSTANCE.getResourceInstance().createSelectorDialog(0, 0, selected::set, () -> {
		});
		dialog.buttonContainer.clearAllChildren();
		dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> {
			if (selected.get() != null && !closed) {
				materialAt(expected, target, selected.get());
			}
			dialog.close();
		}));
		dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
		show(dialog);
	}

	private void png(String target) {
		String expected = session.revision();
		show(Dialog.showFileDialog(PhotonModelTextUtil.key("resources.import_png"), LDLib2.getAssetsDir(), true,
				Dialog.suffixFilter(".png"), file -> {
					if (file != null && !closed) {
						importPngAt(expected, target, file);
					}
				}));
	}

	private void importPng(String target, File file) {
        importPngAt(session.revision(), target, file);
    }

    private void importPngAt(String expected, String target, File file) {
		if (closed) {
			return;
		}
		if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
			show(Dialog.showNotification(PhotonModelTextUtil.key("resources.import_png"),
					Component.translatable(PhotonModelTextUtil.key("resources.png_only")).getString(), null));
			return;
		}
		ResourceFileImport.resolveOrImport(editor, file, "textures", location -> {
			if (!closed) {
				materialAt(expected, target, new TextureMaterial(location));
			}
		}, () -> {
		});
	}

	private void material(String target, IMaterial value) {
		materialAt(session.revision(), target, value);
	}

	private void materialAt(String expected, String target, IMaterial source) {
		if (closed) {
			return;
		}
		var resolved = source instanceof UIResourceMaterial reference ? reference.getRawMaterial() : source;
		if (resolved == IMaterial.MISSING) {
			return;
		}
		var snapshot = IMaterial.CODEC.encodeStart(NbtOps.INSTANCE, resolved).getOrThrow();
		session.changeAt(expected, asset -> {
			var materials = asset.has("materials") ? asset.getAsJsonObject("materials") : new JsonObject();
			String name = target;
			if (name.isEmpty()) {
				name = "material";
				while (materials.has(name)) {
					name += "_copy";
				}
			} else if (!materials.has(name)) {
				throw new IllegalArgumentException("材质槽已不存在。");
			}
			var slot = materials.has(name) ? materials.getAsJsonObject(name) : new JsonObject();
			slot.addProperty("texture", resolved instanceof TextureMaterial texture ? texture.getTexture().toString() : "");
			slot.addProperty("materialData", resolved instanceof TextureMaterial ? "" : snapshot.toString());
			materials.add(name, slot);
			asset.add("materials", materials);
		});
	}

	private void edit(String target) {
		try {
			editMaterial(target);
		} catch (IOException | RuntimeException invalid) {
			show(Dialog.showNotification(PhotonModelTextUtil.key("resources.edit_shader"), String.valueOf(invalid.getMessage()), null));
		}
	}

	private void editMaterial(String target) throws IOException {
		var slot = session.asset().getAsJsonObject("materials").getAsJsonObject(target);
		String data = slot.has("materialData") ? slot.get("materialData").getAsString() : "";
		var raw = data.isEmpty() ? null : PhotonModelAssetUtil.material(data);
		String textureId = slot.has("texture") ? slot.get("texture").getAsString() : "";
		var material = raw != null ? IMaterial.CODEC.parse(NbtOps.INSTANCE, raw).getOrThrow()
				: textureId.isEmpty() ? new TextureMaterial() : new TextureMaterial(Identifier.parse(textureId));
		var editable = material.copy();
		var baseline = (CompoundTag) IMaterial.CODEC.encodeStart(NbtOps.INSTANCE, editable).getOrThrow();
		String expected = session.revision();
		var group = new ConfiguratorGroup(PhotonModelTextUtil.key("resources.shader_settings"), false);
		editable.buildConfigurator(group);
		var dialog = new Dialog().setTitle(PhotonModelTextUtil.key("resources.edit_shader"));
		dialog.addContent(group);
		dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(_ -> {
			if (!closed) {
				var after = (CompoundTag) IMaterial.CODEC.encodeStart(NbtOps.INSTANCE, editable).getOrThrow();
				var merged = raw == null ? after : PhotonLosslessNbtUtil.changed(raw, baseline, after);
				session.changeAt(expected, asset -> {
					var value = asset.getAsJsonObject("materials").getAsJsonObject(target);
					value.addProperty("materialData", merged.toString());
					if (editable instanceof TextureMaterial texture) {
						value.addProperty("texture", texture.getTexture().toString());
					}
				});
			}
			dialog.close();
		}));
		dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(_ -> dialog.close()));
		show(dialog);
	}

	@Override
	public void close() {
		closed = true;
		for (var dialog : List.copyOf(dialogs)) {
			dialog.close();
		}
		dialogs.clear();
	}
}
