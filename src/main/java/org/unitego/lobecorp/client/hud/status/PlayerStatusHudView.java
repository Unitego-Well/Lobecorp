package org.unitego.lobecorp.client.hud.status;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.ContainerHudRenderer;
import org.unitego.lobecorp.client.hud.status.bar.HealthBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.RationalityBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.ShieldBarComponent;
import org.unitego.lobecorp.config.client.HealthBarStyle;
import org.unitego.lobecorp.config.client.HudStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

import java.util.ArrayList;
import java.util.List;

/// 玩家状态条视图：先刷新状态并计算槽位，再按原版图层提交各组件内容。
@NullMarked
public class PlayerStatusHudView extends ContainerHudRenderer {
	private static final int LEFT_OFFSET = 105;
	private static final int BOTTOM_OFFSET = 30;
	private final List<StatusBarComponent> components;
	private @Nullable StatusHudLayout frameLayout;

	public PlayerStatusHudView() {
		var health = new HealthBarComponent();
		var rationality = new RationalityBarComponent();
		var shields = new ArrayList<ShieldBarComponent>(LcDamageType.values().length);
		for (var type : LcDamageType.values()) {
			shields.add(new ShieldBarComponent(type));
		}

		components = new ArrayList<>(2 + shields.size());
		components.add(health);
		components.add(rationality);
		components.addAll(shields);
		for (var component : components) {
			add(component);
		}
	}

	/// 使用当前窗口尺寸计算底部锚点布局，供护甲偏移读取。
	public static StatusHudLayout currentLayout() {
		var minecraft = Minecraft.getInstance();
		return layout(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
				minecraft.player);
	}

	public static StatusHudLayout layout(int screenWidth, int screenHeight, @Nullable LocalPlayer player) {
		float left = StatusHudLayout.left(screenWidth, LEFT_OFFSET);
		float bottom = screenHeight - BOTTOM_OFFSET + verticalOffset(player);
		return StatusHudLayout.stack(left, bottom, bars(player));
	}

	public static boolean survivalVisible(@Nullable LocalPlayer player) {
		return player != null && !player.isSpectator() && !player.isCreative();
	}

	public static boolean playerVisible(@Nullable LocalPlayer player) {
		return player != null && !player.isSpectator();
	}

	private static int verticalOffset(@Nullable LocalPlayer player) {
		if (player != null && player.isCreative()) {
			return 5;
		}

		return PlayerHudConfig.HEALTH_STYLE.get() == HealthBarStyle.VANILLA ? -10 : 0;
	}

	private static List<StatusHudLayout.Bar> bars(@Nullable LocalPlayer player) {
		var bars = new ArrayList<StatusHudLayout.Bar>(2 + LcDamageType.values().length);
		bars.add(new StatusHudLayout.Bar(90, 9,
				survivalVisible(player) && PlayerHudConfig.HEALTH_STYLE.get() == HealthBarStyle.DEFAULT));
		bars.add(new StatusHudLayout.Bar(93, 11, playerVisible(player) && PlayerHudConfig.RATIONALITY_STYLE.get() == HudStyle.DEFAULT));
		for (var type : LcDamageType.values()) {
			bars.add(new StatusHudLayout.Bar(93, 11,
					survivalVisible(player) && !PlayerHudConfig.usesNewShield(type) && player.hasEffect(LcShieldUtil.effect(type))));
		}

		return bars;
	}

	/// 刷新当帧数值与可见性；必须在绘制前调用。
	public void prepare(@Nullable LocalPlayer player, int screenWidth, int screenHeight, float delta) {
		setSize(screenWidth, screenHeight);
		setVisible(true);
		frameLayout = layout(screenWidth, screenHeight, player);

		for (int index = 0; index < components.size(); index++) {
			var slot = frameLayout.slots().get(index);
			components.get(index).setPosition(slot.left(), slot.top());
			components.get(index).setVisible(slot.visible());
			components.get(index).updateFrameVisibility(slot.visible());
		}

		for (var component : components) {
			component.prepare(player, delta);
		}
	}

	public void clear() {
		frameLayout = null;
		setVisible(false);
		for (var component : components) {
			component.clear();
		}
	}
}
