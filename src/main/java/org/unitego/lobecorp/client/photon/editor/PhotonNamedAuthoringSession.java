package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil.Document;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/// 草稿只在提交时写入当前工程；播放实例继续持有原有不可变定义，不由制作事务重启。
public class PhotonNamedAuthoringSession {
	private final FXEditor editor;
	private final FXProject project;
	private final UUID controller;
	private final String projectRevision;
	private final Document original;
	private Document draft;
	private long revision;
	private boolean closed;

	public PhotonNamedAuthoringSession(FXEditor editor, UUID controller) {
		if (!(editor.getCurrentProject() instanceof FXProject currentProject)) {
			throw new IllegalArgumentException("当前工程不支持命名动画制作。");
		}

		this.editor = editor;
		project = currentProject;
		this.controller = controller;
		projectRevision = PhotonEffectProjectUtil.revision(project);
		original = read(project, controller);
		draft = original;
	}

	public long revision() {
		return revision;
	}

	public Document document() {
		return draft;
	}

	public void edit(long expectedRevision, UnaryOperator<Document> operation) {
		requireOpen(expectedRevision);
		var result = Objects.requireNonNull(operation.apply(draft));
		if (!result.equals(draft)) {
			draft = result;
			revision++;
		}
	}

	/// 完整工程版本与目标原树共同校验，验证失败不写入数据或历史。
	public void commit(long expectedRevision) {
		validate(expectedRevision);

		if (draft.equals(original)) {
			closed = true;
			return;
		}

		var committed = draft;
		editor.timelineView.pushEdit("photon.lobecorp.animation.inspector_panel",
				() -> restore(editor, project, controller, committed), () -> restore(editor, project, controller, original));
		closed = true;
	}

	public void validate(long expectedRevision) {
		requireOpen(expectedRevision);
		if (editor.getCurrentProject() != project || !projectRevision.equals(PhotonEffectProjectUtil.revision(project))
				|| !original.equals(read(project, controller))) {
			throw new IllegalStateException("工程或命名定义已改变，请重新打开制作草稿。");
		}

		validateDraft();
	}

	public void cancel() {
		closed = true;
	}

	private void requireOpen(long expectedRevision) {
		if (closed || expectedRevision != revision) {
			throw new IllegalStateException("命名制作草稿已关闭或版本已失效。");
		}
	}

	private void validateDraft() {
		if (editor.runtime == null) {
			throw new IllegalStateException("制作预览实例已失效。");
		}
		if (PhotonEditorProjectGuardUtil.containsExtensions(project)) {
			throw new IllegalArgumentException(PhotonEditorProjectGuardUtil.message());
		}

		var wrapper = resolve(project, controller).serializeWrapper();
		if (wrapper == null) {
			throw new IllegalStateException("命名控制器无法序列化。");
		}

		wrapper.getCompoundOrEmpty("data").put("definition", draft.library());
		PhotonEditorProjectGuardUtil.requireSupported(wrapper);
		PhotonEditorProjectGuardUtil.requireSupported(draft.preview());

		try {
			PhotonNamedRuntime.validateLibrary(editor.runtime, draft.library());

			var preview = draft.preview();
			if (preview != null) {
				new PhotonNamedPlan(Platform.getFrozenRegistry(), editor.runtime, PhotonProjectPreviewUtil.read(preview), true);
			}
		} catch (IOException invalid) {
			throw new IllegalArgumentException("制作定义无法按当前协议读取。", invalid);
		}
	}

	private static Document read(FXProject project, UUID controller) {
		return new Document(resolve(project, controller).definition(), ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview());
	}

	private static PhotonNamedController resolve(FXProject project, UUID controller) {
		PhotonNamedController found = null;
		for (var object : project.getFx().getFxData().objects()) {
			if (object instanceof PhotonNamedController current && current.id().equals(controller)) {
				if (found != null) {
					throw new IllegalStateException("命名控制器身份重复。");
				}

				found = current;
			}
		}

		if (found == null) {
			throw new IllegalStateException("命名控制器已不属于当前工程。");
		}

		return found;
	}

	private static void restore(FXEditor editor, FXProject project, UUID controller, Document source) {
		if (editor.getCurrentProject() != project) {
			throw new IllegalStateException("命名制作历史已失效。");
		}

		var target = resolve(project, controller);
		if (editor.runtime instanceof PhotonNamedAccess access) {
			var playback = access.lobecorp$namedRuntime();
			if (playback != null && !playback.closed() && playback.controllerId().equals(controller)) {
				try {
					playback.redefine(source.library());
				} catch (IOException invalid) {
					throw new IllegalArgumentException("命名运行库无法刷新。", invalid);
				}
			}
		}

		target.definition(source.library());
		((PhotonProjectPreviewAccess) project).lobecorp$projectPreview(source.preview());
	}
}
