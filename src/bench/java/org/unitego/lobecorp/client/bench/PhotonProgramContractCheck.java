package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil;

import java.util.UUID;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonTemplateProgram;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.playback.PhotonSpawnTemplateUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 模板程序真实客户端契约探针；需在已初始化世界与注册表的客户端 bench 环境执行。
public final class PhotonProgramContractCheck {
	private static final String SCHEMA = "photon-program-contract-v1";
	private static final Identifier TRANSIENT_ID = Identifier.parse("lobecorp:program_contract_transient");
	private final Map<String, JsonObject> results = new LinkedHashMap<>();
	private JsonObject active;

	private static ClientLevel world() {
		var client = Minecraft.getInstance();
		require(client.isSameThread(), "必须在客户端线程执行");
		require(client.level != null, "必须已有真实客户端世界");
		require(Platform.getFrozenRegistry() != null, "注册表必须初始化");
		require(PhotonRuntimeFactory.mode() == PhotonRuntimeFactory.Mode.HYBRID, "配置必须为 HYBRID");
		return client.level;
	}

	private PhotonProgramContractCheck() {
	}

	public static JsonObject verify() {
		var check = new PhotonProgramContractCheck();
		check.run("transient_fx_first_consumption_uses_live_fields", check::transientFxFirstConsumption);
		check.run("same_program_leases_are_independent", check::sameProgramLeaseIndependence);
		check.run("project_replacement_compiles_and_invalidates", check::projectReplacement);
		check.run("project_child_hierarchy_compiles_and_invalidates", check::projectHierarchyChange);
		check.run("project_transform_only_change_keeps_match", check::projectTransformChange);
		check.run("transformReferenceTransfersAsIndependentUuidReference", check::transformReferenceTransfersAsIndependentUuidReference);
		check.run("numberFunction3TransfersDeepCopies", check::numberFunction3TransfersDeepCopies);
		var report = new JsonObject();
		report.addProperty("schema", SCHEMA);
		report.addProperty("passed", check.results.size() == 7 && check.results.values().stream()
				.allMatch(value -> value.get("passed").getAsBoolean()));
		var cases = new JsonArray();
		check.results.values().forEach(cases::add);
		report.add("cases", cases);
		return report;
	}

	private void transientFxFirstConsumption() {
		PhotonRuntimeFactory.Lease lease = null;
		FXRuntime runtime = null;
		var actual = active;
		try {
			var source = new FX();
			source.setFxLocation(TRANSIENT_ID);
			var authored = new PhotonChildSpawner();
			authored.config.source = PhotonSpawnConfig.Source.PROJECT;
			authored.config.fxLocation = TRANSIENT_ID;
			authored.config.count = NumberFunction.constant(7);
			source.getFxData().objects().add(authored);
			authored.ownedDefinitionResolver(_ -> source);

			var owner = new PhotonChildSpawner();
			owner.setLevel(world());
			owner.config.source = PhotonSpawnConfig.Source.FX;
			owner.config.fxLocation = TRANSIENT_ID;
			owner.ownedDefinitionResolver(_ -> source);
			var program = PhotonSpawnTemplateUtil.program(owner);
			require(program != null && program.transientFx(), "未登记可变 FX 必须生成本次瞬态程序");
			var path = program.path();
			authored.config.count = NumberFunction.constant(99);
			var consumed = PhotonSpawnTemplateUtil.instantiate(owner, program);
			lease = consumed.lease();
			require(lease != null, "真实实例必须持有受管租约");
			runtime = consumed.runtime();
			require(!runtime.isEmitted(), "契约不得预播放 runtime");
			var live = runtime.objects.values().stream().filter(PhotonChildSpawner.class::isInstance)
					.map(PhotonChildSpawner.class::cast).findFirst().orElseThrow();
			long liveCount = live.config.count.get(0, () -> 0f).longValue();
			live.config.count = NumberFunction.constant(123);
			long sourceCount = authored.config.count.get(0, () -> 0f).longValue();
			require(liveCount == 99, "首次消费必须读取同一 prepare 内容编辑后的 live 字段，实际=" + liveCount);
			require(sourceCount == 99, "源作者对象数量被实例改写，实际=" + sourceCount);
			actual.addProperty("liveIsSource", live == authored);
			require(live != authored, "运行实例不得直接复用源作者对象");
			require(!path.isEmpty(), "瞬态程序必须记录本次来源路径");
			actual.addProperty("programTransient", program.transientFx());
			actual.addProperty("liveCount", liveCount);
			actual.addProperty("sourceCount", sourceCount);
			actual.addProperty("path", path.toString());
		} catch (Throwable failure) {
			fail(actual);
			throw failure;
		} finally {
			var cleanup = cleanup(lease, runtime);
			actual.add("cleanup", cleanup);
		}
		pass("transient_fx_first_consumption_uses_live_fields",
				"瞬态程序首次消费前原地编辑 count 7→99，真实实例读取 99 且与作者对象隔离", actual);
	}


