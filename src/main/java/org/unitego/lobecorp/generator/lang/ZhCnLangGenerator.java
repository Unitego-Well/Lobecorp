package org.unitego.lobecorp.generator.lang;

import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ZhCnLangGenerator extends BasicLangGenerator {
    private static final Map<Supplier<? extends MobEffect>, String> MOB_EFFECTS = new HashMap<>();
    private static final Map<Supplier<? extends Attribute>, String> ATTRIBUTES = new HashMap<>();
    private static final Map<Supplier<? extends SoundEvent>, String> SOUND_EVENTS = new HashMap<>();
    private static final Map<Supplier<? extends Item>, String> ITEMS = new HashMap<>();
    private static final Map<Supplier<? extends EntityType<?>>, String> ENTITY_TYPES = new HashMap<>();
    private static final Map<TagKey<?>, String> TAG_KEYS = new HashMap<>();
    private static final Map<String, String> MAP = new HashMap<>();

    public ZhCnLangGenerator(PackOutput output) {
        super(output, Lobecorp.NAMESPACE, "zh_cn");
    }

    public static void addI18nText(String key, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            MAP.put(key, txt);
        }
    }

    public static void addI18nTagKey(TagKey<?> key, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            TAG_KEYS.put(key, txt);
        }
    }

    public static void addI18nSoundEventText(Supplier<? extends SoundEvent> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            SOUND_EVENTS.put(supplier, txt);
        }
    }

    public static void addI18nAttributeText(Supplier<? extends Attribute> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ATTRIBUTES.put(supplier, txt);
        }
    }

    public static void addI18nMobEffectText(Supplier<? extends MobEffect> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            MOB_EFFECTS.put(supplier, txt);
        }
    }

    public static void addI18nItemText(Supplier<? extends Item> deferredItem, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ITEMS.put(deferredItem, txt);
        }
    }

    public static void addI18nEntityTypeText(Supplier<? extends EntityType<?>> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ENTITY_TYPES.put(supplier, txt);
        }
    }

    @Override
    public void addTranslations() {
        super.addTranslations();
        addPackDescription(Lobecorp.NAMESPACE, "脑叶公司");
        addMobEffectMap(MOB_EFFECTS);
        addAttributeMap(ATTRIBUTES);
        addSoundEventMap(SOUND_EVENTS);
        addItemMap(ITEMS);
        addEntityMap(ENTITY_TYPES);
        addTagKeyMap(TAG_KEYS);
        MAP.forEach(this::add);
        add(EntityCorpse.DISPLAY_NAME_KEY, "%s尸体");
    }
}
