package org.unitego.lobecorp.client.bench;

import org.unitego.lobecorp.util.photon.editor.PhotonFxPackExportUtil;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import org.unitego.lobecorp.util.photon.creation.PhotonIndexedMap;

import java.io.IOException;
import java.util.UUID;
import java.util.Objects;

import net.minecraft.client.Minecraft;

import java.util.ConcurrentModificationException;

import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.unitego.lobecorp.client.photon.editor.PhotonCompositionProjectAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.util.photon.playback.PhotonSpawnTemplateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

import java.util.concurrent.TimeUnit;

/// 仅归档后推进，屏障取真实tick与完成帧。
public class PhotonMigrationNativeCheck implements AutoCloseable {
	private FX source;
	private FX other;
	private PhotonRuntimeFactory.Definition definition;
	private PhotonRuntimeFactory.Lease released;
	private FXRuntime backend;
	private final Object world = new Object();
	private long tick;
	private long frame;
	private long releasedNs;
	private boolean complete;
	private boolean baseComplete;
	private FXRuntime projectScopeA;
	private FXRuntime projectScopeB;
	private PhotonChildSpawner projectOwnerA;
	private PhotonChildSpawner projectOwnerB;
	private FXRuntime projectBackend;
	private PhotonRuntimeFactory.Lease projectReleased;
	private UUID projectTarget;
	private UUID projectNested;
	private FX projectDefinitionA;
	private FX projectDefinitionB;
	private FX naturalSource;
	private PhotonRuntimeFactory.Definition naturalDefinition;
	private PhotonRuntimeFactory.Lease naturalLease;
	private FXRuntime naturalBackend;
	private ParticleEmitter naturalEmitter;
	private boolean naturalReleased;
	private boolean naturalDrained;
	private long phaseStartedNs;
	private boolean projectResetVerified;
	private boolean naturalDrainVerified;

	public boolean step(BenchClientContext context, String label) throws Exception {
		if (complete) {
			return true;
		}
		if (baseComplete) {
			return completeContracts(context, label);
		}
		if (source == null) {
			source = fixture();
			other = fixture();
			definition = PhotonRuntimeFactory.registerPreparedSource(source, new PhotonRuntimeFactory.PreparedSourceKey("migration-fixture-a", 1));
			var second = PhotonRuntimeFactory.registerPreparedSource(other, new PhotonRuntimeFactory.PreparedSourceKey("migration-fixture-b", 1));
			require(definition != second, "private_source_alias");
			var isolated = PhotonRuntimeFactory.acquire(second, world, PhotonRuntimeFactory.Mode.HYBRID);
			try {
				released = PhotonRuntimeFactory.acquire(definition, world, PhotonRuntimeFactory.Mode.HYBRID);
				backend = released.runtime();
				require(backend != isolated.runtime() && !backend.isEmitted(), "fixture_alias_or_emitted");
				backend.objects.values().stream().filter(value -> value != backend.root)
						.forEach(value -> value.transform().localPosition(new Vector3f(9, 8, 7)));
				released.close(true);
			} finally {
				isolated.close(true);
			}
			tick = PhotonRuntimeFactory.ticks();
			frame = PhotonRuntimeFactory.frames();
			releasedNs = System.nanoTime();
			return false;
		}
		if (PhotonRuntimeFactory.ticks() - tick < 2 || PhotonRuntimeFactory.frames() - frame < 2) {
			require(System.nanoTime() - releasedNs < TimeUnit.SECONDS.toNanos(30), "real_barrier_timeout");
			return false;
		}
		long hits = PhotonRuntimeFactory.statistics().get("pool_hit");
		var nativeCopy = PhotonRuntimeFactory.nativeRuntime(source);
		PhotonRuntimeFactory.Lease reused = null;
		PhotonRuntimeFactory.Lease isolatedWorld = null;
		try {
			reused = PhotonRuntimeFactory.acquire(definition, world, PhotonRuntimeFactory.Mode.HYBRID);
			isolatedWorld = PhotonRuntimeFactory.acquire(definition, new Object(), PhotonRuntimeFactory.Mode.HYBRID);
			require(reused.runtime() == backend, "backend_not_reused");
			require(PhotonRuntimeFactory.statistics().get("pool_hit") > hits, "pool_hit_not_recorded");
			require(!released.current() && reused.current(), "lease_ownership");
			require(isolatedWorld.runtime() != backend, "world_scope_alias");
			require(nativeCopy.objects.keySet().equals(backend.objects.keySet()), "native_identity_mismatch");
			for (var entry : nativeCopy.objects.entrySet()) {
				var actual = backend.objects.get(entry.getKey());
				require(actual != entry.getValue(), "native_instance_alias");
				require(actual.transform().localPosition().equals(entry.getValue().transform().localPosition()), "reset_position_mismatch");
			}
		} finally {
			if (isolatedWorld != null) {
				isolatedWorld.close(true);
			}
			if (reused != null) {
				reused.close(true);
			}
			nativeCopy.destroy(true);
			close();
		}
		verifyInstancePlacement();
		beginProjectIsolation();
		baseComplete = true;
		return false;
	}

