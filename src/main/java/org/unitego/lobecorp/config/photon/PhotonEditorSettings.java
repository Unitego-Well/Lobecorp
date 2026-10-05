package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@SuppressWarnings({"FieldMayBeFinal", "UnnecessaryModifier"})
public class PhotonEditorSettings implements Settings {
	/**
	 * 与原编辑器设置共用保存文件的配置 ID。
	 */
	public static final Identifier ID = Lobecorp.id("photon_editor");
	/**
	 * 编辑器功能项；缺省全部开启，不改变粒子运行时数据。
	 */
	public static final List<String> FEATURES = List.of("precise", "time_scale", "batch_move", "fit", "tangents", "range_preview", "parameters", "batch_properties", "presets", "solo", "seed", "step", "diagnostics", "shortcuts", "curve_pan", "axis_lock", "rotation_cycle", "color_cycle", "size_cycle", "velocity_cycle", "force_cycle", "uv_cycle", "time_offset", "depth_sort", "control", "system_clipboard", "rename_dialog", PhotonEmitterSpawnerTextUtil.FEATURE);

	/**
	 * 设置树的原生分类，各功能仅在所属分类中显示。
	 */
	public enum Category {
		CURVES("curves", List.of("precise", "time_scale", "batch_move", "fit", "tangents", "curve_pan", "axis_lock")),
		PREVIEW("preview", List.of("range_preview", "parameters", "solo", "seed", "step", "diagnostics")),
		PARTICLES("particles", List.of("batch_properties", "rotation_cycle", "color_cycle", "size_cycle", "velocity_cycle", "force_cycle", "uv_cycle", "time_offset", "depth_sort", "control", PhotonEmitterSpawnerTextUtil.FEATURE)),
		RESOURCES("resources", List.of("presets", "system_clipboard", "rename_dialog"));
		private final String id;
		private final List<String> features;

		private Category(String id, List<String> features) {
			this.id = id;
			this.features = features;
		}

		public String id() {
			return id;
		}

		public List<String> features() {
			return features;
		}
	}

	/**
	 * 扩展快捷键的默认绑定；鼠标手势沿用原编辑器。
	 */
	public static final Map<String, String> DEFAULT_KEYS = Map.ofEntries(
			Map.entry("fit_selected", "F"),
			Map.entry("fit_all", "HOME"),
			Map.entry("step_back", "LEFT"),
			Map.entry("step_forward", "RIGHT"),
			Map.entry("step_back_large", "SHIFT+LEFT"),
			Map.entry("step_forward_large", "SHIFT+RIGHT"),
			Map.entry("duplicate", "CTRL+D"),
			Map.entry("move", "G"),
			Map.entry("scale", "S"),
			Map.entry("range_in", "I"),
			Map.entry("range_out", "O"),
			Map.entry("range_loop", "L"),
			Map.entry("solo", "SHIFT+H"),
			Map.entry("cancel", "ESCAPE"));
	public static final Codec<PhotonEditorSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("features", Map.of()).forGetter(s -> s.features),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("keys", Map.of()).forGetter(s -> s.keys),
			Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("presets", Map.of()).forGetter(s -> s.presets)
	).apply(instance, PhotonEditorSettings::new));

	private Map<String, Boolean> features;
	private Map<String, String> keys;
	private Map<String, CompoundTag> presets;

	public PhotonEditorSettings() {
		this(Map.of(), Map.of(), Map.of());
	}

	public PhotonEditorSettings(Map<String, Boolean> features, Map<String, String> keys, Map<String, CompoundTag> presets) {
		this.features = new LinkedHashMap<>(features);
		this.keys = new LinkedHashMap<>(keys);
		this.presets = new LinkedHashMap<>(presets);
	}

	public static PhotonEditorSettings of(Editor editor) {
		return editor.editorSettings.getSettings(ID).filter(PhotonEditorSettings.class::isInstance)
				.map(PhotonEditorSettings.class::cast).orElseGet(PhotonEditorSettings::new);
	}

	public boolean enabled(String feature) {
		return features.getOrDefault(feature, true);
	}

	public String binding(String action) {
		return keys.getOrDefault(action, DEFAULT_KEYS.getOrDefault(action, ""));
	}

	public Map<String, CompoundTag> presets() {
		return presets;
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
	public void onApply(Editor editor) {
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		father.addChild(new Label().setText(PhotonEditorTextUtil.key("settings_tip"))
				.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true)));
	}

	public void buildCategory(ConfiguratorGroup father, Category category) {
		for (String feature : FEATURES) {
			if (!category.features().contains(feature)) {
				continue;
			}

			father.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("feature." + feature),
					() -> enabled(feature), value -> features.put(feature, value), true, false));
		}
	}

	public void buildShortcuts(ConfiguratorGroup father) {
		father.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("feature.shortcuts"),
				() -> enabled("shortcuts"), value -> features.put("shortcuts", value), true, false));
		for (String action : DEFAULT_KEYS.keySet().stream().sorted().toList()) {
			var field = new StringConfigurator(PhotonEditorTextUtil.key("action." + action), () -> binding(action),
					value -> keys.put(action, value.trim().toUpperCase(Locale.ROOT)), DEFAULT_KEYS.get(action), false);
			field.setTextValidator(value -> PhotonEditorShortcutUtil.valid(this, action, value));
			field.getStyle().tooltips(PhotonEditorTextUtil.key("shortcut_tip"));
			father.addConfigurator(field);
		}
	}
}
