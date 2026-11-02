package org.unitego.lobecorp.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.filter.DamageFilterState;
import org.unitego.lobecorp.client.hud.filter.DamageFilterView;
import org.unitego.lobecorp.client.hud.filter.RationalityFilterState;
import org.unitego.lobecorp.client.hud.filter.RationalityFilterView;
import org.unitego.lobecorp.client.hud.host.HudHost;
import org.unitego.lobecorp.config.client.PlayerHudConfig;

/// 两个滤镜共用的业务门面；会话生命周期交给 [HudLayerManager]，本类只保留命中、配置和提取入口。
@NullMarked
public class PlayerScreenFilters {
	private static final Identifier DAMAGE_ID = Lobecorp.id("damage_screen_filter");
	private static final Identifier RATIONALITY_ID = Lobecorp.id("rationality_screen_filter");
	public static final PlayerScreenFilters INSTANCE = new PlayerScreenFilters();
	private final DamageFilterState damageState = new DamageFilterState();
	private final RationalityFilterState rationalityState = new RationalityFilterState();
	private @Nullable DamageFilterView damageView;
	private @Nullable RationalityFilterView rationalityView;

	private PlayerScreenFilters() {
	}

	/// 向统一管理器注册两个滤镜宿主；clearAction 在会话切换或显式清理时总是执行。
	public void register() {
		HudLayerManager.INSTANCE.register(DAMAGE_ID, this::damageVisible, () -> {
			var view = damageView();
			return new HudHost(view, view::prepare, _ -> {
			}, this::releaseDamageView);
		}, damageState::clear);
		HudLayerManager.INSTANCE.register(RATIONALITY_ID, this::rationalityVisible, () -> {
			var view = rationalityView();
			return new HudHost(view, view::prepare, _ -> {
			}, this::releaseRationalityView);
		}, rationalityState::clear);
	}

	/// 伤害到达可早于 UI 宿创建；先同步会话再写状态，新会话首击不会被首次提取清掉。
	public void hit(int channels) {
		if (!PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			return;
		}

		HudLayerManager.INSTANCE.synchronizeSession();
		if (!sessionAvailable()) {
			return;
		}

		damageState.hit(channels);
	}

	public void tick() {
		HudLayerManager.INSTANCE.synchronizeSession();
		if (!PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get()) {
			HudLayerManager.INSTANCE.clear(DAMAGE_ID);
		}

		if (!PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get()) {
			HudLayerManager.INSTANCE.clear(RATIONALITY_ID);
		}
	}

	public void extractDamage(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(DAMAGE_ID, graphics, deltaTracker);
	}

	public void extractRationality(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HudLayerManager.INSTANCE.render(RATIONALITY_ID, graphics, deltaTracker);
	}

	private DamageFilterView damageView() {
		if (damageView == null) {
			damageView = new DamageFilterView(damageState);
		}

		return damageView;
	}

	private RationalityFilterView rationalityView() {
		if (rationalityView == null) {
			rationalityView = new RationalityFilterView(rationalityState);
		}

		return rationalityView;
	}

	private void releaseDamageView() {
		var view = damageView;
		damageView = null;
		if (view != null) {
			view.clear();
		}
	}

	private void releaseRationalityView() {
		var view = rationalityView;
		rationalityView = null;
		if (view != null) {
			view.clear();
		}
	}

	private boolean damageVisible() {
		return PlayerHudConfig.ENABLE_LC_COLOR_DAMAGE_FILTER.get() && overlayVisible();
	}

	private boolean rationalityVisible() {
		return PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.get() && overlayVisible();
	}

	private boolean overlayVisible() {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		return minecraft.level != null && player != null && !minecraft.options.hideGui && !player.isSpectator();
	}

	private boolean sessionAvailable() {
		var minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.player != null;
	}

	public void clear() {
		HudLayerManager.INSTANCE.clear(DAMAGE_ID);
		HudLayerManager.INSTANCE.clear(RATIONALITY_ID);
	}
}