	private boolean completeContracts(BenchClientContext context, String label) throws Exception {
		require(System.nanoTime() - phaseStartedNs < TimeUnit.SECONDS.toNanos(30), "migration_contract_phase_timeout");
		if (!projectResetVerified) {
			if (PhotonRuntimeFactory.ticks() - tick < 2 || PhotonRuntimeFactory.frames() - frame < 2) {
				return false;
			}
			verifyProjectReset();
			verifyProjectValueIO();
			beginNaturalDrain(context);
			return false;
		}
		if (!stepNaturalDrain()) {
			return false;
		}
		complete = true;
		var report = new JsonObject();
		report.addProperty("main_window_sample", false);
		report.addProperty("passed", true);
		report.addProperty("fixture_scope", "HYBRID empty-node and PROJECT reuse/reset, source/world isolation, live-host non-force particle drain");
		report.addProperty("coverage_complete", true);
		report.addProperty("project_valueio", "PASSED: composition, knownAs and unknown author field");
		report.addProperty("fxinstance_placement", "PASSED: native and HYBRID expanded placement/template/source parity");
		report.addProperty("project_source_isolation", "PASSED: managed PROJECT scopes, inherited private resolver and reused live-state reset");
		report.addProperty("project_reset_verified", projectResetVerified);
		report.addProperty("natural_drain_verified", naturalDrainVerified);
		context.artifacts().write(label + "_migration-contracts.json", "application/json", report.toString());
		close();
		return true;
	}

