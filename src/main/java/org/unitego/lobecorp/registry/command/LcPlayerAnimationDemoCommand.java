package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.unitego.lobecorp.animation.demo.LcPlayerAnimationDemo;

public class LcPlayerAnimationDemoCommand {
	private static final String COMMAND_NAME = "player";
	private static final String PLAY_COMMAND = "play";

	public static LiteralArgumentBuilder<CommandSourceStack> create() {
		var command = Commands.literal(COMMAND_NAME)
				.then(Commands.literal(PLAY_COMMAND).then(playArguments()));
		return LcAnimationCommand.controls(command, LcPlayerAnimationDemoCommand::control);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playArguments() {
		var players = LcAnimationCommand.playback(LcAnimationCommand.players(),
				context -> play(context, LcPlayerAnimationDemo.Mode.OVERRIDE), false);

		for (var mode : LcPlayerAnimationDemo.Mode.values()) {
			players.then(LcAnimationCommand.playback(Commands.literal(mode.command()),
					context -> play(context, mode), false));
		}
		return players;
	}

	private static int play(CommandContext<CommandSourceStack> context, LcPlayerAnimationDemo.Mode mode)
			throws CommandSyntaxException {
		var players = LcAnimationCommand.selectedPlayers(context);
		var options = LcAnimationCommand.options(context);
		players.forEach(player -> LcPlayerAnimationDemo.play(player, mode, options));
		return players.size();
	}

	private static boolean control(ServerPlayer player, LcAnimationCommand.Action action,
	                               double speed) {
		return switch (action) {
			case PAUSE -> LcPlayerAnimationDemo.setPaused(player, true);
			case RESUME -> LcPlayerAnimationDemo.setPaused(player, false);
			case SPEED -> LcPlayerAnimationDemo.setSpeed(player, speed);
			case STOP -> {
				LcPlayerAnimationDemo.stop(player);
				yield true;
			}
		};
	}
}
