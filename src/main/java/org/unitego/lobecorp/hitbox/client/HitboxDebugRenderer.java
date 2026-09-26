package org.unitego.lobecorp.hitbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.hitbox.BoxSize;
import org.unitego.lobecorp.hitbox.BoxRingCylinderSize;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.ConeSize;
import org.unitego.lobecorp.hitbox.EllipsoidSize;
import org.unitego.lobecorp.hitbox.HitboxGeometry;
import org.unitego.lobecorp.hitbox.HitboxSnapshot;
import org.unitego.lobecorp.hitbox.HitboxPurpose;
import org.unitego.lobecorp.hitbox.SectorCylinderSize;
import org.unitego.lobecorp.hitbox.SphereSize;
import org.unitego.lobecorp.hitbox.RingCylinderSize;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

import java.util.ArrayList;
import java.util.List;

/// F3 判断框线框调试渲染器。
public class HitboxDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	/// 未激活预览阶段的线框颜色。
	private static final int INACTIVE_COLOR = 0xFFFFFFFF;
	/// 已开始处理伤害阶段的线框颜色。
	private static final int ACTIVE_COLOR = 0xFFFF0000;
	/// 已启用检测判断框的线框颜色。
	private static final int DETECTION_COLOR = 0xFFFFFF00;
	/// 圆与圆弧的线段数量。
	private static final int CURVE_SEGMENTS = 32;
	/// 圆柱侧面展示的母线数量。
	private static final int CYLINDER_SIDE_COUNT = 4;

	private final Minecraft minecraft;

	public HitboxDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double camX, double camY, double camZ, @NonNull DebugValueAccess debugValues,
			@NonNull Frustum frustum, float partialTicks) {
		if (minecraft.level == null) {
			return;
		}
		for (HitboxSnapshot snapshot : minecraft.level.getData(LcAttachmentTypes.HITBOX_LEVEL_DATA)
				.clientSnapshots()) {
			render(snapshot);
		}
	}

	private static void render(HitboxSnapshot snapshot) {
		int color = INACTIVE_COLOR;
		if (snapshot.active()) {
			color = snapshot.purpose() == HitboxPurpose.DETECTION ? DETECTION_COLOR : ACTIVE_COLOR;
		}
		switch (snapshot.size()) {
			case SphereSize sphere -> renderSphere(snapshot, sphere, color);
			case CylinderSize cylinder -> renderCylinder(snapshot, cylinder.radius(), cylinder.height(), color);
			case RingCylinderSize ring -> renderRingCylinder(snapshot, ring, color);
			case BoxSize box -> renderBox(snapshot, box, color);
			case BoxRingCylinderSize ring -> renderBoxRingCylinder(snapshot, ring, color);
			case SectorCylinderSize sector -> renderSector(snapshot, sector, color);
			case ConeSize cone -> renderCone(snapshot, cone, color);
			case EllipsoidSize ellipsoid -> renderEllipsoid(snapshot, ellipsoid, color);
			default -> throw new IllegalStateException("Unsupported hitbox size: "
					+ snapshot.size().getClass().getName());
		}
	}

	private static void renderRingCylinder(HitboxSnapshot snapshot, RingCylinderSize ring, int color) {
		renderCylinderBoundary(snapshot, ring.radius(), ring.height(), color);
		if (ring.innerRadius() > 0.0) {
			renderCylinderBoundary(snapshot, ring.innerRadius(), ring.height(), color);
		}
	}

	private static void renderCylinderBoundary(HitboxSnapshot snapshot, double radius, double height, int color) {
		double halfHeight = height / 2.0;
		renderCircle(snapshot, radius, halfHeight, CirclePlane.XZ, color);
		renderCircle(snapshot, radius, -halfHeight, CirclePlane.XZ, color);
		for (int index = 0; index < CYLINDER_SIDE_COUNT; index++) {
			double angle = Math.PI * 2.0 * index / CYLINDER_SIDE_COUNT;
			Vec3 bottom = world(snapshot, new Vec3(Math.sin(angle) * radius, -halfHeight,
					Math.cos(angle) * radius));
			Vec3 top = world(snapshot, new Vec3(Math.sin(angle) * radius, halfHeight,
					Math.cos(angle) * radius));
			Gizmos.line(bottom, top, color);
		}
	}

	private static void renderBoxRingCylinder(HitboxSnapshot snapshot, BoxRingCylinderSize ring, int color) {
		renderBox(snapshot, new BoxSize(ring.width(), ring.height(), ring.depth()), color);
		if (ring.innerWidth() > 0.0 && ring.innerDepth() > 0.0) {
			renderBox(snapshot, new BoxSize(ring.innerWidth(), ring.height(), ring.innerDepth()), color);
		}
	}

	private static void renderEllipsoid(HitboxSnapshot snapshot, EllipsoidSize ellipsoid, int color) {
		renderEllipse(snapshot, ellipsoid.radiusX(), ellipsoid.radiusY(), 0.0, CirclePlane.XY, color);
		renderEllipse(snapshot, ellipsoid.radiusX(), ellipsoid.radiusZ(), 0.0, CirclePlane.XZ, color);
		renderEllipse(snapshot, ellipsoid.radiusY(), ellipsoid.radiusZ(), 0.0, CirclePlane.YZ, color);
	}

	private static void renderCone(HitboxSnapshot snapshot, ConeSize cone, int color) {
		double radius = Math.tan(Math.toRadians(cone.angleDegrees() / 2.0)) * cone.length();
		renderCircle(snapshot, radius, cone.length(), CirclePlane.XY, color);
		Vec3 tip = world(snapshot, Vec3.ZERO);
		for (int index = 0; index < CYLINDER_SIDE_COUNT; index++) {
			double angle = Math.PI * 2.0 * index / CYLINDER_SIDE_COUNT;
			Vec3 edge = world(snapshot,
					new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, cone.length()));
			Gizmos.line(tip, edge, color);
		}
	}

	private static void renderSphere(HitboxSnapshot snapshot, SphereSize sphere, int color) {
		renderCircle(snapshot, sphere.radius(), 0.0, CirclePlane.XY, color);
		renderCircle(snapshot, sphere.radius(), 0.0, CirclePlane.XZ, color);
		renderCircle(snapshot, sphere.radius(), 0.0, CirclePlane.YZ, color);
	}

	private static void renderCylinder(HitboxSnapshot snapshot, double radius, double height, int color) {
		double halfHeight = height / 2.0;
		renderCircle(snapshot, radius, halfHeight, CirclePlane.XZ, color);
		renderCircle(snapshot, radius, -halfHeight, CirclePlane.XZ, color);
		for (int index = 0; index < CYLINDER_SIDE_COUNT; index++) {
			double angle = Math.PI * 2.0 * index / CYLINDER_SIDE_COUNT;
			Vec3 bottom = world(snapshot, new Vec3(Math.sin(angle) * radius, -halfHeight,
					Math.cos(angle) * radius));
			Vec3 top = world(snapshot, new Vec3(Math.sin(angle) * radius, halfHeight,
					Math.cos(angle) * radius));
			Gizmos.line(bottom, top, color);
		}
	}

	private static void renderBox(HitboxSnapshot snapshot, BoxSize box, int color) {
		double x = box.width() / 2.0;
		double y = box.height() / 2.0;
		double z = box.depth() / 2.0;
		List<Vec3> corners = List.of(
				world(snapshot, new Vec3(-x, -y, -z)),
				world(snapshot, new Vec3(x, -y, -z)),
				world(snapshot, new Vec3(x, -y, z)),
				world(snapshot, new Vec3(-x, -y, z)),
				world(snapshot, new Vec3(-x, y, -z)),
				world(snapshot, new Vec3(x, y, -z)),
				world(snapshot, new Vec3(x, y, z)),
				world(snapshot, new Vec3(-x, y, z))
		);
		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		for (int[] edge : edges) {
			Gizmos.line(corners.get(edge[0]), corners.get(edge[1]), color);
		}
	}

	private static void renderSector(HitboxSnapshot snapshot, SectorCylinderSize sector, int color) {
		double halfHeight = sector.height() / 2.0;
		double halfAngle = Math.toRadians(sector.angleDegrees() / 2.0);
		List<Vec3> bottom = new ArrayList<>();
		List<Vec3> top = new ArrayList<>();
		for (int index = 0; index <= CURVE_SEGMENTS; index++) {
			double angle = -halfAngle + sector.angleDegrees() * Math.PI / 180.0
					* index / CURVE_SEGMENTS;
			bottom.add(world(snapshot, new Vec3(Math.sin(angle) * sector.radius(), -halfHeight,
					Math.cos(angle) * sector.radius())));
			top.add(world(snapshot, new Vec3(Math.sin(angle) * sector.radius(), halfHeight,
					Math.cos(angle) * sector.radius())));
		}
		renderPolyline(bottom, color);
		renderPolyline(top, color);
		Vec3 bottomCenter = world(snapshot, new Vec3(0.0, -halfHeight, 0.0));
		Vec3 topCenter = world(snapshot, new Vec3(0.0, halfHeight, 0.0));
		Gizmos.line(bottomCenter, bottom.getFirst(), color);
		Gizmos.line(bottomCenter, bottom.getLast(), color);
		Gizmos.line(topCenter, top.getFirst(), color);
		Gizmos.line(topCenter, top.getLast(), color);
		Gizmos.line(bottomCenter, topCenter, color);
		Gizmos.line(bottom.getFirst(), top.getFirst(), color);
		Gizmos.line(bottom.getLast(), top.getLast(), color);
	}

	private static void renderCircle(HitboxSnapshot snapshot, double radius, double offset,
			CirclePlane plane, int color) {
		renderEllipse(snapshot, radius, radius, offset, plane, color);
	}

	private static void renderEllipse(HitboxSnapshot snapshot, double firstRadius, double secondRadius,
			double offset, CirclePlane plane, int color) {
		List<Vec3> points = new ArrayList<>();
		for (int index = 0; index <= CURVE_SEGMENTS; index++) {
			double angle = Math.PI * 2.0 * index / CURVE_SEGMENTS;
			Vec3 local = switch (plane) {
				case XY -> new Vec3(Math.cos(angle) * firstRadius, Math.sin(angle) * secondRadius, offset);
				case XZ -> new Vec3(Math.cos(angle) * firstRadius, offset, Math.sin(angle) * secondRadius);
				case YZ -> new Vec3(offset, Math.cos(angle) * firstRadius, Math.sin(angle) * secondRadius);
			};
			points.add(world(snapshot, local));
		}
		renderPolyline(points, color);
	}

	private static void renderPolyline(List<Vec3> points, int color) {
		for (int index = 1; index < points.size(); index++) {
			Gizmos.line(points.get(index - 1), points.get(index), color);
		}
	}

	private static Vec3 world(HitboxSnapshot snapshot, Vec3 local) {
		Vec3 rotation = snapshot.rotation();
		return HitboxGeometry.rotate(local, rotation.x, rotation.y, rotation.z).add(snapshot.position());
	}

	private enum CirclePlane {
		/// XY 平面。
		XY,
		/// XZ 平面。
		XZ,
		/// YZ 平面。
		YZ
	}
}
