package org.unitego.lobecorp.client.hud.event;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

/// 深夜清扫的独立事件组件实例；包装归属由注册定义决定。
public class MidnightCleanupHud extends EventHudComponent {
	private static final Identifier DIGITS = texture("digits");
	private static final Identifier TEXT_BOX_MIDDLE = texture("text_box_middle");
	private static final Identifier TEXT_BOX_RIGHT = texture("text_box_right");
	private static final Identifier TIMER = texture("timer");

	public MidnightCleanupHud() {

	}

	private static Identifier texture(String name) {
		return Lobecorp.id("textures/gui/sprites/hud/event/midnight_cleanup/" + name + ".png");
	}
}
