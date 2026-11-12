package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.photon.client.fx.FXRuntime;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedAuthoringUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedTrackAuthoringUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys.*;
import static org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys.ACTION_DUPLICATE;

/// 原工作区的动画、绑定、参数和规则四页检视，统一编辑同一原树草稿。
public class PhotonNamedInspectorPages extends TabView {
	private final PhotonNamedAuthoringSession session;
	private final Supplier<UUID> selected;
	private final Consumer<String> error;
	private final Map<String, UIElement> pages = new LinkedHashMap<>();
	private final UIElement animationProperties;
	private final Runnable advancedRules;

	public PhotonNamedInspectorPages(PhotonNamedAuthoringSession session, Supplier<UUID> selected,
	                                 UIElement animation, Consumer<String> error, Runnable advancedRules) {
		this.session = session;
		this.selected = selected;
		this.error = error;
		this.advancedRules = advancedRules;
		animationProperties = animation;
		pages.put("markers", new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3)));
		layout(layout -> layout.widthPercent(100).flex(1).minHeight(0).flexDirection(FlexDirection.COLUMN_REVERSE));
		tabContentContainer.layout(layout -> layout.flex(1).minHeight(0).paddingAll(0));
		tabContentContainer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
		addPage("title", animation);
		for (var key : List.of("bindings", "parameters", "rules")) {
			var content = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
			pages.put(key, content);
			addPage(key, content);
		}
	}

	private static String availableName(ListTag values, String prefix) {
		var name = new StringBuilder(prefix);
		while (true) {
			String candidate = name.toString();
			if (values.stream().noneMatch(value -> ((CompoundTag) value).getStringOr("name", "").equals(candidate))) {
				return candidate;
			}
			name.append("_copy");
		}
	}

	private static String shape(Tag value) {
		if (value instanceof ListTag list) {
			return list.stream().map(PhotonNamedInspectorPages::shape).toList().toString();
		}
		if (value instanceof CompoundTag compound) {
			return compound.keySet().stream().sorted().map(key -> key + ":" + shape(compound.get(key))).toList().toString();
		}
		return Integer.toString(value.getId());
	}

	private void addPage(String name, UIElement content) {
		var scroll = new ScrollerView();
		scroll.layout(layout -> layout.widthPercent(100).flex(1).minHeight(0));
		scroll.scrollerStyle(style -> style.adaptiveHeight(false));
		scroll.addScrollViewChild(content);
		addTab(new Tab().setText(Component.translatable(PhotonAnimationTranslationKeys.key(name))), scroll);
	}

	public void refresh() {
		animationProperties.addChild(pages.get("markers"));
		for (var entry : pages.entrySet()) {
			var key = entry.getKey();
			var content = entry.getValue();
			content.clearAllChildren();
			if (!key.equals("rules") && selected.get() == null) {
				continue;
			}
			if (key.equals("rules")) {
				defaultAnimation(content);
				content.addChild(new Button().setText(Component.literal("NBT")).setOnClick(_ -> advancedRules.run()));
			}
			var list = list(key);
			for (int index = 0; index < list.size(); index++) {
				var state = new Row(key, index, (CompoundTag) list.get(index));
				var group = new ConfiguratorGroup(state.value.getStringOr("name", Integer.toString(index + 1)), false);
				group.configuratorContainer(container -> PhotonNamedEntryFields.build(container, () -> state.value, state::edit));
				content.addChild(group);
				var buttons = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW).gapAll(3));
				buttons.addChild(new Button().setText(DELETE).setOnClick(_ -> state.remove()));
				buttons.addChild(new Button().setText(ACTION_DUPLICATE).setOnClick(_ -> state.duplicate()));
				buttons.addChild(new Button().setText(Component.literal("↑")).setOnClick(_ -> state.move(-1)));
				buttons.addChild(new Button().setText(Component.literal("↓")).setOnClick(_ -> state.move(1)));
				content.addChild(buttons);
			}
			content.addChild(new Button().setText(NEW).setOnClick(_ -> attempt(() -> {
				var values = list(key).copy();
				values.add(create(key, values));
				update(key, values);
				refresh();
			})));
		}
	}

	private void defaultAnimation(UIElement content) {
		var choices = new ArrayList<String>();
		choices.add("");
		for (var value : session.document().library().getListOrEmpty("animations")) {
			choices.add(((CompoundTag) value).getStringOr("name", ""));
		}
		content.addChild(new SelectorConfigurator<>(DEFAULT,
				() -> session.document().library().getStringOr("defaultAnimation", ""),
				value -> attempt(() -> session.edit(session.revision(), source -> PhotonNamedAuthoringUtil.defaultAnimation(source, value))),
				"", false, choices, value -> value));
	}

	private ListTag list(String key) {
		return key.equals("rules") ? session.document().library().getListOrEmpty(key)
				: PhotonNamedTrackAuthoringUtil.definition(session.document(), requireSelected(), false).getListOrEmpty(key);
	}

	private UUID requireSelected() {
		var id = selected.get();
		if (id == null) {
			throw new IllegalStateException("请先选择命名动画。");
		}
		return id;
	}

	private void update(String key, ListTag list) {
		session.edit(session.revision(), source -> {
			if (key.equals("rules")) {
				return PhotonNamedAuthoringUtil.rules(source, list);
			}
			var patch = new CompoundTag();
			patch.put(key, list);
			return PhotonNamedAuthoringUtil.patchDefinition(source, requireSelected(), patch);
		});
	}

	private CompoundTag create(String key, ListTag list) {
		var tag = new CompoundTag();
		switch (key) {
			case "parameters" -> {
				tag.putString("id", UUID.randomUUID().toString());
				tag.putString("name", availableName(list, "parameter"));
				tag.putString("type", "NUMBER");
				var defaults = new ListTag();
				defaults.add(FloatTag.valueOf(0));
				tag.put("defaults", defaults);
				tag.putFloat("minimum", -Float.MAX_VALUE);
				tag.putFloat("maximum", Float.MAX_VALUE);
			}
			case "bindings" -> {
				tag.putString("target", FXRuntime.ROOT_UUID.toString());
				tag.putString("property", "");
				tag.putString("blend", "OVERRIDE");
				tag.putString("source", "CURVE");
				tag.putInt("component", -1);
				tag.putFloat("scale", 1);
				tag.putFloat("offset", 0);
				tag.putString("parameter", "");
				tag.putString("scaleParameter", "");
				tag.putString("offsetParameter", "");
			}
			case "markers" -> {
				tag.putDouble("time", 0);
				tag.putString("name", availableName(list, "marker"));
				tag.put("data", new CompoundTag());
			}
			case "rules" -> {
				tag.putString("event", "TRIGGER");
				tag.putString("animation", requireSelected().toString());
				tag.putString("channel", "");
				tag.putString("name", "");
				tag.putString("target", "");
				tag.putDouble("duration", -1);
				tag.put("inputs", new ListTag());
			}
			default -> throw new IllegalArgumentException("未知制作列表：" + key);
		}
		return tag;
	}

	private void attempt(Runnable action) {
		try {
			action.run();
			error.accept("");
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			error.accept(invalid.getMessage() == null ? invalid.toString() : invalid.getMessage());
		}
	}

	private class Row {
		private final String key;
		private final int index;
		private CompoundTag value;

		private Row(String key, int index, CompoundTag value) {
			this.key = key;
			this.index = index;
			this.value = value.copy();
		}

		private ListTag checked() {
			var list = list(key).copy();
			if (index >= list.size() || !value.equals(list.get(index))) {
				throw new IllegalStateException("列表已改变，请重新选择动画刷新表单。");
			}
			return list;
		}

		private void edit(CompoundTag changed) {
			attempt(() -> {
				var list = checked();
				boolean structureChanged = !shape(value).equals(shape(changed));
				boolean typeChanged = key.equals("parameters") && !changed.getStringOr("type", "").equals(value.getStringOr("type", ""));
				if (typeChanged) {
					var type = PhotonNamedLibrary.ParameterType.valueOf(changed.getStringOr("type", ""));
					var defaults = changed.getListOrEmpty("defaults").copy();
					while (defaults.size() < type.channels()) {
						defaults.add(FloatTag.valueOf(0));
					}
					while (defaults.size() > type.channels()) {
						defaults.removeLast();
					}
					changed.put("defaults", defaults);
				}
				list.set(index, changed.copy());
				update(key, list);
				value = changed.copy();
				if (typeChanged || structureChanged) {
					refresh();
				}
			});
		}

		private void remove() {
			attempt(() -> {
				var list = checked();
				list.remove(index);
				update(key, list);
				refresh();
			});
		}

		private void duplicate() {
			attempt(() -> {
				var list = checked();
				var copy = value.copy();
				if (key.equals("parameters")) {
					copy.putString("id", UUID.randomUUID().toString());
					copy.putString("name", availableName(list, "parameter"));
				}
				list.add(copy);
				update(key, list);
				refresh();
			});
		}

		private void move(int direction) {
			attempt(() -> {
				var list = checked();
				int target = index + direction;
				if (target < 0 || target >= list.size()) {
					return;
				}
				var moved = list.remove(index);
				list.add(target, moved);
				update(key, list);
				refresh();
			});
		}
	}
}
