package org.unitego.lobecorp.client.conductor.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.TriState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorHud;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.conductor.ability.IConductorTargeting;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConductorRendering {
	/// 渲染状态中的队伍名称键。
	private static final ContextKey<String> TEAM_NAME = new ContextKey<>(Lobecorp.id("conductor_team_name"));
	/// 渲染状态中的选择环颜色键。
	private static final ContextKey<Integer> RING_COLOR = new ContextKey<>(Lobecorp.id("conductor_ring_color"));
	/// 渲染状态中的实体 UUID 键。
	private static final ContextKey<UUID> ENTITY_ID = new ContextKey<>(Lobecorp.id("conductor_entity_id"));
	/// 渲染状态中的成员选择标记键。
	private static final ContextKey<Boolean> SELECTED = new ContextKey<>(Lobecorp.id("conductor_selected"));
	/// 渲染状态中的选择环集合键。
	private static final ContextKey<List<RingState>> RINGS = new ContextKey<>(Lobecorp.id("conductor_rings"));
	/// 渲染状态中的瞄准预览键。
	private static final ContextKey<PreviewState> PREVIEW = new ContextKey<>(Lobecorp.id("conductor_preview"));
	/// 选择环相对实体包围盒的外扩距离，单位为格。
	private static final float RING_MARGIN = 0.25F;
	/// 选择环离地偏移，单位为格。
	private static final float RING_Y_OFFSET = 0.04F;
	/// 选择环线宽，单位为渲染像素。
	private static final float RING_WIDTH = 2.0F;
	/// 未选择成员的环形标记亮度倍率。
	private static final float UNSELECTED_RING_BRIGHTNESS = 0.35F;
	/// 路径线段的最小长度平方，用于避免退化几何。
	private static final double LINE_MIN_LENGTH_SQUARED = 1.0E-6D;
	/// 瞄准范围圆环的分段数量。
	private static final int PREVIEW_RING_SEGMENTS = 64;
	/// 瞄准虚线的单段长度，单位为格。
	private static final double PREVIEW_DASH_LENGTH = 0.6D;
	/// 瞄准虚线的段间距，单位为格。
	private static final double PREVIEW_DASH_GAP = 0.35D;
	/// 瞄准落点标记的半边长，单位为格。
	private static final double PREVIEW_MARKER_HALF_SIZE = 0.3D;
	/// 光标标记的半尺寸，单位为 GUI 像素。
	private static final int CURSOR_HALF_SIZE = 7;
	/// 光标标记的完整尺寸，单位为 GUI 像素。
	private static final int CURSOR_SIZE = CURSOR_HALF_SIZE * 2;
	/// 光标中心与线段之间的空隙，单位为 GUI 像素。
	private static final int CURSOR_GAP = 3;
	/// 光标线段向外延伸的距离，单位为 GUI 像素。
	private static final int CURSOR_REACH = 9;
	/// 指挥光标的 ARGB 颜色。
	private static final int CURSOR_COLOR = 0xFFFFFFFF;
	/// 框选边框的 ARGB 颜色。
	private static final int SELECTION_BOX_COLOR = 0xFF66CCFF;
	/// 攻击目标标记的 ARGB 颜色。
	private static final int TARGET_RING_COLOR = 0xFFFF3333;
	/// 已选择成员标记的 ARGB 颜色。
	private static final int SELECTED_RING_COLOR = 0xFF66CCFF;
	/// 移动命令路径的 ARGB 颜色。
	private static final int MOVE_LINE_COLOR = 0xFF66CCFF;
	/// 攻击命令路径的 ARGB 颜色。
	private static final int ATTACK_LINE_COLOR = 0xFFFF3333;
	/// 瞄准范围预览的 ARGB 颜色。
	private static final int PREVIEW_RANGE_COLOR = 0xAA66CCFF;
	/// 有效瞄准目标的 ARGB 颜色。
	private static final int PREVIEW_TARGET_COLOR = 0xFF66CCFF;
	/// 超出施法范围时使用黄色预览。
	private static final int PREVIEW_LIMIT_COLOR = 0xFFFFFF55;
	/// 目标或施放条件无效时使用红色预览。
	private static final int PREVIEW_INVALID_COLOR = 0xFFFF3333;
	/// 修正落点预览的 ARGB 颜色。
	private static final int PREVIEW_CORRECTED_COLOR = 0xFF55FF55;
	/// 实体标签中名称与队伍名称之间的分隔符。
	private static final String TEAM_NAME_SEPARATOR = " · ";
	private static ItemStack attackCursor;
	private static ItemStack barrierCursor;

	public static boolean isSelected(EntityRenderState state) {
		return state.outlineColor != 0 && Boolean.TRUE.equals(state.getRenderData(SELECTED));
	}

	public static void updateEntitySelection(Entity entity, EntityRenderState state) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}

		state.setRenderData(ENTITY_ID, living.getUUID());
		boolean selected = ConductorControls.active() && ConductorControls.selected().contains(living.getUUID());
		state.setRenderData(SELECTED, selected);
		ConductorData.Unit unit = living instanceof Mob ? ConductorClient.unit(living.getUUID()) : null;
		ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());

		if (team != null) {
			state.setRenderData(TEAM_NAME, team.name());
			state.setRenderData(RING_COLOR, team.color());
		}

		if (selected && team == null) {
			state.setRenderData(RING_COLOR, SELECTED_RING_COLOR);
		}

		if (ConductorControls.active() && ConductorControls.selected().stream()
				.map(ConductorClient.snapshot()::unit)
				.anyMatch(selectedUnit -> selectedUnit != null && living.getUUID().toString().equals(selectedUnit.target()))) {
			state.setRenderData(RING_COLOR, TARGET_RING_COLOR);
		}

		if (selected) {
			Integer color = state.getRenderData(RING_COLOR);
			state.outlineColor = ARGB.opaque(color == null ? SELECTED_RING_COLOR : color);
		}
	}

	public static void onNameTag(RenderNameTagEvent.CanRender event) {
		if (!(event.getEntity() instanceof Mob mob)) {
			return;
		}

		ConductorData.Unit unit = ConductorClient.unit(mob.getUUID());

		if (unit == null) {
			return;
		}

		ConductorData.Team team = ConductorClient.snapshot().team(unit.team());

		if (team == null) {
			return;
		}

		Component teamName = Component.literal(team.name()).withColor(team.color());
		Component original = event.getOriginalContent();
		event.setContent(original == null ? teamName : original.copy().append(Component.literal(TEAM_NAME_SEPARATOR)).append(teamName));
		event.setCanRender(TriState.TRUE);
	}

	public static void onExtract(ExtractLevelRenderStateEvent event) {
		var state = event.getRenderState();
		Minecraft minecraft = Minecraft.getInstance();
		List<UUID> selected = ConductorControls.active() ? ConductorControls.selected() : List.of();
		Set<String> targets = selected.stream().map(ConductorClient.snapshot()::unit)
				.filter(unit -> unit != null && unit.order() == ConductorData.OrderType.ATTACK)
				.map(ConductorData.Unit::target).collect(Collectors.toSet());
		List<RingState> rings = new ArrayList<>();

		if (minecraft.level != null) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (!(entity instanceof LivingEntity living)) {
					continue;
				}

				ConductorData.Unit unit = living instanceof Mob ? ConductorClient.unit(living.getUUID()) : null;
				ConductorData.Team team = unit == null ? null : ConductorClient.snapshot().team(unit.team());
				boolean isSelected = selected.contains(living.getUUID());
				int color = targets.contains(living.getUUID().toString()) ? TARGET_RING_COLOR
						: team != null ? team.color() : SELECTED_RING_COLOR;

				if (team == null && !isSelected && !targets.contains(living.getUUID().toString())) {
					continue;
				}

				float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(
						!minecraft.level.tickRateManager().isEntityFrozen(living));
				rings.add(new RingState(living.getUUID(), living.getPosition(partialTick),
						living.getBbWidth() / 2.0F + RING_MARGIN, color, isSelected));
			}
		}

		state.setRenderData(RINGS, rings);
		PreviewState preview = previewState(minecraft);

		if (preview != null) {
			state.setRenderData(PREVIEW, preview);
		}
	}

	private static @Nullable PreviewState previewState(Minecraft minecraft) {
		if (!ConductorControls.active() || minecraft.level == null) {
			return null;
		}

		ConductorControls.AimPreview aiming = ConductorControls.aimPreview(minecraft);

		if (aiming != null) {
			IConductorTargeting targeting = aiming.targeting();
			Mob mob = aiming.caster();
			double radius = targeting.previewRadius(mob);
			return new PreviewState(mob.position(), radius, radius > 0.0D,
					aiming.requestedPosition(), aiming.effectivePosition(),
					aiming.targetSelection() != ConductorCommandPayload.TargetSelection.NONE,
					aiming.rangeLimited(), !aiming.valid(),
					targeting.previewGeometry(mob, aiming.requestedPosition(), aiming.effectivePosition()));
		}

		ConductorHud.HoveredSkill hovered = ConductorHud.INSTANCE.hoveredSkill(minecraft);

		if (hovered == null) {
			return null;
		}

		IConductorTargeting targeting = hovered.targeting();
		Mob mob = hovered.mob();
		double radius = targeting.previewRadius(mob);

		if ((targeting.previewShape() != IConductorTargeting.PreviewShape.AREA && !targeting.hasRange(mob) && !targeting.directional())
				|| radius <= 0.0D && !targeting.directional()) {
			return null;
		}

		return new PreviewState(mob.position(), radius, radius > 0.0D, mob.position(), mob.position(), false, false, false,
				(targeting.previewShape() == IConductorTargeting.PreviewShape.AREA || targeting.directional())
						? targeting.previewGeometry(mob, targeting.facingPosition(mob), targeting.facingPosition(mob))
						: IConductorTargeting.PreviewGeometry.EMPTY);
	}

	public static void onCustomGeometry(SubmitCustomGeometryEvent event) {
		var state = event.getLevelRenderState();
		var camera = state.cameraRenderState.pos;
		List<RingState> rings = state.getRenderData(RINGS);

		if (rings == null) {
			return;
		}

		for (RingState ring : rings) {
			int color = ring.selected() ? ring.color() : ARGB.scaleRGB(ring.color(), UNSELECTED_RING_BRIGHTNESS);
			float radius = ring.radius();
			PoseStack poseStack = event.getPoseStack();
			poseStack.pushPose();
			poseStack.translate(ring.position().x - camera.x(), ring.position().y - camera.y() + RING_Y_OFFSET,
					ring.position().z - camera.z());
			event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				int argb = ARGB.opaque(color);
				line(pose, buffer, -radius, -radius, radius, -radius, argb);
				line(pose, buffer, radius, -radius, radius, radius, argb);
				line(pose, buffer, radius, radius, -radius, radius, argb);
				line(pose, buffer, -radius, radius, -radius, -radius, argb);
			});
			poseStack.popPose();
			ConductorData.Unit unit = !ring.selected() ? null : ConductorClient.unit(ring.id());

			if (unit == null) {
				continue;
			}

			Vec3 target = null;
			int lineColor = MOVE_LINE_COLOR;

			if (unit.order() == ConductorData.OrderType.MOVE || unit.order() == ConductorData.OrderType.ATTACK_POINT) {
				target = new Vec3(unit.x(), unit.y(), unit.z());

				if (unit.order() == ConductorData.OrderType.ATTACK_POINT) {
					lineColor = ATTACK_LINE_COLOR;
				}
			} else if (unit.order() == ConductorData.OrderType.ATTACK && !unit.target().isEmpty()
					&& Minecraft.getInstance().level != null) {
				try {
					Entity targetEntity = Minecraft.getInstance().level.getEntity(UUID.fromString(unit.target()));

					if (targetEntity != null) {
						float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(
								!Minecraft.getInstance().level.tickRateManager().isEntityFrozen(targetEntity));
						target = targetEntity.getPosition(partialTick).add(0.0D, targetEntity.getBbHeight() / 2.0D, 0.0D);
					}
				} catch (IllegalArgumentException ignored) {
				}

				lineColor = ATTACK_LINE_COLOR;
			}

			if (target != null) {
				Vec3 end = target.subtract(ring.position());

				if (end.lengthSqr() <= LINE_MIN_LENGTH_SQUARED) {
					continue;
				}

				int argb = ARGB.opaque(lineColor);
				poseStack.pushPose();
				poseStack.translate(ring.position().x - camera.x(), ring.position().y - camera.y() + RING_Y_OFFSET,
						ring.position().z - camera.z());
				event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
					var normal = new Vector3f((float) end.x, (float) end.y, (float) end.z).normalize();
					buffer.addVertex(pose, 0.0F, 0.0F, 0.0F)
							.setColor(argb).setNormal(pose, normal).setLineWidth(RING_WIDTH);
					buffer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
							.setColor(argb).setNormal(pose, normal).setLineWidth(RING_WIDTH);
				});
				poseStack.popPose();
			}
		}

		PreviewState preview = state.getRenderData(PREVIEW);

		if (preview != null) {
			renderPreview(event, camera, preview);
		}
	}

	private static void line(PoseStack.Pose pose, VertexConsumer buffer,
	                         float x1, float z1, float x2, float z2, int color) {
		var normal = new Vector3f(x2 - x1, 0.0F, z2 - z1).normalize();
		buffer.addVertex(pose, x1, 0.0F, z1).setColor(color).setNormal(pose, normal).setLineWidth(RING_WIDTH);
		buffer.addVertex(pose, x2, 0.0F, z2).setColor(color).setNormal(pose, normal).setLineWidth(RING_WIDTH);
	}

	private static void renderPreview(SubmitCustomGeometryEvent event, Vec3 camera, PreviewState preview) {
		PoseStack poseStack = event.getPoseStack();
		int geometryColor = preview.rangeLimited() ? PREVIEW_LIMIT_COLOR
				: preview.invalid() ? PREVIEW_INVALID_COLOR : PREVIEW_RANGE_COLOR;

		if (preview.showRadius()) {
			poseStack.pushPose();
			poseStack.translate(preview.source().x - camera.x(), preview.source().y - camera.y() + RING_Y_OFFSET,
					preview.source().z - camera.z());
			event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				for (int segment = 0; segment < PREVIEW_RING_SEGMENTS; segment++) {
					if (preview.rangeLimited() && segment % 3 == 2) {
						continue;
					}

					double firstAngle = Math.PI * 2.0D * segment / PREVIEW_RING_SEGMENTS;
					double secondAngle = Math.PI * 2.0D * (segment + 1) / PREVIEW_RING_SEGMENTS;
					float x1 = (float) (Math.cos(firstAngle) * preview.radius());
					float z1 = (float) (Math.sin(firstAngle) * preview.radius());
					float x2 = (float) (Math.cos(secondAngle) * preview.radius());
					float z2 = (float) (Math.sin(secondAngle) * preview.radius());
					line(pose, buffer, x1, z1, x2, z2, geometryColor);
				}
			});
			poseStack.popPose();
		}

		boolean dashed = preview.rangeLimited() || preview.invalid();

		for (IConductorTargeting.PreviewCircle circle : preview.geometry().circles()) {
			renderPreviewCircle(event, camera, circle, geometryColor, dashed);
		}

		for (IConductorTargeting.PreviewBeam beam : preview.geometry().beams()) {
			renderPreviewBeam(event, camera, beam, geometryColor, dashed);
		}

		for (IConductorTargeting.PreviewSector sector : preview.geometry().sectors()) {
			renderPreviewSector(event, camera, sector, geometryColor, dashed);
		}

		if (!preview.showTarget()) {
			return;
		}

		int targetColor = preview.rangeLimited() ? PREVIEW_LIMIT_COLOR : preview.invalid() ? PREVIEW_INVALID_COLOR : PREVIEW_TARGET_COLOR;
		Vec3 requested = preview.requestedPosition();
		Vec3 requestedOffset = requested.subtract(preview.source());

		if (preview.rangeLimited()) {
			poseStack.pushPose();
			poseStack.translate(preview.source().x - camera.x(), preview.source().y - camera.y() + RING_Y_OFFSET,
					preview.source().z - camera.z());
			event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(),
					(pose, buffer) -> dashedLine(pose, buffer, requestedOffset));
			poseStack.popPose();
		}

		renderMarker(event, camera, requested, targetColor);

		if (preview.rangeLimited() && requested.distanceToSqr(preview.effectivePosition()) > LINE_MIN_LENGTH_SQUARED) {
			renderMarker(event, camera, preview.effectivePosition(), PREVIEW_CORRECTED_COLOR);
		}
	}

	private static void renderPreviewSector(SubmitCustomGeometryEvent event, Vec3 camera,
	                                        IConductorTargeting.PreviewSector sector, int color, boolean dashed) {
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(sector.origin().x - camera.x, sector.origin().y - camera.y + RING_Y_OFFSET,
				sector.origin().z - camera.z);
		event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			double halfAngle = Math.toRadians(sector.angle() / 2.0);
			Vec3 first = sector.forward().yRot((float) -halfAngle).scale(sector.radius());
			renderPreviewSegment(pose, buffer, Vec3.ZERO, first, color, dashed);

			for (int segment = 1; segment <= PREVIEW_RING_SEGMENTS; segment++) {
				double angle = -halfAngle + halfAngle * 2.0 * segment / PREVIEW_RING_SEGMENTS;
				Vec3 next = sector.forward().yRot((float) angle).scale(sector.radius());
				renderPreviewSegment(pose, buffer, first, next, color, dashed);
				first = next;
			}

			renderPreviewSegment(pose, buffer, first, Vec3.ZERO, color, dashed);
		});
		poseStack.popPose();
	}

	private static void renderPreviewCircle(SubmitCustomGeometryEvent event, Vec3 camera,
	                                        IConductorTargeting.PreviewCircle circle, int color, boolean dashed) {
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(circle.center().x - camera.x(), circle.center().y - camera.y() + RING_Y_OFFSET,
				circle.center().z - camera.z());
		event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			for (int segment = 0; segment < PREVIEW_RING_SEGMENTS; segment++) {
				if (dashed && segment % 3 == 2) {
					continue;
				}

				double firstAngle = Math.PI * 2.0D * segment / PREVIEW_RING_SEGMENTS;
				double secondAngle = Math.PI * 2.0D * (segment + 1) / PREVIEW_RING_SEGMENTS;
				line(pose, buffer, (float) (Math.cos(firstAngle) * circle.radius()),
						(float) (Math.sin(firstAngle) * circle.radius()),
						(float) (Math.cos(secondAngle) * circle.radius()),
						(float) (Math.sin(secondAngle) * circle.radius()), color);
			}
		});
		poseStack.popPose();
	}

	private static void renderPreviewBeam(SubmitCustomGeometryEvent event, Vec3 camera,
	                                      IConductorTargeting.PreviewBeam beam, int color, boolean dashed) {
		Vec3 direction = beam.end().subtract(beam.start());
		var horizontalDirection = new Vec3(direction.x, 0.0D, direction.z);
		Vec3 side = horizontalDirection.lengthSqr() <= LINE_MIN_LENGTH_SQUARED
				? new Vec3(beam.halfWidth(), 0.0D, 0.0D)
				: new Vec3(-horizontalDirection.z, 0.0D, horizontalDirection.x).normalize().scale(beam.halfWidth());
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(beam.start().x - camera.x(), beam.start().y - camera.y() + RING_Y_OFFSET,
				beam.start().z - camera.z());
		event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			Vec3 second = direction.add(side);
			Vec3 third = direction.subtract(side);
			Vec3 fourth = side.scale(-1.0D);
			renderPreviewSegment(pose, buffer, side, second, color, dashed);
			renderPreviewSegment(pose, buffer, second, third, color, dashed);
			renderPreviewSegment(pose, buffer, third, fourth, color, dashed);
			renderPreviewSegment(pose, buffer, fourth, side, color, dashed);
		});
		poseStack.popPose();
	}

	private static void renderPreviewSegment(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end,
	                                         int color, boolean dashed) {
		Vec3 direction = end.subtract(start);
		double length = direction.length();

		if (length <= 0.0D) {
			return;
		}

		if (!dashed) {
			renderSpatialLine(pose, buffer, start, end, color);
			return;
		}

		Vec3 normal = direction.scale(1.0D / length);

		for (double offset = 0.0D; offset < length; offset += PREVIEW_DASH_LENGTH + PREVIEW_DASH_GAP) {
			Vec3 first = start.add(normal.scale(offset));
			Vec3 second = start.add(normal.scale(Math.min(offset + PREVIEW_DASH_LENGTH, length)));
			renderSpatialLine(pose, buffer, first, second, color);
		}
	}

	private static void renderSpatialLine(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end, int color) {
		var normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y),
				(float) (end.z - start.z)).normalize();
		buffer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
				.setColor(color).setNormal(pose, normal).setLineWidth(RING_WIDTH);
		buffer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
				.setColor(color).setNormal(pose, normal).setLineWidth(RING_WIDTH);
	}

	private static void dashedLine(PoseStack.Pose pose, VertexConsumer buffer, Vec3 end) {
		double length = end.length();

		if (length <= 0.0D) {
			return;
		}

		Vec3 direction = end.scale(1.0D / length);
		var normal = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
		int argb = ARGB.opaque(PREVIEW_LIMIT_COLOR);

		for (double start = 0.0D; start < length; start += PREVIEW_DASH_LENGTH + PREVIEW_DASH_GAP) {
			double finish = Math.min(start + PREVIEW_DASH_LENGTH, length);
			Vec3 first = direction.scale(start);
			Vec3 second = direction.scale(finish);
			buffer.addVertex(pose, (float) first.x, (float) first.y, (float) first.z)
					.setColor(argb).setNormal(pose, normal).setLineWidth(RING_WIDTH);
			buffer.addVertex(pose, (float) second.x, (float) second.y, (float) second.z)
					.setColor(argb).setNormal(pose, normal).setLineWidth(RING_WIDTH);
		}
	}

	private static void renderMarker(SubmitCustomGeometryEvent event, Vec3 camera, Vec3 position, int color) {
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(position.x - camera.x(), position.y - camera.y() + RING_Y_OFFSET,
				position.z - camera.z());
		event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			float halfSize = (float) PREVIEW_MARKER_HALF_SIZE;
			line(pose, buffer, -halfSize, -halfSize, halfSize, -halfSize, color);
			line(pose, buffer, halfSize, -halfSize, halfSize, halfSize, color);
			line(pose, buffer, halfSize, halfSize, -halfSize, halfSize, color);
			line(pose, buffer, -halfSize, halfSize, -halfSize, -halfSize, color);
		});
		poseStack.popPose();
	}

	public static void onHand(RenderHandEvent event) {
		if (ConductorControls.active()) {
			event.setCanceled(true);
		}
	}

	public static void onGui(RenderGuiEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();

		if (!ConductorControls.active() || minecraft.screen != null) {
			return;
		}

		ConductorCommandPayload.Action action = ConductorControls.pendingAction();

		if (action != ConductorCommandPayload.Action.ATTACK
				&& action != ConductorCommandPayload.Action.MOVE
				&& action != ConductorCommandPayload.Action.CAST && !ConductorControls.dragging()) {
			return;
		}

		GuiGraphicsExtractor graphics = event.getGuiGraphics();
		int x = (int) (minecraft.mouseHandler.xpos() * graphics.guiWidth() / minecraft.getWindow().getWidth());
		int y = (int) (minecraft.mouseHandler.ypos() * graphics.guiHeight() / minecraft.getWindow().getHeight());

		if (action == ConductorCommandPayload.Action.CAST) {
			if (ConductorHud.INSTANCE.contains(minecraft)) {
				return;
			}

			ConductorHud.INSTANCE.renderSelectedSkill(graphics);
			ConductorControls.AimPreview preview = ConductorControls.aimPreview(minecraft);

			if (preview == null || !preview.valid()) {
				if (barrierCursor == null) {
					barrierCursor = new ItemStack(Items.BARRIER);
				}

				graphics.item(barrierCursor, x - CURSOR_HALF_SIZE, y - CURSOR_HALF_SIZE);
				return;
			}
		}

		if (ConductorControls.dragging()) {
			int startX = (int) (ConductorControls.dragStartX() * graphics.guiWidth() / minecraft.getWindow().getWidth());
			int startY = (int) (ConductorControls.dragStartY() * graphics.guiHeight() / minecraft.getWindow().getHeight());
			graphics.outline(Math.min(startX, x), Math.min(startY, y),
					Math.max(1, Math.abs(x - startX)), Math.max(1, Math.abs(y - startY)), SELECTION_BOX_COLOR);
		}

		if (action == ConductorCommandPayload.Action.ATTACK) {
			if (attackCursor == null) {
				attackCursor = new ItemStack(Items.IRON_SWORD);
			}

			graphics.item(attackCursor, x - CURSOR_HALF_SIZE, y - CURSOR_HALF_SIZE);
		} else {
			graphics.outline(x - CURSOR_HALF_SIZE, y - CURSOR_HALF_SIZE, CURSOR_SIZE, CURSOR_SIZE, CURSOR_COLOR);
			graphics.horizontalLine(x - CURSOR_REACH, x - CURSOR_GAP, y, CURSOR_COLOR);
			graphics.horizontalLine(x + CURSOR_GAP, x + CURSOR_REACH, y, CURSOR_COLOR);
			graphics.verticalLine(x, y - CURSOR_REACH, y - CURSOR_GAP, CURSOR_COLOR);
			graphics.verticalLine(x, y + CURSOR_GAP, y + CURSOR_REACH, CURSOR_COLOR);
		}
	}

	private record RingState(UUID id, Vec3 position, float radius, int color, boolean selected) {
	}

	private record PreviewState(Vec3 source, double radius, boolean showRadius, Vec3 requestedPosition,
	                            Vec3 effectivePosition, boolean showTarget, boolean rangeLimited, boolean invalid,
	                            IConductorTargeting.PreviewGeometry geometry) {
	}
}
