package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.editor.keymap.EditorAction;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyChord;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyContext;
import com.lowdragmc.lowdraglib2.editor.keymap.KeymapContext;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class LcPhotonEditorActions {
	public static void register(Editor editor, BiPredicate<String, KeymapContext> available, Consumer<String> handler) {
		for (String action : PhotonEditorSettings.DEFAULT_KEYS.keySet().stream().sorted().toList()) {
			int specificity = action.equals("solo") || action.equals("cancel")
					? KeyContext.SPECIFICITY_STATE : KeyContext.SPECIFICITY_FOCUS;
			var context = KeyContext.notTyping().and(KeyContext.of(value -> available.test(action, value), specificity));
			editor.getKeymap().register(EditorAction.builder(PhotonEditorShortcutUtil.actionId(action))
					.category(PhotonEditorTextUtil.key("shortcuts"))
					.displayName(Component.translatable(PhotonEditorTextUtil.key("action." + action)))
					.defaultChord(KeyChord.parse(PhotonEditorSettings.DEFAULT_KEYS.get(action)).orElse(KeyChord.UNBOUND))
					.when(context).onAction(_ -> {
						handler.accept(action);
						return true;
					}).build());
		}
	}


}
