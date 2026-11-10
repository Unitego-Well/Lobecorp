package org.unitego.lobecorp.client.hud;

import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.client.hud.encounter.EncounterHud;
import org.unitego.lobecorp.client.hud.status.PlayerStatusPanelHud;

/// 左上区域只组合独立组件，堆叠和隐藏补位由通用容器处理。
@NullMarked
public class TopLeftHud extends AbstractHud {
	public static final TopLeftHud INSTANCE = new TopLeftHud();
	private static final int EDGE_MARGIN = 8;
	private static final int ELEMENT_GAP = 4;
	private final PlayerStatusPanelHud statusPanel = new PlayerStatusPanelHud();

	private TopLeftHud() {
		setPosition(EDGE_MARGIN, EDGE_MARGIN);
		setFitToScreen(false);
		setLayout(Layout.VERTICAL);
		setGap(ELEMENT_GAP);
		setAutoSize(true);
		add(statusPanel);
		add(EncounterHud.INSTANCE);
	}

	@Override
	protected void prepareFrame(float delta) {
		statusPanel.setVisible(PlayerStatusPanelHud.visibleForPlayer());
		if (statusPanel.isVisible()) {
			statusPanel.prepare(delta);
		}
		EncounterHud.INSTANCE.prepare(delta);
	}

	@Override
	protected void onSessionChanged() {
		statusPanel.clear();
		EncounterHud.INSTANCE.synchronizeSession();
	}

	@Override
	public void clear() {
		super.clear();
		EncounterHud.INSTANCE.clear();
	}

	@Override
	protected boolean hudVisible() {
		return true;
	}
}
