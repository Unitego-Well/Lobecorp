package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/// 表单重建间的临时折叠和滚动状态；沿用原生分组路径与滚动条，不写入动画资源。
public class PhotonAnimationFormState {
	private final Map<List<GroupKey>, Boolean> groups = new HashMap<>();
	private final Map<List<GroupKey>, GroupIdentity> selectedItems = new HashMap<>();
	private float horizontal;
	private float vertical;

	public record GroupIdentity(UUID model, String role) {
	}

	private record GroupKey(GroupIdentity identity, Component label, int occurrence) {
	}

	public static PhotonAnimationFormState capture(UIElement root, ScrollerView scroller,
	                                               Map<ConfiguratorGroup, GroupIdentity> identities) {
		var state = new PhotonAnimationFormState();
		walk(root, List.of(), new HashMap<>(), identities, (path, group) -> {
			if (group.isCanCollapse()) {
				state.groups.put(path, group.isCollapse());
			}

			if (group instanceof ArrayConfiguratorGroup<?> array && array.getSelected() != null
					&& array.getSelected().inner instanceof ConfiguratorGroup inner) {
				var identity = identities.get(inner);

				if (identity != null) {
					state.selectedItems.put(path, identity);
				}
			}
		});
		state.horizontal = scroller.horizontalScroller.getValue();
		state.vertical = scroller.verticalScroller.getValue();
		return state;
	}

	public void restore(UIElement root, ScrollerView scroller, Map<ConfiguratorGroup, GroupIdentity> identities) {
		walk(root, List.of(), new HashMap<>(), identities, (path, group) -> {
			var collapsed = groups.get(path);

			if (collapsed != null) {
				group.setCollapse(collapsed);
			}

			if (group instanceof ArrayConfiguratorGroup<?> array) {
				restoreSelection(array, selectedItems.get(path), identities);
			}
		});
		scroller.horizontalScroller.setValue(horizontal);
		scroller.verticalScroller.setValue(vertical);
	}

	@SuppressWarnings("unchecked") // 条目来自同一个原生数组，只恢复匹配模型身份的行。
	private static <T> void restoreSelection(ArrayConfiguratorGroup<T> array, GroupIdentity selected,
	                                         Map<ConfiguratorGroup, GroupIdentity> identities) {
		if (selected == null) {
			array.setSelected(null);
			return;
		}

		for (var configurator : array.getConfigurators()) {
			if (configurator instanceof ArrayConfiguratorGroup<?>.ItemConfigurator item
					&& item.inner instanceof ConfiguratorGroup inner && selected.equals(identities.get(inner))) {
				array.setSelected((ArrayConfiguratorGroup<T>.ItemConfigurator) item);
				return;
			}
		}

		array.setSelected(null);
	}

	private static void walk(UIElement element, List<GroupKey> path, Map<Component, Integer> occurrences,
	                         Map<ConfiguratorGroup, GroupIdentity> identities,
	                         BiConsumer<List<GroupKey>, ConfiguratorGroup> action) {
		for (var child : element.getChildren()) {
			if (!(child instanceof ConfiguratorGroup group)) {
				walk(child, path, occurrences, identities, action);
				continue;
			}

			var identity = identities.get(group);
			var label = identity == null ? group.getLabel().copy() : null;
			var occurrence = identity == null ? occurrences.getOrDefault(label, 0) : 0;

			if (identity == null) {
				occurrences.put(label, occurrence + 1);
			}

			var next = new ArrayList<>(path);
			next.add(new GroupKey(identity, label, occurrence));
			var key = List.copyOf(next);
			action.accept(key, group);

			walk(group, key, new HashMap<>(), identities, action);
		}
	}
}
