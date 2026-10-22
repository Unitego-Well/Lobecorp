package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// 表单逐字段复制原 NBT 类型，未知复合数据保留；不以摘要重建参数、绑定或规则。
public class PhotonNamedEntryFields {
	private static final Set<String> KNOWN = Set.of("id", "name", "type", "defaults", "minimum", "maximum",
			"target", "property", "blend", "source", "parameter", "component", "scale", "offset", "scaleParameter",
			"offsetParameter", "event", "animation", "channel", "duration", "inputs", "value", "follow", "time", "data");
	private static final Set<String> BOOLEANS = Set.of("follow", "loop", "autoDuration", "autoEnableModules");
	private static final Map<String, List<String>> CHOICES = Map.of(
			"type", List.of("NUMBER", "INTEGER", "BOOLEAN", "VECTOR", "COLOR", "FLOAT_VEC2", "FLOAT_VEC4", "INTEGER_VEC2", "INTEGER_VEC3", "INTEGER_VEC4"),
			"blend", List.of("OVERRIDE", "ADD", "MULTIPLY"),
			"source", List.of("CURVE", "PARAMETER", "EFFECT_TIME", "ANIMATION_TIME", "PROGRESS", "LOOP_COUNT"),
			"event", List.of("START", "COMPLETE", "MARKER", "SIGNAL", "TRIGGER"));

	public static void build(UIElement parent, Supplier<CompoundTag> current, Consumer<CompoundTag> change) {
		if (current.get().getStringOr("type", "").equals("COLOR") && current.get().getListOrEmpty("defaults").size() == 4) {
			parent.addChild(new ColorConfigurator("photon.lobecorp.animation.value", () -> color(current.get().getListOrEmpty("defaults")), value -> {
				var copy = current.get().copy();
				var rgba = new ListTag();
				for (int shift : new int[]{16, 8, 0, 24}) {
					rgba.add(FloatTag.valueOf(((value >>> shift) & 255) / 255f));
				}
				copy.put("defaults", rgba);
				change.accept(copy);
			}, color(current.get().getListOrEmpty("defaults")), false));
		}
		for (var key : current.get().keySet()) {
			if (!KNOWN.contains(key)) {
				continue;
			}
			field(parent, key, () -> current.get().get(key), next -> {
				var copy = current.get().copy();
				copy.put(key, next);
				change.accept(copy);
			});
		}
	}

	private static int color(ListTag rgba) {
		int value = 0;
		int[] shifts = {16, 8, 0, 24};
		for (int index = 0; index < 4; index++) {
			value |= Math.round(Math.clamp(((NumericTag) rgba.get(index)).floatValue(), 0, 1) * 255) << shifts[index];
		}
		return value;
	}

	private static void field(UIElement parent, String key, Supplier<Tag> current, Consumer<Tag> change) {
		var value = current.get();
		var label = label(key);
		switch (value) {
			case CompoundTag _ -> {
				var group = new ConfiguratorGroup(label, false);
				group.configuratorContainer(container -> build(container, () -> (CompoundTag) current.get(), change::accept));
				parent.addChild(group);
			}
			case ListTag list -> list(parent, key, current, change, label, list);
			case StringTag(var text) -> {
				if (key.equals("id")) {
					parent.addChild(new TextElement().setText(Component.literal(text)));
				} else if (CHOICES.containsKey(key) && CHOICES.get(key).contains(text)) {
					parent.addChild(new SelectorConfigurator<>(label, () -> ((StringTag) current.get()).value(),
							next -> change.accept(StringTag.valueOf(next)), text, false, CHOICES.get(key), next -> next));
				} else {
					parent.addChild(new StringConfigurator(label, () -> ((StringTag) current.get()).value(),
							next -> change.accept(StringTag.valueOf(next)), text, false));
				}
			}
			case ByteTag number when BOOLEANS.contains(key) -> parent.addChild(new BooleanConfigurator(label,
					() -> ((NumericTag) current.get()).byteValue() != 0, next -> change.accept(ByteTag.valueOf(next)),
					number.byteValue() != 0, false));
			case LongTag _ -> parent.addChild(new TextElement().setText(Component.literal(key + ": " + value)));
			case NumericTag number ->
					parent.addChild(new NumberConfigurator(label, () -> ((NumericTag) current.get()).doubleValue(),
							next -> change.accept(number(value, next)), number.doubleValue(), false).setRange(minimum(value), maximum(value)));
			case null -> {
			}
			default -> parent.addChild(new TextElement().setText(Component.literal(key + ": " + value)));
		}
	}

