package org.unitego.lobecorp.generator.lang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.unitego.lobecorp.mixin.accessor.LanguageProviderAccessor;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Supplier;

public class BasicLangGenerator extends LanguageProvider {
	protected final String modId;
	private final LangHandler.LangSet langSet;

	public BasicLangGenerator(PackOutput output, String modId, String locale) {
		super(output, modId, locale);
		this.modId = modId;
		this.langSet = LangHandler.getLang(modId, locale);
	}

	public static String getFormattedKey(String namespace, String... key) {
		StringBuilder builder = new StringBuilder(namespace);
		builder.append(".commands");
		for (String s : key) {
			builder.append(".").append(s);
		}
		return builder.toString();
	}

	public static String getConfigTranslation(String modId, String... keys) {
		if (keys.length == 0) {
			return modId + ".config";
		}
		StringBuilder builder = new StringBuilder();
		for (String key : keys) {
			builder.append(".");
			builder.append(key);
		}
		return modId + ".config" + builder;
	}

	@Override
	public void addTranslations() {
		mergeManualEntries();
		LangHandler.LangSet lang = getLangSet();
		lang.map.forEach(this::add);
		addMobEffectMap(lang.mobEffects);
		addAttributeMap(lang.attributes);
		addSoundEventMap(lang.soundEvents);
		addItemMap(lang.items);
		addBlockMap(lang.blocks);
		addEntityMap(lang.entityTypes);
		addTagKeyMap(lang.tagKeys);
	}

	public LangHandler.LangSet getLangSet() {
		return langSet;
	}

	/// 读取并合并现有的手动维护的 json 条目。
	/// 这样手动文件只需保留到下次 datagen 运行，之后可安全删除。
	public void mergeManualEntries() {
		String locale = getLocale();
		Path manualPath = Paths.get(System.getProperty("user.dir")).getParent()
				.resolve("lang/" + locale + ".json");
		if (!Files.isRegularFile(manualPath)) {
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(manualPath)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			json.entrySet().forEach(entry -> add(entry.getKey(), entry.getValue().getAsString()));
		} catch (Exception e) {
			LOGGER.warn("无法合并手动 {} 翻译文件: {}", locale, e.getMessage());
		}
	}

	public String getLocale() {
		return ((LanguageProviderAccessor) this).lobecorp$getLocale();
	}

	protected void addPackDescription(String a, String description) {
		add("pack." + a + ".description", description);
	}

	protected void addItemMap(Map<Supplier<? extends Item>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	protected void addBlockMap(Map<Supplier<? extends Block>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	protected void addEntityMap(Map<Supplier<? extends EntityType<?>>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	protected void addMobEffectMap(Map<Supplier<? extends MobEffect>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	protected void addAttributeMap(Map<Supplier<? extends Attribute>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	protected void addTagKeyMap(Map<TagKey<?>, String> map) {
		map.forEach(this::add);
	}

	/// 生物属性翻译
	protected void add(Attribute attribute, String name) {
		add(attribute.getDescriptionId(), name);
	}

	protected void addSoundEventMap(Map<Supplier<? extends SoundEvent>, String> map) {
		map.forEach((holder, txt) -> add(holder.get(), txt));
	}

	public void add(ModConfigSpec.ConfigValue<?> configValue, String value, String tooltipValue) {
		add(configValue, value);
		add(getConfigTranslation(modId, configValue.getPath().toArray(new String[0])), tooltipValue);
	}

	public void add(ModConfigSpec.ConfigValue<?> configValue, String value) {
		add(getConfigTranslation(modId, configValue.getPath().toArray(new String[0])), value);
	}

	protected <T> void add(DataComponentType<T> dataComponentType, String name) {
		add(dataComponentType.toString(), name);
	}

	/// 声音字幕翻译
	protected void addSoundEvent(Holder<SoundEvent> holder, String name) {
		add(holder.value(), name);
	}

	protected void add(SoundEvent damageType, String name) {
		add("sound." + damageType.location().toLanguageKey(), name);
	}

	/// 死亡消息翻译
	protected void addDeathMessage(ResourceKey<DamageType> damageType, String name) {
		add("death.attack." + damageType.identifier().getPath(), name);
	}

	/// 玩家死亡消息翻译
	protected void addPlayerDeathMessage(ResourceKey<DamageType> damageType, String name) {
		add("death.attack." + damageType.identifier().getPath() + ".player", name);
	}
}
