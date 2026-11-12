package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

public class PhotonEditorNotificationUtil {

	/// 导出成功沿用原生保存成功通知的显示时间，单位为秒。
	public static final float SUCCESS_DURATION_SECONDS = 2;
	/// 同一 Photon 编辑器最多同时显示的定时通知数量。
	private static final int MAX_NOTIFICATIONS = 3;
	/// 同一编辑器内查找通知容器的稳定标识。
	private static final String STACK_ID = "lobecorp_photon_notifications";
	/// 通知间距，单位为界面像素。
	private static final float STACK_GAP = 2;
	/// 通知距编辑器顶部的距离，沿用原生 top() 的 10 像素。
	private static final float TOP_INSET = 10;
	/// 在窗口内与工程标签栏同级排序，沿用原生对话框的层级。
	private static final int STACK_Z_INDEX = 1;

	public static @Nullable UIElement parent(Dialog dialog, UIElement requestedParent) {
		var ui = requestedParent.getModularUI();
		var editor = ui == null ? null : PhotonEditorSaveUtil.currentProjectEditor(ui, requestedParent);

		if (!(editor instanceof PhotonEffectEditor)) {
			return null;
		}

		var host = editor.getWindow() == null ? editor : editor.getWindow().window;
		var stack = host.getChildren().stream()
				.filter(child -> child instanceof NotificationStack notifications && notifications.editor == editor).findFirst()
				.orElseGet(() -> createStack(editor, host));

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

	private static UIElement createStack(Editor editor, UIElement host) {
		var stack = new NotificationStack(editor).setId(STACK_ID).setAllowHitTest(false);
		stack.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).top(TOP_INSET).widthPercent(100)
				.flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER).gapAll(STACK_GAP));
		stack.style(style -> style.zIndex(STACK_Z_INDEX));
		stack.addEventListener(UIEvents.TICK, event -> removeEmptyStack(event.currentElement));
		host.addChild(stack);
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

	private static class NotificationStack extends UIElement {
		private final Editor editor;

		private NotificationStack(Editor editor) {
			this.editor = editor;
		}

		@Override
		protected boolean shouldDrawChildren() {
			var window = editor.getWindow();
			return editor.isDisplayed() && editor.isVisible() && editor.isActive()
					&& (window == null || window.getCurrentEditor() == editor);
		}

		@Override
		public void screenTick() {
			var parent = getParent();
			var window = editor.getWindow();

			if (parent != editor && (window == null || parent != window.window || !window.getEditors().containsKey(editor))) {
				if (parent != null) {
					parent.removeChild(this);
				}

				return;
			}

			if (shouldDrawChildren()) {
				super.screenTick();
			}
		}
	}
}
