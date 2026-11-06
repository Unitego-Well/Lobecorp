package org.unitego.lobecorp.registry.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.debug.LcDebugUtil;
import org.unitego.lobecorp.util.debug.LcDebugUtil.Category;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 使用真实 Brigadier 树检查统一命令的语法、权限谓词与同根追加。
public class LcDebugCommandTest {
	@AfterEach
	public void resetSwitches() {
		LcDebugUtil.setAllEnabled(false);
	}

	@Test
	public void serverTreeContainsStatusAndEveryCategory() {
		var dispatcher = new CommandDispatcher<CommandSourceStack>();
		LcDebugCommand.registerServer(dispatcher);

		CommandNode<CommandSourceStack> debug = debug(dispatcher);
		assertNotNull(debug.getChild("status"));
		assertNotNull(debug.getChild("skill"));
		assertNotNull(debug.getChild("animation"));
		assertNotNull(debug.getChild("attachment"));
		assertNotNull(debug.getChild("all"));
		assertNotNull(debug.getChild("skill").getChild("on"));
		assertNotNull(debug.getChild("skill").getChild("off"));
		assertNotNull(debug.getCommand());
	}

	@Test
	public void clientTreeHasNoPermissionPredicate() {
		var dispatcher = new CommandDispatcher<CommandSourceStack>();
		LcDebugCommand.registerClient(dispatcher);

		CommandNode<CommandSourceStack> debug = debug(dispatcher);
		assertNotNull(debug.getChild("status"));
		assertNotNull(debug.getChild("all"));
		assertTrue(debug.getRequirement().test(source(PermissionSet.ALL_PERMISSIONS)));
	}

	@Test
	public void serverTreeRequiresPermissionWhileClientDoesNot() {
		var server = new CommandDispatcher<CommandSourceStack>();
		LcDebugCommand.registerServer(server);
		assertFalse(debug(server).getRequirement().test(source(PermissionSet.NO_PERMISSIONS)));
		assertTrue(debug(server).getRequirement().test(source(PermissionSet.ALL_PERMISSIONS)));

		var client = new CommandDispatcher<CommandSourceStack>();
		LcDebugCommand.registerClient(client);
		assertTrue(debug(client).getRequirement().test(source(PermissionSet.NO_PERMISSIONS)));
	}

	@Test
	public void serverPermissionPredicateUsesGameMasterPermission() {
		assertFalse(source(PermissionSet.NO_PERMISSIONS).permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
		assertTrue(source(PermissionSet.ALL_PERMISSIONS).permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
	}

	@Test
	public void existingSiblingCommandsAreMergedNotReplaced() {
		var dispatcher = new CommandDispatcher<CommandSourceStack>();
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("lobecorp")
				.then(LiteralArgumentBuilder.literal("animation")));
		dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("lobecorp")
				.then(LiteralArgumentBuilder.literal("horde")));

		LcDebugCommand.registerClient(dispatcher);

		CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("lobecorp");
		assertNotNull(root.getChild("animation"));
		assertNotNull(root.getChild("horde"));
		assertNotNull(root.getChild("debug"));
	}

	@Test
	public void categorySettersChangeOnlyTheirCategoryAndAllCoversEveryCategory() {
		LcDebugUtil.setAllEnabled(false);
		LcDebugUtil.setEnabled(Category.SKILL, true);
		assertEquals(Set.of(Category.SKILL), LcDebugUtil.snapshot());

		LcDebugUtil.setAllEnabled(true);
		assertEquals(EnumSet.allOf(Category.class), LcDebugUtil.snapshot());

		LcDebugUtil.setEnabled(Category.ANIMATION, false);
		assertEquals(EnumSet.of(Category.SKILL, Category.ATTACHMENT), LcDebugUtil.snapshot());
	}

	private static CommandNode<CommandSourceStack> debug(CommandDispatcher<CommandSourceStack> dispatcher) {
		CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("lobecorp");
		assertNotNull(root);
		CommandNode<CommandSourceStack> debug = root.getChild("debug");
		assertNotNull(debug);
		return debug;
	}

	@SuppressWarnings("DataFlowIssue")
	private static CommandSourceStack source(PermissionSet permissions) {
		return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissions, "test",
				Component.empty(), null, null);
	}
}
