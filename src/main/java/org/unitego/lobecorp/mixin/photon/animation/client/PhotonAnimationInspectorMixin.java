package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.view.InspectorView;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationNameUtil;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;

import java.util.function.Consumer;

@Mixin(InspectorView.class)
public abstract class PhotonAnimationInspectorMixin {
	@Shadow
	@Final
	public Editor editor;

	@WrapMethod(method = "inspect(Lcom/lowdragmc/lowdraglib2/configurator/IConfigurable;Ljava/util/function/Consumer;Ljava/lang/Runnable;Ljava/lang/Runnable;)Lcom/lowdragmc/lowdraglib2/configurator/ui/ConfiguratorGroup;")
	@SuppressWarnings("resource") // 工作区拥有预览，此处只借用原检视。
	private ConfiguratorGroup lobecorp$animationInspection(IConfigurable configurable, Consumer<Configurator> listener,
	                                                       Runnable onClose, Runnable historyAction, Operation<ConfiguratorGroup> original) {
		if (!(editor instanceof FXEditor owner)) {
			return original.call(configurable, listener, onClose, historyAction);
		}

		var workspace = PhotonAnimationTab.editing(owner);
		if (workspace == null) {
			return original.call(configurable, listener, onClose, historyAction);
		}

		if (!(configurable instanceof FXObject object)) {
			return workspace.preview().inspectTimeline(workspace, configurable, listener, onClose);
		}

		workspace.selectTarget(object.id());
		workspace.registerSceneHistorySource(object);
		var group = PhotonAnimationNameUtil.inspect(() -> original.call(configurable, (Consumer<Configurator>) field -> {
			if (listener != null) {
				listener.accept(field);
			}

			workspace.sceneInspectorChanged(field);
		}, onClose, (Runnable) () -> {
			if (historyAction != null) {
				historyAction.run();
			}

			workspace.sceneHistoryRestored();
		}));
		workspace.preview().showInspector();
		return group;
	}
}
