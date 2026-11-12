package org.unitego.lobecorp.registry.client.photon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformancePanel;
import org.unitego.lobecorp.client.photon.performance.PhotonRepeatedEffectTrial;
import org.unitego.lobecorp.registry.translation.photon.performance.PhotonPerformanceTranslationKeys;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;

import java.util.Locale;

public class LcPhotonProfileCommand {
	private static final String LABEL = PhotonPerformanceCapture.WORLD_LABEL;

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal(PhotonPerformanceCapture.COMMAND_NAME);
		var start = Commands.literal("start").executes(context -> start(context.getSource(),
				PhotonPerformanceCapture.DEFAULT_SECONDS, Mode.DETAIL));
		var seconds = Commands.argument("seconds", DoubleArgumentType.doubleArg(PhotonFrameCapture.minSeconds(), PhotonFrameCapture.maxSeconds()))
				.executes(context -> start(context.getSource(), DoubleArgumentType.getDouble(context, "seconds"), Mode.DETAIL));
		for (var mode : Mode.values()) {
			seconds.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(context ->
					start(context.getSource(), DoubleArgumentType.getDouble(context, "seconds"), mode)));
		}
		start.then(seconds);
		root.then(start);
		var repeat = Commands.literal("repeat");
		var repeatStart = Commands.literal("start");
		for (var kind : PhotonRepeatedEffectTrial.Kind.values()) {
			repeatStart.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
					.then(Commands.argument("label", StringArgumentType.word()).executes(context ->
							repeat(context.getSource(), "start", kind, StringArgumentType.getString(context, "label")))));
		}
		repeat.then(repeatStart);
		for (var action : new String[]{"status", "close", "cancel"}) {
			repeat.then(Commands.literal(action).then(Commands.argument("label", StringArgumentType.word())
					.executes(context -> repeat(context.getSource(), action, null, StringArgumentType.getString(context, "label")))));
		}
		root.then(repeat);
		root.then(Commands.literal("open").executes(_ -> {
			PhotonPerformancePanel.openWorld();
			return 1;
		}));
		root.then(Commands.literal("stop").executes(context -> {
			PhotonPerformanceCapture.stop();
			context.getSource().sendSuccess(() -> message("stopped"), false);
			return 1;
		}));
		root.then(Commands.literal("cancel").executes(context -> {
			PhotonPerformanceCapture.cancel();
			context.getSource().sendSuccess(() -> message("cancelled"), false);
			return 1;
		}));
		root.then(Commands.literal("status").executes(context -> status(context.getSource())));
		root.executes(context -> status(context.getSource()));
		dispatcher.register(Commands.literal(Lobecorp.NAMESPACE).then(root));
	}

	private static int repeat(CommandSourceStack source, String action, PhotonRepeatedEffectTrial.Kind kind, String label) {
		try {
			switch (action) {
				case "start" -> PhotonRepeatedEffectTrial.start(kind, PhotonRepeatedEffectTrial.Mode.BASE, label, null);
				case "cancel" -> PhotonRepeatedEffectTrial.cancel(label);
				case "close" -> PhotonRepeatedEffectTrial.close(label);
				default -> {
				}
			}
			var status = PhotonRepeatedEffectTrial.status(label).toString();
			source.sendSuccess(() -> Component.literal(status), false);
			return 1;
		} catch (Exception problem) {
			source.sendFailure(Component.literal(problem.toString()));
			return 0;
		}
	}

	private static int start(CommandSourceStack source, double seconds, Mode mode) {
		try {
			PhotonPerformanceCapture.start(LABEL, seconds, mode);
			source.sendSuccess(() -> Component.translatable(PhotonPerformanceTranslationKeys.key("started"), seconds,
					Component.translatable(PhotonPerformanceTranslationKeys.key("mode." + mode.name().toLowerCase(Locale.ROOT)))), false);
			return 1;
		} catch (RuntimeException problem) {
			source.sendFailure(Component.translatable(PhotonPerformanceTranslationKeys.key("error"), Component.translatable(String.valueOf(problem.getMessage()))));
			return 0;
		}
	}

	private static int status(CommandSourceStack source) {
		var status = PhotonPerformanceCapture.status();
		source.sendSuccess(() -> Component.translatable(PhotonPerformanceTranslationKeys.key("status"),
				status.get("active").getAsBoolean(), status.get("exporting").getAsBoolean(),
				status.has("nodes") ? status.get("nodes").getAsInt() : 0,
				status.has("frame") ? status.get("frame").getAsInt() : 0), false);
		if (!status.get("output").isJsonNull()) {
			source.sendSuccess(() -> Component.translatable(PhotonPerformanceTranslationKeys.key("output"), status.get("output").getAsString()), false);
		}
		if (!status.get("error").isJsonNull()) {
			source.sendFailure(Component.translatable(PhotonPerformanceTranslationKeys.key("error"), status.get("error").getAsString()));
		}
		return 1;
	}

	private static Component message(String key) {
		return Component.translatable(PhotonPerformanceTranslationKeys.key(key));
	}
}
