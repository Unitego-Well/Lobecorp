package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.unitego.lobecorp.animation.demo.LcAnimationDemoSession;
import org.unitego.lobecorp.world.item.animation.LcItemAnimationSession;

public class LcAnimationDemoCommand {
	private static final String ATTACHMENT_COMMAND = "attachment";
	private static final String ITEM_COMMAND = "item";
	private static final String PLAY_COMMAND = "play";
	private static final String ANCHOR_COMMAND = "anchor";
	private static final String ANCHOR_ARGUMENT = "anchor";

	public static LiteralArgumentBuilder<CommandSourceStack> attachment(CommandBuildContext context) {
		var command = Commands.literal(ATTACHMENT_COMMAND).then(Commands.literal(PLAY_COMMAND)
				.then(LcAnimationCommand.players().then(LcAnimationCommand.playback(
						LcAnimationCommand.itemArgument(context), LcAnimationDemoCommand::playAttachment, true))));
		command.then(Commands.literal(ANCHOR_COMMAND).then(LcAnimationCommand.players().then(
				Commands.argument(ANCHOR_ARGUMENT, EnumArgument.enumArgument(LcAnimationDemoSession.Anchor.class))
						.executes(LcAnimationDemoCommand::switchAnchor))));
		command.then(LcAnimationCommand.controls(Commands.literal(ITEM_COMMAND), LcAnimationDemoCommand::controlAttachedItem));
		return LcAnimationCommand.controls(command, LcAnimationDemoCommand::controlAttachment);
	}

	public static LiteralArgumentBuilder<CommandSourceStack> item(CommandBuildContext context) {
		var command = Commands.literal(ITEM_COMMAND).then(Commands.literal(PLAY_COMMAND)
				.then(LcAnimationCommand.players().then(LcAnimationCommand.playback(
						LcAnimationCommand.itemArgument(context), LcAnimationDemoCommand::playItem, false))));
		return LcAnimationCommand.controls(command, LcAnimationDemoCommand::controlItem);
	}

	private static int playAttachment(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var players = LcAnimationCommand.selectedPlayers(context);
		var stack = LcAnimationCommand.selectedItem(context);
		var options = LcAnimationCommand.options(context);
		var itemSpeed = LcAnimationCommand.itemSpeed(context);
		players.forEach(player -> LcAnimationDemoSession.play(player, stack, options, itemSpeed));
		return players.size();
	}

	private static int playItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var players = LcAnimationCommand.selectedPlayers(context);
		var stack = LcAnimationCommand.selectedItem(context);
		var options = LcAnimationCommand.options(context);
		players.forEach(player -> LcItemAnimationSession.playWorld(player, stack, options));
		return players.size();
	}

	private static int switchAnchor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var anchor = context.getArgument(ANCHOR_ARGUMENT, LcAnimationDemoSession.Anchor.class);
		int count = 0;
		for (var player : LcAnimationCommand.selectedPlayers(context)) {
			if (LcAnimationDemoSession.switchAnchor(player, anchor)) {
				count++;
			}
		}
		return count;
	}

	private static boolean controlAttachment(ServerPlayer player, LcAnimationCommand.Action action, double speed) {
		return switch (action) {
			case PAUSE -> LcAnimationDemoSession.setPaused(player, true);
			case RESUME -> LcAnimationDemoSession.setPaused(player, false);
			case SPEED -> LcAnimationDemoSession.setSpeed(player, speed);
			case STOP -> {
				LcAnimationDemoSession.stop(player);
				yield true;
			}
		};
	}

	private static boolean controlItem(ServerPlayer player, LcAnimationCommand.Action action, double speed) {
		return switch (action) {
			case PAUSE -> LcItemAnimationSession.pauseWorld(player, true);
			case RESUME -> LcItemAnimationSession.pauseWorld(player, false);
			case SPEED -> LcItemAnimationSession.speedWorld(player, speed);
			case STOP -> {
				LcItemAnimationSession.stopWorld(player);
				yield true;
			}
		};
	}

	private static boolean controlAttachedItem(ServerPlayer player, LcAnimationCommand.Action action, double speed) {
		return switch (action) {
			case PAUSE -> LcAnimationDemoSession.pauseItem(player, true);
			case RESUME -> LcAnimationDemoSession.pauseItem(player, false);
			case SPEED -> LcAnimationDemoSession.speedItem(player, speed);
			case STOP -> LcAnimationDemoSession.stopItem(player);
		};
	}
}
