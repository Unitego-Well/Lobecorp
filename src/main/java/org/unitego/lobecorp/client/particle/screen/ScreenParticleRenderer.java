package org.unitego.lobecorp.client.particle.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public class ScreenParticleRenderer {

	/// 初次显示的放大与回落分别持续的客户端 tick 数。
	private static final float POP_TICKS = 5;
	private static final float SETTLE_TICKS = 5;
	private static final float INITIAL_SCALE = 0.65F;
	private static final float PEAK_SCALE = 1.25F;
	/// 漂移使用 GUI 像素，达到终点后保持，合并延长寿命不会倒退。
	private static final float DRIFT_PIXELS = 18;
	private static final float DRIFT_TICKS = 20;
	/// 仅按剩余客户端 tick 淡出，不随合并后总寿命重新归一。
	private static final float FADE_TICKS = 6;
	/// 累计伤害与最新目标最大生命的比值，震动强度最多按 200% 计算。
	private static final float MAX_DAMAGE_RATIO = 2;
	/// 基础字号在 0% 至 100% 伤害比例间线性变化；之后仍乘原有弹出倍率。
	private static final float LARGE_DAMAGE_RATIO = 1;
	private static final float ZERO_DAMAGE_SCALE = 0.7F;
	private static final float LARGE_DAMAGE_SCALE = 2;
	/// 每次命中后十个客户端 tick 内线性衰减，平移以 GUI 像素、角度以弧度计。
	private static final float SHAKE_TICKS = 10;
	private static final float MAX_SHAKE_PIXELS = 4;
	private static final float MAX_SHAKE_ANGLE = (float) Math.toRadians(6);
	/// 三个通道使用不同的弧度每 tick 频率，避免整段只沿直线往返。
	private static final float SHAKE_X_FREQUENCY = 2.7F;
	private static final float SHAKE_Y_FREQUENCY = 3.9F;
	private static final float SHAKE_ANGLE_FREQUENCY = 2.1F;
	/// 无法沿用命中深度时，在近裁剪面外到相机前十六格之间回退。
	private static final double MIN_CENTER_DEPTH = Camera.PROJECTION_Z_NEAR * 2;
	private static final double MAX_CENTER_DEPTH = 16;
	private static final float HOMOGENEOUS_EPSILON = 1E-6F;
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Matrix4f INVERSE_VIEW_PROJECTION = new Matrix4f();
	private static final Vector3f CAMERA_FORWARD = new Vector3f();
	private static final Vector4f PROJECTED = new Vector4f();
	private static Vec3 cameraPosition = Vec3.ZERO;
	private static boolean projectionReady;
	private static boolean inversePrepared;
	private static boolean zeroToOneDepth;
	private static float halfWidth;
	private static float halfHeight;

	/// 同一 GUI 帧内共享相机矩阵，世界坐标先以 double 减去相机位置。
	public static void beginFrame(GuiGraphicsExtractor graphics) {
		halfWidth = graphics.guiWidth() / 2F;
		halfHeight = graphics.guiHeight() / 2F;
		inversePrepared = false;
		var minecraft = Minecraft.getInstance();
		var camera = minecraft.gameRenderer.getMainCamera();
		projectionReady = minecraft.level != null && camera.isInitialized();
		if (!projectionReady) {
			return;
		}

		camera.getViewRotationProjectionMatrix(VIEW_PROJECTION);
		cameraPosition = camera.position();
		CAMERA_FORWARD.set(camera.forwardVector());
		zeroToOneDepth = RenderSystem.getDevice().isZZeroToOne();
		projectionReady = VIEW_PROJECTION.isFinite();
	}

	/// 仅首次提取时调用；返回值由实例保存，之后不随相机重新计算。
	public static @Nullable Vec3 centerAnchor(Vec3 hitPosition) {
		if (!projectionReady || !hitPosition.isFinite()) {
			return null;
		}

		if (!inversePrepared) {
			VIEW_PROJECTION.invert(INVERSE_VIEW_PROJECTION);
			inversePrepared = true;
		}

		if (!INVERSE_VIEW_PROJECTION.isFinite()) {
			return null;
		}

		boolean hasHitDepth = project(hitPosition);
		if (!hasHitDepth) {
			double distance = Mth.clamp(cameraPosition.distanceTo(hitPosition), MIN_CENTER_DEPTH, MAX_CENTER_DEPTH);
			PROJECTED.set((float) (CAMERA_FORWARD.x * distance), (float) (CAMERA_FORWARD.y * distance),
					(float) (CAMERA_FORWARD.z * distance), 1).mul(VIEW_PROJECTION);
			if (!validClipPosition()) {
				return null;
			}
		}

		// 保留命中的齐次深度，只把屏幕坐标置于中心；矩阵本身不含世界平移。
		PROJECTED.set(0, 0, PROJECTED.z, PROJECTED.w).mul(INVERSE_VIEW_PROJECTION);
		if (!Float.isFinite(PROJECTED.w) || Math.abs(PROJECTED.w) <= HOMOGENEOUS_EPSILON) {
			return null;
		}

		var anchor = new Vec3(cameraPosition.x + PROJECTED.x / PROJECTED.w,
				cameraPosition.y + PROJECTED.y / PROJECTED.w, cameraPosition.z + PROJECTED.z / PROJECTED.w);
		return anchor.isFinite() ? anchor : null;
	}

	public static void render(GuiGraphicsExtractor graphics, IScreenParticleContent content, Vec3 position,
	                          float ageTicks, float remainingTicks, float damageRatio, float impactAge) {
		if (!(remainingTicks > 0) || !project(position)) {
			return;
		}

		float x = (PROJECTED.x / PROJECTED.w + 1) * halfWidth;
		float y = (1 - PROJECTED.y / PROJECTED.w) * halfHeight;
		float age = Math.max(ageTicks, 0);
		// NaN 专供通用内容；正无穷仍是有效的大伤害，必须进入强度上限。
		boolean damageStyle = !Float.isNaN(damageRatio);
		float ratio = damageStyle ? Mth.clamp(damageRatio, 0, MAX_DAMAGE_RATIO) : 0;
		float scale = scale(age) * (damageStyle ? damageScale(ratio) : 1);
		float drift = DRIFT_PIXELS * Mth.clamp(age / DRIFT_TICKS, 0, 1);
		float opacity = Mth.clamp(remainingTicks / FADE_TICKS, 0, 1);
		float impact = Math.max(impactAge, 0);
		float shake = damageStyle ? ratio / MAX_DAMAGE_RATIO * (1 - Mth.clamp(impact / SHAKE_TICKS, 0, 1)) : 0;
		float shakeX = (float) Math.sin(impact * SHAKE_X_FREQUENCY) * MAX_SHAKE_PIXELS * shake;
		float shakeY = (float) Math.sin(impact * SHAKE_Y_FREQUENCY) * MAX_SHAKE_PIXELS * shake;
		float shakeAngle = (float) Math.sin(impact * SHAKE_ANGLE_FREQUENCY) * MAX_SHAKE_ANGLE * shake;
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(x + shakeX, y - drift + shakeY);
			pose.rotate(shakeAngle);
			pose.scale(scale);
			if (intersectsScreen(pose, content.width(), content.height())) {
				content.extract(graphics, opacity);
			}
		} finally {
			pose.popMatrix();
		}
	}

	private static float damageScale(float ratio) {
		return Mth.lerp(Mth.clamp(ratio / LARGE_DAMAGE_RATIO, 0, 1), ZERO_DAMAGE_SCALE, LARGE_DAMAGE_SCALE);
	}

	private static float scale(float age) {
		if (age < POP_TICKS) {
			return Mth.lerp(age / POP_TICKS, INITIAL_SCALE, PEAK_SCALE);
		}

		return Mth.lerp(Mth.clamp((age - POP_TICKS) / SETTLE_TICKS, 0, 1), PEAK_SCALE, 1);
	}

	private static boolean project(Vec3 position) {
		if (!projectionReady) {
			return false;
		}

		PROJECTED.set((float) (position.x - cameraPosition.x), (float) (position.y - cameraPosition.y),
				(float) (position.z - cameraPosition.z), 1).mul(VIEW_PROJECTION);
		return validClipPosition();
	}

	private static boolean validClipPosition() {
		if (!Float.isFinite(PROJECTED.x) || !Float.isFinite(PROJECTED.y) || !Float.isFinite(PROJECTED.z)
				|| !Float.isFinite(PROJECTED.w) || PROJECTED.w <= 0) {
			return false;
		}

		float nearDepth = zeroToOneDepth ? 0 : -PROJECTED.w;
		return PROJECTED.z >= nearDepth && PROJECTED.z <= PROJECTED.w;
	}

	private static boolean intersectsScreen(Matrix3x2fc pose, float width, float height) {
		float extentX = (Math.abs(pose.m00()) * width + Math.abs(pose.m10()) * height) / 2;
		float extentY = (Math.abs(pose.m01()) * width + Math.abs(pose.m11()) * height) / 2;
		return pose.m20() + extentX >= 0 && pose.m20() - extentX <= halfWidth * 2
				&& pose.m21() + extentY >= 0 && pose.m21() - extentY <= halfHeight * 2;
	}
}
