package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;

public class PhotonAnimationForms {
	public enum Page {
		ANIMATION("title"), BINDINGS("bindings"), PARAMETERS("parameters"), RULES("rules");
		private final String key;

		@SuppressWarnings("UnnecessaryModifier") // 项目规范要求明确最小可见性。
		private Page(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	public static void build(PhotonAnimationWorkspace workspace, Page page, UIElement container) {
		var library = workspace.library();
		migrationReport(library, container);

		if (library.migrationBlocked()) {
			return;
		}

		switch (page) {
			case ANIMATION -> animation(workspace, container);
			case BINDINGS -> PhotonAnimationBindingEditor.build(workspace, container);
			case PARAMETERS -> PhotonAnimationParameterEditor.build(workspace, container);
			case RULES -> PhotonAnimationRuleEditor.build(workspace, container);
		}
	}

	public static void migrationReport(PhotonAnimationLibrary library, UIElement container) {
		if (library.migrationBlocked()) {
			reportItems(group(container, "migration_blocked"), library.migrationProblems());
		}

		var warnings = library.migrationWarnings();

		if (!warnings.isEmpty()) {
			reportItems(group(container, "migration_discarded"), warnings);
		}
	}

	private static void reportItems(ConfiguratorGroup group, List<String> items) {
		for (var item : items) {
			var text = new TextElement().setText(Component.literal(item));
			text.layout(layout -> layout.widthPercent(100));
			group.addConfigurator(new Configurator().addInlineChild(text));
		}
	}

	public static ConfiguratorGroup group(UIElement container, String key) {
		var group = new ConfiguratorGroup(PhotonAnimationTextUtil.key(key), false);
		PhotonAnimationUiUtil.tips(group, key);
		container.addChild(group);
		return group;
	}

	public static void animationSelector(PhotonAnimationWorkspace workspace, ConfiguratorGroup group,
	                                     String key, Supplier<String> getter, Consumer<String> setter) {
		var candidates = new ArrayList<String>();
		candidates.add("");
		workspace.library().animations.forEach(value -> candidates.add(value.id.toString()));
		select(group, key, getter, setter, candidates, value -> {
			var animation = workspace.library().find(value);
			return animation == null ? value : animation.name;
		});
	}

	public static <T> void select(ConfiguratorGroup group, String key, Supplier<T> getter, Consumer<T> setter,
	                              List<T> candidates, Function<T, String> name) {
		var field = new SelectorConfigurator<>(PhotonAnimationTextUtil.key(key), getter, setter,
				candidates.getFirst(), false, candidates, name);
		PhotonAnimationUiUtil.tips(field, key);
		group.addConfigurator(field);
	}

	private static void animation(PhotonAnimationWorkspace workspace, UIElement container) {
		var group = group(container, "title");
		animationSelector(workspace, group, "default", () -> workspace.library().defaultAnimation,
				value -> workspace.edit(() -> workspace.library().defaultAnimation = value));

		if (!workspace.hasSelection()) {
			var text = new TextElement().setText(PhotonAnimationTextUtil.key("test_timeline"));
			text.layout(layout -> layout.widthPercent(100).flexShrink(0));
			text.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP));
			PhotonAnimationUiUtil.tooltip(text, "test_timeline");
			container.addChild(text);
			workspace.testControls().build(container);
			return;
		}

		var animation = workspace.selected();
		authoredDuration(group, animation);
		PhotonAnimationUiUtil.bool(group, "auto_duration", () -> animation.autoDuration,
				value -> workspace.edit(() -> animation.autoDuration = value));
		PhotonAnimationUiUtil.number(group, "duration", () -> animation.duration,
				value -> workspace.edit(() -> animation.duration = Math.max(animation.loop ? 1 : 0, value.doubleValue())), animation.duration);
		PhotonAnimationInputEditor.parameterSelector(group, "duration_parameter", () -> animation.durationParameter,
				value -> workspace.edit(() -> animation.durationParameter = value), animation,
				PhotonAnimationInputEditor::scalar);
		PhotonAnimationUiUtil.bool(group, "auto_enable_modules", () -> animation.autoEnableModules,
				value -> workspace.edit(() -> animation.autoEnableModules = value));
		PhotonAnimationUiUtil.bool(group, "loop", () -> animation.loop,
				value -> workspace.edit(() -> animation.loop = value && animation.playbackDuration() > 0));
		PhotonAnimationUiUtil.number(group, "priority", () -> animation.priority,
				value -> workspace.edit(() -> animation.priority = value.intValue()), 0);
		PhotonAnimationUiUtil.enumeration(group, "completion", () -> animation.completion,
				value -> workspace.edit(() -> animation.completion = value), PhotonAnimationDefinition.Completion.values());
		workspace.testControls().build(container);
	}

	public static NumberConfigurator liveNumber(ConfiguratorGroup group, String key, Supplier<Number> getter,
	                                            Consumer<Number> setter, Number defaultValue) {
		var field = new NumberConfigurator(PhotonAnimationTextUtil.key(key), getter, setter, defaultValue, false) {
			@Override
			public void screenTick() {
				super.screenTick();

				if (!textField.isFocused()) {
					onValueUpdatePassively(supplier.get());
				}
			}
		};
		PhotonAnimationUiUtil.tips(field, key);
		group.addConfigurator(field);
		return field;
	}

	public static void readOnlyNumber(ConfiguratorGroup group, String key, Supplier<Number> getter, Number defaultValue) {
		var field = liveNumber(group, key, getter, _ -> {
		}, defaultValue);
		field.textField.setActive(false);
		field.setPastable(_ -> false, _ -> {
		});
		field.setCanDropPredicate(_ -> false);
	}

	private static void authoredDuration(ConfiguratorGroup group, PhotonAnimationDefinition animation) {
		readOnlyNumber(group, "authored_duration", animation::authoredDuration, animation.authoredDuration());
	}

}
