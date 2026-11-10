package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.VisualQualityConfig;
import org.unitego.lobecorp.registry.translation.photon.visual.PhotonVisualQualityTranslations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 编辑器只持有设置草稿；应用时写入同一NeoForge客户端配置，取消不改运行值。
public class VisualQualitySettings implements Settings {
	public static final Identifier ID = Lobecorp.id("visual_quality");
	public static final Codec<VisualQualitySettings> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
			.xmap(VisualQualitySettings::new, settings -> settings.values);
	private final Map<String, String> values;

	public VisualQualitySettings() {
		this(Map.of());
	}

	public VisualQualitySettings(Map<String, String> values) {
		this.values = new LinkedHashMap<>(values);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public String getPath() {
		return "Lobecorp";
	}

	@Override
	public void onLoaded(Editor editor) {
		values.clear();
		entries().forEach((key, value) -> values.put(key, text(value.get())));
	}

	@Override
	public void onApply(Editor editor) {
		entries().forEach((key, value) -> update(value, values.getOrDefault(key, text(value.get()))));
		VisualQualityConfig.ANIMATIONS.save();
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		var groups = new LinkedHashMap<String, ConfiguratorGroup>();
		entries().forEach((key, value) -> {
			var path = value.getPath();
            var section = path.size() > 2 ? path.get(1) : "general";
			var group = groups.computeIfAbsent(section, name -> {
				var created = new ConfiguratorGroup(PhotonVisualQualityTranslations.group(name));
				father.addConfigurator(created);
				return created;
			});
			var initial = value.get();
            var relativePath = String.join(".", path.subList(1, path.size()));
            var label = PhotonVisualQualityTranslations.label(relativePath);
			Configurator control;
			if (initial instanceof Boolean) {
				control = new BooleanConfigurator(label, () -> Boolean.parseBoolean(values.get(key)),
						next -> values.put(key, next.toString()), (Boolean) value.getDefault(), false);
			} else if (initial instanceof Number number) {
				var numeric = new NumberConfigurator(label, () -> {
					if (number instanceof Integer) {
						return Integer.valueOf(values.get(key));
					}
					return Double.valueOf(values.get(key));
				}, next -> values.put(key,
						number instanceof Integer ? Integer.toString(next.intValue()) : Double.toString(next.doubleValue())),
						(Number) value.getDefault(), false);
				var range = value.getSpec().getRange();
				if (range != null && range.getMin() instanceof Number min && range.getMax() instanceof Number max) {
					numeric.setRange(min, max);
				}
				control = numeric;
			} else {
				control = new StringConfigurator(label, () -> values.get(key), next -> values.put(key, next),
						text(value.getDefault()), false);
			}
			var tips = new ArrayList<Component>();
			tips.add(Component.translatable(PhotonVisualQualityTranslations.description(relativePath)));
			var defaultValue = value.getDefault();
			Object defaultText = defaultValue instanceof Boolean enabled
					? Component.translatable(enabled ? "options.on" : "options.off")
					: defaultValue instanceof List<?> list && list.isEmpty()
					? Component.translatable(PhotonVisualQualityTranslations.EMPTY) : text(defaultValue);
			tips.add(Component.translatable(PhotonVisualQualityTranslations.DEFAULT_VALUE, defaultText));
			var range = value.getSpec().getRange();

			if (range != null) {
				tips.add(Component.translatable(PhotonVisualQualityTranslations.RANGE, range.getMin(), range.getMax()));
			}

			control.setTips(tips.toArray(Component[]::new));
			group.addConfigurator(control);
		});
	}

	private static Map<String, ModConfigSpec.ConfigValue<?>> entries() {
		var result = new LinkedHashMap<String, ModConfigSpec.ConfigValue<?>>();
		for (var field : VisualQualityConfig.class.getFields()) {
			try {
				if (field.get(null) instanceof ModConfigSpec.ConfigValue<?> value) {
					result.put(String.join(".", value.getPath()), value);
				}
			} catch (IllegalAccessException exception) {
				throw new IllegalStateException(exception);
			}
		}
		return result;
	}

	private static String text(Object value) {
		return value instanceof List<?> list ? String.join(", ", list.stream().map(Object::toString).toList()) : value.toString();
	}

	private static void update(ModConfigSpec.ConfigValue<?> value, String input) {
		Object parsed;
		if (value.getDefault() instanceof Boolean) {
			parsed = Boolean.parseBoolean(input);
		} else if (value.getDefault() instanceof Integer) {
			parsed = Integer.parseInt(input);
		} else if (value.getDefault() instanceof Number) {
			parsed = Double.parseDouble(input);
		} else {
			parsed = Arrays.stream(input.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
		}
		set(value, value.getSpec().test(parsed) ? parsed : value.getSpec().correct(parsed));
	}

	@SuppressWarnings("unchecked")
	private static void set(ModConfigSpec.ConfigValue<?> value, Object parsed) {
		((ModConfigSpec.ConfigValue<Object>) value).set(parsed);
	}
}
