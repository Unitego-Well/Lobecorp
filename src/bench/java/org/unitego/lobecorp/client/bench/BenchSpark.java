package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// Spark 性能采样：整个浪潮场景期间保持开启（不再用 30 秒超时），测量结束后停止并在后续场景等待上传链接，
/// 客户端退出前必须已拿到本次采样链接，避免 Spark 结果随进程结束丢失。
public final class BenchSpark {
	private static final Pattern LINK = Pattern.compile("https://spark\\.lucko\\.me/[A-Za-z0-9]+");
	private static Set<String> before = Set.of();
	private static boolean running;
	private static boolean saved;

	private BenchSpark() {
	}

	public static void start(BenchClientContext context) {
		if (running) {
			return;
		}
		before = links(context);
		saved = false;
		if (!ClientCommandHandler.runCommand("sparkc profiler start --timeout 600")) {
			throw new IllegalStateException("Spark client profiler command is unavailable");
		}
		running = true;
	}

	public static void stop() {
		if (!running) {
			return;
		}
		ClientCommandHandler.runCommand("sparkc profiler stop");
		running = false;
	}

	/// 在每个 tick 调用；上传完成后把本次链接写入制品并登记。
	public static void capture(BenchClientContext context) {
		if (saved) {
			return;
		}
		for (String link : links(context)) {
			if (before.contains(link)) {
				continue;
			}
			Path file = context.resultDirectory().resolve("artifacts/spark/horde-profile.txt");
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, link + System.lineSeparator());
				context.artifacts().register(file, "text/plain");
			} catch (IOException exception) {
				throw new IllegalStateException("无法保存 Spark 采样链接", exception);
			}
			saved = true;
			return;
		}
	}

	public static void requireSaved() {
		if (!saved) {
			throw new AssertionError("Spark 未在上传完成后返回新链接；不能在不确认 Spark 输出的情况下结束客户端");
		}
	}

	private static Set<String> links(BenchClientContext context) {
		try {
			var field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			var links = new LinkedHashSet<String>();
			for (var entry : (List<?>) field.get(context.minecraft().gui.getChat())) {
				if (entry instanceof GuiMessage message) {
					var matcher = LINK.matcher(message.content().getString());
					while (matcher.find()) {
						links.add(matcher.group());
					}
				}
			}
			return links;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot identify Spark profile links for this run", exception);
		}
	}
}
