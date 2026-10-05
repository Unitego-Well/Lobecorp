package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.ability.ConductorTargeting;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.network.ts.ConductorCommandPayload;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ConductorScreen extends Screen {
	private static final int PANEL_WIDTH = 500;
	private static final int PANEL_HEIGHT = 394;
	private static final int COLUMN_WIDTH = 230;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_SPACING = 23;
	private static final int TEAM_PAGE_SIZE = 6;
	private static final int SKILL_PAGE_SIZE = 4;
	private static final int PANEL_COLOR = 0xDD101820;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int LEFT_INSET = 12;
	private static final int RIGHT_INSET = 258;
	private static final int FIRST_ROW = 32;
	private static final int TEAM_LIST_ROW = 84;
	private static final int ACTION_ROW = 286;

	private String team = "";
	private String nameText = "";
	private String enemyText = "";
	private String xText = "";
	private String yText = "";
	private String zText = "";
	private String targetText = "";
	private String skillText = "";
	private int teamPage;
	private int skillPage;
	private int revision;
	private EditBox nameInput;
	private EditBox enemyInput;
	private EditBox xInput;
	private EditBox yInput;
	private EditBox zInput;
	private EditBox targetInput;
	private EditBox skillInput;

	public ConductorScreen() {
		super(Component.translatable(ConductorTexts.TITLE));
	}

	@Override
	protected void init() {
		revision = ConductorClient.revision();
		if (team.isBlank() && !ConductorClient.snapshot().teams().isEmpty()) {
			team = ConductorClient.snapshot().teams().keySet().stream().sorted().findFirst().orElse("");
		}
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		nameInput = input(left + LEFT_INSET, top + FIRST_ROW, COLUMN_WIDTH - 72, ConductorTexts.TEAM, nameText);
		button(left + LEFT_INSET + COLUMN_WIDTH - 68, top + FIRST_ROW, 68, ConductorTexts.CREATE,
				() -> {
					String name = nameInput.getValue().trim();
					if (!name.isEmpty()) {
						team = name;
						send(ConductorCommandPayload.Action.CREATE_TEAM, name, "", null,
								0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, "");
					}
				});
		List<String> teams = ConductorClient.snapshot().teams().keySet().stream().sorted().toList();
		for (int index = 0; index < TEAM_PAGE_SIZE; index++) {
			int teamIndex = teamPage * TEAM_PAGE_SIZE + index;
			if (teamIndex >= teams.size()) {
				break;
			}
			String candidate = teams.get(teamIndex);
			button(left + LEFT_INSET, top + TEAM_LIST_ROW + index * ROW_SPACING,
					COLUMN_WIDTH, Component.literal(candidate), () -> {
						team = candidate;
						ConductorControls.selectTeam(candidate);
						rebuildWidgets();
					});
		}
		button(left + LEFT_INSET, top + TEAM_LIST_ROW + TEAM_PAGE_SIZE * ROW_SPACING,
				COLUMN_WIDTH / 2, ConductorTexts.PREVIOUS, () -> {
					teamPage = Math.max(0, teamPage - 1);
					rebuildWidgets();
				});
		button(left + LEFT_INSET + COLUMN_WIDTH / 2, top + TEAM_LIST_ROW + TEAM_PAGE_SIZE * ROW_SPACING,
				COLUMN_WIDTH / 2, ConductorTexts.NEXT, () -> {
					if ((teamPage + 1) * TEAM_PAGE_SIZE < teams.size()) {
						teamPage++;
						rebuildWidgets();
					}
				});
		button(left + LEFT_INSET, top + ACTION_ROW, COLUMN_WIDTH / 2, ConductorTexts.ASSIGN,
				() -> send(ConductorCommandPayload.Action.ASSIGN, team, "", null,
						0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, ""));
		button(left + LEFT_INSET + COLUMN_WIDTH / 2, top + ACTION_ROW, COLUMN_WIDTH / 2, ConductorTexts.RELEASE,
				() -> send(ConductorCommandPayload.Action.RELEASE, team, "", null,
						0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, ""));
		button(left + LEFT_INSET, top + ACTION_ROW + ROW_SPACING, COLUMN_WIDTH / 2, ConductorTexts.MOVE,
				() -> prepare(ConductorCommandPayload.Action.MOVE));
		button(left + LEFT_INSET + COLUMN_WIDTH / 2, top + ACTION_ROW + ROW_SPACING,
				COLUMN_WIDTH / 2, ConductorTexts.ATTACK,
				() -> prepare(ConductorCommandPayload.Action.ATTACK));
		button(left + LEFT_INSET, top + ACTION_ROW + ROW_SPACING * 2,
				COLUMN_WIDTH / 3, ConductorTexts.BEHAVIOR_ACTIVE, () -> setBehavior(ConductorData.CombatBehavior.ACTIVE));
		button(left + LEFT_INSET + COLUMN_WIDTH / 3, top + ACTION_ROW + ROW_SPACING * 2,
				COLUMN_WIDTH / 3, ConductorTexts.BEHAVIOR_PASSIVE, () -> setBehavior(ConductorData.CombatBehavior.PASSIVE));
		button(left + LEFT_INSET + COLUMN_WIDTH * 2 / 3, top + ACTION_ROW + ROW_SPACING * 2,
				COLUMN_WIDTH / 3, ConductorTexts.BEHAVIOR_NEUTRAL, () -> setBehavior(ConductorData.CombatBehavior.NEUTRAL));

		button(left + LEFT_INSET, top + ACTION_ROW + ROW_SPACING * 3,
				COLUMN_WIDTH / 2, ConductorTexts.COMMAND, () -> setMode(ConductorData.ControlMode.COMMAND));
		button(left + LEFT_INSET + COLUMN_WIDTH / 2, top + ACTION_ROW + ROW_SPACING * 3,
				COLUMN_WIDTH / 2, ConductorTexts.SOFT, () -> setMode(ConductorData.ControlMode.SOFT));

		int right = left + RIGHT_INSET;
		enemyInput = input(right, top + FIRST_ROW, COLUMN_WIDTH, ConductorTexts.ENEMY_TEAM, enemyText);
		button(right, top + FIRST_ROW + ROW_SPACING, COLUMN_WIDTH / 2, ConductorTexts.WAR,
				() -> hostility(true));
		button(right + COLUMN_WIDTH / 2, top + FIRST_ROW + ROW_SPACING,
				COLUMN_WIDTH / 2, ConductorTexts.PEACE, () -> hostility(false));
		ConductorData.Team selectedTeam = ConductorClient.snapshot().team(team);
		button(right, top + FIRST_ROW + ROW_SPACING * 2, COLUMN_WIDTH,
				selectedTeam != null && selectedTeam.forceLoad()
						? ConductorTexts.LOAD_ON : ConductorTexts.LOAD_OFF,
				() -> send(ConductorCommandPayload.Action.FORCE_LOAD, team, "", null,
						0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL,
						selectedTeam == null || !selectedTeam.forceLoad(), ""));
		int coordinateRow = top + FIRST_ROW + ROW_SPACING * 3;
		xInput = input(right, coordinateRow, COLUMN_WIDTH / 3, ConductorTexts.X, xText);
		yInput = input(right + COLUMN_WIDTH / 3, coordinateRow, COLUMN_WIDTH / 3, ConductorTexts.Y, yText);
		zInput = input(right + COLUMN_WIDTH * 2 / 3, coordinateRow, COLUMN_WIDTH / 3, ConductorTexts.Z, zText);
		button(right, coordinateRow + ROW_SPACING, COLUMN_WIDTH, ConductorTexts.REMOTE_MOVE, this::remoteMove);

		List<ConductorAbility> skills = selectedMobSkills();
		for (int index = 0; index < SKILL_PAGE_SIZE; index++) {
			int skillIndex = skillPage * SKILL_PAGE_SIZE + index;
			if (skillIndex >= skills.size()) {
				break;
			}
			ConductorAbility skill = skills.get(skillIndex);
			button(right, coordinateRow + ROW_SPACING * (index + 2), COLUMN_WIDTH,
					Component.literal(skill.id().toString()), () -> {
						if (skill.targetKind() != ConductorTargeting.TargetKind.SELF) {
							ConductorControls.setPendingSkill(skill.id().toString());
						} else if (!ConductorControls.selected().isEmpty()) {
							ConductorData.Unit unit = ConductorClient.unit(ConductorControls.selected().getFirst());
							if (unit != null) {
								ConductorClient.send(ConductorCommandPayload.Action.CAST, unit.team(), "",
										List.of(ConductorControls.selected().getFirst()), null,
										0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, skill.id().toString());
							}
						}
						onClose();
					});
		}
		targetInput = input(right, top + 240, COLUMN_WIDTH, ConductorTexts.TARGET_UUID, targetText);
		button(right, top + 263, COLUMN_WIDTH, ConductorTexts.REMOTE_ATTACK, this::remoteAttack);
		skillInput = input(right, top + 286, COLUMN_WIDTH, ConductorTexts.SKILL_ID, skillText);
		button(right, top + 309, COLUMN_WIDTH, ConductorTexts.REMOTE_CAST, this::remoteCast);
		button(right, top + ACTION_ROW + ROW_SPACING * 2, COLUMN_WIDTH / 2,
				ConductorTexts.PREVIOUS, () -> {
					skillPage = Math.max(0, skillPage - 1);
					rebuildWidgets();
				});
		button(right + COLUMN_WIDTH / 2, top + ACTION_ROW + ROW_SPACING * 2,
				COLUMN_WIDTH / 2, ConductorTexts.NEXT, () -> {
					if ((skillPage + 1) * SKILL_PAGE_SIZE < skills.size()) {
						skillPage++;
						rebuildWidgets();
					}
				});
		button(right, top + ACTION_ROW + ROW_SPACING * 3, COLUMN_WIDTH, ConductorTexts.CLOSE, this::onClose);
	}

	private EditBox input(int x, int y, int width, String key, String value) {
		EditBox box = new EditBox(font, x, y, width, BUTTON_HEIGHT, Component.translatable(key));
		box.setHint(Component.translatable(key));
		box.setValue(value);
		addRenderableWidget(box);
		return box;
	}

	private void button(int x, int y, int width, String key, Runnable action) {
		button(x, y, width, Component.translatable(key), action);
	}

	private void button(int x, int y, int width, Component label, Runnable action) {
		addRenderableWidget(Button.builder(label, ignored -> action.run())
				.bounds(x, y, width, BUTTON_HEIGHT).build());
	}

	private void send(ConductorCommandPayload.Action action, String teamName, String other,
	                  UUID target, double x, double y, double z,
	                  ConductorData.ControlMode mode, boolean flag, String skill) {
		ConductorClient.send(action, teamName, other, ConductorControls.selected(),
				target, x, y, z, mode, flag, skill);
	}

	private void setBehavior(ConductorData.CombatBehavior behavior) {
		ConductorClient.setCombatBehavior(team, ConductorControls.selected(), behavior);
	}

	private void setMode(ConductorData.ControlMode mode) {
		send(ConductorCommandPayload.Action.SET_MODE, team, "", null,
				0.0D, 0.0D, 0.0D, mode, false, "");
	}

	private void hostility(boolean hostile) {
		send(ConductorCommandPayload.Action.HOSTILITY, team, enemyInput.getValue().trim(), null,
				0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, hostile, "");
	}

	private void remoteMove() {
		try {
			double x = Double.parseDouble(xInput.getValue());
			double y = Double.parseDouble(yInput.getValue());
			double z = Double.parseDouble(zInput.getValue());
			send(ConductorCommandPayload.Action.MOVE, team, "", null,
					x, y, z, ConductorData.ControlMode.FULL, false, "");
			onClose();
		} catch (NumberFormatException ignored) {
		}
	}

	private void remoteAttack() {
		try {
			UUID target = UUID.fromString(targetInput.getValue().trim());
			send(ConductorCommandPayload.Action.ATTACK, team, "", target,
					0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, "");
			onClose();
		} catch (IllegalArgumentException ignored) {
		}
	}

	private void remoteCast() {
		String skill = skillInput.getValue().trim();
		if (skill.isEmpty()) {
			return;
		}
		UUID target;
		try {
			target = targetInput.getValue().isBlank() ? null : UUID.fromString(targetInput.getValue().trim());
		} catch (IllegalArgumentException ignored) {
			return;
		}
		send(ConductorCommandPayload.Action.CAST, team, "", target,
				0.0D, 0.0D, 0.0D, ConductorData.ControlMode.FULL, false, skill);
		onClose();
	}

	private void prepare(ConductorCommandPayload.Action action) {
		ConductorControls.setPending(action);
		onClose();
	}

	private List<ConductorAbility> selectedMobSkills() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || ConductorControls.selected().isEmpty()) {
			return List.of();
		}
		Entity entity = minecraft.level.getEntity(ConductorControls.selected().getFirst());
		if (!(entity instanceof Mob mob)) {
			return List.of();
		}
		ConductorClient.requestAbilitiesIfMissing(mob.getUUID());
		return ConductorUtil.abilities(mob).stream()
				.filter(ability -> ConductorClient.hasAbility(mob.getUUID(), ability.id()))
				.sorted(Comparator.comparing(skill -> skill.id().toString())).toList();
	}

	@Override
	public void tick() {
		if (ConductorClient.revision() != revision) {
			saveInputs();
			rebuildWidgets();
		}
	}

	private void saveInputs() {
		if (nameInput != null)
			nameText = nameInput.getValue();
		if (enemyInput != null)
			enemyText = enemyInput.getValue();
		if (xInput != null)
			xText = xInput.getValue();
		if (yInput != null)
			yText = yInput.getValue();
		if (zInput != null)
			zText = zInput.getValue();
		if (targetInput != null)
			targetText = targetInput.getValue();
		if (skillInput != null)
			skillText = skillInput.getValue();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = (width - PANEL_WIDTH) / 2;
		int top = (height - PANEL_HEIGHT) / 2;
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);
		graphics.text(font, getTitle(), left + LEFT_INSET, top + LEFT_INSET, TEXT_COLOR);
		graphics.text(font, Component.translatable(ConductorTexts.SELECTED, ConductorControls.selected().size()),
				left + RIGHT_INSET, top + LEFT_INSET, TEXT_COLOR);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