	private static void list(UIElement parent, String key, Supplier<Tag> current, Consumer<Tag> change, String label, ListTag list) {
		var group = new ConfiguratorGroup(label, false);
		group.configuratorContainer(container -> {
			for (int index = 0; index < list.size(); index++) {
				int selected = index;
				field(container, Integer.toString(index), () -> ((ListTag) current.get()).get(selected), next -> {
					var changed = ((ListTag) current.get()).copy();
					changed.set(selected, next);
					change.accept(changed);
				});
			}
		});
		if (key.equals("value")) {
			group.configuratorContainer(container -> {
				container.addChild(new Button().setText("+").setOnClick(_ -> {
					var changed = ((ListTag) current.get()).copy();
					if (changed.size() < 4) {
						changed.add(FloatTag.valueOf(0));
						change.accept(changed);
					}
				}));
				container.addChild(new Button().setText("-").setOnClick(_ -> {
					var changed = ((ListTag) current.get()).copy();
					if (changed.size() > 1) {
						changed.removeLast();
						change.accept(changed);
					}
				}));
			});
		}
		if (key.equals("inputs")) {
			group.configuratorContainer(container -> container.addChild(new Button().setText("photon.lobecorp.animation.new").setOnClick(_ -> {
				var input = new CompoundTag();
				input.putString("target", "");
				input.putString("source", "");
				var defaults = new ListTag();
				defaults.add(FloatTag.valueOf(1));
				input.put("value", defaults);
				input.putBoolean("follow", false);
				var changed = ((ListTag) current.get()).copy();
				changed.add(input);
				change.accept(changed);
			})));
		}
		parent.addChild(group);
	}

	private static String label(String key) {
		if (key.matches("[0-9]+")) {
			return key;
		}
		String suffix = switch (key) {
			case "defaults", "data" -> "value";
			case "inputs" -> "test_inputs";
			case "follow" -> "input_follow";
			case "animation" -> "rule_target";
			case "scaleParameter" -> "scale_parameter";
			case "offsetParameter" -> "offset_parameter";
			default -> key;
		};
		return "photon.lobecorp.animation." + suffix;
	}

	private static double minimum(Tag type) {
		return switch (type) {
			case ByteTag _ -> Byte.MIN_VALUE;
			case ShortTag _ -> Short.MIN_VALUE;
			case IntTag _ -> Integer.MIN_VALUE;
			case FloatTag _ -> -Float.MAX_VALUE;
			default -> -Double.MAX_VALUE;
		};
	}

	private static double maximum(Tag type) {
		return switch (type) {
			case ByteTag _ -> Byte.MAX_VALUE;
			case ShortTag _ -> Short.MAX_VALUE;
			case IntTag _ -> Integer.MAX_VALUE;
			case FloatTag _ -> Float.MAX_VALUE;
			default -> Double.MAX_VALUE;
		};
	}

	private static Tag number(Tag template, Number value) {
		return switch (template) {
			case ByteTag _ -> ByteTag.valueOf(value.byteValue());
			case ShortTag _ -> ShortTag.valueOf(value.shortValue());
			case IntTag _ -> IntTag.valueOf(value.intValue());
			case LongTag _ -> LongTag.valueOf(value.longValue());
			case FloatTag _ -> FloatTag.valueOf(value.floatValue());
			case DoubleTag _ -> DoubleTag.valueOf(value.doubleValue());
			default -> throw new IllegalArgumentException("字段不是可编辑的数值。");
		};
	}
}
