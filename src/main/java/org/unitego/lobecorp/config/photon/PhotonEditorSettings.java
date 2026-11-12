package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.editor.keymap.KeyChord;
import com.lowdragmc.lowdraglib2.editor.settings.KeymapSettings;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorShortcutUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTooltipUtil;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("FieldMayBeFinal")
public class PhotonEditorSettings implements Settings {
	/// 扩展编辑器设置的配置 ID。
	public static final Identifier ID = Lobecorp.id("photon_editor");
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
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("keys", Map.of()).forGetter(s -> s.keys),
			Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("presets", Map.of()).forGetter(s -> s.presets),
			Codec.STRING.optionalFieldOf("curveEasingPreset", PhotonEditorTranslationKeys.DEFAULT_CURVE_EASING_PRESET).forGetter(PhotonEditorSettings::curveEasingPreset)
	).apply(instance, PhotonEditorSettings::new));

	/// 仅保留旧设置的读取入口；迁移成功后删除对应项，恢复原生默认时不再次导入。
	private Map<String, String> keys;
	private Map<String, CompoundTag> presets;
	private String curveEasingPreset;

	public PhotonEditorSettings() {
		this(Map.of(), Map.of());
	}

	public PhotonEditorSettings(Map<String, String> keys, Map<String, CompoundTag> presets) {
		this(keys, presets, PhotonEditorTranslationKeys.DEFAULT_CURVE_EASING_PRESET);
	}

	public PhotonEditorSettings(Map<String, String> keys, Map<String, CompoundTag> presets, String curveEasingPreset) {
		this.keys = new LinkedHashMap<>(keys);
		this.presets = new LinkedHashMap<>(presets);
		setCurveEasingPreset(curveEasingPreset);
	}

	public static PhotonEditorSettings of(Editor editor) {
		return editor.editorSettings.getSettings(ID).filter(PhotonEditorSettings.class::isInstance)
				.map(PhotonEditorSettings.class::cast).orElseGet(PhotonEditorSettings::new);
	}

	public Map<String, CompoundTag> presets() {
		return presets;
	}

	public String curveEasingPreset() {
		return curveEasingPreset;
	}

	public void setCurveEasingPreset(String value) {
		curveEasingPreset = value != null && PhotonEditorTranslationKeys.CURVE_EASING_PRESETS.contains(value)
				? value : PhotonEditorTranslationKeys.DEFAULT_CURVE_EASING_PRESET;
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
		var easing = new SelectorConfigurator<>(PhotonEditorTranslationKeys.CURVE_EASING_DEFAULT, this::curveEasingPreset,
				this::setCurveEasingPreset, PhotonEditorTranslationKeys.DEFAULT_CURVE_EASING_PRESET, false,
				PhotonEditorTranslationKeys.CURVE_EASING_PRESETS, PhotonEditorTranslationKeys::key);
		PhotonEditorTooltipUtil.tips(easing, PhotonEditorTranslationKeys.CURVE_EASING_TIP);
		father.addConfigurator(easing);
	}
}
