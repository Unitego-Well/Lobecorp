package org.unitego.lobecorp.util.photon.clipboard;

import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.UUIDUtil;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class PhotonObjectClipboardUtil {
	/**
	 * 系统剪贴板中完整粒子分支的内容类型。
	 */
	public static final String OBJECTS = "objects";
	/**
	 * 复制后的默认名称后缀，重名时再追加序号。
	 */
	private static final String COPY_SUFFIX = "_copy";
	/**
	 * 复制分支和配置的最大嵌套层数。
	 */
	private static final int MAX_DEPTH = 64;

	public static void copy(FXHierarchyView view) {
		try {
			var runtime = view.getRuntime();
			if (runtime == null)
				throw new IllegalArgumentException();
			var selected = view.treeList.getSelected().stream().map(FXObjectTreeNode::getKey).filter(object -> object != runtime.root).toList();
			var objects = new ArrayList<IFXObject>();
			var parents = new ArrayList<Integer>();
			for (var object : selected) {
				if (selected.stream().anyMatch(parent -> parent != object && object.transform().isInheritedParent(parent.transform())))
					continue;
				collect(object, -1, 0, objects, parents);
			}
			if (objects.isEmpty())
				throw new IllegalArgumentException();
			var entries = new ListTag();
			for (int i = 0; i < objects.size(); i++) {
				var entry = new CompoundTag();
				var data = objects.get(i).serializeWrapper();
				if (data == null)
					throw new IllegalArgumentException();
				entry.put("object", data);
				entry.putInt("parent", parents.get(i));
				entries.add(entry);
			}
			var data = new CompoundTag();
			data.put("objects", entries);
			data.put("references", PhotonTimelineClipboardUtil.references(runtime));
			PhotonSystemClipboardUtil.write(OBJECTS, data);
		} catch (RuntimeException ignored) {
			invalid(view);
		}
	}

	private static void collect(IFXObject object, int parent, int depth, List<IFXObject> objects, List<Integer> parents) {
		if (depth > MAX_DEPTH || objects.size() >= PhotonSystemClipboardUtil.MAX_ITEMS || objects.contains(object))
			throw new IllegalArgumentException();
		int index = objects.size();
		objects.add(object);
		parents.add(parent);
		for (var child : object.children())
			if (child instanceof IFXObject fxObject)
				collect(fxObject, index, depth + 1, objects, parents);
	}

	public static void paste(FXHierarchyView view) {
		try {
			var runtime = view.getRuntime();
			var data = PhotonSystemClipboardUtil.read(OBJECTS);
			if (runtime == null || data == null)
				throw new IllegalArgumentException();
			var entries = data.getListOrEmpty("objects");
			if (entries.isEmpty() || entries.size() > PhotonSystemClipboardUtil.MAX_ITEMS)
				throw new IllegalArgumentException();
			var objects = new ArrayList<IFXObject>();
			var parents = new ArrayList<Integer>();
			var usedNames = new HashSet<String>();
			runtime.objects.values().forEach(object -> usedNames.add(object.getName()));
			var resolved = new HashMap<UUID, UUID>();
			for (var value : entries) {
				if (!(value instanceof CompoundTag entry))
					throw new IllegalArgumentException();
				var object = IFXObject.deserializeWrapper(entry.getCompoundOrEmpty("object"));
				if (object == null || resolved.put(object.transform().id(), UUID.randomUUID()) != null)
					throw new IllegalArgumentException();
			}
			for (int i = 0; i < entries.size(); i++) {
				if (!(entries.get(i) instanceof CompoundTag entry))
					throw new IllegalArgumentException();
				int parent = entry.getIntOr("parent", -2);
				if (parent < -1 || parent >= i)
					throw new IllegalArgumentException();
				var encoded = remap(entry.getCompoundOrEmpty("object").copy(), runtime, data.getCompoundOrEmpty("references"), resolved, 0);
				var object = IFXObject.deserializeWrapper(encoded);
				if (object == null)
					throw new IllegalArgumentException();
				object.transform()._setInternalParentID(null);
				object.transform()._setInternalChildID(List.of());
				String base = object.getName() + COPY_SUFFIX, name = base;
				int count = 1;
				while (!usedNames.add(name))
					name = base + "_" + count++;
				object.setName(name);
				objects.add(object);
				parents.add(parent);
			}
			var selected = view.treeList.getSelected();
			var father = selected.size() == 1 ? selected.iterator().next().getKey().transform().parent() : null;
			var destination = father == null ? runtime.root.transform() : father;
			view.fxEditor.historyView.pushHistory(Component.translatable("photon.copy_fx_object"), EditAction.of(() -> {
				for (int i = 0; i < objects.size(); i++) {
					objects.get(i).transform().parent(parents.get(i) < 0 ? destination : objects.get(parents.get(i)).transform(), false);
					view.addSceneObject(objects.get(i));
				}
				view.fxEditor.reloadEffect();
			}, () -> {
				for (int i = objects.size() - 1; i >= 0; i--)
					view.removeSceneObject(objects.get(i));
				view.fxEditor.reloadEffect();
			}));
		} catch (RuntimeException ignored) {
			invalid(view);
		}
	}

	private static Tag remap(Tag value, FXRuntime runtime, CompoundTag references, Map<UUID, UUID> resolved, int depth) {
		if (depth > MAX_DEPTH)
			throw new IllegalArgumentException();
		if (value instanceof CompoundTag compound) {
			for (String key : List.copyOf(compound.keySet())) {
				if (key.equals("_parentId") || key.equals("_childrenId")) {
					compound.remove(key);
					continue;
				}
				compound.put(key, remap(compound.get(key), runtime, references, resolved, depth + 1));
			}
		} else if (value instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++)
				list.set(i, remap(list.get(i), runtime, references, resolved, depth + 1));
		} else if (value instanceof IntArrayTag array && array.getAsIntArray().length == 4) {
			var source = UUIDUtil.CODEC.parse(NbtOps.INSTANCE, value).result().orElse(null);
			if (source != null && (resolved.containsKey(source) || references.contains(source.toString()))) {
				var target = PhotonTimelineClipboardUtil.resolve(runtime, source, references, resolved);
				return UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, target).getOrThrow();
			}
		} else if (value instanceof StringTag(var string) && references.contains(string)) {
			var target = PhotonTimelineClipboardUtil.resolve(runtime, UUID.fromString(string), references, resolved);
			return StringTag.valueOf(target.toString());
		}
		return value;
	}

	public static void command(FXHierarchyView view, UIEvent event) {
		if (!PhotonKeyClipboardUtil.systemEnabled(view.fxEditor) || PhotonResourceClipboardUtil.textTarget(event.target))
			return;
		boolean copy = CommandEvents.COPY.equals(event.command) && !view.treeList.getSelected().isEmpty();
		boolean paste = CommandEvents.PASTE.equals(event.command) && OBJECTS.equals(PhotonSystemClipboardUtil.kind());
		if (!copy && !paste)
			return;
		if (UIEvents.EXECUTE_COMMAND.equals(event.type)) {
			if (copy)
				copy(view);
			else
				paste(view);
		}
		event.stopPropagation();
	}

	public static void invalid(FXHierarchyView view) {
		Dialog.showNotification(PhotonEditorTextUtil.key("clipboard"), PhotonEditorTextUtil.key("clipboard_invalid"), null).show(view.fxEditor);
	}
}
