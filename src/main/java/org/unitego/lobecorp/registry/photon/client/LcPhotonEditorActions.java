package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.editor.keymap.EditorAction;
import com.lowdragmc.lowdraglib2.editor.keymap.EditorActions;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyChord;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyContext;
import com.lowdragmc.lowdraglib2.editor.keymap.KeymapContext;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class LcPhotonEditorActions {
	/// 模型专用动作共用原生 Keymap 持久化，不与扩展曲线动作混用 ID。
	private static final String MODEL_ACTION_PREFIX = "photon_model.";
	/// 历史和通用编辑共用原生动作 ID，保持已有用户绑定。
	private static final Map<String, Identifier> MODEL_NATIVE_ACTIONS = Map.of(
			"undo", EditorActions.UNDO, "redo", EditorActions.REDO,
			"select_all", EditorActions.SELECT_ALL, "duplicate", EditorActions.DUPLICATE);
	/// 几何和关键帧删除按焦点分流；关键帧另保留 Backspace 副绑定。
	private static final Map<String, KeyChord> MODEL_KEYS = Map.of(
			"box_select", KeyChord.key(GLFW.GLFW_KEY_B), "cancel", KeyChord.key(GLFW.GLFW_KEY_ESCAPE),
			"delete", KeyChord.key(GLFW.GLFW_KEY_DELETE), "delete_key", KeyChord.key(GLFW.GLFW_KEY_DELETE));

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

	private static Identifier modelActionId(String action) {
		return MODEL_NATIVE_ACTIONS.getOrDefault(action, Lobecorp.id(MODEL_ACTION_PREFIX + action));
	}

	/// 工作区关闭时恢复被接管的原生命令，避免编辑器保留已关闭会话。
	public static ISubscription registerModel(Editor editor, BiPredicate<String, KeymapContext> available,
	                                          Consumer<String> handler) {
		ISubscription subscriptions = () -> {
		};

		for (var entry : MODEL_NATIVE_ACTIONS.entrySet()) {
			var original = editor.getKeymap().getAction(entry.getValue()).orElse(null);

			if (original == null) {
				continue;
			}

			var replacement = new EditorAction(original.id(), original.category(), original.displayName(),
					original.defaultPrimary(), original.defaultSecondary(), original.when(), original.priority(), context -> {
				if (!available.test(entry.getKey(), context)) {
					return original.handler().run(context);
				}

				handler.accept(entry.getKey());
				return true;
			});
			var subscription = editor.getKeymap().register(replacement);
			subscriptions = subscriptions.andThen(() -> {
				if (editor.getKeymap().getAction(original.id()).orElse(null) == replacement) {
					subscription.unsubscribe();
					editor.getKeymap().register(original);
				}
			});
		}

		for (var entry : MODEL_KEYS.entrySet()) {
			String action = entry.getKey();
			var context = KeyContext.notTyping().and(KeyContext.withProject())
					.and(KeyContext.of(value -> available.test(action, value), KeyContext.SPECIFICITY_FOCUS));
			subscriptions = subscriptions.andThen(editor.getKeymap().register(EditorAction.builder(modelActionId(action))
					.category(PhotonModelTextUtil.key("editor"))
					.displayName(Component.translatable(PhotonModelTextUtil.key(switch (action) {
						case "box_select" -> "editor.box_select";
						case "delete_key" -> "editor.delete_key";
						default -> action;
					})))
					.defaultChord(entry.getValue())
					.defaultAlternative(action.equals("delete_key") ? KeyChord.key(GLFW.GLFW_KEY_BACKSPACE) : KeyChord.UNBOUND)
					.when(context).onAction(_ -> {
						handler.accept(action);
						return true;
					}).build()));
		}

		return subscriptions;
	}
}
