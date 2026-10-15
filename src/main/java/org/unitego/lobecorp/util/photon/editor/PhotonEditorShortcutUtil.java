package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

public class PhotonEditorShortcutUtil {
	/// 扩展动作在原生 Keymap 中使用稳定 ID，旧绑定也迁移至同一 ID。
	private static final String ACTION_PREFIX = "photon_editor.";

	public static Identifier actionId(String action) {
		return Lobecorp.id(ACTION_PREFIX + action);
	}

	public static String binding(Editor editor, String action) {
		return binding(editor, actionId(action));
	}

	public static String binding(Editor editor, Identifier action) {
		var bindings = editor.getKeymap().bindingsOf(action);
		String primary = bindings.primary().isBound() ? bindings.primary().toDisplayString() : "";
		String secondary = bindings.secondary().isBound() ? bindings.secondary().toDisplayString() : "";
		return primary.isEmpty() ? secondary : secondary.isEmpty() ? primary : primary + " / " + secondary;
	}
}
