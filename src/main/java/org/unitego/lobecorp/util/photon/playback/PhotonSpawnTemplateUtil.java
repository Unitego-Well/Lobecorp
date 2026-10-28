package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/// 从已有定义或项目子树制作独占实例；只过滤未复制目标的贡献，不修改原库和原文件。
public class PhotonSpawnTemplateUtil {
	/// 来源定义可跨实例共享；每个实例仍从它复制独立的 runtime 和可变对象树。
	public record Template(FXData source, Set<String> path) {
		public Template {
			path = Set.copyOf(path);
		}

		public FXRuntime createRuntime() {
			var runtime = new FXRuntime(source.copy(true));

			for (var object : runtime.objects.values()) {
				if (object instanceof PhotonChildSpawner child) {
					child.source(runtime, path);
				}
			}

			return runtime;
		}
	}

	public static Template create(PhotonChildSpawner owner) {
		if (owner.config.source == PhotonSpawnConfig.Source.FX) {
			var location = owner.config.fxLocation;

			if (location == null) {
				throw new IllegalArgumentException("子发射来源缺少FX资源ID。");
			}

			var path = descend(owner, "fx:" + location);
			var fx = FXHelper.getFX(location);

			if (fx == null || fx.getFxData().objects().size() > PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
				throw new IllegalArgumentException("子发射FX缺失或超出对象预算：" + location);
			}

			return new Template(fx.getFxData(), path);
		}

		var scope = owner.sourceScope();
		var reference = owner.config.template.getTransform(scope);

		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			throw new IllegalArgumentException("项目子发射模板缺失或递归。");
		}

		var path = descend(owner, "project:" + target.id());
		var pending = new ArrayDeque<Transform>();
		var seen = new HashSet<Transform>();
		var copies = new ArrayList<IFXObject>();
		pending.add(target.transform());

		while (!pending.isEmpty()) {
			var next = pending.removeFirst();

			if (!seen.add(next) || copies.size() >= PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS || next.sceneObject() == owner
					|| !(next.sceneObject() instanceof IFXObject object)) {
				throw new IllegalArgumentException("项目子模板循环、无效或超出对象预算。");
			}

			var copied = object.copy(true);

			if (copied == null) {
				throw new IllegalArgumentException("项目子模板复制失败：" + object.id());
			}

			if (!owner.config.includeChildren) {
				copied.transform()._setInternalChildID(new ArrayList<>());
			}

			copies.add(copied);

			if (owner.config.includeChildren) {
				pending.addAll(next.children());
			}
		}

		copies.getFirst().transform()._setInternalParentID(FXRuntime.ROOT_UUID);
		copies.getFirst().transform().localPosition(new Vector3f());
		includeController(scope, copies);
		return new Template(new FXData(copies, new Timeline()), path);
	}

	private static Set<String> descend(PhotonChildSpawner owner, String identity) {
		var path = owner.sourcePath();

		if (path.contains(identity) || path.size() >= Math.clamp(owner.config.maxDepth, 1, PhotonSpawnConfig.MAX_DEPTH)) {
			throw new IllegalArgumentException("子发射来源循环或超过深度预算：" + identity);
		}

		var next = new HashSet<>(path);
		next.add(identity);
		return Set.copyOf(next);
	}

	private static void includeController(IScene scope, List<IFXObject> copies) {
		var controllers = scope.getAllSceneObjects().stream().filter(PhotonNamedController.class::isInstance)
				.map(PhotonNamedController.class::cast).toList();

		if (controllers.isEmpty()) {
			return;
		}

		if (controllers.size() != 1 || copies.size() >= PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			throw new IllegalArgumentException("项目模板命名控制器数量或对象预算无效。");
		}

		var source = controllers.getFirst();
		var targets = copies.stream().filter(object -> !(object instanceof PhotonNamedController))
				.map(IFXObject::id).collect(Collectors.toSet());
		var definition = source.definition();

		for (var value : definition.getListOrEmpty("animations")) {
			if (!(value instanceof CompoundTag animation)) {
				throw new IllegalArgumentException("项目命名库含无效动画。");
			}

			animation.getListOrEmpty("tracks").removeIf(track -> !retainTrack((CompoundTag) track, targets));
			animation.getListOrEmpty("bindings").removeIf(binding ->
					!targets.contains(UUID.fromString(((CompoundTag) binding).getStringOr("target", ""))));
		}

		var existing = copies.stream().filter(PhotonNamedController.class::isInstance).map(PhotonNamedController.class::cast).toList();

		if (existing.size() > 1) {
			throw new IllegalArgumentException("复制子树含多个命名控制器。");
		}

		var controller = existing.isEmpty() ? source.shallowCopy() : existing.getFirst();

		if (existing.isEmpty()) {
			controller.setName(source.getName());
			controller.transform().set(source.transform(), true);
			controller.transform()._setInternalID(source.id());
			controller.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
			controller.transform()._setInternalChildID(new ArrayList<>());
			copies.add(controller);
		}

		controller.definition(definition);
	}

	private static boolean retainTrack(CompoundTag source, Set<UUID> targets) {
		var data = source.getCompoundOrEmpty("data");
		String type = source.getStringOr("type", "");

		if (type.equals("group")) {
			var children = data.getListOrEmpty("children");
			children.removeIf(value -> !retainTrack((CompoundTag) value, targets));
			return !children.isEmpty();
		}

		if (type.equals("control")) {
			var clips = data.getListOrEmpty("clips");
			clips.removeIf(value -> ((CompoundTag) value).read("clipTarget", UUIDUtil.CODEC).filter(targets::contains).isEmpty());
			return !clips.isEmpty();
		}

		var target = data.read("target", UUIDUtil.CODEC);

		if (target.isPresent() && !targets.contains(target.get())) {
			return false;
		}

		if (data.get("clips") instanceof ListTag clips) {
			clips.removeIf(value -> {
				var clipTarget = ((CompoundTag) value).read("clipTarget", UUIDUtil.CODEC);
				return clipTarget.isPresent() && !targets.contains(clipTarget.get());
			});
		}

		return true;
	}
}
