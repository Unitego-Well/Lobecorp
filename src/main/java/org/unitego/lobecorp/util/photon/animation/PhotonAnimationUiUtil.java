package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock.State;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhotonAnimationUiUtil {
	/// 状态文本只传入原生允许的翻译参数；问题集合在显示边界转成字符串。
	public static Component animationStatus(State state, double time, double progress, long loops, List<String> problems) {
		return Component.translatable(PhotonAnimationTextUtil.key("status_format"),
				Component.translatable(PhotonAnimationTextUtil.key("enum." + state.name())),
				time, progress, loops, problems.toString());
	}

	public static UIElement row() {
		return new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
	}

	public static UIElement column() {
		return new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
	}

	public static ScrollerView scroller(UIElement content) {
		var view = new ScrollerView();
		view.layout(layout -> layout.flex(1).widthPercent(100).minHeight(0));
		content.getLayout().setFlexShrink(0);
		view.addScrollViewChild(content);
		return view;
	}

	public static Button button(String key, Runnable action) {
		var value = new Button().setText(PhotonAnimationTextUtil.key(key)).setOnClick(_ -> action.run());
		value.getLayout().setFlexShrink(0);
		tooltip(value, key);
		return value;
	}

	public static void tooltip(UIElement value, String key) {
		PhotonEditorTooltipUtil.tooltip(value, PhotonAnimationTextUtil.key(key + ".tooltip"));
	}

	public static void tips(Configurator value, String key) {
		PhotonEditorTooltipUtil.tips(value, PhotonAnimationTextUtil.key(key + ".tooltip"));
	}

	public static void number(ConfiguratorGroup group, String key, Supplier<Number> getter,
	                          Consumer<Number> setter, Number defaultValue) {
		var field = new NumberConfigurator(PhotonAnimationTextUtil.key(key), getter, setter, defaultValue, false);
		tips(field, key);
		group.addConfigurator(field);
	}

	public static void text(ConfiguratorGroup group, String key, Supplier<String> getter, Consumer<String> setter) {
		var field = new StringConfigurator(PhotonAnimationTextUtil.key(key), getter, setter, "", false);
		tips(field, key);
		group.addConfigurator(field);
	}

	public static void bool(ConfiguratorGroup group, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		var field = new BooleanConfigurator(PhotonAnimationTextUtil.key(key), getter, setter, false, false);
		tips(field, key);
		group.addConfigurator(field);
	}

	public static void parameterValues(ConfiguratorGroup group, String key, PhotonAnimationParameter parameter,
	                                   Supplier<float[]> getter, Consumer<float[]> setter) {
		if (parameter.type == PhotonAnimationParameter.Type.BOOLEAN) {
			var field = new BooleanConfigurator(PhotonAnimationTextUtil.key(key), () -> getter.get()[0] >= 0.5f,
					value -> setter.accept(parameter.validate(new float[]{value ? 1 : 0})), parameter.defaults[0] >= 0.5f, false);
			tips(field, key);
			group.addConfigurator(field);
			return;
		}

		if (parameter.type == PhotonAnimationParameter.Type.COLOR) {
			parameterColor(group, key, parameter, getter, setter);
		}

		for (int axis = 0; axis < parameter.type.channels(); axis++) {
			parameterComponent(group, key, parameter, getter, setter, axis);
		}
	}

	private static void parameterComponent(ConfiguratorGroup group, String key, PhotonAnimationParameter parameter,
	                                       Supplier<float[]> getter, Consumer<float[]> setter, int axis) {
		var label = parameter.type.channels() == 1 ? key : PhotonAnimationTextUtil.componentSuffix(parameter.type, axis);
		var field = new NumberConfigurator(PhotonAnimationTextUtil.key(label), () -> getter.get()[axis], value -> {
			var values = getter.get().clone();
			values[axis] = value.floatValue();
			setter.accept(parameter.validate(values));
		}, parameter.defaults[axis], false);
		field.setType(parameter.type.isInteger() ? ConfigNumber.Type.INTEGER : ConfigNumber.Type.FLOAT);
		field.setRange(parameter.effectiveMinimum(), parameter.effectiveMaximum());
		tips(field, key);
		group.addConfigurator(field);
	}

	private static void parameterColor(ConfiguratorGroup group, String key, PhotonAnimationParameter parameter,
	                                   Supplier<float[]> getter, Consumer<float[]> setter) {
		var field = new ColorConfigurator(PhotonAnimationTextUtil.key(key), () -> color(getter.get()), value -> {
			var color = ColorUtils.toVector4f(value);
			setter.accept(parameter.validate(new float[]{color.x, color.y, color.z, color.w}));
		}, color(parameter.defaults), false);
		tips(field, key);
		group.addConfigurator(field);
	}

	private static int color(float[] values) {
		return ColorUtils.fromVector4f(new Vector4f(values[0], values[1], values[2], values[3]));
	}

	public static boolean editingInput(UIElement focused) {
		if (focused == null) {
			return false;
		}

		if (PhotonResourceClipboardUtil.textTarget(focused)) {
			return true;
		}

		for (var element = focused; element != null; element = element.getParent()) {
			if (element instanceof ColorSelector) {
				return true;
			}
		}

		return false;
	}

	public static <T extends Enum<T>> void enumeration(ConfiguratorGroup group, String key, Supplier<T> getter,
	                                                   Consumer<T> setter, T[] values) {
		var field = new SelectorConfigurator<>(PhotonAnimationTextUtil.key(key), getter, setter, values[0],
				false, Arrays.asList(values), value -> PhotonAnimationTextUtil.key("enum." + value.name()));
		tips(field, key);
		group.addConfigurator(field);
	}
}
