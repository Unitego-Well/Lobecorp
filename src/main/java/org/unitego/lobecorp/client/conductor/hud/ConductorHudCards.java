package org.unitego.lobecorp.client.conductor.hud;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.conductor.data.ConductorData;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

/// 指挥家 HUD 的虚拟列表、卡片复用与短时动画状态。
public class ConductorHudCards {
	private final ConductorHudModel model;
	private final ConductorHudLayout layout;
	private final Map<String, Button> cardPool = new LinkedHashMap<>();
	private final ArrayDeque<Button> spareCards = new ArrayDeque<>();
	private final Map<String, List<UUID>> mountedMembers = new HashMap<>();
	private final Map<String, Geometry> mountedGeometry = new HashMap<>();
	private final Map<String, UUID> rosterIds = new HashMap<>();
	private final Map<String, UUID> selectedIds = new HashMap<>();
	private final Map<String, String> skillIds = new HashMap<>();
	private List<Identifier> mountedSkills = List.of();
	private Geometry mountedSkillsGeometry;
	private int selectedColumns = 1;
	private int rosterColumns = -1;

	public ConductorHudCards(ConductorHudModel model, ConductorHudLayout layout) {
		this.model = model;
		this.layout = layout;
	}

	public Map<String, UUID> rosterIds() {
		return rosterIds;
	}

	public Map<String, UUID> selectedIds() {
		return selectedIds;
	}

	public Map<String, String> skillIds() {
		return skillIds;
	}

	public int selectedColumns() {
		return selectedColumns;
	}

	public int rosterColumns() {
		return rosterColumns;
	}

	public Button card(String id) {
		return cardPool.get(id);
	}

	public List<Identifier> mountedSkills() {
		return mountedSkills;
	}

	public void setMountedSkills(List<Identifier> skills) {
		mountedSkills = skills;
		mountedSkillsGeometry = null;
	}

	public void setMountedMembers(String listId, List<UUID> members) {
		mountedMembers.put(listId, members);
		mountedGeometry.remove(listId);
	}

	public void removeMountedMembers(String listId) {
		mountedMembers.remove(listId);
		mountedGeometry.remove(listId);
	}

	public Map<String, List<UUID>> mountedMembers() {
		return mountedMembers;
	}

	public void rebuildRoster() {
		List<UUID> members = ConductorClient.snapshot().units().entrySet().stream()
				.filter(entry -> entry.getValue().team().equals(model.teamSelection()))
				.map(entry -> {
					try {
						return UUID.fromString(entry.getKey());
					} catch (IllegalArgumentException ignored) {
						return null;
					}
				})
				.filter(java.util.Objects::nonNull).filter(this::matchesSearch)
				.sorted(java.util.Comparator.comparing(UUID::toString)).toList();

		if (!members.equals(model.rosterMembers())) {
			model.setRosterMembers(members);
			removeMountedMembers(ConductorHudLayout.ROSTER_LIST);
		}
	}

	private boolean matchesSearch(UUID member) {
		if (model.appliedRosterSearch().isBlank()) {
			return true;
		}

		ConductorData.MemberInfo info = ConductorClient.snapshot().memberInfo(member);
		ConductorData.Unit unit = ConductorClient.unit(member);
		String query = model.appliedRosterSearch().toLowerCase(java.util.Locale.ROOT);
		String name = info == null ? "" : info.name();
		String type = info == null ? "" : info.type();
		String dimension = unit == null ? "" : unit.dimension();
		return member.toString().toLowerCase(java.util.Locale.ROOT).contains(query)
				|| name.toLowerCase(java.util.Locale.ROOT).contains(query)
				|| type.toLowerCase(java.util.Locale.ROOT).contains(query)
				|| dimension.toLowerCase(java.util.Locale.ROOT).contains(query);
	}

	public void rebuildSelection() {
		removeMountedMembers(ConductorHudLayout.SELECTED_LIST);
	}

	public void rebuildSkills() {
		mountedSkills = List.of();
		mountedSkillsGeometry = null;
		skillIds.clear();
		layout.scroller(ConductorHudLayout.SKILL_LIST).clearAllScrollViewChildren();
	}

