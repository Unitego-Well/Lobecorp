package org.unitego.lobecorp.client.conductor.hud;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConductorHudModelTest {
	@Test
	public void transientClearKeepsPreferencesAndDropsDisplayState() {
		var preferences = new ConductorHudPreferences();
		preferences.rosterCollapsed = true;
		preferences.skillsCollapsed = false;
		preferences.reduceMotion = true;
		preferences.formationMode = ConductorData.FormationMode.UNIFORM;
		var model = new ConductorHudModel(preferences);
		var member = UUID.randomUUID();
		model.setFocused(member);
		model.selectTeam("red");
		model.setRosterMembers(List.of(member));
		model.setRosterSearch("query");
		model.setTeamDropdown(true);

		model.clearTransient();

		assertNull(model.focused());
		assertEquals("", model.teamSelection());
		assertTrue(model.rosterMembers().isEmpty());
		assertEquals("", model.rosterSearch());
		assertFalse(model.teamDropdown());
		assertTrue(model.reducedMotion());
		assertEquals(ConductorData.FormationMode.UNIFORM, model.formationMode());
	}

	@Test
	public void teamNamesCacheFollowsRevisionOnly() {
		var model = new ConductorHudModel(new ConductorHudPreferences());
		var first = model.teams(1, () -> Set.of("b", "a"));
		var same = model.teams(1, () -> Set.of("c"));
		var next = model.teams(2, () -> Set.of("d", "c"));

		assertEquals(List.of("a", "b"), first);
		assertEquals(first, same);
		assertEquals(List.of("c", "d"), next);
	}
}
