package org.unitego.lobecorp;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Lobecorp.NAMESPACE)
public class Lobecorp {
    public static final String NAMESPACE = "lobecorp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Lobecorp(IEventBus iEventBus, ModContainer modContainer) {
        LOGGER.info("Unitego.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
