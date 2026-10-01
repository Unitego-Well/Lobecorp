package org.unitego.lobecorp.registry;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.unitego.lobecorp.Lobecorp;

import java.util.UUID;

/// 指挥家远程单位的区块票据注册与运行时控制。
public class ConductorTicketControllers {
	/// 指挥家远程单位保持加载的票据控制器。
	private static final TicketController TICKETS = new TicketController(Lobecorp.id("conductor_directory_units"),
			(level, tickets) -> {
				tickets.getEntityTickets().keySet().forEach(tickets::removeAllTickets);
				tickets.getBlockTickets().keySet().forEach(tickets::removeAllTickets);
			});

	public static void register(RegisterTicketControllersEvent event) {
		event.register(TICKETS);
		event.register(VIEW_TICKETS);
	}

	/// 镜头观察票据只在当前会话有效，重启时清理遗留票据。
	private static final TicketController VIEW_TICKETS = new TicketController(Lobecorp.id("conductor_views"),
			(level, tickets) -> {
				tickets.getEntityTickets().keySet().forEach(tickets::removeAllTickets);
				tickets.getBlockTickets().keySet().forEach(tickets::removeAllTickets);
			});

	public static void forceViewChunk(ServerLevel level, UUID player, int x, int z, boolean enable) {
		VIEW_TICKETS.forceChunk(level, player, x, z, enable, true);
	}

	public static void forceChunk(ServerLevel level, UUID uuid, int x, int z, boolean enable) {
		TICKETS.forceChunk(level, uuid, x, z, enable, true);
	}
}
