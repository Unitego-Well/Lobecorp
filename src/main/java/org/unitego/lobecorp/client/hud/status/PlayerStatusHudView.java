package org.unitego.lobecorp.client.hud.status;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.api.LcDamageType;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.status.bar.HealthBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.RationalityBarComponent;
import org.unitego.lobecorp.client.hud.status.bar.ShieldBarComponent;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.util.entity.damage.LcShieldUtil;

import java.util.ArrayList;
import java.util.List;

/// 玩家状态条视图：先在宿主布局前刷新状态并计算局部槽位，再由各组件绘制自身内容。
@NullMarked
public class PlayerStatusHudView extends UIElement {
	private static final int LEFT_OFFSET = 105;
	private static final int BOTTOM_OFFSET = 30;
	private final List<StatusBarComponent> components;

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
		lss("position", "absolute");
		layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).widthPercent(100).heightPercent(100));
		for (var component : components) {
			addChild(component);
		}
	}

	/// 刷新当帧数值和槽位；必须在 ModularUI 本帧布局计算前调用。
	public void prepare(HudFrame frame) {
		var player = frame.player();
		var layout = layout(frame.screenWidth(), frame.screenHeight(), player);
		for (int index = 0; index < components.size(); index++) {
			var slot = layout.slots().get(index);
			components.get(index).place(slot.left(), slot.top(), slot.visible());
		}

		for (var component : components) {
			component.prepare(frame);
		}
	}

	public void clear() {
		for (var component : components) {
			component.clear();
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

		return PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get() ? 0 : -10;
	}

	private static List<StatusHudLayout.Bar> bars(@Nullable LocalPlayer player) {
		var bars = new ArrayList<StatusHudLayout.Bar>(2 + LcDamageType.values().length);
		bars.add(new StatusHudLayout.Bar(90, 9, survivalVisible(player) && PlayerHudConfig.ENABLE_NEW_HEALTH_BAR.get()));
		bars.add(new StatusHudLayout.Bar(93, 11, playerVisible(player)));
		for (var type : LcDamageType.values()) {
			bars.add(new StatusHudLayout.Bar(93, 11,
					survivalVisible(player) && player.hasEffect(LcShieldUtil.effect(type))));
		}

		return bars;
	}
}
