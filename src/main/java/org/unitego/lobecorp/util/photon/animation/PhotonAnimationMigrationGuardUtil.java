package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;

import java.util.ArrayList;
import java.util.List;

/// 未完成迁移时阻止覆盖项目或导出；成功迁移只在序列化前更新内存定义。
public class PhotonAnimationMigrationGuardUtil {
	public static List<String> problems(FXData data) {
		var result = new ArrayList<String>();

		for (var object : data.objects()) {
			if (object instanceof PhotonAnimationController controller) {
				for (var problem : controller.library().migrationProblems()) {
					result.add(controller.getName() + ": " + problem);
				}
			}
		}

		return List.copyOf(result);
	}

	public static boolean rejectSave(Editor editor) {
		if (!(editor.getCurrentProject() instanceof FXProject project)) {
			return false;
		}

		return rejectExport(editor, project);
	}

	public static boolean rejectExport(Editor editor, FXProject project) {
		var problems = problems(project.getFx().getFxData());

		if (problems.isEmpty()) {
			return false;
		}

		Dialog.showNotification(PhotonAnimationTextUtil.key("migration_blocked"),
				String.join("\n", problems), null).show(editor.getModularUI());
		return true;
	}

	public static void prepare(FXData data) {
		var controllers = data.objects().stream().filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList();
		var libraries = controllers.stream().map(PhotonAnimationController::library).toList();

		for (var library : libraries) {
			library.requireMigrated();
		}

		for (var index = 0; index < controllers.size(); index++) {
			controllers.get(index).library(libraries.get(index));
		}
	}
}
