package org.unitego.lobecorp.util.photon.playback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// 仅显式启用时连接已进入接管模式的客户端；普通单元测试不操作游戏。
@EnabledIfEnvironmentVariable(named = "LOBECORP_MCP_LIVE", matches = "true")
public class PhotonNamedMcpIntegrationTest {
	@Test
	public void exercisesTheRealStdioAnimationTools() throws Exception {
		assumeTrue(Boolean.parseBoolean(System.getenv("LOBECORP_MCP_LIVE")), "仅显式启用时连接游戏。");
		var root = Path.of("").toAbsolutePath();
		while (root != null && !Files.isDirectory(root.resolve("lib/minecraft-mcp/bridge"))) {
			root = root.getParent();
		}

		assertNotNull(root);
		var bridge = root.resolve("lib/minecraft-mcp/bridge");
		assertTrue(Files.isRegularFile(bridge.resolve("dist/cli.js")), "先通过IDEA运行Minecraft MCP Bridge。");
		var evidence = root.resolve("backups/photon-api-mcp-20261020");
		Files.createDirectories(evidence);
		var output = evidence.resolve("stdio-animation-" + UUID.randomUUID() + ".log");
		String mode = Boolean.parseBoolean(System.getenv("LOBECORP_MCP_READONLY")) ? "--animation-readonly" : "--animation";
		System.out.println("G1_MCP_TEST_MODE=" + mode);
		var process = new ProcessBuilder("node", "scripts/check-effect-tools.mjs", mode)
				.directory(bridge.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			boolean completed = process.waitFor(180, TimeUnit.SECONDS);
			assertTrue(completed, "MCP验证超时，日志：" + output);
			var text = Files.readString(output, StandardCharsets.UTF_8);
			assertEquals(0, process.exitValue(), text);
			System.out.println("G1_MCP_STDIO_LOG=" + output);
			System.out.println(text);
		} finally {
			if (process.isAlive()) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly();
			}
		}
	}
}
