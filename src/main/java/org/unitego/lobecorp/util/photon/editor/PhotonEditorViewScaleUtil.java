package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.settings.AppearanceSettings;
import com.lowdragmc.lowdraglib2.editor.settings.EditorSettings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.client.Minecraft;

public class PhotonEditorViewScaleUtil {

	/// 仅在视图大小菜单同步应用设置期间生效，不能改变普通设置面板的宿主选择。
	private static final ThreadLocal<Editor> VIEW_SCALE_EDITOR = new ThreadLocal<>();

	public static void applyFromViewMenu(EditorSettings settings, Runnable apply) {
		var editor = settings.editor;

		if (!(editor instanceof FXEditor)) {
			apply.run();
			return;
		}

		var previous = VIEW_SCALE_EDITOR.get();
		VIEW_SCALE_EDITOR.set(editor);

		try {
			apply.run();

			var settingsValue = settings.getSettings(AppearanceSettings.ID).orElse(null);

			if (settingsValue instanceof AppearanceSettings appearanceSettings
					&& Minecraft.getInstance().options.guiScale().get() != appearanceSettings.getScreenScale()) {
				// 宿主切换可能恢复全局倍率；相同设置快照会被原生应用流程跳过。
				appearanceSettings.onApply(editor);
			}
		} finally {
			if (previous == null) {
				VIEW_SCALE_EDITOR.remove();
			} else {
				VIEW_SCALE_EDITOR.set(previous);
			}
		}
	}

	public static boolean isApplyingViewScale(Editor editor) {
		return VIEW_SCALE_EDITOR.get() == editor;
	}
}