	private void projectReplacement() {
		ProjectScopeFixture fixture = null;
		PhotonRuntimeFactory.Lease consumed = null;
		try {
			fixture = new ProjectScopeFixture();
			var scene = fixture.scene();
			var target = fixture.target();
			var program = project(scene, target);
			require(program.matchesProject(scene, target.id(), true, Set.of()), "初始 PROJECT 必须匹配");
			var replacement = emitter(91);
			fixture.replace(replacement);
			boolean stale = program.matchesProject(scene, target.id(), true, Set.of());
			active.addProperty("oldMatchesAfterReplacement", stale);
			require(!stale, "同 UUID 同类型替换必须失效");
			var current = project(scene, replacement);
			require(current.matchesProject(scene, replacement.id(), true, Set.of()), "当前来源必须匹配");
			consumed = PhotonRuntimeFactory.project(scene, List.of(replacement), replacement.id(), true, world(), current);
			require(consumed != null, "必须取得真实 PROJECT 租约");
			var copied = consumed.runtime().objects.values().stream().filter(ParticleEmitter.class::isInstance)
					.map(ParticleEmitter.class::cast).findFirst().orElseThrow();
			long value = copied.config.emission.getEmissionRate().get(0, () -> 0f).longValue();
			active.addProperty("replacementCount", value);
			require(value == 91, "替换后的消费必须读取当前配置");
		} finally {
			active.add("cleanup", cleanupProject(consumed, fixture));
		}
		pass("project_replacement_compiles_and_invalidates", active);
	}

	private void projectHierarchyChange() {
		ProjectScopeFixture fixture = null;
		try {
			fixture = new ProjectScopeFixture();
			var scene = fixture.scene();
			var target = fixture.target();
			var initial = project(scene, target);
			require(initial.matchesProject(scene, target.id(), true, Set.of()), "初始结构必须匹配");
			var child = emitter(0);
			fixture.add(child);
			child.transform().parent(target.transform(), false);
			boolean added = initial.matchesProject(scene, target.id(), true, Set.of());
			active.addProperty("oldMatchesAfterAdd", added);
			require(!added, "增加真实子节点必须失效");
			var withChild = project(scene, target);
			require(withChild.matchesProject(scene, target.id(), true, Set.of()), "增加后的结构必须匹配");
			child.transform().parent(null, false);
			scene.removeSceneObject(child);
			boolean removed = withChild.matchesProject(scene, target.id(), true, Set.of());
			active.addProperty("oldMatchesAfterRemove", removed);
			require(!removed, "移除真实子节点必须失效");
			require(project(scene, target).matchesProject(scene, target.id(), true, Set.of()), "移除后的结构必须匹配");
		} finally {
			active.add("cleanup", cleanupProject(null, fixture));
		}
		pass("project_child_hierarchy_compiles_and_invalidates", active);
	}

	private void projectTransformChange() {
		ProjectScopeFixture fixture = null;
		try {
			fixture = new ProjectScopeFixture();
			var scene = fixture.scene();
			var target = fixture.target();
			var program = project(scene, target);
			require(program.matchesProject(scene, target.id(), true, Set.of()), "初始程序必须匹配");
			long before = PhotonRuntimeFactory.statistics().get("project_compilations");
			target.transform().localPosition(new Vector3f(3, 4, 5));
			require(program.matchesProject(scene, target.id(), true, Set.of()), "仅变换必须仍匹配");
			var refreshed = project(scene, target);
			long after = PhotonRuntimeFactory.statistics().get("project_compilations");
			active.addProperty("compilationsBefore", before);
			active.addProperty("compilationsAfter", after);
			require(refreshed.definition() == program.definition() && before == after, "仅变换不得重复编译静态程序");
		} finally {
			active.add("cleanup", cleanupProject(null, fixture));
		}
		pass("project_transform_only_change_keeps_match", active);
	}

