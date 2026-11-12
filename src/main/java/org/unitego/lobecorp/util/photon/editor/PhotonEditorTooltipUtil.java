package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTooltipTranslationKeys;

import java.util.function.Supplier;

/// 扩展选项的悬浮说明，复用 LDLib2 原生配置行提示图标。
public class PhotonEditorTooltipUtil {
	/// 配置标签对应的悬浮说明翻译后缀。
	public static final String TOOLTIP_SUFFIX = PhotonEditorTooltipTranslationKeys.TOOLTIP_SUFFIX;

	public static boolean hasTooltip(String name) {
		return PhotonEditorTooltipTranslationKeys.hasTooltip(name);
	}

	public static void apply(Configurator configurator, String name) {
		if (!hasTooltip(name)) {
			return;
		}

		tips(configurator, name + TOOLTIP_SUFFIX);
	}

	/// 配置说明仅放在原生帮助图标，保留输入控件自己的校验提示。
	public static <T extends Configurator> T tips(T configurator, String name) {
		var message = Component.translatable(name);
		configurator.tip.getStyle().tooltips(message);
		configurator.tip.setDisplay(true);
		wrap(configurator.tip, message);
		return configurator;
	}

	/// 普通控件使用自身提示；配置行统一转到帮助图标。
	public static void tooltip(UIElement element, String name) {
		if (element instanceof Configurator configurator) {
			tips(configurator, name);
			return;
		}

		var message = Component.translatable(name);
		element.getStyle().tooltips(message);
		wrap(element, message);
	}

	/// 悬浮时复用原生提示换行与语言缓存，避免提前读取客户端字体。
	public static void wrap(UIElement element, Component message) {
		wrap(element, () -> message);
	}

	/// 提示内容变化时更新原生缓存；语言变化继续由原生提示处理。
	public static void wrap(UIElement element, Supplier<Component> messageSupplier) {
		for (var listener : element.getBubbleListeners(UIEvents.HOVER_TOOLTIPS)) {
			if (listener instanceof WrappedTooltip wrapped) {
				wrapped.messageSupplier = messageSupplier;
				return;
			}
		}

		element.addEventListener(UIEvents.HOVER_TOOLTIPS, new WrappedTooltip(messageSupplier));
	}


	/// 监听器归控件所有，重复注册只替换消息来源，不持有静态窗口引用。
	private static class WrappedTooltip implements UIEventListener {
		private Supplier<Component> messageSupplier;
		private Component message;
		private Tooltip tooltip;

		private WrappedTooltip(Supplier<Component> messageSupplier) {
			this.messageSupplier = messageSupplier;
		}

		@Override
		public void handleEvent(UIEvent event) {
			var currentMessage = messageSupplier.get();

			if (tooltip == null || !currentMessage.equals(message)) {
				message = currentMessage;
				tooltip = Tooltip.create(currentMessage);
			}

			event.hoverTooltips = HoverTooltips.create(tooltip.toCharSequence(Minecraft.getInstance()).toArray());
		}
	}
}
