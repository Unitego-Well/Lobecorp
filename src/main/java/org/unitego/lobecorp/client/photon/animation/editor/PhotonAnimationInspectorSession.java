package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/// 原生 TL 配置器直接修改动画模型；会话将变更提交到工作区定义与动画历史。
public class PhotonAnimationInspectorSession implements IConfigurable {
	private final PhotonAnimationWorkspace workspace;
	private final IConfigurable configurable;
	private final @Nullable Consumer<Configurator> listener;
	private final @Nullable Runnable onClose;
	private boolean closed;

	public PhotonAnimationInspectorSession(PhotonAnimationWorkspace workspace, IConfigurable configurable,
	                                       @Nullable Consumer<Configurator> listener, @Nullable Runnable onClose) {
		this.workspace = workspace;
		this.configurable = configurable;
		this.listener = listener;
		this.onClose = onClose;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup group) {
		configurable.buildConfigurator(group);
	}

	public void changed(Configurator field) {
		if (closed) {
			return;
		}

		if (listener != null) {
			listener.accept(field);
		}

		workspace.commitInspectorChange(field.getNotifyName(), field);
	}

	public boolean matches(PhotonAnimationWorkspace owner, IConfigurable candidate) {
		return workspace == owner && (candidate == this || candidate == configurable);
	}

	public boolean active() {
		return !closed;
	}

	public void close() {
		if (closed) {
			return;
		}

		closed = true;

		if (onClose != null) {
			onClose.run();
		}
	}
}