	private void sameProgramLeaseIndependence() {
		PhotonRuntimeFactory.Lease first = null;
		PhotonRuntimeFactory.Lease second = null;
		var actual = active;
		try {
			var source = new FX();
			source.setFxLocation(TRANSIENT_ID);
			var emitter = emitter(7);
			source.getFxData().objects().add(emitter);
			var program = PhotonRuntimeFactory.rootProgram(source);
			first = program.acquire(world());
			second = program.acquire(world());
			var firstRuntime = first.runtime();
			var secondRuntime = second.runtime();
			require(!firstRuntime.isEmitted() && !secondRuntime.isEmitted(), "契约不得预播放 runtime");
			require(firstRuntime != secondRuntime && firstRuntime.getFxData() != secondRuntime.getFxData(),
					"同 Program 两次 acquire 必须取得独立 runtime/FXData");
			var firstEmitter = firstRuntime.objects.values().stream()
					.filter(ParticleEmitter.class::isInstance).map(ParticleEmitter.class::cast)
					.findFirst().orElseThrow();
			var secondEmitter = secondRuntime.objects.values().stream()
					.filter(ParticleEmitter.class::isInstance).map(ParticleEmitter.class::cast)
					.findFirst().orElseThrow();
			var firstCount = firstEmitter.config.emission.getEmissionRate();
			firstEmitter.config.emission.setEmissionRate(NumberFunction.constant(99));
			long secondValue = secondEmitter.config.emission.getEmissionRate().get(0, () -> 0f).longValue();
			require(secondValue == 7, "修改一例不得影响另一例，实际=" + secondValue);
			require(firstEmitter.config.emission.getEmissionRate().get(0, () -> 0f).longValue() == 99,
					"第一例修改必须保留，实际="
							+ firstEmitter.config.emission.getEmissionRate().get(0, () -> 0f).longValue());
			first.close(true);
			boolean firstClosed = !first.current();
			actual.addProperty("firstClosed", firstClosed);
			require(firstClosed, "关闭后的第一个 Lease 必须失效");
			first = null;
			require(second.current(), "关闭第一个 Lease 不应破坏第二个");
			require(second.runtime().getFxData()
					== secondRuntime.getFxData(), "第二个 Lease 的 runtime 必须仍有效");
			require(secondEmitter.config.emission.getEmissionRate().get(0, () -> 0f).longValue() == 7,
					"关闭第一个 Lease 不得改写第二个实例");
			actual.addProperty("firstCountBefore", firstCount.get(0, () -> 0f).longValue());
			actual.addProperty("firstCountAfter", 99);
			actual.addProperty("secondCount", secondValue);
			actual.addProperty("secondCurrent", second.current());
		} catch (Throwable failure) {
			fail(actual);
			throw failure;
		} finally {
			actual.add("cleanup", cleanupLeases(first, second));
		}
		pass("same_program_leases_are_independent",
				"同一 Program 两次真实 acquire 后实例配置独立，LIFO 关闭一个不影响另一个", actual);
	}


