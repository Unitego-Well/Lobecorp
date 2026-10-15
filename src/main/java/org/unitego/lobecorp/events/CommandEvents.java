package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.command.LcAnimationCommand;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class CommandEvents {
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		LcAnimationCommand.register(event.getDispatcher(), event.getBuildContext());
	}
}
