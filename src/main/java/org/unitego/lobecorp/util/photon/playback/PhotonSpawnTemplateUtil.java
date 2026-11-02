package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTemplateProgram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

import java.util.stream.Collectors;

/// 从已有定义或项目子树制作独占实例；只过滤未复制目标的贡献，不修改原库和原文件。
public class PhotonSpawnTemplateUtil {
	/// 不可变模板程序：只解析/编译结构并递归准备静态子程序，不取得 runtime。
	public static @Nullable PhotonTemplateProgram program(PhotonChildSpawner owner) {
		if (owner.config.source == PhotonSpawnConfig.Source.FX) {
			return fxProgram(owner);
		}
		return projectProgram(owner);
	}

	/// 计算当前来源路径；仅供子发射器刷新瞬态程序使用，不解析或实例化定义。
	public static @Nullable Set<String> path(PhotonChildSpawner owner) {
		if (owner.config.source == PhotonSpawnConfig.Source.FX) {
			var location = owner.config.fxLocation;
			return location == null ? null : descend(owner.sourcePath(), "fx:" + location, owner.config.maxDepth);
		}
		if (owner.config.source != PhotonSpawnConfig.Source.PROJECT) {
			return null;
		}
		try {
			var scope = owner.sourceScope();
			var reference = owner.config.template.getTransform(scope);
			if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
				return null;
			}
			return descend(owner.sourcePath(), "project:" + target.id(), owner.config.maxDepth);
		} catch (RuntimeException unavailable) {
			return null;
		}
	}

	/// 低成本核验当前来源/路径/目标/结构；可变作者 FX 不允许跨调用信任旧缓存。
	public static boolean matches(PhotonChildSpawner owner, PhotonTemplateProgram program) {
		if (program == null || !program.current()) {
			return false;
		}
		if (owner.config.source == PhotonSpawnConfig.Source.FX) {
			var location = owner.config.fxLocation;
			if (location == null) {
				return false;
			}
			var fx = owner.resolveDefinition(location);
			return fx != null && program.matchesFx(location,
					descend(owner.sourcePath(), "fx:" + location, owner.config.maxDepth), fx.getFxData());
		}
		if (owner.config.source != PhotonSpawnConfig.Source.PROJECT) {
			return false;
		}
		IScene scope;
		try {
			scope = owner.sourceScope();
		} catch (RuntimeException unavailable) {
			return false;
		}
		var reference = owner.config.template.getTransform(scope);
		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			return false;
		}
		var path = descend(owner.sourcePath(), "project:" + target.id(), owner.config.maxDepth);
		return program.matchesProject(scope, target.id(), owner.config.includeChildren, path);
	}

	/// 真实发射时由程序取得独占租约并接线来源；动态槽和随机值仍由实例创建阶段处理。
	public static Template instantiate(PhotonChildSpawner owner, PhotonTemplateProgram program) {
		if (program == null) {
			return nativeProjectTemplate(owner);
		}
		if (!program.current()) {
			throw new IllegalStateException("子模板程序已失效。");
		}
		if (program.source() == PhotonTemplateProgram.Source.FX) {
			var location = program.fxLocation();
			var fx = location == null ? null : owner.resolveDefinition(location);
			if (fx == null || fx.getFxData().objects().size() > PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
				throw new IllegalArgumentException("子发射FX缺失或超出对象预算：" + location);
			}

			var lease = !program.reusable()
					? PhotonRuntimeFactory.acquire(PhotonRuntimeFactory.prepareTransient(fx), owner.getLevel(), program.mode())
					: program.mode() == PhotonRuntimeFactory.Mode.NATIVE
					? PhotonRuntimeFactory.acquire(fx, owner.getLevel()) : program.acquire(owner.getLevel());
			var runtime = lease.runtime();
			wireChildren(owner, runtime, runtime, program.path());
			return new Template(runtime, runtime, program.path(), lease);
		}
		if (program.source() == PhotonTemplateProgram.Source.ROOT) {
			throw new IllegalArgumentException("根模板不能直接作为子发射实例。");
		}
		var scope = owner.sourceScope();
		var reference = program.projectTarget() == null ? null : owner.config.template.getTransform(scope);
		if (reference == null || !(reference.sceneObject() instanceof Emitter target)
				|| !target.id().equals(program.projectTarget())) {
			throw new IllegalArgumentException("项目子发射模板缺失或已变更。");
		}
		var selected = selectProject(owner, target);
		var lease = PhotonRuntimeFactory.project(scope, selected, target.id(),
				program.includeChildren(), owner.getLevel(), program);
		if (lease == null) {
			return nativeProjectTemplate(owner, scope, selected, program.path());
		}
		var runtime = lease.runtime();
		wireChildren(owner, runtime, scope, program.path());
		return new Template(runtime, scope, program.path(), lease);
	}

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
		return instantiate(owner, program(owner));
	}

	private static Template nativeProjectTemplate(PhotonChildSpawner owner) {
		var scope = owner.sourceScope();
		var reference = owner.config.template.getTransform(scope);
		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			throw new IllegalArgumentException("项目子发射模板缺失或递归。");
		}
		var path = descend(owner, "project:" + target.id());
		return nativeProjectTemplate(owner, scope, selectProject(owner, target), path);
	}

	private static PhotonTemplateProgram fxProgram(PhotonChildSpawner owner) {
		var location = owner.config.fxLocation;
		if (location == null) {
			throw new IllegalArgumentException("子发射来源缺少FX资源ID。");
		}
		var path = descend(owner, "fx:" + location);
		var fx = owner.resolveDefinition(location);
		if (fx == null || fx.getFxData().objects().size() > PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			throw new IllegalArgumentException("子发射FX缺失或超出对象预算：" + location);
		}
		var current = PhotonRuntimeFactory.currentDefinition(fx);
		var reusable = current != null;
		var definition = reusable ? current : PhotonRuntimeFactory.prepareTransient(fx);
		var scoped = PhotonTemplateProgram.fx(definition, location, path, fx, fx.getFxData(), reusable);
		prepareNestedPrograms(scoped, new HashSet<>());
		return scoped;
	}

	private static PhotonTemplateProgram projectProgram(PhotonChildSpawner owner) {
		var scope = owner.sourceScope();
		var reference = owner.config.template.getTransform(scope);
		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			throw new IllegalArgumentException("项目子发射模板缺失或递归。");
		}
		var path = descend(owner, "project:" + target.id());
		if (PhotonRuntimeFactory.nativeProjectFallback(scope)) {
			return null;
		}
		var program = PhotonRuntimeFactory.projectProgram(scope, target.id(), owner.config.includeChildren, path, null);
		if (program == null) {
			return null;
		}
		prepareNestedPrograms(program, new HashSet<>());
		return program;
	}

	/// 沿已编译节点递归准备静态子程序；只读取未绑定编译节点，不创建运行树或求值动态配置。
	private static void prepareNestedPrograms(PhotonTemplateProgram parent, Set<String> visited) {
		if (!visited.add(parent.definition().key())) {
			return;
		}
		for (var node : parent.definition().nodes()) {
			for (var child : node.childPrograms()) {
				if (child.config.source == PhotonSpawnConfig.Source.PROJECT) {
					// 编译节点中的 PROJECT 目标需要所属场景，留到真实来源作用域校验。
					continue;
				}
				var nested = nestedProgram(child, parent.path());
				prepareNestedPrograms(nested, visited);
			}
		}
	}

	private static PhotonTemplateProgram nestedProgram(PhotonChildSpawner spawner, Set<String> parentPath) {
		var location = spawner.config.fxLocation;
		if (location == null) {
			throw new IllegalArgumentException("子发射来源缺少FX资源ID。");
		}
		var identity = "fx:" + location;
		var path = descend(parentPath, identity, spawner.config.maxDepth);
		var fx = spawner.resolveDefinition(location);
		if (fx == null || fx.getFxData().objects().size() > PhotonSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			throw new IllegalArgumentException("子发射FX缺失或超出对象预算：" + location);
		}
		var current = PhotonRuntimeFactory.currentDefinition(fx);
		var reusable = current != null;
		var definition = reusable ? current : PhotonRuntimeFactory.prepareTransient(fx);
		return PhotonTemplateProgram.fx(definition, location, path, fx, fx.getFxData(), reusable);
	}

	private static ArrayList<IFXObject> selectProject(PhotonChildSpawner owner, Emitter target) {
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
		return selected;
	}

	private static Template nativeProjectTemplate(PhotonChildSpawner owner, IScene scope, List<IFXObject> selected,
	                                              Set<String> path) {
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
		wireChildren(owner, runtime, scope, path);
		return new Template(runtime, scope, path);
	}

	private static void wireChildren(PhotonChildSpawner owner, FXRuntime runtime, IScene scope, Set<String> path) {
		runtime.objects.values().stream().filter(PhotonChildSpawner.class::isInstance)
				.map(PhotonChildSpawner.class::cast).forEach(child -> {
					child.inheritDefinitionResolver(owner);
					child.source(scope, path);
				});
	}

	private static Set<String> descend(PhotonChildSpawner owner, String identity) {
		return descend(owner.sourcePath(), identity, owner.config.maxDepth);
	}

	private static Set<String> descend(Set<String> path, String identity, int maxDepth) {

		if (path.contains(identity) || path.size() >= Math.clamp(maxDepth, 1, PhotonSpawnConfig.MAX_DEPTH)) {
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
