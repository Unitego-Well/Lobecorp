package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.debug.LcDebugUtil;
import org.unitego.lobecorp.util.debug.LcDebugUtil.Category;

/// 统一调试日志命令；服务端保留管理员权限，客户端仅开发环境本地生效。
public class LcDebugCommand {
	public static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(root(true));
	}

	public static void registerClient(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(root(false));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root(boolean requirePermission) {
		var debug = LiteralArgumentBuilder.<CommandSourceStack>literal("debug").executes(LcDebugCommand::status);
		if (requirePermission) {
			debug.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
		}

		debug.then(LiteralArgumentBuilder.<CommandSourceStack>literal("status").executes(LcDebugCommand::status));
		debug.then(category("skill", Category.SKILL));
		debug.then(category("animation", Category.ANIMATION));
		debug.then(category("attachment", Category.ATTACHMENT));
		debug.then(LiteralArgumentBuilder.<CommandSourceStack>literal("all")
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("on").executes(context -> setAll(context, true)))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("off").executes(context -> setAll(context, false))));
		return LiteralArgumentBuilder.<CommandSourceStack>literal(Lobecorp.NAMESPACE).then(debug);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> category(String name, Category category) {
		return LiteralArgumentBuilder.<CommandSourceStack>literal(name)
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("on").executes(context -> set(context, category, true)))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("off").executes(context -> set(context, category, false)));
	}

	private static int set(CommandContext<CommandSourceStack> context, Category category, boolean value) {
		LcDebugUtil.setEnabled(category, value);
		context.getSource().sendSuccess(() -> Component.literal(statusText()), false);
		return 1;
	}

	private static int setAll(CommandContext<CommandSourceStack> context, boolean value) {
		LcDebugUtil.setAllEnabled(value);
		context.getSource().sendSuccess(() -> Component.literal(statusText()), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal(statusText()), false);
		return 1;
	}

	private static String statusText() {
		return "当前 JVM 调试：技能=" + state(Category.SKILL)
				+ " 动画=" + state(Category.ANIMATION)
				+ " 附着=" + state(Category.ATTACHMENT);
	}

	private static String state(Category category) {
		return LcDebugUtil.isEnabled(category) ? "开" : "关";
	}
}
