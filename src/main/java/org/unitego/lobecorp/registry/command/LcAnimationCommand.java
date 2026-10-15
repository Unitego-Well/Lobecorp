package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.demo.LcAnimationPlaybackOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/// 唯一动画调试命令树；玩家选择器和物品参数复用当前版本原版解析。
public class LcAnimationCommand {
	private static final String COMMAND_NAME = "animation";
	private static final String PLAYERS = "players";
	private static final String ITEM = "item";
	private static final String SPEED = "speed";
	private static final String ITEM_SPEED = "item_speed";
	private static final String FADE_IN = "fade_in";
	private static final String FADE_OUT = "fade_out";
	private static final String TRANSITION = "transition";
	private static final String ROTATION = "rotation";
	private static final String EMPTY_ITEM_KEY = "commands.lobecorp.animation.empty_item";
	private static final SimpleCommandExceptionType EMPTY_ITEM = new SimpleCommandExceptionType(Component.translatable(EMPTY_ITEM_KEY));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(Commands.literal(Lobecorp.NAMESPACE).then(Commands.literal(COMMAND_NAME)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(LcPlayerAnimationDemoCommand.create())
				.then(LcAnimationDemoCommand.attachment(buildContext))
				.then(LcAnimationDemoCommand.item(buildContext))));
	}

	public static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> players() {
		return Commands.argument(PLAYERS, EntityArgument.players());
	}

	public static RequiredArgumentBuilder<CommandSourceStack, ItemInput> itemArgument(CommandBuildContext context) {
		return Commands.argument(ITEM, ItemArgument.item(context));
	}

	public static Collection<ServerPlayer> selectedPlayers(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		return EntityArgument.getPlayers(context, PLAYERS);
	}

	public static ItemStack selectedItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var stack = ItemArgument.getItem(context, ITEM).createItemStack(1);
		if (stack.isEmpty()) {
			throw EMPTY_ITEM.create();
		}

		return stack;
	}

	public static <B extends ArgumentBuilder<CommandSourceStack, B>> B playback(
			B base, Command<CommandSourceStack> command, boolean separateItemSpeed) {
		List<ArgumentBuilder<CommandSourceStack, ?>> arguments = new ArrayList<>();
		arguments.add(Commands.argument(SPEED, DoubleArgumentType.doubleArg(0)));
		if (separateItemSpeed) {
			arguments.add(Commands.argument(ITEM_SPEED, DoubleArgumentType.doubleArg(0)));
		}

		arguments.add(Commands.argument(FADE_IN, IntegerArgumentType.integer(0)));
		arguments.add(Commands.argument(FADE_OUT, IntegerArgumentType.integer(0)));
		arguments.add(Commands.argument(TRANSITION, EnumArgument.enumArgument(LcTransitionMode.class)));
		arguments.add(Commands.argument(ROTATION, EnumArgument.enumArgument(LcRotationTransitionMode.class)));
		arguments.forEach(argument -> argument.executes(command));
		for (int index = arguments.size() - 2; index >= 0; index--) {
			arguments.get(index).then(arguments.get(index + 1));
		}

		return base.executes(command).then(arguments.getFirst());
	}

	public static LcAnimationPlaybackOptions options(CommandContext<CommandSourceStack> context) {
		var defaults = LcAnimationPlaybackOptions.DEFAULT;
		return LcAnimationPlaybackOptions.of(value(context, SPEED, Double.class, defaults.speed()),
				value(context, FADE_IN, Integer.class, defaults.transitions().fadeInTicks()),
				value(context, FADE_OUT, Integer.class, defaults.transitions().fadeOutTicks()),
				value(context, TRANSITION, LcTransitionMode.class, defaults.transitions().animationTransitionMode()),
				value(context, ROTATION, LcRotationTransitionMode.class, defaults.transitions().rotationTransitionMode()));
	}

	public static double itemSpeed(CommandContext<CommandSourceStack> context) {
		return value(context, ITEM_SPEED, Double.class, LcAnimationPlaybackOptions.DEFAULT.speed());
	}

	public static LiteralArgumentBuilder<CommandSourceStack> controls(
			LiteralArgumentBuilder<CommandSourceStack> root, Control control) {
		for (var action : Action.values()) {
			var target = players();
			if (action == Action.SPEED) {
				target.then(Commands.argument(SPEED, DoubleArgumentType.doubleArg(0))
						.executes(context -> control(context, action, control)));
			} else {
				target.executes(context -> control(context, action, control));
			}
			root.then(Commands.literal(action.name().toLowerCase(Locale.ROOT)).then(target));
		}
		return root;
	}

	private static int control(CommandContext<CommandSourceStack> context, Action action, Control control)
			throws CommandSyntaxException {
		int count = 0;
		for (var player : selectedPlayers(context)) {
			if (control.apply(player, action, value(context, SPEED, Double.class, LcAnimationPlaybackOptions.DEFAULT.speed()))) {
				count++;
			}
		}
		return count;
	}

	private static <T> T value(CommandContext<CommandSourceStack> context, String name, Class<T> type, T fallback) {
		return context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(name))
				? context.getArgument(name, type) : fallback;
	}

	public enum Action {
		PAUSE, RESUME, STOP, SPEED
	}

	@FunctionalInterface
	public interface Control {
		boolean apply(ServerPlayer player, Action action, double speed);
	}
}
