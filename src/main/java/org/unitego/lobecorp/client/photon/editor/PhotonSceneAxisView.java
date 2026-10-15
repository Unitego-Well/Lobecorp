package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.joml.Vector2f;
import org.joml.Vector3f;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 在原生场景左下角显示随相机转动的世界坐标轴，不接管场景输入。
@ParametersAreNonnullByDefault
public class PhotonSceneAxisView extends UIElement {
	/// 坐标提示区域与视图边缘留白，单位为 GUI 像素。
	private static final int SIZE = 64;
	private static final int MARGIN = 6;
	/// 轴长、端点半径与线宽，单位为 GUI 像素。
	private static final int AXIS_LENGTH = 22;
	private static final int POINT_RADIUS = 5;
	private static final float LINE_WIDTH = 1;
	/// 世界坐标轴的名称与标准红、绿、蓝配色。
	private static final List<String> AXIS_NAMES = List.of("X", "Y", "Z");
	private static final int[] AXIS_COLORS = {0xFFCE4358, 0xFF53BE5C, 0xFF537DD5};
	private final SceneView view;

	public PhotonSceneAxisView(SceneView view) {
		this.view = view;
		setAllowHitTest(false);
		layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(MARGIN).bottom(MARGIN)
				.width(SIZE).height(SIZE));
	}

	@Override
	public void screenTick() {
		super.screenTick();
		var panel = view.fxObjectAnimationView;
		float panelHeight = panel.getParent() != null && panel.isDisplayed() ? panel.getSizeHeight() : 0;
		getLayout().bottom(MARGIN + panelHeight);
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext context) {
		if (!(context instanceof GUIContext gui)) {
			return;
		}

		var renderer = view.sceneEditor.scene.<WorldSceneRenderer>getRenderer();

		if (renderer == null) {
			return;
		}

		var forward = new Vector3f(renderer.getLookAt()).sub(renderer.getEyePos()).normalize();
		var right = new Vector3f(forward).cross(renderer.getWorldUp()).normalize();
		var up = new Vector3f(right).cross(forward).normalize();
		var center = new Vector2f(getContentX() + SIZE / 2f, getContentY() + SIZE / 2f);
		var endpoints = new ArrayList<AxisEndpoint>();

		for (int axis = 0; axis < AXIS_NAMES.size(); axis++) {
			for (int sign : new int[]{-1, 1}) {
				var direction = new Vector3f().setComponent(axis, sign);
				var point = new Vector2f(center).add(direction.dot(right) * AXIS_LENGTH,
						-direction.dot(up) * AXIS_LENGTH);
				endpoints.add(new AxisEndpoint(axis, sign > 0, direction.dot(forward), point));
			}
		}

		endpoints.sort(Comparator.comparingDouble(AxisEndpoint::depth).reversed());

		for (var endpoint : endpoints) {
			drawEndpoint(gui, center, endpoint);
		}
	}

	private void drawEndpoint(GUIContext context, Vector2f center, AxisEndpoint endpoint) {
		int color = AXIS_COLORS[endpoint.axis()];
		int displayColor = endpoint.positive() ? color : (color & 0xFFFFFF) | 0x80000000;
		DrawerHelperClient.drawLines(context, List.of(center, endpoint.point()), displayColor, displayColor, LINE_WIDTH);

		for (int y = -POINT_RADIUS; y <= POINT_RADIUS; y++) {
			float radius = (float) Math.sqrt(POINT_RADIUS * POINT_RADIUS - y * y);
			DrawerHelperClient.drawSolidRect(context, endpoint.point().x - radius,
					endpoint.point().y + y, radius * 2, 1, displayColor);
		}

		if (endpoint.positive()) {
			DrawerHelperClient.drawStringSized(context, AXIS_NAMES.get(endpoint.axis()), endpoint.point().x,
					endpoint.point().y - POINT_RADIUS + 1, 0xFF101418, false, 0.8f, true);
		}
	}

	private record AxisEndpoint(int axis, boolean positive, float depth, Vector2f point) {
	}
}
