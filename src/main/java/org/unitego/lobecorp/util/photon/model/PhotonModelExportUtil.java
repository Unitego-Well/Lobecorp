package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;

import java.io.File;
import java.io.IOException;

public class PhotonModelExportUtil {
	public static FXProject createProject(PhotonModelProject model) {
		var project = new ModelExportProject();
		project.getFx().getFxData().objects().add(new DefinitionEmitter(model));
		return project;
	}

	public static boolean isModelExportProject(Object project) {
		return project instanceof ModelExportProject;
	}

	/// 定义沿用原生压缩 NBT，只接受导出流程产生的单个模型特效对象。
	public static PhotonModelEmitter readDefinition(File file) throws IOException {
		try {
			var fx = new FX();
			fx.deserializeNBT(Platform.getFrozenRegistry(), NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
			var objects = fx.getFxData().objects();

			if (objects.size() != 1 || !(objects.getFirst() instanceof PhotonModelEmitter emitter)) {
				throw new IOException(PhotonModelTextUtil.key("project.missing_asset"));
			}

			return emitter;
		} catch (RuntimeException exception) {
			throw new IOException(PhotonModelTextUtil.key("project.missing_asset"), exception);
		}
	}

	private static class ModelExportProject extends FXProject {
	}

	/// 原生导出在文件确认后才序列化，此时重新读取会话，且不带入预览实例的强制配置。
	private static class DefinitionEmitter extends PhotonModelEmitter {
		private final PhotonModelProject project;

		private DefinitionEmitter(PhotonModelProject project) {
			this.project = project;
		}

		@Override
		public @Nullable CompoundTag serializeWrapper() {
			var emitter = new PhotonModelEmitter();
			project.applyPlayback(emitter.config);
			project.applyRenderer(emitter.config);
			emitter.setAsset(project.session().asset());
			emitter.setName(project.displayModelName());
			return emitter.serializeWrapper();
		}
	}
}
