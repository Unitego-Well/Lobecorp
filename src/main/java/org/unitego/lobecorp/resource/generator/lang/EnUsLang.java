package org.unitego.lobecorp.resource.generator.lang;

import net.minecraft.data.PackOutput;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import org.unitego.lobecorp.Lobecorp;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EnUsLang extends BasicGeneratorLang {
    private static final Map<Supplier<? extends SoundEvent>, String> SOUND_EVENT = new HashMap<>();
    private static final Map<Supplier<? extends Item>, String> ITEMS = new HashMap<>();
    private static final Map<Supplier<? extends MobEffect>, String> MOB_EFFECT = new HashMap<>();
    private static final Map<Supplier<? extends Attribute>, String> ATTRIBUTE = new HashMap<>();
    private static final Map<Supplier<? extends EntityType<?>>, String> ENTITY_TYPES = new HashMap<>();
    private static final Map<String, String> MAP = new HashMap<>();

    public EnUsLang(PackOutput output) {
        super(output, Lobecorp.NAMESPACE, "en_us");
    }

    @Override
    public void addTranslations() {
        super.addTranslations();
        addPackDescription(Lobecorp.NAMESPACE, "Lobotomy Corporation");
        addMobEffectList(MOB_EFFECT);
        addAttributeList(ATTRIBUTE);
        addSoundEventList(SOUND_EVENT);
        addItemList(ITEMS);
        addEntityList(ENTITY_TYPES);
        MAP.forEach(this::add);

        MOB_EFFECT.clear();
        ATTRIBUTE.clear();
        SOUND_EVENT.clear();
        ITEMS.clear();
        ENTITY_TYPES.clear();
        MAP.clear();
    }

    public static void addI18nText(String key, String zhCn) {
        if (DatagenModLoader.isRunningDataGen()) {
            MAP.put(key, zhCn);
        }
    }

    public static void addI18nItemText(Supplier<? extends Item> deferredItem, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ITEMS.put(deferredItem, txt);
        }
    }

    public static void addI18nMobEffectText(Supplier<? extends MobEffect> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            MOB_EFFECT.put(supplier, txt);
        }
    }

    public static void addI18nAttributeText(Supplier<? extends Attribute> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ATTRIBUTE.put(supplier, txt);
        }
    }

    public static void addI18nSoundEventText(Supplier<? extends SoundEvent> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            SOUND_EVENT.put(supplier, txt);
        }
    }

    public static void addI18nEntityTypeText(Supplier<? extends EntityType<?>> supplier, String txt) {
        if (DatagenModLoader.isRunningDataGen()) {
            ENTITY_TYPES.put(supplier, txt);
        }
    }
}
