package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformancePanel;
import org.unitego.lobecorp.client.photon.acceptance.PhotonLogicRegression;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;


/// 原生工具菜单中的独立检查入口；不借用正在编辑的实例。
public class PhotonEditorTestMenu {
	public static void append(FXEditor editor, TreeBuilder.Menu menu) {
		menu.branch(PhotonEditorTextUtil.key("tests"), tests -> {
            if (PhotonLogicRegression.available()) {
                tests.leaf(Component.literal("独立逻辑回归（一次）"), () -> PhotonLogicRegression.start(editor));
            }

			tests.leaf(PhotonPerformanceTextUtil.key("title"), () -> PhotonPerformancePanel.open(editor));
			tests.leaf(PhotonEditorTextUtil.key("test.all"), () -> Dialog.showNotification(
					PhotonEditorTextUtil.key("tests"), Component.translatable(PhotonEditorTextUtil.key("test.removed")).getString(), null).show(editor));
		});
	}


}
