package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.horde.HordeEvents;
import org.unitego.lobecorp.world.horde.HordeStartRequest;
import org.unitego.lobecorp.world.horde.HordeStopMode;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import org.unitego.lobecorp.world.horde.HordeRuntime;

import java.util.ArrayList;
import java.util.OptionalLong;
import java.util.UUID;

public class HordeCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var start = Commands.literal("start")
				.then(Commands.argument("profile", IdentifierArgument.id())
						.then(Commands.argument("from", Vec3Argument.vec3())
								.then(Commands.argument("to", Vec3Argument.vec3())
										.executes(context -> start(context, 0))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(20, 1728000))
												.executes(context -> start(context, 1))
												.then(Commands.argument("width", DoubleArgumentType.doubleArg(1, 512))
														.executes(context -> start(context, 2))
														.then(Commands.argument("seed", LongArgumentType.longArg())
																.executes(context -> start(context, 3))
																.then(Commands.argument("waypoints", StringArgumentType.greedyString())
																		.executes(context -> start(context, 4)))))))));
		dispatcher.register(Commands.literal("lobecorp").then(Commands.literal("horde")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(start)
				.then(Commands.literal("stop").then(Commands.argument("event", StringArgumentType.word())
						.executes(context -> stop(context, HordeStopMode.GRACEFUL))))
				.then(Commands.literal("clear").then(Commands.argument("event", StringArgumentType.word())
						.executes(context -> stop(context, HordeStopMode.CLEAR))))
				.then(Commands.literal("list").executes(context -> {
					var events = HordeSavedData.get(context.getSource().getServer()).events();
					events.values().forEach(event -> context.getSource().sendSuccess(() -> Component.literal(
							event.id() + " " + event.profile() + " tick=" + event.tick()
									+ (event.running() ? " RUNNING" : " DRAINING")), false));
					return events.size();
				}))
				.then(Commands.literal("stats").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(
							HordeRuntime.get(context.getSource().getServer()).statistics()), false);
					return 1;
				}))));
	}

	private static int start(CommandContext<CommandSourceStack> context, int depth) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var source = context.getSource();
		var points = new ArrayList<Vec3>();
		if (depth >= 4) {
			for (String point : StringArgumentType.getString(context, "waypoints").split(";")) {
				String[] xyz = point.trim().split(",");
				if (xyz.length != 3 || points.size() >= 126) {
					source.sendFailure(Component.translatable("horde.lobecorp.invalid_waypoints"));
					return 0;
				}
				try {
					points.add(new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
				} catch (NumberFormatException exception) {
					source.sendFailure(Component.translatable("horde.lobecorp.invalid_waypoints"));
					return 0;
				}
			}
		}
		Identifier profile = IdentifierArgument.getId(context, "profile");
		if (profile == null) {
			source.sendFailure(Component.translatable("horde.lobecorp.invalid_profile"));
			return 0;
		}
		var request = new HordeStartRequest(Vec3Argument.getVec3(context, "from"), Vec3Argument.getVec3(context, "to"),
				points, depth >= 3 ? OptionalLong.of(LongArgumentType.getLong(context, "seed")) : OptionalLong.empty(),
				depth >= 1 ? IntegerArgumentType.getInteger(context, "ticks") : 0,
				depth >= 2 ? DoubleArgumentType.getDouble(context, "width") : 0);
		var result = HordeEvents.start(source.getLevel(), profile, request);
		if (!result.started()) {
			source.sendFailure(Component.translatable("horde.lobecorp.start_failed", result.message()));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("horde.lobecorp.started", result.eventId().toString()), true);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> context, HordeStopMode mode) {
		try {
			UUID id = UUID.fromString(StringArgumentType.getString(context, "event"));
			var result = HordeEvents.stop(context.getSource().getServer(), id, mode);
			context.getSource().sendSuccess(() -> Component.translatable("horde.lobecorp.stopped", id.toString(), result.message()), true);
			return result.stopped() ? 1 : 0;
		} catch (IllegalArgumentException exception) {
			context.getSource().sendFailure(Component.translatable("horde.lobecorp.invalid_event"));
			return 0;
		}
	}
}