	public void mountCards(String listId, List<UUID> members, Map<String, UUID> ids, boolean horizontal) {
		ScrollerView list = layout.scroller(listId);
		int contentWidth = (int) list.viewPort.getContentWidth();
		int contentHeight = (int) list.viewPort.getContentHeight();
		int cardHeight = horizontal ? CARD_SIZE : ROSTER_ROW_HEIGHT;
		int cardWidth = horizontal ? CARD_SIZE : Math.max(1, contentWidth);
		int step = cardHeight + CARD_GAP;
		int columns = horizontal ? Math.max(1, (contentWidth + CARD_GAP) / step) : 1;
		int count = (members.size() + columns - 1) / columns;
		int extent = Math.max(0, count * step - CARD_GAP);
		int viewport = Math.max(0, contentHeight);
		float normalized = list.verticalScroller.getNormalizedValue();
		int first = Math.max(0, (int) (normalized * Math.max(0, extent - viewport)) / step - BUFFER_ROWS);
		int last = Math.min(count, first + (viewport + step - 1) / step + BUFFER_ROWS * 2);
		List<UUID> visible = members.subList(Math.min(members.size(), first * columns), Math.min(members.size(), last * columns));
		Geometry geometry = new Geometry(contentWidth, contentHeight, columns, extent);

		if (visible.equals(mountedMembers.get(listId)) && geometry.equals(mountedGeometry.get(listId))) {
			return;
		}

		if (horizontal) {
			selectedColumns = columns;
		} else {
			rosterColumns = columns;
		}
		mountedMembers.put(listId, List.copyOf(visible));
		mountedGeometry.put(listId, geometry);
		list.clearAllScrollViewChildren();
		ids.clear();
		list.getScrollerViewStyle().mode(ScrollerMode.VERTICAL)
				.horizontalScrollDisplay(ScrollDisplay.NEVER).verticalScrollDisplay(ScrollDisplay.AUTO);
		var spacer = new UIElement();
		spacer.setAllowHitTest(false);
		spacer.layout(layout -> layout.widthPercent(100).height(extent));
		list.addScrollViewChild(spacer);

		for (int index = first * columns; index < Math.min(members.size(), last * columns); index++) {
			UUID member = members.get(index);
			String id = (horizontal ? ConductorHudLayout.SELECTED_PREFIX : ConductorHudLayout.UNIT_PREFIX) + member;
			Button card = cardPool.computeIfAbsent(id, key -> acquireCard(key, cardWidth, cardHeight));
			int x = horizontal ? index % columns * step : 0;
			int y = index / columns * step;
			card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(cardWidth).height(cardHeight));
			list.addScrollViewChild(card);
			ids.put(id, member);
		}
	}

	public void mountSkills(List<Identifier> abilities) {
		ScrollerView list = layout.scroller(ConductorHudLayout.SKILL_LIST);
		int contentWidth = (int) list.viewPort.getContentWidth();
		int contentHeight = (int) list.viewPort.getContentHeight();
		int step = SKILL_SIZE + BUTTON_GAP;
		int viewport = Math.max(0, contentHeight);
		int extent = Math.max(0, abilities.size() * step - BUTTON_GAP);
		int first = Math.max(0, (int) (list.verticalScroller.getNormalizedValue() * Math.max(0, extent - viewport)) / step - BUFFER_ROWS);
		int last = Math.min(abilities.size(), first + (viewport + step - 1) / step + BUFFER_ROWS * 2);
		first = Math.min(first, abilities.size());
		List<Identifier> visible = abilities.subList(first, Math.max(first, last));
		Geometry geometry = new Geometry(contentWidth, contentHeight, 1, extent);

		if (visible.equals(mountedSkills) && geometry.equals(mountedSkillsGeometry)) {
			return;
		}

		mountedSkills = List.copyOf(visible);
		mountedSkillsGeometry = geometry;
		skillIds.clear();
		list.clearAllScrollViewChildren();
		list.getScrollerViewStyle().mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER);
		var spacer = new UIElement();
		spacer.setAllowHitTest(false);
		spacer.layout(layout -> layout.widthPercent(100).height(extent));
		list.addScrollViewChild(spacer);

		for (int index = first; index < last; index++) {
			Identifier ability = abilities.get(index);
			String id = ConductorHudLayout.SKILL_PREFIX + ability;
			Button card = cardPool.computeIfAbsent(id, key -> acquireCard(key, 0, SKILL_SIZE));
			int y = index * step;
			card.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(0).top(y).widthPercent(100).height(SKILL_SIZE));
			list.addScrollViewChild(card);
			skillIds.put(id, ability.toString());
		}
	}

	public void recycleCards(List<UUID> selected) {
		var iterator = cardPool.entrySet().iterator();

		while (iterator.hasNext()) {
			var entry = iterator.next();

			if (!rosterIds.containsKey(entry.getKey()) && !selectedIds.containsKey(entry.getKey()) && !skillIds.containsKey(entry.getKey())) {
				if (spareCards.size() < rosterIds.size() + selectedIds.size() + skillIds.size()) {
					spareCards.add(entry.getValue());
				}
				iterator.remove();
			}
		}

	}

	private Button acquireCard(String id, int width, int height) {
		Button button = spareCards.pollFirst();

		if (button == null) {
			return ConductorHudLayout.portraitButton(id, width, height);
		}

		button.setId(id);
		button.layout(layout -> layout.width(width).height(height));
		return button;
	}

	public int countVisibleCards(boolean rosterHidden) {
		Set<UUID> visible = new HashSet<>();

		for (String listId : List.of(ConductorHudLayout.ROSTER_LIST, ConductorHudLayout.SELECTED_LIST)) {
			if (ConductorHudLayout.ROSTER_LIST.equals(listId) && rosterHidden) {
				continue;
			}

			UIElement viewport = layout.scroller(listId).viewPort;
			Map<String, UUID> ids = ConductorHudLayout.ROSTER_LIST.equals(listId) ? rosterIds : selectedIds;

			for (var entry : ids.entrySet()) {
				Button card = cardPool.get(entry.getKey());

				if (card != null && card.getPositionX() < viewport.getPositionX() + viewport.getSizeWidth()
						&& card.getPositionX() + card.getSizeWidth() > viewport.getPositionX()
						&& card.getPositionY() < viewport.getPositionY() + viewport.getSizeHeight()
						&& card.getPositionY() + card.getSizeHeight() > viewport.getPositionY()) {
					visible.add(entry.getValue());
				}
			}
		}

		return visible.size();
	}

	/// 一次挂载所用的实际视口与步进范围；任一变化都必须重建卡片与滚动占位。
	private record Geometry(int contentWidth, int contentHeight, int columns, int extent) {
	}

	/// 绘制已采样的生命值；读取快照，不推进插值。
	public void healthBar(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, int width,
	                      float ratio, float shown) {
		int color = ratio > HEALTH_HIGH ? HP_GREEN : ratio >= HEALTH_LOW ? HP_YELLOW : HP_RED;
		int light = ratio > HEALTH_HIGH ? HP_GREEN_HI : ratio >= HEALTH_LOW ? HP_YELLOW_HI : HP_RED_HI;
		graphics.fill(x, y, x + width, y + HEALTH_HEIGHT, LINE_OUTER);
		int fill = Math.round(width * shown);

		if (fill > 0) {
			graphics.fill(x, y, x + fill, y + HEALTH_HEIGHT, color);
			graphics.fill(x, y, x + fill, y + 1, light);
		}
	}

	/// 清空虚拟列表与动画状态；偏好由 Model 保留。
	public void clearTransient() {
		cardPool.clear();
		spareCards.clear();
		mountedSkills = List.of();
		mountedSkillsGeometry = null;
		mountedMembers.clear();
		mountedGeometry.clear();
		rosterIds.clear();
		selectedIds.clear();
		skillIds.clear();
		selectedColumns = 1;
		rosterColumns = -1;
	}
}
