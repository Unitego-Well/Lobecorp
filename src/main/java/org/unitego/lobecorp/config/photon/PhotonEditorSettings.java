package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyChord;
import com.lowdragmc.lowdraglib2.editor.settings.KeymapSettings;
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
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings({"FieldMayBeFinal", "UnnecessaryModifier"})
public class PhotonEditorSettings implements Settings {
	/// 与原编辑器设置共用保存文件的配置 ID。
	public static final Identifier ID = Lobecorp.id("photon_editor");
	/// 编辑器功能项；缺省全部开启，不改变粒子运行时数据。
	public static final List<String> FEATURES = Stream.concat(
			Stream.of(Category.values()).flatMap(category -> category.features().stream()),
			Stream.of("shortcuts")).toList();

	/// 设置树的原生分类，各功能仅在所属分类中显示。
	public enum Category {
		CURVES("curves", List.of(PhotonEditorTextUtil.PRECISE_FEATURE, "time_scale", "batch_move", "fit", "tangents", PhotonEditorTextUtil.CURVE_PAN_FEATURE, PhotonEditorTextUtil.AXIS_LOCK_FEATURE, PhotonEditorTextUtil.SCALE_LINK_FEATURE, PhotonEditorTextUtil.CURVE_EASING_FEATURE, PhotonEditorTextUtil.BATCH_EASING_FEATURE)),
		PREVIEW("preview", List.of("range_preview", "parameters", "solo", "seed", "step", "diagnostics", PhotonEditorTextUtil.TEST_FEATURE, PhotonEditorTextUtil.HOVER_STYLE_DELTA_FEATURE, PhotonEditorTextUtil.ZERO_TIME_EMISSION_FEATURE)),
		PARTICLES("particles", List.of("batch_properties", "rotation_cycle", "color_cycle", "size_cycle", "velocity_cycle", "force_cycle", "uv_cycle", "time_offset", "depth_sort", PhotonEditorTextUtil.OCCLUSION_FEATURE, "control", PhotonEmitterSpawnerTextUtil.FEATURE, PhotonAnimationTextUtil.FEATURE, PhotonEditorTextUtil.TIMELINE_EVENT_TRAVERSAL_FEATURE)),
		RESOURCES("resources", List.of("presets", "system_clipboard", "rename_dialog", PhotonEditorTextUtil.COMPACT_TITLE_FEATURE, PhotonEditorTextUtil.TITLE_CONFLICT_FEATURE, PhotonEditorTextUtil.TITLE_PATH_TOOLTIP_FEATURE, PhotonEditorTextUtil.NOTIFICATIONS_FEATURE));
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

	/// 扩展快捷键的默认绑定；鼠标手势沿用原编辑器。
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
			Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("presets", Map.of()).forGetter(s -> s.presets),
			Codec.STRING.optionalFieldOf("curveEasingPreset", PhotonEditorTextUtil.DEFAULT_CURVE_EASING_PRESET).forGetter(PhotonEditorSettings::curveEasingPreset)
	).apply(instance, PhotonEditorSettings::new));

	private Map<String, Boolean> features;
	/// 仅保留旧设置的读取入口；迁移成功后删除对应项，恢复原生默认时不再次导入。
	private Map<String, String> keys;
	private Map<String, CompoundTag> presets;
	private String curveEasingPreset;

	public PhotonEditorSettings() {
		this(Map.of(), Map.of(), Map.of());
	}

	public PhotonEditorSettings(Map<String, Boolean> features, Map<String, String> keys, Map<String, CompoundTag> presets) {
		this(features, keys, presets, PhotonEditorTextUtil.DEFAULT_CURVE_EASING_PRESET);
	}

	public PhotonEditorSettings(Map<String, Boolean> features, Map<String, String> keys, Map<String, CompoundTag> presets, String curveEasingPreset) {
		this.features = new LinkedHashMap<>(features);
		this.keys = new LinkedHashMap<>(keys);
		this.presets = new LinkedHashMap<>(presets);
		setCurveEasingPreset(curveEasingPreset);
	}

	public static PhotonEditorSettings of(Editor editor) {
		return editor.editorSettings.getSettings(ID).filter(PhotonEditorSettings.class::isInstance)
				.map(PhotonEditorSettings.class::cast).orElseGet(PhotonEditorSettings::new);
	}

	public boolean enabled(String feature) {
		return features.getOrDefault(feature, true);
	}

	public Map<String, CompoundTag> presets() {
		return presets;
	}

	public String curveEasingPreset() {
		return curveEasingPreset;
	}

	public void setCurveEasingPreset(String value) {
		curveEasingPreset = value != null && PhotonEditorTextUtil.CURVE_EASING_PRESETS.contains(value)
				? value : PhotonEditorTextUtil.DEFAULT_CURVE_EASING_PRESET;
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
		var nativeSettings = KeymapSettings.of(editor);
		var legacyKeys = keys.entrySet().iterator();
		boolean migrated = false;

		while (legacyKeys.hasNext()) {
			var entry = legacyKeys.next();

			if (!DEFAULT_KEYS.containsKey(entry.getKey())) {
				continue;
			}

			var chord = KeyChord.parse(entry.getValue());

			if (chord.isEmpty()) {
				continue;
			}

			nativeSettings.bindIfUnset(PhotonEditorShortcutUtil.actionId(entry.getKey()), chord.get());
			legacyKeys.remove();
			migrated = true;
		}

		if (migrated) {
			nativeSettings.onApply(editor);
		}
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

			var field = new BooleanConfigurator(PhotonEditorTextUtil.key("feature." + feature),
					() -> enabled(feature), value -> features.put(feature, value), true, false);
			PhotonEditorTooltipUtil.tips(field, PhotonEditorTextUtil.key("feature." + feature + ".tooltip"));
			father.addConfigurator(field);
		}

		if (category != Category.CURVES) {
			return;
		}

		var easing = new SelectorConfigurator<>(PhotonEditorTextUtil.key("curve_easing_default"), this::curveEasingPreset,
				this::setCurveEasingPreset, PhotonEditorTextUtil.DEFAULT_CURVE_EASING_PRESET, false,
				PhotonEditorTextUtil.CURVE_EASING_PRESETS, PhotonEditorTextUtil::key);
		PhotonEditorTooltipUtil.tips(easing, PhotonEditorTextUtil.key("curve_easing_tip"));
		father.addConfigurator(easing);
	}

	public void buildShortcuts(ConfiguratorGroup father) {
		father.addConfigurator(new BooleanConfigurator(PhotonEditorTextUtil.key("feature.shortcuts"),
				() -> enabled("shortcuts"), value -> features.put("shortcuts", value), true, false));
	}
}
