package org.unitego.lobecorp.registry.photon.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

public class LcPhotonEditorCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LcPhotonProfileCommand.register(dispatcher);
		dispatcher.register(Commands.literal(Lobecorp.NAMESPACE)
				.then(Commands.literal(PhotonEffectEditor.COMMAND_NAME).executes(context -> {
					if (!PhotonEffectEditor.open()) {
						context.getSource().sendFailure(Component.translatable("command.failed"));
						return 0;
					}

					return 1;
				})));
	}
}
