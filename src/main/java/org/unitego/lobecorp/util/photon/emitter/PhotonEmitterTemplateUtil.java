package org.unitego.lobecorp.util.photon.emitter;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 从项目子树或完整 FX 定义构造隔离实例，并携带异步递归检测路径。
 */
public class PhotonEmitterTemplateUtil {
	@Nullable
	public static SpawnTemplate create(PhotonEmitterSpawner owner) {
		var scope = owner.templateScope();
		if (owner.config.source == PhotonEmitterSpawnConfig.Source.FX) {
			return createFx(owner);
		}

		var reference = owner.config.template.getTransform(scope);
		if (reference == null || !(reference.sceneObject() instanceof Emitter target) || target == owner) {
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_MISSING_TEMPLATE);
			return null;
		}

		var path = descend(owner, "project:" + target.id());
		if (path == null) {
			return null;
		}

		var copies = copySubtree(target.transform(), owner.config.includeChildren, owner);
		if (copies == null) {
			return null;
		}

		copies.getFirst().transform()._setInternalParentID(FXRuntime.ROOT_UUID);
		copies.getFirst().transform().localPosition(new Vector3f());
		return new SpawnTemplate(new FXRuntime(new FXData(copies)), path, scope);
	}

	@Nullable
	private static SpawnTemplate createFx(PhotonEmitterSpawner owner) {
		var location = owner.config.fxLocation;
		if (location == null) {
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_MISSING_FX);
			return null;
		}

		var path = descend(owner, "fx:" + location);
		if (path == null) {
			return null;
		}

		var fx = FXHelper.getFX(location);
		if (fx == null) {
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_MISSING_FX);
			return null;
		}

		if (fx.getFxData().objects().size() > PhotonEmitterSpawnConfig.MAX_TEMPLATE_OBJECTS) {
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
			return null;
		}

		var runtime = fx.createRuntime(true);
		return new SpawnTemplate(runtime, path, runtime);
	}

	@Nullable
	private static Set<String> descend(PhotonEmitterSpawner owner, String source) {
		var path = owner.sourcePath();
		if (path.contains(source) || path.size() >= Math.clamp(owner.config.maxDepth, 1, PhotonEmitterSpawnConfig.DEFAULT_MAX_DEPTH)) {
			owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_RECURSIVE);
			return null;
		}

		var next = new HashSet<>(path);
		next.add(source);
		return Set.copyOf(next);
	}

	@Nullable
	private static ArrayList<IFXObject> copySubtree(Transform source, boolean children, PhotonEmitterSpawner owner) {
		var copies = new ArrayList<IFXObject>();
		var pending = new ArrayDeque<Transform>();
		var visited = new HashSet<Transform>();
		pending.add(source);
		while (!pending.isEmpty()) {
			var next = pending.removeFirst();
			if (!visited.add(next) || copies.size() >= PhotonEmitterSpawnConfig.MAX_TEMPLATE_OBJECTS
					|| next.sceneObject() == owner || !(next.sceneObject() instanceof IFXObject object)) {
				owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_RECURSIVE);
				return null;
			}

			var copy = object.copy(true);
			if (copy == null) {
				owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
				return null;
			}

			if (!children) {
				copy.transform()._setInternalChildID(new ArrayList<>());
			}
			copies.add(copy);
			if (children) {
				pending.addAll(next.children());
			}
		}
		return copies;
	}

	public record SpawnTemplate(FXRuntime runtime, Set<String> sourcePath, @Nullable IScene scope) {
	}
}
