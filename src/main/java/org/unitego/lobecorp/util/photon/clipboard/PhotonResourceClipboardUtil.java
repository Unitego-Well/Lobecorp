package org.unitego.lobecorp.util.photon.clipboard;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class PhotonResourceClipboardUtil {
	public static boolean enabled(ResourceProviderContainer<?> container) {
		return container.getEditor() instanceof FXEditor editor && PhotonEditorSettings.of(editor).enabled("system_clipboard");
	}

	public static <T> void copy(ResourceProviderContainer<T> container, IResourcePath path) {
		try {
			if (path == null || !container.getCanCopy().test(path))
				throw new IllegalArgumentException();
			var provider = container.resourceProvider;
			var value = provider.getResource(path);
			if (value == null)
				throw new IllegalArgumentException();
			var tag = provider.getResourceInstance().resource.serializeResource(value, Platform.getFrozenRegistry());
			if (tag == null)
				throw new IllegalArgumentException();
			var data = new CompoundTag();
			data.putString("type", provider.getResourceInstance().resource.getName());
			data.putString("name", provider.getResourceName(path));
			data.put("value", tag);
			PhotonSystemClipboardUtil.write(PhotonSystemClipboardUtil.RESOURCE, data);
		} catch (RuntimeException ignored) {
			invalid(container);
		}
	}

	public static boolean compatible(ResourceProviderContainer<?> container) {
		var data = PhotonSystemClipboardUtil.read(PhotonSystemClipboardUtil.RESOURCE);
		return data != null && data.contains("value") && container.getSupportAdd().getAsBoolean()
				&& container.resourceProvider.getResourceInstance().resource.getName().equals(data.getStringOr("type", ""));
	}

	public static <T> void paste(ResourceProviderContainer<T> container) {
		try {
			var data = PhotonSystemClipboardUtil.read(PhotonSystemClipboardUtil.RESOURCE);
			if (data == null || !compatible(container))
				throw new IllegalArgumentException();
			var value = container.resourceProvider.getResourceInstance().resource.deserializeResource(data.get("value"), Platform.getFrozenRegistry());
			if (value == null)
				throw new IllegalArgumentException();
			container.addNewResource(value, data.getStringOr("name", ""));
		} catch (RuntimeException ignored) {
			invalid(container);
		}
	}

	public static void menu(ResourceProviderContainer<?> container, IResourcePath path, TreeBuilder.Menu menu) {
		if (!enabled(container))
			return;
		if (path != null && container.getCanCopy().test(path))
			menu.leaf(PhotonEditorTextUtil.key("copy_system"), () -> copy(container, path));
		if (compatible(container))
			menu.leaf(PhotonEditorTextUtil.key("paste_system"), () -> paste(container));
	}

	public static void command(ResourceProviderContainer<?> container, UIEvent event) {
		if (!enabled(container) || textTarget(event.target))
			return;
		boolean copy = CommandEvents.COPY.equals(event.command) && container.getSelected() != null
				&& container.getCanCopy().test(container.getSelected());
		boolean paste = CommandEvents.PASTE.equals(event.command) && compatible(container);
		if (!copy && !paste)
			return;
		if (UIEvents.EXECUTE_COMMAND.equals(event.type)) {
			if (copy)
				copy(container, container.getSelected());
			else
				paste(container);
		}
		event.stopPropagation();
	}

	public static boolean textTarget(UIElement element) {
		for (var current = element; current != null; current = current.getParent())
			if (current instanceof TextField || current instanceof TextArea)
				return true;
		return false;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean renameDialog(UIElement owner, String initial, Predicate<Character> validator, Consumer<String> commit) {
		var editor = PhotonEditorTools.findEditor(owner);
		if (editor == null || !PhotonEditorSettings.of(editor).enabled("rename_dialog"))
			return false;
		Predicate<String> valid = value -> !value.trim().isEmpty() && value.trim().chars().allMatch(ch -> validator.test((char) ch));
		Dialog.stringEditorDialog("ldlib.gui.editor.menu.rename", initial, valid, value -> {
			if (valid.test(value))
				commit.accept(value.trim());
		}).show(editor);
		return true;
	}

	private static void invalid(ResourceProviderContainer<?> container) {
		Dialog.showNotification(PhotonEditorTextUtil.key("clipboard"), PhotonEditorTextUtil.key("clipboard_invalid"), null).show(container.getEditor());
	}
}
