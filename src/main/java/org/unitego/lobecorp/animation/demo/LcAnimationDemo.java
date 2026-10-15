package org.unitego.lobecorp.animation.demo;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.attachment.LcTransform;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/// 独立示例的资源契约与可调参数，不持有世界或播放实例。
public class LcAnimationDemo {
	public static final Identifier DEFINITION_ID = Lobecorp.id("example/attachment_draw");
	public static final Identifier MODEL_ID = Lobecorp.id("example/attachment_draw");
	public static final Identifier ANIMATION_ID = Lobecorp.id("example/attachment_draw");
	public static final String ANIMATION_NAME = "animation.example.attachment_draw";
	public static final String HEAD_BONE = "head";
	public static final String DRAW_BONE = "draw";
	public static final String DROP_BONE = "drop";
	public static final String FLOURISH_PARAMETER = "v.flourish";
	public static final String FLOURISH_QUERY = "q.example_flourish";
	public static final String HAND_X_PARAMETER = "v.hand_x";
	public static final Identifier PLAYER_RENDER_PROVIDER = Lobecorp.id("player");
	public static final String BACK_RENDER_NODE = "back";
	public static final String RIGHT_HAND_RENDER_NODE = "right_hand";
	public static final String LEFT_HAND_RENDER_NODE = "left_hand";
	/// 玩家逻辑锚点的近似位置，单位为格；渲染端以最终实际身体／手部姿态替换。
	public static final LcTransform BACK_OFFSET = new LcTransform(new Vec3(0, 18.0 / 16, -4.0 / 16),
			new Quaternionf(), LcTransform.identity().scale());
	public static final double HAND_X = 5.0 / 16;
	public static final double HAND_HEIGHT = 1;
	public static final double MODEL_UNITS = 16;
	public static final double DURATION_SECONDS = 6;
	/// 对应手写资源的交接时间，单位为秒；调整时同步服务端与视觉覆盖的关键帧。
	public static final double HAND_TIME = 1.5;
	public static final double WORLD_TIME = 3;
	/// 头部摆动幅度，单位为度，由代码包装注入并随查询快照同步。
	public static final double DEFAULT_FLOURISH = 20;
	/// 在命令来源水平朝向前方放置白模，单位为格。
	public static final double SPAWN_DISTANCE = 2;
}
