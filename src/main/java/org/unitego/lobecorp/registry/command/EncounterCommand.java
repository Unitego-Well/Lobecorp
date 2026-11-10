package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.AngleArgument;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import java.util.List;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.registry.translation.EncounterTranslationKeys;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.horde.Hordes;
import org.unitego.lobecorp.world.encounter.Encounters;
import org.unitego.lobecorp.world.horde.HordeStartRequest;
import org.unitego.lobecorp.world.horde.HordeStopMode;

import java.util.ArrayList;
import java.util.UUID;

public class EncounterCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lobecorp").then(Commands.literal("encounter")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("template").then(Commands.literal("midnight_cleanup")
						.executes(context -> template(context, false))
						.then(Commands.argument("center", Vec3Argument.vec3(false))
								.then(Commands.argument("yaw", AngleArgument.angle())
										.executes(context -> template(context, true))))))));
		var start = Commands.literal("start")
				.then(Commands.argument("profile", IdentifierArgument.id())
						.then(Commands.argument("from", Vec3Argument.vec3())
								.then(Commands.argument("to", Vec3Argument.vec3())
										.executes(context -> start(context, 0))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(20, 1728000))
												.executes(context -> start(context, 1))
												.then(Commands.argument("width", DoubleArgumentType.doubleArg(1, 512))
														.executes(context -> start(context, 2))
																.then(Commands.argument("waypoints", StringArgumentType.greedyString())
																		.executes(context -> start(context, 3))))))));
		dispatcher.register(Commands.literal("lobecorp").then(Commands.literal("encounter")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(start)
				.then(Commands.literal("stop").then(Commands.argument("event", StringArgumentType.word())
						.executes(context -> stop(context, HordeStopMode.GRACEFUL))))
				.then(Commands.literal("clear").then(Commands.argument("event", StringArgumentType.word())
						.executes(context -> stop(context, HordeStopMode.CLEAR))))
				.then(Commands.literal("list").executes(context -> {
					var events = Encounters.active(context.getSource().getServer());
					events.forEach(event -> context.getSource().sendSuccess(() -> Component.literal(
							event.id() + " " + event.profile() + " tick=" + event.tick()
									+ (event.running() ? " RUNNING" : " DRAINING")), false));
					return events.size();
				}))
				.then(Commands.literal("stats").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(
							"encounters=" + Encounters.active(context.getSource().getServer()).size()), false);
					return 1;
				}))));
	}

	private static int template(CommandContext<CommandSourceStack> context, boolean explicit) {
		var source = context.getSource();
		var center = explicit ? Vec3Argument.getVec3(context, "center") : source.getPosition();
		float yaw = explicit ? AngleArgument.getAngle(context, "yaw") : source.getRotation().y;
		double angle = Math.toRadians(yaw);
		var offset = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(MidnightCleanupEncounter.TEMPLATE_LENGTH / 2);
		var from = center.subtract(offset);
		var to = center.add(offset);
		var profile = Hordes.profile(MidnightCleanupEncounter.ID);
		if (profile == null) {
			return 0;
		}

		int travel = (int) Math.ceil(MidnightCleanupEncounter.TEMPLATE_LENGTH / profile.movementSpeed());
		int generation = MidnightCleanupEncounter.TEMPLATE_TICKS - travel;
		if (generation < 20) {
			source.sendFailure(Component.translatable(EncounterTranslationKeys.TEMPLATE_TOO_SLOW));
			return 0;
		}

		var result = Encounters.start(source.getLevel(), MidnightCleanupEncounter.ID,
				new HordeStartRequest(from, to, List.of(), generation, MidnightCleanupEncounter.TEMPLATE_WIDTH));
		var id = result.eventId();
		if (id == null) {
			source.sendFailure(Component.translatable(EncounterTranslationKeys.START_FAILED, result.message()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable(EncounterTranslationKeys.STARTED, id.toString()), true);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> context, int depth) {
		var source = context.getSource();
		var points = new ArrayList<Vec3>();
		if (depth >= 3) {
			for (String point : StringArgumentType.getString(context, "waypoints").split(";")) {
				String[] xyz = point.trim().split(",");
				if (xyz.length != 3 || points.size() >= 126) {
					source.sendFailure(Component.translatable(EncounterTranslationKeys.INVALID_WAYPOINTS));
					return 0;
				}
				try {
					points.add(new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
				} catch (NumberFormatException exception) {
					source.sendFailure(Component.translatable(EncounterTranslationKeys.INVALID_WAYPOINTS));
					return 0;
				}
			}
		}
		Identifier profile = IdentifierArgument.getId(context, "profile");
		var request = new HordeStartRequest(Vec3Argument.getVec3(context, "from"), Vec3Argument.getVec3(context, "to"),
				points,
				depth >= 1 ? IntegerArgumentType.getInteger(context, "ticks") : 0,
				depth >= 2 ? DoubleArgumentType.getDouble(context, "width") : 0);
		var result = Encounters.start(source.getLevel(), profile, request);
		var id = result.eventId();
		if (id == null) {
			source.sendFailure(Component.translatable(EncounterTranslationKeys.START_FAILED, result.message()));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable(EncounterTranslationKeys.STARTED, id.toString()), true);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> context, HordeStopMode mode) {
		try {
			UUID id = UUID.fromString(StringArgumentType.getString(context, "event"));
			var result = Encounters.stop(context.getSource().getServer(), id, mode);
			context.getSource().sendSuccess(() -> Component.translatable(EncounterTranslationKeys.STOPPED, id.toString(), result.message()), true);
			return result.stopped() ? 1 : 0;
		} catch (IllegalArgumentException exception) {
			context.getSource().sendFailure(Component.translatable(EncounterTranslationKeys.INVALID_EVENT));
			return 0;
		}
	}
}
