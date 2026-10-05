package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;

public class PhotonEditorRenameUtil {
	public static void object(FXHierarchyView view, IFXObject object) {
		var runtime = view.getRuntime();
		if (runtime == null || object == runtime.root)
			return;
		String before = object.getName();
		Dialog.stringEditorDialog("ldlib.gui.editor.menu.rename", before,
				value -> !value.isBlank() && runtime.objects.values().stream().noneMatch(other -> other != object && other.getName().equals(value.trim())),
				value -> {
					String after = value.trim();
					if (view.getRuntime() != runtime || !runtime.objects.containsValue(object) || after.isEmpty() || before.equals(after)
							|| runtime.objects.values().stream().anyMatch(other -> other != object && other.getName().equals(after)))
						return;
					view.fxEditor.historyView.pushHistory(Component.translatable("photon.gui.editor.timeline.rename"), EditAction.of(
							() -> object.setName(after), () -> object.setName(before)));
				}).show(view.fxEditor);
	}

	public static boolean track(TimelineContext ctx, Track track) {
		if (!PhotonEditorSettings.of(ctx.editor()).enabled("rename_dialog") || track.lock())
			return false;
		var runtime = ctx.runtime();
		String before = track.displayName();
		Dialog.stringEditorDialog("ldlib.gui.editor.menu.rename", before, value -> !value.isBlank(), value -> {
			String after = value.trim();
			if (ctx.runtime() != runtime || runtime == null || runtime.fxData.timeline().parentListOf(track) == null
					|| track.lock() || after.isEmpty() || before.equals(after))
				return;
			ctx.pushEdit("photon.gui.editor.timeline.rename",
					() -> {
						track.displayName(after);
						ctx.requestRebuild();
					},
					() -> {
						track.displayName(before);
						ctx.requestRebuild();
					});
		}).show(ctx.editor());
		return true;
	}
}

