package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;

import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public class PhotonEditorShortcutUtil {
	/**
	 * 原生文件、历史与剪贴板命令不能被扩展绑定覆盖。
	 */
	private static final Set<String> RESERVED = Set.of("CTRL+S", "CTRL+SHIFT+S", "CTRL+ALT+S", "ALT+S",
			"CTRL+Z", "CTRL+Y", "CTRL+SHIFT+Z", "CTRL+C", "CTRL+V", "CTRL+X", "SPACE", "DELETE");

	public static boolean matches(String binding, UIEvent event) {
		int key = key(binding);
		if (key < 0)
			return false;
		String upper = binding.toUpperCase(Locale.ROOT);
		return event.keyCode == key && event.isCtrlDown() == upper.contains("CTRL+")
				&& event.isShiftDown() == upper.contains("SHIFT+") && event.isAltDown() == upper.contains("ALT+");
	}

	public static boolean valid(PhotonEditorSettings settings, String action, String binding) {
		String upper = binding.trim().toUpperCase(Locale.ROOT);
		if (upper.isEmpty())
			return true;
		if (key(upper) < 0 || RESERVED.contains(normalize(upper)))
			return false;
		for (String other : PhotonEditorSettings.DEFAULT_KEYS.keySet()) {
			if (!other.equals(action) && normalize(settings.binding(other)).equals(normalize(upper)))
				return false;
		}
		return true;
	}

	private static int key(String binding) {
		if (binding.isBlank())
			return -1;
		String[] parts = binding.trim().toUpperCase(Locale.ROOT).split("\\+", -1);
		var modifiers = new HashSet<String>();
		for (int i = 0; i < parts.length - 1; i++) {
			if (!parts[i].equals("CTRL") && !parts[i].equals("SHIFT") && !parts[i].equals("ALT"))
				return -1;
			if (!modifiers.add(parts[i]))
				return -1;
		}
		try {
			return GLFW.class.getField("GLFW_KEY_" + parts[parts.length - 1]).getInt(null);
		} catch (ReflectiveOperationException ignored) {
			return -1;
		}
	}

	private static String normalize(String binding) {
		String upper = binding.trim().toUpperCase(Locale.ROOT);
		return (upper.contains("CTRL+") ? "CTRL+" : "") + (upper.contains("SHIFT+") ? "SHIFT+" : "")
				+ (upper.contains("ALT+") ? "ALT+" : "") + upper.substring(upper.lastIndexOf('+') + 1);
	}
}
