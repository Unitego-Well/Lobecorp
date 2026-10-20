package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.gui.editor.FXProject;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

/// 原生工程快照恢复会替换对象实例，预览不能继续引用恢复前的场景对象。
public class PhotonEditorRuntimeUtil {
	public static void refreshRestoredProject(PhotonEffectEditor editor) {
		if (!(editor.getCurrentProject() instanceof FXProject project) || editor.runtime == null) {
			return;
		}
		var data = project.getFx().getFxData();
		var previous = editor.runtime;
		boolean detached = previous.fxData != data || previous.objects.size() != data.objects().size() + 1
				|| data.objects().stream().anyMatch(object -> previous.objects.get(object.id()) != object);
		if (!detached) {
			return;
		}

		long time = editor.timelineView.currentTimeTicks();
		boolean playing = editor.sceneView.particleManager.isPlaying();
		previous.destroy(true);
		editor.runtime = project.getFx().createInternalRuntime();
		// 与Photon原生加载入口相同的预览偏移，不写回工程对象。
		editor.runtime.root.updatePos(new Vector3f(0.5f, 2, 0.5f));
		editor.hierarchyView.loadFXRuntime(editor.runtime);
		editor.timelineView.rebuild();
		editor.reloadEffect();
		if (!playing) {
			editor.sceneView.particleManager.pause();
		}
		editor.sceneView.requestSimulateTo(time);
	}
}