	private static void verifyInstancePlacement() {
		var authored = new FX();
		var instance = new FXInstanceObject(Lobecorp.id("the_queen_of_hatred/owned_laser_v1_circle"));
		authored.getFxData().objects().add(instance);
		var restored = new FX();
		restored.deserializeNBT(Platform.getFrozenRegistry(), authored.serializeNBT(Platform.getFrozenRegistry()));
		var expected = restored.getFxData().objects().stream().filter(FXInstanceObject.class::isInstance)
				.map(FXInstanceObject.class::cast).findFirst().orElseThrow();
		require(expected.getPlacement() != null && expected.getPlacement().isPlaced(), "instance_source_not_placed");
		try {
			var archive = PhotonFxPackExportUtil.capture("lobecorp", "migration_instance_fixture", restored,
					Platform.getFrozenRegistry());
			require(archive.definitions().containsKey("assets/lobecorp/fx/migration_instance_fixture.fx"), "instance_export_root_missing");
			var sourceId = Objects.requireNonNull(expected.getFx(), "instance_source_missing");
			var sourcePath = "assets/" + sourceId.getNamespace() + "/fx/" + sourceId.getPath() + ".fx";
			var resource = Minecraft.getInstance().getResourceManager()
					.getResource(FXHelper.fileLocation(sourceId)).orElseThrow();
			boolean bundled = resource.sourcePackId().equals("vanilla") || resource.sourcePackId().startsWith("mod");
			require(bundled || archive.definitions().containsKey(sourcePath), "instance_export_dependency_missing");
			require(FXHelper.readTag(sourceId) != null, "instance_export_dependency_unreadable");
		} catch (IOException failure) {
			throw new IllegalStateException("instance_export_dependency_failure", failure);
		}
		var nativeCopy = PhotonRuntimeFactory.nativeRuntime(restored);
		var compiled = PhotonRuntimeFactory.prepare(restored);
		PhotonRuntimeFactory.Lease actual = null;
		try {
			actual = PhotonRuntimeFactory.acquire(compiled, new Object(), PhotonRuntimeFactory.Mode.HYBRID);
			var nativeInstance = nativeCopy.objects.values().stream().filter(FXInstanceObject.class::isInstance)
					.map(FXInstanceObject.class::cast).findFirst().orElseThrow();
			var hybridInstance = actual.runtime().objects.values().stream().filter(FXInstanceObject.class::isInstance)
					.map(FXInstanceObject.class::cast).findFirst().orElseThrow();
			require(nativeInstance.getPlacement() != null && hybridInstance.getPlacement() != null, "instance_placement_lost");
			require(nativeInstance.getPlacement().isPlaced() && hybridInstance.getPlacement().isPlaced(), "instance_placement_not_restored");
			require(nativeInstance.getFx().equals(hybridInstance.getFx()), "instance_source_mismatch");
			require(nativeInstance.getPlacement().status() == hybridInstance.getPlacement().status()
					&& nativeInstance.getPlacement().template().equals(hybridInstance.getPlacement().template()), "instance_template_mismatch");
			require(nativeCopy.objects.keySet().equals(actual.runtime().objects.keySet()), "instance_expanded_objects_mismatch");
			require(nativeInstance != hybridInstance && hybridInstance != expected, "instance_runtime_alias");
		} finally {
			if (actual != null) {
				actual.close(true);
			}
			nativeCopy.destroy(true);
		}
	}

	private void beginProjectIsolation() {
		var fx = new FX();
		var target = new ParticleEmitter();
		target.setName("migration_project_template");
		var nested = new PhotonChildSpawner();
		nested.setName("migration_project_nested");
		nested.config.source = PhotonSpawnConfig.Source.FX;
		nested.config.fxLocation = Lobecorp.id("migration_private_fixture");
		nested.transform()._setInternalParentID(target.id());
		target.transform()._setInternalChildID(new ArrayList<>(List.of(nested.id())));
		var owner = new PhotonChildSpawner();
		owner.setName("migration_project_owner");
		owner.config.source = PhotonSpawnConfig.Source.PROJECT;
		owner.config.includeChildren = true;
		owner.config.template.setTransform(target.transform());
		fx.getFxData().objects().addAll(List.of(target, nested, owner));
		var definition = PhotonRuntimeFactory.prepare(fx);
		var first = PhotonRuntimeFactory.instantiate(definition, PhotonRuntimeFactory.Mode.HYBRID);
		projectScopeA = first;
		var second = PhotonRuntimeFactory.instantiate(definition, PhotonRuntimeFactory.Mode.HYBRID);
		projectScopeB = second;
		var definitionA = fixture();
		var definitionB = fixture();
		PhotonSpawnTemplateUtil.Template a = null;
		PhotonSpawnTemplateUtil.Template b = null;
		try {
			var firstOwner = (PhotonChildSpawner) first.objects.get(owner.id());
			var secondOwner = (PhotonChildSpawner) second.objects.get(owner.id());
			projectOwnerA = firstOwner;
			projectOwnerB = secondOwner;
			projectTarget = target.id();
			projectNested = nested.id();
			projectDefinitionA = definitionA;
			projectDefinitionB = definitionB;
			firstOwner.source(first, Set.of());
			secondOwner.source(second, Set.of());
			firstOwner.ownedDefinitionResolver(_ -> definitionA);
			secondOwner.ownedDefinitionResolver(_ -> definitionB);
			a = PhotonSpawnTemplateUtil.create(firstOwner);
			b = PhotonSpawnTemplateUtil.create(secondOwner);
			require(a.lease() != null && b.lease() != null, "project_unmanaged_fallback");
			projectBackend = a.runtime();
			projectReleased = a.lease();
			require(a.runtime() != b.runtime() && a.scope() == first && b.scope() == second, "project_scope_alias");
			var copied = a.runtime().objects.get(target.id());
			var independent = b.runtime().objects.get(target.id());
			copied.transform().localScale(new Vector3f(3, 4, 5));
			require(!copied.transform().localScale().equals(independent.transform().localScale())
					&& !copied.transform().localScale().equals(first.objects.get(target.id()).transform().localScale()), "project_state_alias");
			var inheritedA = (PhotonChildSpawner) a.runtime().objects.get(nested.id());
			var inheritedB = (PhotonChildSpawner) b.runtime().objects.get(nested.id());
			require(inheritedA.resolveDefinition(nested.config.fxLocation) == definitionA
					&& inheritedB.resolveDefinition(nested.config.fxLocation) == definitionB, "project_private_source_leak");
		} finally {
			if (a != null) {
				a.close(true);
			}
			if (b != null) {
				b.close(true);
			}
		}
		tick = PhotonRuntimeFactory.ticks();
		frame = PhotonRuntimeFactory.frames();
		phaseStartedNs = System.nanoTime();
	}


