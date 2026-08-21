package org.unitego.lobecorp;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.tag.LcTags;

@Mod(Lobecorp.NAMESPACE)
public class Lobecorp {
    public static final String NAMESPACE = "lobecorp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Lobecorp(IEventBus iEventBus, ModContainer modContainer) {
        LOGGER.info("Unitego.");

        LcTags.init();
        LcMemoryModuleTypes.REGISTER.register(iEventBus);
        LcSensorTypes.REGISTER.register(iEventBus);
        LcParticleTypes.REGISTER.register(iEventBus);
        LcEntityDataSerializers.REGISTER.register(iEventBus);
        LcEntityTypes.init(iEventBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static <T> DeferredRegister<T> register(Registry<T> registry) {
        return DeferredRegister.create(registry, NAMESPACE);
    }

    public static <T> DeferredRegister<T> register(ResourceKey<Registry<T>> registry) {
        return DeferredRegister.create(registry, NAMESPACE);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String identifier) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, identifier));
    }
}
