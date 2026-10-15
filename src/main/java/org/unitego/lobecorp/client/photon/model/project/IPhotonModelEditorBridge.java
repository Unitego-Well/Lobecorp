package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/// 模型工程复用 Editor 基类加载生命周期，绕过 FXProject 专属加载分支。
@SuppressWarnings("UnnecessaryModifier") // 项目规范要求接口成员显式声明可见性。
public interface IPhotonModelEditorBridge {
	public void lobecorp$loadProjectBase(IProject project, @Nullable File projectFile);
}