	private void verifyProjectReset() {
		var live = (ParticleEmitter) projectScopeA.objects.get(projectTarget);
		live.transform().localScale(new Vector3f(2, 3, 4));
		live.config.setDuration(7);
		var oldValues = projectBackend.objects.values();
		long hits = PhotonRuntimeFactory.statistics().get("pool_hit");
		var template = PhotonSpawnTemplateUtil.create(projectOwnerA);
		try {
			require(template.lease() != null && template.runtime() == projectBackend, "project_backend_not_reused");
			require(PhotonRuntimeFactory.statistics().get("pool_hit") > hits, "project_pool_hit_missing");
			require(!projectReleased.current() && template.lease().current(), "project_stale_lease");
			require(template.scope() == projectScopeA, "project_reused_scope_changed");
			var copied = (ParticleEmitter) template.runtime().objects.get(projectTarget);
			require(copied.transform().localScale().equals(live.transform().localScale())
					&& copied.config.getDuration() == live.config.getDuration(), "project_live_state_not_restored");
			require(!copied.transform().localScale().equals(projectScopeB.objects.get(projectTarget).transform().localScale()),
					"project_live_state_scope_leak");
			var child = (PhotonChildSpawner) template.runtime().objects.get(projectNested);
			require(child.resolveDefinition(child.config.fxLocation) == projectDefinitionA, "project_reused_private_source_lost");
			require(projectOwnerB.resolveDefinition(child.config.fxLocation) == projectDefinitionB, "project_other_source_changed");
			require(template.runtime().objects instanceof PhotonIndexedMap<?> indexed && !indexed.isMaterialized(),
					"project_reset_layout_fallback");
			try {
				oldValues.iterator();
				throw new IllegalStateException("project_old_view_rebound");
			} catch (ConcurrentModificationException expected) {
				// 上一租约的视图不能读取刚复位的实例。
			}
			projectResetVerified = true;
		} finally {
			template.close(true);
		}
	}

	private void beginNaturalDrain(BenchClientContext context) {
		naturalSource = new FX();
		var emitter = new ParticleEmitter();
		emitter.config.setLooping(true);
		emitter.config.setDuration(20);
		emitter.config.setStartLifetime(NumberFunction.constant(8));
		emitter.config.emission.setEmissionRate(NumberFunction.constant(1));
		naturalSource.getFxData().objects().add(emitter);
		naturalDefinition = PhotonRuntimeFactory.registerPreparedSource(naturalSource,
				new PhotonRuntimeFactory.PreparedSourceKey("migration-natural-fixture", 1));
		naturalLease = PhotonRuntimeFactory.acquire(naturalDefinition, world, PhotonRuntimeFactory.Mode.HYBRID);
		naturalBackend = naturalLease.runtime();
		naturalEmitter = (ParticleEmitter) naturalBackend.objects.get(emitter.id());
		var level = context.minecraft().level;
		require(level != null, "natural_drain_world_missing");
		var player = Objects.requireNonNull(context.minecraft().player, "natural_drain_player_missing");
		naturalBackend.root.transform().localPosition(new Vector3f((float) player.getX(), (float) player.getY() + 1, (float) player.getZ()));
		naturalBackend.emit(() -> level);
		phaseStartedNs = System.nanoTime();
	}

