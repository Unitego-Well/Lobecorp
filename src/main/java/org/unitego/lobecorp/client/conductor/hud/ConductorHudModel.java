package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 指挥家 HUD 的持久显示状态；不持有 UI、图形上下文或宿主引用。
public class ConductorHudModel {
	private final ConductorHudPreferences preferences;
	private final Map<String, Component> hints = new HashMap<>();
	private @Nullable UUID focused;
	private String teamSelection = "";
	private String previousTeamSelection = "";
	private int teamNamesRevision = -1;
	private List<String> teamNames = List.of();
	private List<UUID> rosterMembers = List.of();
	private List<Identifier> previousAbilities = List.of();
	private @Nullable List<UUID> previousSelection;
	private @Nullable UUID previousFocus;
	private int previousRevision = -1;
	private long teamMemberCount;
	private boolean rosterCollapsed;
	private boolean skillsCollapsed;
	private boolean preferSkillsOnNarrow;
	private boolean teamDropdown;
	private String modeDropdown = "";
	private int modeCursor;
	private int teamCursor;
	private int teamScroll;
	private String rosterSearch = "";
	private String skillSearch = "";
	private long searchChangedAt;
	private @Nullable UUID clickedMember;
	private double clickX;
	private double clickY;
	private @Nullable UUID selectionAnchor;
	private String appliedRosterSearch = "";
	private String appliedSkillSearch = "";
	private String previousSearch = "";

	public ConductorHudModel(ConductorHudPreferences preferences) {
		this.preferences = preferences;
		this.rosterCollapsed = preferences.rosterCollapsed;
		this.skillsCollapsed = preferences.skillsCollapsed;
	}

	public ConductorHudPreferences preferences() {
		return preferences;
	}

	public @Nullable UUID focused() {
		return focused;
	}

	public void setFocused(@Nullable UUID member) {
		focused = member;
	}

	public String teamSelection() {
		return teamSelection;
	}

	public void selectTeam(String team) {
		teamSelection = team;
	}

	public Map<String, Component> hints() {
		return hints;
	}

	public String appliedRosterSearch() {
		return appliedRosterSearch;
	}

	public void setAppliedRosterSearch(String query) {
		appliedRosterSearch = query;
	}

	public String appliedSkillSearch() {
		return appliedSkillSearch;
	}

	public void setAppliedSkillSearch(String query) {
		appliedSkillSearch = query;
	}

	public String previousSearch() {
		return previousSearch;
	}

	public void setPreviousSearch(String query) {
		previousSearch = query;
	}

	public List<String> teams(int revision, java.util.function.Supplier<java.util.Set<String>> names) {
		if (teamNamesRevision != revision) {
			teamNames = names.get().stream().sorted().toList();
			teamNamesRevision = revision;
		}

		return teamNames;
	}

	public List<UUID> rosterMembers() {
		return rosterMembers;
	}

	public void setRosterMembers(List<UUID> members) {
		rosterMembers = members;
	}

	public List<Identifier> previousAbilities() {
		return previousAbilities;
	}

	public void setPreviousAbilities(List<Identifier> abilities) {
		previousAbilities = abilities;
	}

	public @Nullable List<UUID> previousSelection() {
		return previousSelection;
	}

	public void setPreviousSelection(@Nullable List<UUID> selection) {
		previousSelection = selection;
	}

	public @Nullable UUID previousFocus() {
		return previousFocus;
	}

	public void setPreviousFocus(@Nullable UUID member) {
		previousFocus = member;
	}

	public int previousRevision() {
		return previousRevision;
	}

	public void setPreviousRevision(int revision) {
		previousRevision = revision;
	}

	public String previousTeamSelection() {
		return previousTeamSelection;
	}

	public void setPreviousTeamSelection(String team) {
		previousTeamSelection = team;
	}

	public long teamMemberCount() {
		return teamMemberCount;
	}

	public void setTeamMemberCount(long count) {
		teamMemberCount = count;
	}

	public boolean rosterCollapsed() {
		return rosterCollapsed;
	}

	public void setRosterCollapsed(boolean collapsed) {
		rosterCollapsed = collapsed;
	}

	public boolean skillsCollapsed() {
		return skillsCollapsed;
	}

	public void setSkillsCollapsed(boolean collapsed) {
		skillsCollapsed = collapsed;
	}

	public boolean preferSkillsOnNarrow() {
		return preferSkillsOnNarrow;
	}

	public void setPreferSkillsOnNarrow(boolean prefer) {
		preferSkillsOnNarrow = prefer;
	}

	public boolean teamDropdown() {
		return teamDropdown;
	}

	public void setTeamDropdown(boolean open) {
		teamDropdown = open;
	}

	public String modeDropdown() {
		return modeDropdown;
	}

	public void setModeDropdown(String id) {
		modeDropdown = id;
	}

	public int modeCursor() {
		return modeCursor;
	}

	public void setModeCursor(int cursor) {
		modeCursor = cursor;
	}

	public int teamCursor() {
		return teamCursor;
	}

	public void setTeamCursor(int cursor) {
		teamCursor = cursor;
	}

	public int teamScroll() {
		return teamScroll;
	}

	public void setTeamScroll(int scroll) {
		teamScroll = scroll;
	}

	public String rosterSearch() {
		return rosterSearch;
	}

	public void setRosterSearch(String query) {
		rosterSearch = query;
	}

	public String skillSearch() {
		return skillSearch;
	}

	public void setSkillSearch(String query) {
		skillSearch = query;
	}

	public long searchChangedAt() {
		return searchChangedAt;
	}

	public void setSearchChangedAt(long time) {
		searchChangedAt = time;
	}

	public @Nullable UUID clickedMember() {
		return clickedMember;
	}

	public void setClickedMember(@Nullable UUID member) {
		clickedMember = member;
	}

	public double clickX() {
		return clickX;
	}

	public double clickY() {
		return clickY;
	}

	public void setClickPosition(double x, double y) {
		clickX = x;
		clickY = y;
	}

	public @Nullable UUID selectionAnchor() {
		return selectionAnchor;
	}

	public void setSelectionAnchor(@Nullable UUID member) {
		selectionAnchor = member;
	}

	public ConductorData.FormationMode formationMode() {
		return preferences.formationMode;
	}

	public boolean reducedMotion() {
		return preferences.reduceMotion;
	}

	public void toggleReducedMotion() {
		preferences.reduceMotion = !preferences.reduceMotion;
		preferences.save();
	}

	/// 清除会话内的显示状态，保留用户偏好与折叠设置。
	public void clearTransient() {
		hints.clear();
		focused = null;
		teamSelection = "";
		previousTeamSelection = "";
		teamNamesRevision = -1;
		teamNames = List.of();
		rosterMembers = List.of();
		previousAbilities = List.of();
		previousSelection = null;
		previousFocus = null;
		previousRevision = -1;
		teamMemberCount = 0;
		preferSkillsOnNarrow = false;
		appliedRosterSearch = "";
		appliedSkillSearch = "";
		previousSearch = "";
		teamDropdown = false;
		modeDropdown = "";
		modeCursor = 0;
		teamCursor = 0;
		teamScroll = 0;
		rosterSearch = "";
		skillSearch = "";
		searchChangedAt = 0L;
		clickedMember = null;
		clickX = 0;
		clickY = 0;
		selectionAnchor = null;
	}
}
