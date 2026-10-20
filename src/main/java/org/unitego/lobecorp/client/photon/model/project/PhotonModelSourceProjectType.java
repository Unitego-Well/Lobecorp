package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.nbt.ByteArrayTag;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.model.PhotonModelObjUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/// 交换文件只读导入到新工程，原件从不成为新工程的保存目标。
public class PhotonModelSourceProjectType extends ProjectType {
	public static final ProjectType OBJ = new PhotonModelSourceProjectType("OBJ", ".obj");
	public static final ProjectType JSON = new PhotonModelSourceProjectType("JSON", ".lcmodel.json");
	public static final ProjectType DEFINITION = new PhotonModelSourceProjectType(".mfx", ".mfx");

	private PhotonModelSourceProjectType(String name, String suffix) {
		super(name, suffix, PhotonModelProject::new);
	}

	@Override
	public IProject loadProjectFromFile(File file) throws Exception {
		var document = PhotonResourceReader.read(file.toPath());
		var project = new PhotonModelProject();
		var data = project.document();
		if (suffix.equals(".mfx")) {
			PhotonEditorProjectGuardUtil.requireSupportedFile(file);
			var definition = document.nbt();
			if (definition == null) {
				throw new IOException("模型定义缺少NBT数据。");
			}
			var fx = new FX();
			fx.deserializeNBT(Platform.getFrozenRegistry(), definition);
			var objects = fx.getFxData().objects();
			if (definition.getCompoundOrEmpty("fxData").getListOrEmpty("objects").size() != 1
					|| objects.size() != 1 || !(objects.getFirst() instanceof PhotonModelOutput model)) {
				throw new IOException("独立模型工程导入要求定义包含一个模型对象；组合定义请使用FX工程。");
			}
			var wrapper = model.serializeWrapper();
			if (wrapper == null) {
				throw new IOException("模型对象无法序列化。");
			}
			var raw = wrapper.getCompoundOrEmpty("data");
			data.put("asset", model.config.assetSnapshot());
			data.put("playback", raw.getCompoundOrEmpty("config").copy());
			data.put("renderer", raw.getCompoundOrEmpty("config").getCompoundOrEmpty("modelRenderer").copy());
			data.put("emitterData", raw.copy());
			data.put("definitionSource", definition);
		} else {
			var json = suffix.equals(".obj") ? PhotonModelObjUtil.read(new String(document.bytes(), StandardCharsets.UTF_8), false) : document.json();
			if (json == null || !json.isJsonObject()) {
				throw new IOException("模型交换根数据不是JSON对象。");
			}
			data.put("asset", new ByteArrayTag(json.toString().getBytes(StandardCharsets.UTF_8)));
		}
		project.restore(data);
		return project;
	}

	@Override
	public void openProject(Editor editor, File file) throws Exception {
		editor.loadProject(loadProjectFromFile(file), null);
	}
}