	private boolean stepNaturalDrain() {
		if (!naturalReleased) {
			if (naturalEmitter.getParticleAmount() == 0) {
				return false;
			}
			naturalLease.close(false);
			require(!naturalLease.current() && naturalEmitter.getParticleAmount() > 0, "natural_tails_force_cleared");
			var early = PhotonRuntimeFactory.acquire(naturalDefinition, world, PhotonRuntimeFactory.Mode.HYBRID);
			try {
				require(early.runtime() != naturalBackend, "natural_draining_backend_reused");
			} finally {
				early.close(true);
			}
			naturalReleased = true;
			return false;
		}
		if (!naturalDrained) {
			if (naturalEmitter.getParticleAmount() != 0 || naturalEmitter.isAlive() || !naturalBackend.isFinished()) {
				return false;
			}
			tick = PhotonRuntimeFactory.ticks();
			frame = PhotonRuntimeFactory.frames();
			naturalDrained = true;
			return false;
		}
		if (PhotonRuntimeFactory.ticks() - tick < 2 || PhotonRuntimeFactory.frames() - frame < 2) {
			return false;
		}
		long hits = PhotonRuntimeFactory.statistics().get("pool_hit");
		var reused = PhotonRuntimeFactory.acquire(naturalDefinition, world, PhotonRuntimeFactory.Mode.HYBRID);
		try {
			require(reused.runtime() == naturalBackend && reused.current() && !naturalLease.current(), "natural_backend_not_reused");
			require(PhotonRuntimeFactory.statistics().get("pool_hit") > hits, "natural_pool_hit_missing");
			require(!naturalBackend.isEmitted() && naturalEmitter.getParticleAmount() == 0, "natural_reset_not_clean");
			naturalDrainVerified = true;
			return true;
		} finally {
			reused.close(true);
		}
	}

	private static void verifyProjectValueIO() {
		var project = new FXProject();
		project.addKnownAs(Lobecorp.id("migration_roundtrip"));
		var composition = new CompoundTag();
		composition.put("definition", PhotonOwnedLaserComposition.serialize(PhotonOwnedLaserComposition.defaults()));
		composition.putString("unknown_author_field", "preserved");
		((PhotonCompositionProjectAccess) project).lobecorp$composition(composition);
		var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, Platform.getFrozenRegistry());
		project.serialize(output);
		var saved = output.buildResult();
		saved.getCompoundOrEmpty("data").putString("migration_unknown_project_field", "preserved");
		var restored = new FXProject();
		restored.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, Platform.getFrozenRegistry(), saved));
		require(composition.equals(((PhotonCompositionProjectAccess) restored).lobecorp$composition()), "composition_valueio_lost");
		require(restored.getKnownAs().equals(project.getKnownAs()), "known_as_lost");
		var second = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, Platform.getFrozenRegistry());
		restored.serialize(second);
		require(second.buildResult().getCompoundOrEmpty("data").getStringOr("migration_unknown_project_field", "").equals("preserved"),
				"unknown_project_field_lost");
	}

	private static FX fixture() {
		var source = new FX();
		source.getFxData().objects().add(new EmptyFXObject());
		return source;
	}

	public void verify() {
		require(complete, "migration_contracts_incomplete");
	}

	@Override
	public void close() {
		if (released != null) {
			released.close(true);
		}
		if (naturalLease != null) {
			naturalLease.close(true);
		}
		if (naturalSource != null) {
			PhotonRuntimeFactory.unregisterPreparedSource(naturalSource);
		}
		if (projectScopeA != null) {
			projectScopeA.destroy(true);
		}
		if (projectScopeB != null) {
			projectScopeB.destroy(true);
		}
		if (source != null) {
			PhotonRuntimeFactory.unregisterPreparedSource(source);
		}
		if (other != null) {
			PhotonRuntimeFactory.unregisterPreparedSource(other);
		}
	}

	private static void require(boolean valid, String reason) {
		if (!valid) {
			throw new IllegalStateException(reason);
		}
	}
}
