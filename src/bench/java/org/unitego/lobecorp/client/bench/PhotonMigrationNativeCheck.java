package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
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
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;
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

	public boolean step(BenchClientContext context, String label) throws Exception {
		if (complete) {
			return true;
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
		verifyProjectIsolation();
		verifyProjectValueIO();
		complete = true;
		var report = new JsonObject();
		report.addProperty("main_window_sample", false);
		report.addProperty("passed", true);
		report.addProperty("fixture_scope", "empty-node native copy, HYBRID reset, private source and world isolation");
		report.addProperty("coverage_complete", true);
		report.addProperty("project_valueio", "PASSED: composition, knownAs and unknown author field");
		report.addProperty("fxinstance_placement", "PASSED: native and HYBRID expanded placement/template/source parity");
		report.addProperty("project_source_isolation", "PASSED: independent scopes/state and inherited private resolver");
		context.artifacts().write(label + "_migration-contracts.json", "application/json", report.toString());
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
			var archive = PhotonModelPackExportUtil.capture("lobecorp", "migration_instance_fixture", restored,
					Platform.getFrozenRegistry(), false);
			require(archive.definitions().containsKey("assets/lobecorp/fx/migration_instance_fixture.fx"), "instance_export_root_missing");
		} catch (java.io.IOException failure) {
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

	private static void verifyProjectIsolation() {
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
		owner.config.template.setTransform(target.transform());
		fx.getFxData().objects().addAll(List.of(target, nested, owner));
		var first = PhotonRuntimeFactory.nativeRuntime(fx);
		var second = PhotonRuntimeFactory.nativeRuntime(fx);
		var definitionA = fixture();
		var definitionB = fixture();
		PhotonSpawnTemplateUtil.Template a = null;
		PhotonSpawnTemplateUtil.Template b = null;
		try {
			var firstOwner = (PhotonChildSpawner) first.objects.get(owner.id());
			var secondOwner = (PhotonChildSpawner) second.objects.get(owner.id());
			firstOwner.source(first, Set.of());
			secondOwner.source(second, Set.of());
			firstOwner.ownedDefinitionResolver(_ -> definitionA);
			secondOwner.ownedDefinitionResolver(_ -> definitionB);
			a = PhotonSpawnTemplateUtil.create(firstOwner);
			b = PhotonSpawnTemplateUtil.create(secondOwner);
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
			first.destroy(true);
			second.destroy(true);
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
