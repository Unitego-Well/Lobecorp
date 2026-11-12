package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.command.EncounterCommand;
import org.unitego.lobecorp.registry.command.HordeCommand;
import org.unitego.lobecorp.registry.command.LcAnimationCommand;
import org.unitego.lobecorp.registry.command.LcDebugCommand;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class CommandEvents {
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		LcAnimationCommand.register(event.getDispatcher(), event.getBuildContext());
		if (!FMLEnvironment.isProduction()) {
			LcDebugCommand.registerServer(event.getDispatcher());
		}
		HordeCommand.register(event.getDispatcher());
		EncounterCommand.register(event.getDispatcher());
	}
}
