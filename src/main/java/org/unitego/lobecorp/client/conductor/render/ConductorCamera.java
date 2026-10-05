package org.unitego.lobecorp.client.conductor.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class ConductorCamera {
	private static final double START_ZOOM = 24.0;
	private static final double MIN_ZOOM = 6.0;
	private static final double MAX_ZOOM = 80.0;
	private static final double ZOOM_INCREMENT = 3.0;
	private static final double PAN_PER_SECOND = 13.0;
	private static final double TURN_PER_SECOND = 40.0;
	private static final double DRAG_DEGREES_PER_PIXEL = 0.35;
	private static final double MAX_FRAME_SECONDS = 0.05;
	private static final double VIEW_RESPONSE_PER_SECOND = 12.0;
	private static final double NANOS_PER_SECOND = 1_000_000_000.0;
	private static final double MAX_PICK_DISTANCE = 4096.0;
	private static final double MIN_PICK_DISTANCE = 256.0;
	private static final double RAY_EPSILON = 1.0E-6;
	private static final float PITCH = 58.0F;
	private static LocalPlayer owner;
	private static ClientLevel world;
	private static Vec3 pivot = Vec3.ZERO;
	private static Vec3 displayedPivot = Vec3.ZERO;
	private static double displayedHeading;
	private static double displayedZoom;
	private static double heading;
	private static double zoom;
	private static double dragHeading;
	private static double dragOrigin;
	private static long lastFrame;

	public static void open(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null)
			return;
		owner = minecraft.player;
		world = minecraft.level;
		pivot = owner.position();
		heading = owner.getYRot();
		zoom = START_ZOOM;
		displayedPivot = pivot;
		displayedHeading = heading;
		displayedZoom = zoom;
		lastFrame = System.nanoTime();
	}

	public static boolean valid(Minecraft minecraft) {
		return owner != null && owner == minecraft.player && world == minecraft.level && owner.isAlive();
	}

	public static void close() {
		owner = null;
		world = null;
		pivot = Vec3.ZERO;
		displayedPivot = Vec3.ZERO;
		lastFrame = 0;
	}

	public static void focus(Vec3 position) {
		pivot = position;
	}

	public static Vec3 center() {
		return displayedPivot;
	}

	public static void releaseFollowing() {
		pivot = displayedPivot;
	}

	public static void zoom(double steps) {
		zoom = Mth.clamp(zoom - steps * ZOOM_INCREMENT, MIN_ZOOM, MAX_ZOOM);
	}

	public static void beginDrag(double mouseX) {
		dragHeading = heading;
		dragOrigin = mouseX;
	}

	public static void drag(double mouseX) {
		heading = dragHeading + (mouseX - dragOrigin) * DRAG_DEGREES_PER_PIXEL;
	}

	public static View frame(float partialTick, int forwardInput, int sideInput, int turnInput, @Nullable UUID following) {
		long now = System.nanoTime();
		double elapsed = Math.clamp((now - lastFrame) / NANOS_PER_SECOND, 0.0, MAX_FRAME_SECONDS);
		lastFrame = now;
		heading += turnInput * TURN_PER_SECOND * elapsed;
		Entity target = following == null || world == null ? null : world.getEntity(following);
		if (target instanceof LivingEntity living && living.isAlive()) {
			pivot = living.getPosition(partialTick);
		} else if (forwardInput != 0 || sideInput != 0) {
			double angle = Math.toRadians(heading);
			Vec3 motion = new Vec3(-Math.sin(angle) * forwardInput - Math.cos(angle) * sideInput,
					0, Math.cos(angle) * forwardInput - Math.sin(angle) * sideInput).normalize();
			pivot = pivot.add(motion.scale(PAN_PER_SECOND * elapsed));
		}
		double blend = 1.0 - Math.exp(-VIEW_RESPONSE_PER_SECOND * elapsed);
		displayedPivot = displayedPivot.lerp(pivot, blend);
		displayedHeading += Mth.wrapDegrees(heading - displayedHeading) * blend;
		displayedZoom += (zoom - displayedZoom) * blend;
		double azimuth = Math.toRadians(displayedHeading);
		double elevation = Math.toRadians(PITCH);
		double horizontal = displayedZoom * Math.cos(elevation);
		Vec3 eye = displayedPivot.add(Math.sin(azimuth) * horizontal, Math.sin(elevation) * displayedZoom, -Math.cos(azimuth) * horizontal);
		return new View(eye, (float) Mth.wrapDegrees(displayedHeading), PITCH);
	}

	public static Vec3 pointerRay(Minecraft minecraft) {
		Camera camera = minecraft.gameRenderer.getMainCamera();
		float horizontal = (float) (minecraft.mouseHandler.xpos() / minecraft.getWindow().getWidth() * 2.0 - 1.0);
		float vertical = (float) (1.0 - minecraft.mouseHandler.ypos() / minecraft.getWindow().getHeight() * 2.0);
		return camera.getNearPlane(camera.getFov()).getPointOnPlane(horizontal, vertical).normalize();
	}

	public static double pickDistance(Minecraft minecraft, Vec3 direction) {
		if (minecraft.level == null)
			return MIN_PICK_DISTANCE;
		double height = minecraft.gameRenderer.getMainCamera().position().y - minecraft.level.getMinY();
		double toBottom = direction.y < -RAY_EPSILON ? (height + 1.0) / -direction.y : MIN_PICK_DISTANCE;
		return Math.clamp(toBottom, MIN_PICK_DISTANCE, MAX_PICK_DISTANCE);
	}

	public static @Nullable Vec3 pickGround(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null)
			return null;
		Vec3 start = minecraft.gameRenderer.getMainCamera().position();
		Vec3 ray = pointerRay(minecraft);
		BlockHitResult hit = minecraft.level.clip(new ClipContext(start, start.add(ray.scale(pickDistance(minecraft, ray))),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
		return hit.getType() == HitResult.Type.BLOCK && minecraft.level.hasChunkAt(hit.getBlockPos())
				? hit.getLocation() : null;
	}

	public record View(Vec3 position, float yaw, float pitch) {
	}
}