	private void run(String name, Body body) {
		active = new JsonObject();
		try {
			body.run();
			require(results.containsKey(name), "case 必须记录结果");
		} catch (Throwable failure) {
			var result = new JsonObject();
			result.addProperty("name", name);
			result.addProperty("passed", false);
			result.addProperty("expected", name);
			result.add("actual", active);
			result.add("cleanup", active.has("cleanup") ? active.get("cleanup") : cleanup(null, null));
			var failures = new JsonArray();
			failures.add(failure.toString());
			result.add("failure", failures);
			var stack = new JsonArray();
			for (var frame : failure.getStackTrace()) {
				stack.add(frame.toString());
			}
			result.add("stack", stack);
			results.put(name, result);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private void pass(String name, JsonObject actual) {
		pass(name, "真实调用路径与清理完成", actual);
	}

	private void pass(String name, String detail, JsonObject actual) {
		var result = new JsonObject();
		result.addProperty("name", name);
		var cleanup = actual.getAsJsonObject("cleanup");
		boolean cleanupCompleted = cleanup != null && cleanup.get("completed").getAsBoolean();
		result.addProperty("passed", cleanupCompleted);
		if (!cleanupCompleted) {
			var failure = new JsonArray();
			failure.add("cleanup 未完成");
			result.add("failure", failure);
		}
		var expected = new JsonArray();
		expected.add(detail);
		result.add("expected", expected);
		result.add("actual", actual);
		result.add("cleanup", cleanup);
		result.add("stack", new JsonArray());
		if (!result.has("failure")) {
			result.add("failure", new JsonArray());
		}
		results.put(name, result);
	}

	private static void fail(JsonObject actual) {
		var cleanup = actual.getAsJsonObject("cleanup");
		if (cleanup == null) {
			cleanup = new JsonObject();
			cleanup.addProperty("completed", false);
			cleanup.add("steps", new JsonArray());
			var failures = new JsonArray();
			failures.add("case 在 cleanup 字段建立前失败");
			cleanup.add("failures", failures);
			actual.add("cleanup", cleanup);
		}
	}

	private static JsonObject cleanup(PhotonRuntimeFactory.Lease lease, FXRuntime runtime) {
		var cleanup = new JsonObject();
		var steps = new JsonArray();
		var failures = new JsonArray();
		if (lease != null) {
			try {
				lease.close(true);
				steps.add("lease.close(true)");
			} catch (Throwable failure) {
				failures.add("lease.close: " + failure);
			}
		}
		if (runtime != null && (lease == null || !runtime.isDestroyed())) {
			try {
				runtime.destroy(true);
				steps.add("runtime.destroy(true)");
			} catch (Throwable failure) {
				failures.add("runtime.destroy: " + failure);
			}
		}
		cleanup.addProperty("completed", failures.isEmpty());
		cleanup.add("steps", steps);
		cleanup.add("failures", failures);
		return cleanup;
	}

	private static JsonObject cleanupLeases(PhotonRuntimeFactory.Lease first,
	                                        PhotonRuntimeFactory.Lease second) {
		var cleanup = new JsonObject();
		var steps = new JsonArray();
		var failures = new JsonArray();
		if (second != null) {
			try {
				second.close(true);
				steps.add("second.close(true)");
			} catch (Throwable failure) {
				failures.add("second.close: " + failure);
			}
		}
		if (first != null) {
			try {
				first.close(true);
				steps.add("first.close(true)");
			} catch (Throwable failure) {
				failures.add("first.close: " + failure);
			}
		}
		cleanup.addProperty("completed", failures.isEmpty());
		cleanup.add("steps", steps);
		cleanup.add("failures", failures);
		return cleanup;
	}

	private static PhotonTemplateProgram project(FXRuntime scene, ParticleEmitter target) {
		var program = PhotonRuntimeFactory.projectProgram(scene, target.id(), true, Set.of(), null);
		require(program != null, "真实 PROJECT 程序不得为空");
		return program;
	}

	private static JsonObject cleanupProject(PhotonRuntimeFactory.Lease lease, ProjectScopeFixture fixture) {
		var result = cleanup(lease, null);
		if (fixture != null) {
			try {
				fixture.close();
				result.getAsJsonArray("steps").add("fixture.close()");
			} catch (Throwable failure) {
				result.addProperty("completed", false);
				result.getAsJsonArray("failures").add(failure.toString());
			}
		}
		return result;
	}

	private static final class ProjectScopeFixture implements AutoCloseable {
		private final PhotonRuntimeFactory.Lease lease;
		private final FXRuntime scene;
		private final ParticleEmitter target;
		private final List<ParticleEmitter> detached = new ArrayList<>();

		private ProjectScopeFixture() {
			var source = new FX();
			source.getFxData().objects().add(emitter(3));
			lease = PhotonRuntimeFactory.rootProgram(source).acquire(world());
			try {
				scene = lease.runtime();
				require(PhotonRuntimeFactory.managed(scene), "夹具必须受管");
				target = scene.objects.values().stream().filter(ParticleEmitter.class::isInstance)
						.map(ParticleEmitter.class::cast).findFirst().orElseThrow();
				target.setLevel(world());
			} catch (Throwable failure) {
				lease.close(true);
				throw failure;
			}
		}

		private FXRuntime scene() {
			return scene;
		}

		private ParticleEmitter target() {
			return target;
		}

		private void add(ParticleEmitter object) {
			object.setLevel(world());
			detached.add(object);
			scene.addSceneObject(object);
		}

		private void replace(ParticleEmitter replacement) {
			detached.add(target);
			target.transform().parent(null, false);
			scene.removeSceneObject(target);
			replacement.transform()._setInternalID(target.id());
			add(replacement);
			replacement.transform().parent(scene.root.transform(), false);
		}

		@Override
		public void close() {
			try {
				lease.close(true);
			} finally {
				for (var object : detached) {
					try {
						object.remove(true);
					} finally {
						object.setScene(null);
					}
				}
			}
		}
	}

	private static ParticleEmitter emitter(int emissionRate) {
		var emitter = new ParticleEmitter();
		emitter.config.setDuration(20);
		emitter.config.emission.setEmissionRate(NumberFunction.constant(emissionRate));
		emitter.config.emission.setBursts(new ArrayList<>(List.of(new EmissionSetting.Burst())));
		emitter.config.shape.setPosition(new NumberFunction3(3, 4, 5));
		emitter.config.shape.setRotation(new NumberFunction3(6, 7, 8));
		emitter.config.shape.setScale(new NumberFunction3(9, 10, 11));
		return emitter;
	}

	private void transformReferenceTransfersAsIndependentUuidReference() {
		try {
			world();
			var source = new TransformReferenceOwner();
			var sourceId = UUID.randomUUID();
			source.reference = new TransformRef(sourceId);
			var tag = (CompoundTag) PersistedParser.serialize(NbtOps.INSTANCE, source, Platform.getFrozenRegistry()).getOrThrow();
			var plan = PhotonStatePlanUtil.compile(source, tag, TransformReferenceOwner::new);
			var target = new TransformReferenceOwner();
			plan.transfer(source, target);
			require(sourceId.equals(target.reference.getTransformId()), "UUID 必须转移");
			require(source.reference != target.reference, "UUID 引用必须独占");
			target.reference.setTransformId(UUID.randomUUID());
			require(sourceId.equals(source.reference.getTransformId()), "修改副本不得改写源 UUID");
			require(plan.assignmentCount() == 1, "必须编译实际引用字段");
			require(plan.fallbackReasons().stream().noneMatch(reason -> reason.contains("readPersisted/writePersisted")), "不得回退序列化复制");
		} finally {
			active.add("cleanup", cleanup(null, null));
		}
		pass("transformReferenceTransfersAsIndependentUuidReference", active);
	}

	private void numberFunction3TransfersDeepCopies() {
		try {
			world();
			var source = new NumberFunction3Owner();
			source.function = new NumberFunction3(new Constant(1), new RandomConstant(2, 3), new Constant(4));
			var tag = (CompoundTag) PersistedParser.serialize(NbtOps.INSTANCE, source, Platform.getFrozenRegistry()).getOrThrow();
			var plan = PhotonStatePlanUtil.compile(source, tag, NumberFunction3Owner::new);
			var target = new NumberFunction3Owner();
			plan.transfer(source, target);
			var copied = target.function;
			require(source.function != copied, "NumberFunction3 及分量必须深复制");
			require(source.function.x != copied.x, "NumberFunction3 及分量必须深复制");
			require(source.function.y != copied.y, "NumberFunction3 及分量必须深复制");
			require(source.function.z != copied.z, "NumberFunction3 及分量必须深复制");
			require(source.function.get(0.5f, () -> 0.5f).equals(copied.get(0.5f, () -> 0.5f)), "副本采样值必须一致");
			var original = source.function.get(0.5f, () -> 0.5f);
			copied.x = new Constant(99);
			require(original.equals(source.function.get(0.5f, () -> 0.5f)), "修改副本不得改写源函数");
			require(plan.assignmentCount() == 1, "必须编译实际函数字段");
		} finally {
			active.add("cleanup", cleanup(null, null));
		}
		pass("numberFunction3TransfersDeepCopies", active);
	}

	private static class TransformReferenceOwner {
		@Persisted
		private TransformRef reference = new TransformRef();
	}

	private static class NumberFunction3Owner {
		@Persisted
		private NumberFunction3 function = new NumberFunction3(new Constant(0), new Constant(0), new Constant(0));
	}

	private interface Body {
		void run() throws Exception;
	}
}
