package org.unitego.lobecorp.util.resource;

import com.lowdragmc.lowdraglib2.editor.ui.browser.AssetBrowser;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// 开发模式下使用互相独立的目录入口，复用原生浏览、选择和保存行为。
public class DevelopmentResourceUiUtil {
	public static void assetBrowser(AssetBrowser browser) {
		var source = DevelopmentResourceUtil.sourceDirectory();
		var selected = workspaceRoot(browser.getRoot());
		if (source == null || selected == null) {
			return;
		}

		var root = browser.getRoot();
		if (root.toPath().toAbsolutePath().normalize().equals(DevelopmentResourceUtil.runDirectory().toPath().resolve("ldlib2"))) {
			root = source;
			selected = source;
		}

		var selector = selector(selected, browser::setRoot);
		selector.layout(layout -> layout.width(110).height(14).flexShrink(0));
		browser.toolbar.addChildAt(selector, 0);
		browser.setRoot(root);
	}

	public static void fileDialog(Dialog dialog, File directory, boolean isSelector,
	                              TextField textField, Consumer<File> navigate) {
		var selected = workspaceRoot(directory);
		if (selected == null) {
			return;
		}

		var selector = selector(selected, root -> {
			var name = textField.getText();
			var target = isSelector || name.isBlank() ? root : new File(root, new File(name).getName());
			navigate.accept(target);
		});
		selector.layout(layout -> layout.widthPercent(100).height(14).minWidth(0));
		dialog.contentContainer.addChildAt(selector, 0);
	}

	private static Selector<File> selector(File selected, Consumer<File> navigate) {
		var source = Objects.requireNonNull(DevelopmentResourceUtil.sourceDirectory());
		var run = DevelopmentResourceUtil.runDirectory();
		return new Selector<File>().setCandidates(List.of(run, source))
				.setCandidateUIProvider(root -> new Label().setText(Component.literal(run.equals(root) ? "run" : "src/main/resources")))
				.setSelected(selected, false).setOnValueChanged(navigate);
	}

	private static @Nullable File workspaceRoot(File directory) {
		var source = DevelopmentResourceUtil.sourceDirectory();
		if (source == null) {
			return null;
		}

		var path = directory.toPath().toAbsolutePath().normalize();
		if (path.startsWith(source.toPath())) {
			return source;
		}

		var run = DevelopmentResourceUtil.runDirectory();
		return path.startsWith(run.toPath()) ? run : null;
	}
}
