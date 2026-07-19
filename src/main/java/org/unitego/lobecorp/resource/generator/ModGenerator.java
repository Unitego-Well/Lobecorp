package org.unitego.lobecorp.resource.generator;

import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.resource.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.resource.generator.lang.ZhCnLangGenerator;

@SuppressWarnings("UnusedReturnValue")
@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ModGenerator {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        buildFactory(event, EnUsLangGenerator::new);
        buildFactory(event, ZhCnLangGenerator::new);
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Server event) {
    }

    public static <T extends DataProvider> T build(GatherDataEvent event, T provider) {
        return event.getGenerator().addProvider(true, provider);
    }

    public static <T extends DataProvider> T buildFactory(GatherDataEvent event, DataProvider.Factory<T> provider) {
        return event.getGenerator().addProvider(true, provider);
    }

    @SafeVarargs
    public static <T extends DataProvider> void buildServer(GatherDataEvent event, T... providers) {
        for (T provider : providers) {
            buildServer(event, provider);
        }
    }

    @SafeVarargs
    public static <T extends DataProvider> void build(GatherDataEvent event, T... providers) {
        for (T provider : providers) {
            build(event, provider);
        }
    }
}
