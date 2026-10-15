package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;

public class PhotonEditorNotificationUtil {

	/// 同一 Photon 编辑器最多同时显示的定时通知数量。
	private static final int MAX_NOTIFICATIONS = 3;
	/// 导出成功沿用原生保存成功通知的显示时间，单位为秒。
	public static final float SUCCESS_DURATION_SECONDS = 2;
	/// 同一编辑器内查找通知容器的稳定标识。
	private static final String STACK_ID = "lobecorp_photon_notifications";
	/// 通知间距，单位为界面像素。
	private static final float STACK_GAP = 2;
	/// 通知距编辑器顶部的距离，沿用原生 top() 的 10 像素。
	private static final float TOP_INSET = 10;
	/// 定时提示绘制在普通编辑器面板之后，沿用原生对话框的层级。
	private static final int STACK_Z_INDEX = 1;

	public static @Nullable UIElement parent(Dialog dialog, UIElement requestedParent) {
		var ui = requestedParent.getModularUI();
		var editor = ui == null ? null : PhotonEditorSaveUtil.currentProjectEditor(ui, requestedParent);

		if (editor == null || !PhotonEditorSettings.of(editor).enabled(PhotonEditorTextUtil.NOTIFICATIONS_FEATURE)) {
			return null;
		}

		var stack = editor.getChildren().stream().filter(child -> STACK_ID.equals(child.getId())).findFirst()
				.orElseGet(() -> createStack(editor));

		if (dialog.getParent() == stack) {
			return stack;
		}

		while (stack.getChildren().size() >= MAX_NOTIFICATIONS) {
			((Dialog) stack.getChildren().getFirst()).close();
		}

		dialog.getLayout().positionType(TaffyPosition.RELATIVE).flexShrink(0);
		dialog.overlay.getLayout().top(0);
		dialog.selfAndAllChildren().forEach(element -> element.setAllowHitTest(false));
		return stack;
	}

	public static boolean isNotificationStack(@Nullable UIElement element) {
		return element != null && STACK_ID.equals(element.getId());
	}

	private static UIElement createStack(UIElement editor) {
		var stack = new UIElement().setId(STACK_ID).setAllowHitTest(false);
		stack.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).top(TOP_INSET).widthPercent(100)
				.flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER).gapAll(STACK_GAP));
		stack.style(style -> style.zIndex(STACK_Z_INDEX));
		stack.addEventListener(UIEvents.TICK, event -> removeEmptyStack(event.currentElement));
		editor.addChild(stack);
		return stack;
	}

	public static void removeEmptyStack(@Nullable UIElement stack) {
		if (stack == null || !STACK_ID.equals(stack.getId()) || !stack.getChildren().isEmpty()) {
			return;
		}

		var parent = stack.getParent();

		if (parent != null) {
			parent.removeChild(stack);
		}
	}
}
