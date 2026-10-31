package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;

import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/// 与原生入口使用不同窗口身份，标签和恢复流程始终保留扩展编辑器类型。
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PhotonEffectEditor extends FXEditor {
	public static final String COMMAND_NAME = "photon_editor";
	public static final String OPEN_COMMAND = Lobecorp.NAMESPACE + " " + COMMAND_NAME;
	public static final Identifier WINDOW_ID = Lobecorp.id(COMMAND_NAME);

	private PhotonOwnedLaser ownedPreview;
	private long ownedPreviewRevision;

	public PhotonEffectEditor() {
		sceneView.addChild(new Button().setText("自有激光：读取当前工程").setOnClick(_ -> beginOwnedPreview()));
		sceneView.addChild(new Button().setText("自有激光：发射").setOnClick(_ -> outputOwnedPreview()));
		sceneView.addChild(new Button().setText("自有激光：停止").setOnClick(_ -> stopOwnedPreview()));
	}

	private void beginOwnedPreview() {
		if (!(getCurrentProject() instanceof FXProject project)) {
			return;
		}

		stopOwnedPreview();
		ownedPreview = PhotonOwnedLaser.preview(project.getFx(), ++ownedPreviewRevision, sceneView.level);
		if (runtime != null) {
			runtime.destroy(true);
		}

		sceneView.particleManager.play();
		for (var name : List.of("circle", "circle2", "circle3", "light_ball", "accumulate_particle")) {
			ownedPreview.activate(name, "charge_start", -1, Map.of());
		}
	}

	private void outputOwnedPreview() {
		if (ownedPreview == null) {
			return;
		}

		ownedPreview.stop("accumulate_particle", true);
		ownedPreview.activate("light_ball", "laser_start", -1, Map.of());
		ownedPreview.activate("laser", "laser_start", -1, Map.of("length", List.of(16f), "width", List.of(1f), "enabled", List.of(1f)));
	}

	private void stopOwnedPreview() {
		if (ownedPreview != null) {
			ownedPreview.close();
			ownedPreview = null;
			PhotonOwnedEffectResources.invalidateAuthoring(PhotonOwnedLaser.ID);
		}
	}

	@Override
	protected void closeCurrentProject() {
		stopOwnedPreview();
		super.closeCurrentProject();
	}

	@Override
	protected Editor createNewEditorInstance() {
		return new PhotonEffectEditor();
	}

	public static boolean open() {
		var minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}

		var existing = EditorWindow.osWindowOf(WINDOW_ID);
		if (existing != null && existing.isOpen()) {
			return existing.getEditorWindow().dockIntoGameWindow();
		}

		EditorWindow.open(WINDOW_ID, PhotonEffectEditor::new).hostAsScreen();
		return true;
	}
}
