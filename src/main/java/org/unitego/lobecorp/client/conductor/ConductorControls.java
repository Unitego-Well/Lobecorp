package org.unitego.lobecorp.client.conductor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.client.conductor.ConductorCamera.View;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.ability.ConductorTargetingResolver;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorDirectory;
import org.unitego.lobecorp.mixin.client.ClientInputAccessor;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;
import org.unitego.lobecorp.registry.client.ConductorKeyMappings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class ConductorControls {
	private static final double DRAG_THRESHOLD = 5.0D;
	private static final int MOUSE_LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
	private static final int MOUSE_RIGHT = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
	private static final Set<Identifier> HIDDEN_HUD_LAYERS = Set.of(
			VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH,
			VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL,
			VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.EXPERIENCE_LEVEL,
			VanillaGuiLayers.CONTEXTUAL_INFO_BAR, VanillaGuiLayers.SELECTED_ITEM_NAME);
	private static final long DOUBLE_CLICK_MS = 250L;
	private static final int MOUSE_MIDDLE = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
	private static final Set<UUID> SELECTED = new LinkedHashSet<>();
	private static boolean active;
	private static boolean following;
	private static UUID followedUnit;
	private static UUID lastClickedUnit;
	private static long lastUnitClick;
	private static boolean moveUp;
	private static boolean moveDown;
	private static boolean moveLeft;
	private static boolean moveRight;
	private static boolean rotateLeft;
	private static boolean rotateRight;
	private static boolean dragging;
	private static double dragX;
	private static double dragY;
	private static boolean rightPressed;
	private static boolean rightDragging;
	private static double rightDownX;
	private static double rightDownY;
	private static ConductorCommandPayload.Action pending;
	private static String pendingSkill = "";
	private static UUID pendingSkillUnit;

	public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
		if (active && HIDDEN_HUD_LAYERS.contains(event.getName())) {
			event.setCanceled(true);
		}
	}

	public static boolean active() {
		return active;
	}

	public static boolean handleKeyboard(long handle, KeyEvent event, int action) {
		if (!active) return false;
		Minecraft minecraft = Minecraft.getInstance();
		if (handle != minecraft.getWindow().handle()) return false;
		if (minecraft.screen != null) return false;
		if (minecraft.options.keyDebugModifier.matches(event) || minecraft.options.keyDebugModifier.isDown()
				|| minecraft.options.keyDebugOverlay.matches(event) || minecraft.options.keyScreenshot.matches(event)
				|| minecraft.options.keyFullscreen.matches(event)) return false;
		if (ConductorHud.INSTANCE.handleSearchKey(event, action)) return true;
		if (ConductorHud.INSTANCE.handleSkillKey(event, action)) return true;
		if (action == InputConstants.PRESS && event.isEscape()) {
			if (pending != null) setPending(null);
			else if (following) cancelFollowing();
			else exit(minecraft);
			return true;
		}
		boolean down = action != InputConstants.RELEASE;
		if (minecraft.options.keyUp.matches(event)) moveUp = down;
		if (minecraft.options.keyDown.matches(event)) moveDown = down;
		if (minecraft.options.keyLeft.matches(event)) moveLeft = down;
		if (minecraft.options.keyRight.matches(event)) moveRight = down;
		if (ConductorKeyMappings.matchesRotateLeft(event)) rotateLeft = down;
		if (ConductorKeyMappings.matchesRotateRight(event)) rotateRight = down;
		return minecraft.options.keyUp.matches(event) || minecraft.options.keyDown.matches(event)
				|| minecraft.options.keyLeft.matches(event) || minecraft.options.keyRight.matches(event)
				|| ConductorKeyMappings.matchesRotateLeft(event) || ConductorKeyMappings.matchesRotateRight(event);
	}

	public static boolean handleCharTyped(long handle, CharacterEvent event) {
		return active && Minecraft.getInstance().screen == null
				&& ConductorHud.INSTANCE.handleCharTyped(handle, event);
	}

	public static List<UUID> selected() {
		return List.copyOf(SELECTED);
	}

	public static boolean isSelected(UUID member) {
		return SELECTED.contains(member);
	}

	public static List<UUID> controlledSelection() {
		return selected().stream().filter(id -> ConductorClient.unit(id) != null).toList();
	}

	public static boolean isDrag(double startX, double startY, double endX, double endY) {
		return Math.abs(endX - startX) + Math.abs(endY - startY) >= DRAG_THRESHOLD;
	}

	public static void cancelDoubleClick() {
		lastClickedUnit = null;
	}

	public static void clickedUnit(UUID member) {
		ConductorHud.INSTANCE.setFocusedMember(member);
		long now = System.currentTimeMillis();
		if (member.equals(lastClickedUnit) && now - lastUnitClick < DOUBLE_CLICK_MS) {
			followUnit(member, false);
			lastClickedUnit = null;
		} else lastClickedUnit = member;
		lastUnitClick = now;
	}

	public static void followUnit(UUID member, boolean toggle) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(member) instanceof LivingEntity living) || !living.isAlive())
			return;
		SELECTED.add(member);
		ConductorHud.INSTANCE.setFocusedMember(member);
		if (toggle && following && member.equals(followedUnit)) cancelFollowing();
		else {
			followedUnit = member;
			following = true;
			ConductorCamera.focus(living.position());
		}
	}

	private static void cancelFollowing() {
		if (following) ConductorCamera.releaseFollowing();
		following = false;
		followedUnit = null;
	}

	public static void selectMember(UUID member, boolean multiple) {
		if (!multiple) SELECTED.clear();
		if (multiple && SELECTED.contains(member)) SELECTED.remove(member);
		else SELECTED.add(member);
	}

	public static void selectRange(List<UUID> members, UUID anchor, UUID member) {
		int start = members.indexOf(anchor);
		int end = members.indexOf(member);
		if (end < 0) return;
		if (start < 0) start = end;
		SELECTED.addAll(members.subList(Math.min(start, end), Math.max(start, end) + 1));
	}

	public static List<UUID> skillUnits(UUID current) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity source = minecraft.level == null || current == null ? null : minecraft.level.getEntity(current);
		if (source == null)
			return current != null && ConductorClient.unit(current) != null ? List.of(current) : List.of();
		List<UUID> units = selected().stream().filter(member -> {
			Entity entity = minecraft.level.getEntity(member);
			return entity instanceof Mob && entity.getType() == source.getType()
					&& ConductorClient.unit(member) != null;
		}).toList();
		return units;
	}

	public static void removeMember(UUID member) {
		SELECTED.remove(member);
	}

	public static boolean focusMember(UUID member) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(member);
		if (entity == null) return false;
		ConductorCamera.focus(entity.position());
		return true;
	}

	public static void releaseCameraKeys() {
		clearCameraKeys();
	}

	public static boolean dragging() {
		return dragging;
	}

	public static double dragStartX() {
		return dragX;
	}

	public static double dragStartY() {
		return dragY;
	}

	public static ConductorCommandPayload.Action pendingAction() {
		return pending;
	}

	public static void setPending(ConductorCommandPayload.Action action) {
		pending = action;
		pendingSkill = "";
		pendingSkillUnit = null;
	}

	public static boolean isPendingSkill(String skill, UUID unit) {
		return pending == ConductorCommandPayload.Action.CAST && pendingSkill.equals(skill)
				&& unit != null && unit.equals(pendingSkillUnit);
	}

	public static String pendingSkill() {
		return pendingSkill;
	}

	public static UUID pendingSkillUnit() {
		return pendingSkillUnit;
	}

	public static void castFacing() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || pendingSkillUnit == null
				|| !(minecraft.level.getEntity(pendingSkillUnit) instanceof Mob mob)) return;
		ConductorTargeting targeting = ConductorTargetingResolver.find(mob, pendingSkill);
		if (targeting == null || !targeting.directional()) return;
		ConductorData.Unit unit = ConductorClient.unit(pendingSkillUnit);
		if (unit == null) return;
		ConductorClient.sendTarget(ConductorCommandPayload.Action.CAST, unit.team(), "", skillUnits(pendingSkillUnit),
				null, 0, 0, 0, ConductorData.ControlMode.FULL, true, pendingSkill,
				ConductorCommandPayload.TargetSelection.POSITION);
	}

	public static void setPendingSkill(String skill, UUID unit) {
		pending = ConductorCommandPayload.Action.CAST;
		pendingSkill = skill;
		pendingSkillUnit = unit;
	}

	public static void setPendingSkill(String skill) {
		setPendingSkill(skill, SELECTED.isEmpty() ? null : SELECTED.iterator().next());
	}

	public static void selectTeam(String team) {
		SELECTED.clear();
		ConductorClient.snapshot().units().forEach((id, unit) -> {
			if (unit.team().equals(team)) {
				SELECTED.add(UUID.fromString(id));
			}
		});
	}

	public static String selectedTeam() {
		for (UUID uuid : SELECTED) {
			ConductorData.Unit unit = ConductorClient.unit(uuid);
			if (unit != null) {
				return unit.team();
			}
		}
		return "";
	}

	public static void clearSession() {
		exit(Minecraft.getInstance());
		ConductorHud.INSTANCE.setFocusedMember(null);
		ConductorClient.reset();
	}

	public static void onTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
			if (active) exit(minecraft);
			return;
		}
		while (ConductorKeyMappings.consumeToggle()) {
			if (!active && minecraft.screen == null) enter(minecraft);
		}
		if (!active) return;
		if (!ConductorCamera.valid(minecraft)) {
			exit(minecraft);
			return;
		}
		if (following && (!(minecraft.level.getEntity(followedUnit) instanceof LivingEntity target) || !target.isAlive()))
			cancelFollowing();
		if (minecraft.screen != null || !minecraft.isWindowActive()) {
			rightPressed = false;
			rightDragging = false;
			clearCameraKeys();
		}
		if (minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()) minecraft.mouseHandler.releaseMouse();
	}

	public static @Nullable View cameraFrame(float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active) return null;
		if (!ConductorCamera.valid(minecraft)) {
			exit(minecraft);
			return null;
		}
		boolean input = minecraft.screen == null && minecraft.isWindowActive();
		if (input) updateRightDrag(minecraft);
		int forward = input ? (moveUp ? 1 : 0) - (moveDown ? 1 : 0) : 0;
		int sideways = input ? (moveRight ? 1 : 0) - (moveLeft ? 1 : 0) : 0;
		int turning = input && !rightDragging ? (rotateRight ? 1 : 0) - (rotateLeft ? 1 : 0) : 0;
		if (forward != 0 || sideways != 0) cancelFollowing();
		return ConductorCamera.frame(partialTick, forward, sideways, turning, following ? followedUnit : null);
	}

	private static void enter(Minecraft minecraft) {
		active = true;
		clearCameraKeys();
		KeyMapping.releaseAll();
		ConductorHud.INSTANCE.resetLayout();
		cancelFollowing();
		ConductorCamera.open(minecraft);
		minecraft.mouseHandler.releaseMouse();
		ConductorClient.requestSnapshot();
	}

	private static void exit(Minecraft minecraft) {
		active = false;
		cancelFollowing();
		lastClickedUnit = null;
		clearCameraKeys();
		KeyMapping.releaseAll();
		dragging = false;
		rightPressed = false;
		rightDragging = false;
		pending = null;
		pendingSkill = "";
		pendingSkillUnit = null;
		SELECTED.clear();
		ConductorHud.INSTANCE.resetLayout();
		ConductorCamera.close();
		if (minecraft.screen == null) {
			minecraft.mouseHandler.grabMouse();
		}
	}

	private static void clearCameraKeys() {
		moveUp = false;
		moveDown = false;
		moveLeft = false;
		moveRight = false;
		rotateLeft = false;
		rotateRight = false;
	}

	public static void onMovement(MovementInputUpdateEvent event) {
		if (!active) {
			return;
		}
		ClientInput input = event.getInput();
		input.keyPresses = Input.EMPTY;
		((ClientInputAccessor) input).lobecorp$setMoveVector(Vec2.ZERO);
	}

	public static void onScroll(InputEvent.MouseScrollingEvent event) {
		if (active && Minecraft.getInstance().screen == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (ConductorHud.INSTANCE.scroll(minecraft, event.getScrollDeltaY())) {
				event.setCanceled(true);
				return;
			}
			ConductorCamera.zoom(event.getScrollDeltaY());
			event.setCanceled(true);
		}
	}

	public static void onMouse(InputEvent.MouseButton.Pre event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active || minecraft.screen != null) {
			return;
		}
		if (event.getButton() == MOUSE_MIDDLE) {
			if (event.getAction() == InputConstants.PRESS) {
				UUID member = ConductorHud.INSTANCE.memberAtPointer(minecraft);
				if (member == null && !ConductorHud.INSTANCE.contains(minecraft)) {
					LivingEntity living = pickLivingEntity(minecraft);
					if (living != null) member = living.getUUID();
				}
				if (member != null) followUnit(member, true);
			}
			event.setCanceled(true);
		} else if (event.getButton() == MOUSE_LEFT) {
			if (event.getAction() == InputConstants.PRESS) {
				if (ConductorHud.INSTANCE.click(minecraft)) {
					dragging = false;
					event.setCanceled(true);
					return;
				}
				if (pending != null) {
					order(minecraft);
					dragging = false;
					event.setCanceled(true);
					return;
				}
				dragging = true;
				dragX = minecraft.mouseHandler.xpos();
				dragY = minecraft.mouseHandler.ypos();
			} else if (event.getAction() == InputConstants.RELEASE && ConductorHud.INSTANCE.release()) {
				dragging = false;
				event.setCanceled(true);
				return;
			} else if (event.getAction() == InputConstants.RELEASE && dragging) {
				dragging = false;
				double endX = minecraft.mouseHandler.xpos();
				double endY = minecraft.mouseHandler.ypos();
				if (Math.abs(endX - dragX) + Math.abs(endY - dragY) >= DRAG_THRESHOLD) {
					lastClickedUnit = null;
					selectBox(minecraft, dragX, dragY, endX, endY);
				} else {
					LivingEntity entity = pickLivingEntity(minecraft);
					if (entity != null) {
						selectMember(entity.getUUID(), minecraft.hasControlDown());
						if (SELECTED.contains(entity.getUUID())) clickedUnit(entity.getUUID());
						else lastClickedUnit = null;
					} else {
						if (!minecraft.hasControlDown()) SELECTED.clear();
						lastClickedUnit = null;
					}
				}
			}
			event.setCanceled(true);
		} else if (event.getButton() == MOUSE_RIGHT) {
			if (event.getAction() == InputConstants.PRESS && ConductorHud.INSTANCE.rightClick(minecraft)) {
				rightPressed = false;
				event.setCanceled(true);
				return;
			}
			if (event.getAction() == InputConstants.PRESS) {
				if (pending != null) {
					pending = null;
					pendingSkill = "";
					pendingSkillUnit = null;
					rightPressed = false;
				} else if (!ConductorHud.INSTANCE.contains(minecraft)) {
					rightPressed = true;
					rightDragging = false;
					rightDownX = minecraft.mouseHandler.xpos();
					rightDownY = minecraft.mouseHandler.ypos();
					ConductorCamera.beginDrag(rightDownX);
				}
			} else if (event.getAction() == InputConstants.RELEASE && rightPressed) {
				updateRightDrag(minecraft);
				if (!rightDragging && !ConductorHud.INSTANCE.contains(minecraft)) {
					order(minecraft);
				}
				rightPressed = false;
				rightDragging = false;
			}
			event.setCanceled(true);
		} else event.setCanceled(true);
	}

	private static void updateRightDrag(Minecraft minecraft) {
		if (!rightPressed) return;
		if (isDrag(rightDownX, rightDownY, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()))
			rightDragging = true;
		if (rightDragging) ConductorCamera.drag(minecraft.mouseHandler.xpos());
	}

	private static void selectBox(Minecraft minecraft, double x1, double y1, double x2, double y2) {
		Camera camera = minecraft.gameRenderer.getMainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		double minX = Math.min(x1, x2);
		double maxX = Math.max(x1, x2);
		double minY = Math.min(y1, y2);
		double maxY = Math.max(y1, y2);
		SELECTED.clear();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity mob) || !mob.isAlive()) continue;
			Vec3 relative = mob.getBoundingBox().getCenter().subtract(camera.position());
			Vector4f point = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0F).mul(matrix);
			if (point.w <= 0.0F) {
				continue;
			}
			double screenX = (point.x / point.w + 1.0D) * minecraft.getWindow().getWidth() / 2.0D;
			double screenY = (1.0D - point.y / point.w) * minecraft.getWindow().getHeight() / 2.0D;
			if (screenX >= minX && screenX <= maxX && screenY >= minY && screenY <= maxY) {
				SELECTED.add(mob.getUUID());
			}
		}
		String team = ConductorHud.INSTANCE.selectedTeam();
		ConductorDirectory snapshot = ConductorClient.snapshot();
		if (!team.isEmpty() && SELECTED.stream().anyMatch(member -> {
			ConductorData.Unit unit = snapshot.unit(member);
			return unit != null && team.equals(unit.team());
		})) {
			SELECTED.removeIf(member -> {
				ConductorData.Unit unit = snapshot.unit(member);
				return unit == null || !team.equals(unit.team());
			});
		}
	}

	private static LivingEntity pickLivingEntity(Minecraft minecraft) {
		return pickLivingEntity(minecraft, LivingEntity::isAlive);
	}

	private static LivingEntity pickLivingEntity(Minecraft minecraft, Predicate<LivingEntity> predicate) {
		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 start = camera.position();
		Vec3 ray = ConductorCamera.pointerRay(minecraft);
		Vec3 end = start.add(ray.scale(ConductorCamera.pickDistance(minecraft, ray)));
		BlockHitResult block = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, minecraft.player));
		double limit = block.getLocation().distanceToSqr(start);
		LivingEntity picked = null;
		for (Entity candidate : minecraft.level.entitiesForRendering()) {
			if (!(candidate instanceof LivingEntity entity)) continue;
			if (!predicate.test(entity)) {
				continue;
			}
			var hit = entity.getBoundingBox().inflate(0.3D).clip(start, end);
			if (hit.isPresent() && hit.get().distanceToSqr(start) < limit) {
				limit = hit.get().distanceToSqr(start);
				picked = entity;
			}
		}
		return picked;
	}

	protected static LivingEntity hoveredLivingEntity(Minecraft minecraft) {
		return active && minecraft.level != null && minecraft.screen == null
				&& !ConductorHud.INSTANCE.contains(minecraft) ? pickLivingEntity(minecraft) : null;
	}

	public static @Nullable AimPreview aimPreview(Minecraft minecraft) {
		AimPreview fallback = null;
		for (UUID unit : skillUnits(pendingSkillUnit)) {
			AimPreview preview = aimPreview(minecraft, unit);
			if (preview == null) continue;
			if (fallback == null) fallback = preview;
			if (preview.valid() && ConductorClient.abilityCooldownTicks(unit, Identifier.parse(pendingSkill)) == 0)
				return preview;
		}
		return fallback;
	}

	private static @Nullable AimPreview aimPreview(Minecraft minecraft, UUID caster) {
		if (!active || pending != ConductorCommandPayload.Action.CAST || minecraft.player == null
				|| minecraft.level == null
				|| caster == null || !(minecraft.level.getEntity(caster) instanceof Mob mob)) {
			return null;
		}
		ConductorTargeting targeting = ConductorTargetingResolver.find(mob, pendingSkill);
		if (targeting == null || !ConductorClient.hasAbility(mob.getUUID(), Identifier.parse(pendingSkill))) {
			return null;
		}
		LivingEntity target = pickLivingEntity(minecraft, entity -> entity != minecraft.player && entity != mob
				&& targeting.canTarget(mob, entity)
				&& !ConductorClient.snapshot().allied(mob.getUUID(), entity.getUUID()));
		Vec3 position = ConductorCamera.pickGround(minecraft);
		if (position == null && targeting.targetKind() == ConductorTargeting.TargetKind.POSITION) return null;
		if (position == null) position = mob.position();
		ConductorTargeting.TargetKind kind = targeting.targetKind();
		ConductorCommandPayload.TargetSelection selection = switch (kind) {
			case SELF -> ConductorCommandPayload.TargetSelection.NONE;
			case ENTITY -> ConductorCommandPayload.TargetSelection.ENTITY;
			case POSITION -> ConductorCommandPayload.TargetSelection.POSITION;
			case EITHER -> target == null ? ConductorCommandPayload.TargetSelection.POSITION
					: ConductorCommandPayload.TargetSelection.ENTITY;
		};
		boolean valid = targeting.isAvailable(mob)
				&& ConductorClient.abilityCooldownTicks(mob.getUUID(), Identifier.parse(pendingSkill)) == 0;
		boolean rangeLimited = false;
		Vec3 requested = position;
		if (selection == ConductorCommandPayload.TargetSelection.ENTITY) {
			if (target == null || !targeting.canTarget(mob, target)
					|| ConductorClient.snapshot().allied(mob.getUUID(), target.getUUID())) {
				valid = false;
			} else {
				requested = targeting.targetPosition(mob, target, position);
				rangeLimited = targeting.hasRange(mob) && !targeting.isWithinRange(mob, requested);
				if (rangeLimited && kind == ConductorTargeting.TargetKind.EITHER) {
					selection = ConductorCommandPayload.TargetSelection.POSITION;
					target = null;
				} else if (rangeLimited) {
					valid = false;
				}
			}
		}
		if (selection == ConductorCommandPayload.TargetSelection.POSITION) {
			rangeLimited = targeting.hasRange(mob) && !targeting.isWithinRange(mob, requested);
		}
		Vec3 effective = targeting.correctPosition(mob, requested);
		if (selection == ConductorCommandPayload.TargetSelection.POSITION
				&& !targeting.canTargetPosition(mob, effective)) {
			valid = false;
		}
		return new AimPreview(mob, targeting, target, requested, effective, selection, valid, rangeLimited);
	}

	private static void order(Minecraft minecraft) {
		if (SELECTED.isEmpty() || minecraft.player == null || minecraft.level == null) {
			return;
		}
		String team = selectedTeam();
		if (pending == ConductorCommandPayload.Action.CAST && pendingSkillUnit != null) {
			ConductorData.Unit unit = ConductorClient.unit(pendingSkillUnit);
			if (unit != null) team = unit.team();
		}
		if (team.isBlank()) {
			return;
		}
		boolean smartOrder = pending == null;
		ConductorCommandPayload.Action action = pending;
		LivingEntity target;
		if (smartOrder) {
			target = pickCommandTarget(minecraft, selected());
			action = target == null ? ConductorCommandPayload.Action.MOVE : ConductorCommandPayload.Action.ATTACK;
		} else if (action == ConductorCommandPayload.Action.ATTACK) {
			LivingEntity rawTarget = pickLivingEntity(minecraft);
			target = pickCommandTarget(minecraft, selected());
			if (target == null && rawTarget != null) {
				return;
			}
		} else {
			target = null;
		}
		Vec3 pos = ConductorCamera.pickGround(minecraft);
		if (pos == null) {
			if (target == null && action != ConductorCommandPayload.Action.CAST) return;
			pos = target == null ? Vec3.ZERO : target.position();
		}
		ConductorCommandPayload.TargetSelection targetSelection = ConductorCommandPayload.TargetSelection.NONE;
		if (action == ConductorCommandPayload.Action.CAST) {
			AimPreview preview = aimPreview(minecraft);
			if (preview == null || !preview.valid()) {
				return;
			}
			target = preview.target();
			targetSelection = preview.targetSelection();
			pos = preview.requestedPosition();
		}
		if (action == ConductorCommandPayload.Action.ATTACK && target == null) {
			action = ConductorCommandPayload.Action.ATTACK_POINT;
		}
		List<UUID> units = action == ConductorCommandPayload.Action.CAST && pendingSkillUnit != null
				? skillUnits(pendingSkillUnit) : controlledSelection();
		ConductorClient.sendTarget(action, team, "", units,
				(targetSelection == ConductorCommandPayload.TargetSelection.ENTITY
						|| action == ConductorCommandPayload.Action.ATTACK) && target != null
						? target.getUUID() : null,
				pos.x, pos.y, pos.z, ConductorData.ControlMode.FULL, false, pendingSkill, targetSelection);
		if (action != ConductorCommandPayload.Action.CAST) setPending(null);
	}

	private static LivingEntity pickCommandTarget(Minecraft minecraft, List<UUID> units) {
		ConductorDirectory snapshot = ConductorClient.snapshot();
		return pickLivingEntity(minecraft, entity -> entity != minecraft.player
				&& !units.contains(entity.getUUID())
				&& units.stream().anyMatch(uuid -> snapshot.unit(uuid) != null
				&& !snapshot.allied(uuid, entity.getUUID())));
	}

	public static void openPanel() {
		openPanel(ConductorLdlibScreen.Tab.TEAMS);
	}

	public static void openPanel(ConductorLdlibScreen.Tab tab) {
		Minecraft minecraft = Minecraft.getInstance();
		if (active && minecraft.screen == null) {
			minecraft.setScreen(ConductorLdlibScreen.create(tab));
			ConductorClient.requestSnapshot();
		}
	}

	public record AimPreview(Mob caster, ConductorTargeting targeting, @Nullable LivingEntity target,
	                         Vec3 requestedPosition, Vec3 effectivePosition,
	                         ConductorCommandPayload.TargetSelection targetSelection,
	                         boolean valid, boolean rangeLimited) {
	}
}
