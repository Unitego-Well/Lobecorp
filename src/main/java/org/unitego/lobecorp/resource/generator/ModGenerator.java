package org.unitego.lobecorp.resource.generator;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.resource.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.resource.generator.lang.ZhCnLangGenerator;
import org.unitego.lobecorp.resource.generator.tag.EntityTypeTagGenerator;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnusedReturnValue")
@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ModGenerator {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        buildFactory(event, EnUsLangGenerator::new);
        buildFactory(event, ZhCnLangGenerator::new);
        buildFactory(event, ParticleGenerator::new);
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Server event) {
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        build(event, new EntityTypeTagGenerator(packOutput, lookupProvider));
    }

    public static <T extends DataProvider> T build(GatherDataEvent event, T provider) {
        return event.getGenerator().addProvider(true, provider);
    }

    public static <T extends DataProvider> T buildFactory(GatherDataEvent event, DataProvider.Factory<T> provider) {
        return event.getGenerator().addProvider(true, provider);
    }
}
