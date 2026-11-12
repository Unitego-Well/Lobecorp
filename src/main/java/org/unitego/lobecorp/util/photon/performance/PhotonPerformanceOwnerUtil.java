package org.unitego.lobecorp.util.photon.performance;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.Job;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Metric;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Node;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.lang.ref.WeakReference;
import java.util.*;

/// 将运行对象身份、定义来源和延迟作业关联；不持有世界或运行实例的强引用。
public class PhotonPerformanceOwnerUtil {
	private static final Map<DrawJob, Integer> jobs = new IdentityHashMap<>();
	private static final ArrayDeque<Map<RenderType, Integer>> drawing = new ArrayDeque<>();
	private static final Map<Integer, WeakReference<IFXObject>> targets = new HashMap<>();
	private static long targetSession;

	public static int definition(FX effect) {
		int known = PhotonPerformanceTraceUtil.knownNode(effect);
		if (known != 0) {
			return known;
		}

		var location = effect.getFxLocation();
		var resource = location == null ? "" : location.toString();
		return PhotonPerformanceTraceUtil.node(effect, new Node(0, 0, "fx_definition",
				resource.isEmpty() ? "FX" : resource, resource, resource));
	}

	public static int runtime(FXRuntime runtime) {
		int known = PhotonPerformanceTraceUtil.knownNode(runtime);
		if (known != 0) {
			return known;
		}

		var root = runtime.getRoot();
		return PhotonPerformanceTraceUtil.node(runtime, new Node(0, 0, "fx_runtime",
				root == null ? "FXRuntime" : root.getName(),
				root == null ? "" : root.transform().id().toString(), ""));
	}

	public static void bind(FXRuntime runtime, FX definition) {
		var resource = definition.getFxLocation();
		var location = resource == null ? "" : resource.toString();
		var root = runtime.getRoot();
		PhotonPerformanceTraceUtil.describe(runtime, new Node(0, definition(definition), "fx_runtime",
				root == null ? "FXRuntime" : root.getName(), root == null ? "" : root.transform().id().toString(), location));
	}

	public static int object(IFXObject object) {
		int known = PhotonPerformanceTraceUtil.knownNode(object);
		if (known != 0) {
			return known;
		}

		int parent = 0;
		var transform = object.transform().parent();
		if (transform != null && transform.sceneObject() instanceof IFXObject parentObject && parentObject != object) {
			parent = object(parentObject);
		} else if (object instanceof FXObject particle && particle.getScene() instanceof FXRuntime runtime) {
			parent = runtime(runtime);
		}

		int id = PhotonPerformanceTraceUtil.node(object, new Node(0, parent, object.getClass().getSimpleName(),
				object.getName(), object.transform().id().toString(), ""));
		synchronized (targets) {
			long session = PhotonPerformanceTraceUtil.sessionId();
			if (session != targetSession) {
				targets.clear();
				targetSession = session;
			}
			if (id != 0) {
				targets.put(id, new WeakReference<>(object));
			}
		}
		return id;
	}

	public static int player(TimelinePlayer player, FXRuntime runtime) {
		int known = PhotonPerformanceTraceUtil.knownNode(player);
		if (known != 0) {
			return known;
		}

		return PhotonPerformanceTraceUtil.node(player, new Node(0, runtime(runtime), "timeline_player",
				"timeline", "", ""));
	}

	public static int track(Track track, int parent) {
		int context = PhotonPerformanceTraceUtil.currentNode(Stage.ANIMATION, Stage.ANIMATION_FRAME);
		int known = PhotonPerformanceTraceUtil.knownNode(track, parent, context);
		if (known != 0) {
			return known;
		}

		return PhotonPerformanceTraceUtil.node(track, new Node(0, parent, "track", track.displayName(),
				track.targetId() == null ? "" : track.targetId().toString(), ""), context);
	}

	public static void baked(Emitter emitter, List<DrawJob> output, int start) {
		int owner = object(emitter);
		PhotonPerformanceTraceUtil.counter(owner, Metric.LIVE_PARTICLES, emitter.getParticleAmount());
		PhotonPerformanceTraceUtil.counter(owner, Metric.DRAW_JOBS, Math.max(0, output.size() - start));
		synchronized (jobs) {
			for (int index = start; index < output.size(); index++) {
				if (jobs.size() < PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD) {
					jobs.put(output.get(index), owner);
				}
			}
		}
	}

	public static @Nullable IFXObject target(int id, long session) {
		synchronized (targets) {
			var reference = session == targetSession ? targets.get(id) : null;
			return reference == null ? null : reference.get();
		}
	}

	public static int jobOwner(DrawJob job) {
		synchronized (jobs) {
			return jobs.getOrDefault(job, 0);
		}
	}

	/// 一个 RenderType 在当前 drain 中的所有贡献者都一致时才可归属到单对象；否则共享。
	public static void beginDraws(List<DrawJob> input) {
		var owners = new IdentityHashMap<RenderType, Integer>();
		for (var entry : input) {
			if (!(entry instanceof Job job)) {
				continue;
			}
			var type = job.renderType();
			int owner = jobOwner(job);
			var previous = owners.putIfAbsent(type, owner);
			if (previous != null && previous != owner) {
				owners.put(type, 0);
			}
			if (owners.size() >= PhotonPerformanceTraceUtil.MAX_AGGREGATES_PER_THREAD) {
				owners.clear();
				break;
			}
		}
		drawing.push(owners);
	}

	public static int drawOwner(RenderType type) {
		return drawing.isEmpty() ? 0 : drawing.peek().getOrDefault(type, 0);
	}

	public static void endDraws() {
		drawing.poll();
	}

	public static void clearJobs() {
		synchronized (jobs) {
			jobs.clear();
			drawing.clear();
		}
	}
}
