package org.unitego.lobecorp.registry.conductor.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.hud.ConductorTexts;

/// 指挥家客户端键位及其注册入口。
public class ConductorKeyMappings {
	/// 指挥家控制键位分类。
	private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Lobecorp.id("conductor"));
	/// 打开或关闭指挥家控制模式的默认键位。
	private static final KeyMapping TOGGLE = new KeyMapping(ConductorTexts.TOGGLE, GLFW.GLFW_KEY_K, CATEGORY);
	/// 指挥家镜头向左旋转的默认键位。
	private static final KeyMapping ROTATE_LEFT = new KeyMapping(ConductorTexts.ROTATE_LEFT, GLFW.GLFW_KEY_Q, CATEGORY);
	/// 指挥家镜头向右旋转的默认键位。
	private static final KeyMapping ROTATE_RIGHT = new KeyMapping(ConductorTexts.ROTATE_RIGHT, GLFW.GLFW_KEY_E, CATEGORY);

	public static void register(RegisterKeyMappingsEvent event) {
		event.registerCategory(CATEGORY);
		event.register(TOGGLE);
		event.register(ROTATE_LEFT);
		event.register(ROTATE_RIGHT);
	}

	public static boolean consumeToggle() {
		return TOGGLE.consumeClick();
	}

	public static boolean matchesRotateLeft(KeyEvent event) {
		return ROTATE_LEFT.matches(event);
	}

	public static boolean matchesRotateRight(KeyEvent event) {
		return ROTATE_RIGHT.matches(event);
	}
}
