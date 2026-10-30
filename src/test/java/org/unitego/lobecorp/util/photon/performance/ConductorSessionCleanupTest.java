package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 读取实际类调用图，确保断连清理不触发鼠标抓取；不初始化游戏或模拟系统输入。
public class ConductorSessionCleanupTest {
	@Test
	public void disconnectCleanupUsesLocalStateWithoutMouseOrNetworkRecovery() throws IOException {
		var calls = calls("org/unitego/lobecorp/client/conductor/ConductorControls");
		var clear = calls.get("clearSession");
		assertTrue(clear.contains("org/unitego/lobecorp/client/conductor/ConductorControls#clearSessionState"));
		assertTrue(clear.contains("org/unitego/lobecorp/client/conductor/ConductorClient#reset"));
		assertFalse(clear.stream().anyMatch(call -> call.endsWith("#exit") || call.endsWith("#grabMouse") || call.endsWith("#syncView")));
		assertFalse(calls.get("clearSessionState").stream().anyMatch(call ->
				call.endsWith("#grabMouse") || call.endsWith("#setScreen") || call.endsWith("#syncView")));
	}

	@Test
	public void explicitExitKeepsNormalMouseRecoveryAfterStateReset() throws IOException {
		var exit = calls("org/unitego/lobecorp/client/conductor/ConductorControls").get("exit");
		var reset = "org/unitego/lobecorp/client/conductor/ConductorControls#clearSessionState";
		assertTrue(exit.contains("org/unitego/lobecorp/client/conductor/ConductorControls#syncView"));
		assertTrue(exit.contains(reset));
		assertTrue(exit.contains("net/minecraft/client/MouseHandler#grabMouse"));
		assertTrue(exit.indexOf(reset) < exit.indexOf("net/minecraft/client/MouseHandler#grabMouse"));
	}

	@Test
	public void worldUnloadStillReachesConductorAndPhotonCleanup() throws IOException {
		var unload = calls("org/unitego/lobecorp/events/client/ClientRuntimeEvents").get("onClientLevelUnload");
		assertTrue(unload.contains("org/unitego/lobecorp/client/conductor/ConductorControls#clearSession"));
		assertTrue(unload.contains("org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects#clear"));
		assertTrue(calls("org/unitego/lobecorp/client/photon/runtime/QueenBeamEffects").get("clear")
				.contains("org/unitego/lobecorp/util/photon/model/PhotonModelSourceUtil#purgeClearedEmbeddedPreparations"));
	}

	private static Map<String, List<String>> calls(String type) throws IOException {
		var result = new HashMap<String, List<String>>();
		try (var input = ConductorSessionCleanupTest.class.getResourceAsStream("/" + type + ".class")) {
			if (input == null) {
				throw new IOException("缺少待检查类：" + type);
			}

			new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					var calls = result.computeIfAbsent(name, _ -> new ArrayList<>());
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
							calls.add(owner + "#" + method);
						}
					};
				}
			}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}

		return result;
	}
}
