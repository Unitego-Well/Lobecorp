package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.animation.validation.PhotonAnimationChecks;
import org.unitego.lobecorp.client.photon.render.validation.PhotonOcclusionRegression;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.List;
import java.util.function.IntSupplier;

/// 原生工具菜单中的独立检查入口；不借用正在编辑的实例。
public class PhotonEditorTestMenu {
	public static void append(FXEditor editor, TreeBuilder.Menu menu) {
		menu.branch(PhotonEditorTextUtil.key("tests"), tests -> {
			tests.leaf(PhotonEditorTextUtil.key("test.all"), () -> run(editor, List.of(PhotonAnimationChecks.values()), true));

			for (var check : PhotonAnimationChecks.values()) {
				tests.leaf(check.title(), () -> run(editor, List.of(check), false));
			}

			tests.leaf(PhotonEditorTextUtil.key("test.occlusion"), () -> run(editor, List.of(), true));
		});
	}

	private static void run(FXEditor editor, List<PhotonAnimationChecks> checks, boolean occlusion) {
		var report = Component.translatable(PhotonEditorTextUtil.key("test.isolation"));

		for (var check : checks) {
			report.append("\n").append(result(check.title(), check.name(), check::run));
		}

		if (occlusion) {
			var title = PhotonEditorTextUtil.key("test.occlusion");
			report.append("\n").append(result(title, title, PhotonOcclusionRegression::runLoaded));
		}

		Dialog.showNotification(PhotonEditorTextUtil.key("tests"), report.getString(), null).show(editor);
	}

	private static Component result(String titleKey, String identity, IntSupplier check) {
		var title = Component.translatable(titleKey);

		try {
			return Component.translatable(PhotonEditorTextUtil.key("test.passed"), title, check.getAsInt());
		} catch (AssertionError | RuntimeException failure) {
			Lobecorp.LOGGER.error(PhotonEditorTextUtil.TEST_FAILURE_LOG, identity, failure);
			var reason = failure.getMessage();
			return Component.translatable(PhotonEditorTextUtil.key("test.failed"), title,
					reason == null ? failure.getClass().getSimpleName() : reason);
		}
	}
}
