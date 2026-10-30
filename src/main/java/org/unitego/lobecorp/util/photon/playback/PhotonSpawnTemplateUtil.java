package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
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

import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

import java.util.stream.Collectors;

/// 从已有定义或项目子树制作独占实例；只过滤未复制目标的贡献，不修改原库和原文件。
public class PhotonSpawnTemplateUtil {
	/// 一次准备的独占 runtime；消费后所有权转移给实例，未消费则由生成器销毁。
	public record Template(FXRuntime runtime, IScene scope, Set<String> path,
						   @Nullable PhotonRuntimeFactory.Lease lease) {
		public Template(FXRuntime runtime, IScene scope, Set<String> path) {
			this(runtime, scope, path, null);
		}

		public void close(boolean force) {
			if (lease == null) {
				runtime.destroy(force);
			} else {
				lease.close(force);
			}
		}

		public Template {
			path = Set.copyOf(path);
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

			var lease = PhotonRuntimeFactory.acquire(fx, owner.getLevel());
			var runtime = lease.runtime();
			runtime.objects.values().stream()
					.filter(PhotonChildSpawner.class::isInstance)
					.map(PhotonChildSpawner.class::cast)
					.forEach(child -> child.source(runtime, path));
			return new Template(runtime, runtime, path, lease);
		}

		var scope = owner.sourceScope();
		var reference = owner.config.template.getTransform(scope);

		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			throw new IllegalArgumentException("项目子发射模板缺失或递归。");
		}

		var path = descend(owner, "project:" + target.id());
		var pending = new ArrayDeque<Transform>();
		var seen = new HashSet<Transform>();
		var selected = new ArrayList<IFXObject>();
		pending.add(target.transform());

		while (!pending.isEmpty()) {
			var next = pending.removeFirst();
			if (!seen.add(next) || selected.size() >= PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS || next.sceneObject() == owner
					|| !(next.sceneObject() instanceof IFXObject object)) {
				throw new IllegalArgumentException("项目子模板循环、无效或超出对象预算。");
			}
			selected.add(object);
			if (owner.config.includeChildren) {
				pending.addAll(next.children());
			}
		}

		var lease = PhotonRuntimeFactory.project(scope, selected, target.id(), owner.config.includeChildren, owner.getLevel());
		if (lease != null) {
			var runtime = lease.runtime();
			runtime.objects.values().stream().filter(PhotonChildSpawner.class::isInstance)
					.map(PhotonChildSpawner.class::cast).forEach(child -> child.source(scope, path));
			return new Template(runtime, scope, path, lease);
		}

		var copies = new ArrayList<IFXObject>();
		for (var object : selected) {
			var copied = object.copy(true);
			if (copied == null) {
				throw new IllegalArgumentException("项目子模板复制失败：" + object.id());
			}
			if (!owner.config.includeChildren) {
				copied.transform()._setInternalChildID(new ArrayList<>());
			}
			copies.add(copied);
		}

		copies.getFirst().transform()._setInternalParentID(FXRuntime.ROOT_UUID);
		copies.getFirst().transform().localPosition(new Vector3f());
		includeController(scope, copies);
		var runtime = new FXRuntime(new FXData(copies, new Timeline()));
		runtime.objects.values().stream()
				.filter(PhotonChildSpawner.class::isInstance)
				.map(PhotonChildSpawner.class::cast)
				.forEach(child -> child.source(scope, path));
		return new Template(runtime, scope, path);
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
		var definition = PhotonProjectDefinitionUtil.filterOwned(source.definition(), targets);

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


}
