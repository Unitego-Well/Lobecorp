package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import net.neoforged.fml.ModList;
import org.unitego.lobecorp.client.photon.runtime.QueenOwnedLaserEffects;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.registry.world.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.world.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/// 独立 Run 负载与完整 renderFrame 入口采样，不启动剖析器或操作系统焦点。
public class PhotonRunMatrixCapture {
	public static final String RUN_PROPERTY = "lobecorp.photon.run.runId";
	public static final String BATCH_PROPERTY = "lobecorp.photon.run.batchId";
	public static final String SOURCE_COMMIT_PROPERTY = "lobecorp.photon.run.sourceCommit";
	public static final String SOURCE_VARIANT_PROPERTY = "lobecorp.photon.run.sourceVariant";
	public static final String SHADER_PROPERTY = "lobecorp.photon.run.expectedShader";
	public static final String EXPECTED_DIMENSIONS_PROPERTY = "lobecorp.photon.run.expectedDimensions";
	private static volatile Session session;

	public enum Variant {
		BLANK45("blank45", 45), COW25("cow25", 25), QUEEN25("queen25", 25), LASER3("laser3", 45);

		private final String label;
		private final int seconds;

		private Variant(String label, int seconds) {
			this.label = label;
			this.seconds = seconds;
		}

		public String id() {
			return "lobecorp.photon-12-run-" + label;
		}

		public int seconds() {
			return seconds;
		}

		public static Variant selected() {
			for (var value : values()) {
				if (value.id().equals(System.getProperty("modBench.scenarios", ""))) {
					return value;
				}
			}
			return null;
		}
	}

	private record World(ServerLevel level, Vec3 origin) {
	}

	private record Frame(long head, long interval, int width, int height, boolean focused,
	                     boolean iconified, boolean maximized) {
	}

	public static void setup(BenchClientContext context, Variant variant) {
		require(session == null && variant == Variant.selected(), "run_session_conflict");
		session = new Session(variant);
		try {
			session.prepare(context.minecraft());
		} catch (RuntimeException | LinkageError problem) {
			session.failure = problem;
			session.prepared = CompletableFuture.failedFuture(problem);
		}
	}

	@SuppressWarnings("resource")
	public static void observeSkillTick(LivingEntity entity) {
		var value = session;
		if (value == null || entity.level().isClientSide() || !entity.getUUID().equals(value.target)
				|| value.variant != Variant.LASER3 || value.cleanup != null || value.failure != null) {
			return;
		}

		try {
			value.observeLaser(entity);
		} catch (RuntimeException | LinkageError problem) {
			value.failure = problem;
		}
	}

	public static void observeEntitySubmit(EntityRenderState state) {
		var value = session;
		if (value == null || value.finished || value.start == 0 || value.target == null || state == null) {
			return;
		}

		var expected = value.variant == Variant.COW25 ? EntityType.COW
				: AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get();
		if (state.entityType == expected && value.variant != Variant.BLANK45) {
			value.targetSubmissions++;
			value.lastSubmission = System.nanoTime();
		}
	}

	public static void beforeFrame(Minecraft minecraft) {
		var value = session;
		if (value == null || value.finished || value.failure != null || !value.prepared.isDone()) {
			return;
		}
		try {
			value.frame(minecraft);
		} catch (RuntimeException | LinkageError problem) {
			value.failure = problem;
		}
	}

	public static BenchClientStepResult step(BenchClientContext context) throws Exception {
		var value = session;
		require(value != null, "run_not_setup");
		if (value.prepared.isCompletedExceptionally()) {
			try {
				value.prepared.join();
			} catch (RuntimeException problem) {
				value.failure = problem;
			}
		}
		if (value.failure != null) {
			value.cleanup();
			if (!value.cleanup.isDone()) {
				return BenchClientStepResult.CONTINUE;
			}
			try {
				value.cleanup.join();
			} catch (RuntimeException problem) {
				value.failure.addSuppressed(problem);
			}
			value.archive(context, false);
			throw new IllegalStateException("run_failed", value.failure);
		}
		if (!value.finished) {
			return BenchClientStepResult.CONTINUE;
		}
		if (value.cleanup == null) {
			value.cleanup();
		}
		if (!value.cleanup.isDone()) {
			return BenchClientStepResult.CONTINUE;
		}
		try {
			value.cleanup.join();
			value.archive(context, true);
		} catch (Exception problem) {
			value.failure = problem;
			value.archive(context, false);
			throw problem;
		}
		return BenchClientStepResult.COMPLETE;
	}

	public static void verify(BenchClientContext context) {
		require(session != null && session.archived && session.finished && session.failure == null,
				"run_not_complete");
	}

	public static void teardown(BenchClientContext context) throws Exception {
		var value = session;
		if (value == null) {
			return;
		}
		value.cleanup();
		if (!value.archived) {
			value.failure = value.failure == null ? new IllegalStateException("run_interrupted") : value.failure;
			value.archive(context, false);
		}
		value.cleanup();
		// teardown 可能位于等待服务端的客户端线程；清理已排队，不能阻塞等待。
		if (value.cleanup.isDone()) {
			value.cleanup.join();
		}
		session = null;
	}

	private static void require(boolean condition, String reason) {
		if (!condition) {
			throw new IllegalStateException(reason);
		}
	}

	private static class Session {
		private final Variant variant;
		private final String runId = System.getProperty(RUN_PROPERTY, "");
		private final String batchId = System.getProperty(BATCH_PROPERTY, "");
		private final String commit = System.getProperty(SOURCE_COMMIT_PROPERTY, "");
		private final String source = System.getProperty(SOURCE_VARIANT_PROPERTY, "");
		private final boolean shader = Boolean.parseBoolean(System.getProperty(SHADER_PROPERTY, ""));
		private String dimensions = System.getProperty(EXPECTED_DIMENSIONS_PROPERTY, "");
		private final boolean pilot = "auto".equals(dimensions);
		private final String label = "run_" + UUID.randomUUID().toString().replace("-", "");
		private final List<Frame> frames = new ArrayList<>();
		private final JsonArray actions = new JsonArray();
		private CompletableFuture<World> prepared;
		private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
		private volatile CompletableFuture<Void> cleanup;
		private volatile UUID target;
		private volatile int targetEntityId = -1;
		private long firstSeen;
		private long lastSeen;
		private int visibleFrames;
		private long targetSubmissions;
		private long lastSubmission;
		private final JsonArray laserTransitions = new JsonArray();
		private int completedCasts;
		private JsonObject currentCast;
		private boolean laserActive;
		private final QueenOwnedLaserEffects.Statistics ownedBefore = QueenOwnedLaserEffects.statistics();
		private QueenOwnedLaserEffects.Statistics ownedAfter;
		private boolean tailsFinished;
		private JsonObject environment;
		private static final int MAX_FRAMES = 131072;
		private long start;
		private long startEpochMs;
		private long clockPairUncertaintyNs;
		private long stop;
		private long previous;
		private int scheduled;
		private boolean finished;
		private boolean archived;
		private volatile Throwable failure;

		private Session(Variant variant) {
			this.variant = variant;
			require(!runId.isBlank() && !batchId.isBlank() && !commit.isBlank() && !source.isBlank(),
					"run_identity_missing");
			require(pilot || dimensions.matches("[0-9]+x[0-9]+"), "run_dimensions_missing");
			require(List.of("true", "false").contains(System.getProperty(SHADER_PROPERTY, "")),
					"run_shader_missing");
		}

		private void prepare(Minecraft minecraft) {
			var server = minecraft.getSingleplayerServer();
			require(server != null && minecraft.level != null && minecraft.player != null, "run_world_missing");
			var playerId = minecraft.player.getUUID();
			var origin = new Vec3(0.5, minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5);
			prepared = CompletableFuture.supplyAsync(() -> {
				var level = server.overworld();
				require(server.getWorldData().getLevelName().equals(
						System.getProperty("modBench.client.worldId", "") + "-flat"), "run_world_identity");
				require("flat".equals(System.getProperty("modBench.client.worldPreset", "")), "run_flat_required");
				require(server.clockManager().moveToTimeMarker(level.dimensionType().defaultClock().orElseThrow(),
						ClockTimeMarkers.NOON), "run_noon");
				level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
				level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
				level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
				server.setWeatherParameters(0, 0, false, false);
				server.setDifficulty(Difficulty.NORMAL, true);
				var player = server.getPlayerList().getPlayer(playerId);
				require(player != null, "run_player_missing");
				player.teleportTo(level, origin.x, origin.y, origin.z - 10, Set.of(), 0, 0, false);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				require(level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(64),
								entity -> entity.getType() == EntityType.COW
										|| entity.getType() == AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get()).isEmpty(),
						"run_existing_target");
				return new World(level, origin);
			}, server);
		}

		private void frame(Minecraft minecraft) {
			var world = prepared.join();
			require(minecraft.level != null && minecraft.player != null, "run_world_lost");
			minecraft.player.setPos(world.origin.x, world.origin.y, world.origin.z - 10);
			minecraft.player.setDeltaMovement(Vec3.ZERO);
			minecraft.player.setYRot(0);
			minecraft.player.setXRot(0);
			var window = minecraft.getWindow();
			if (pilot && start == 0 && minecraft.screen == null && minecraft.getOverlay() == null) {
				dimensions = window.getWidth() + "x" + window.getHeight();
			}
			require(pilot && start == 0 || dimensions.equals(window.getWidth() + "x" + window.getHeight()), "run_dimensions_changed");
			require(IrisCompat.isUsingShaderPack() == shader, "run_shader_changed");
			if (start == 0 && (minecraft.screen != null || minecraft.getOverlay() != null)) {
				return;
			}
			long now = System.nanoTime();
			if (start == 0) {
				start = now;
				startEpochMs = System.currentTimeMillis();
				clockPairUncertaintyNs = System.nanoTime() - now;
			}
			require(frames.size() < MAX_FRAMES, "run_frame_capacity");
			observe(minecraft, now);
			frames.add(new Frame(now, previous == 0 ? 0 : now - previous, window.getWidth(), window.getHeight(),
					GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE,
					GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE,
					GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE));
			previous = now;
			double elapsed = (now - start) / 1e9;
			if (scheduled == 0 && elapsed >= 5) {
				dispatch(world, 0, start + 5_000_000_000L);
				scheduled++;
			}
			if (variant == Variant.LASER3 && scheduled >= 1 && scheduled <= 3
					&& elapsed >= scheduled * 10) {
				dispatch(world, scheduled, start + scheduled * 10_000_000_000L);
				scheduled++;
			}
			if (elapsed >= variant.seconds) {
				ownedAfter = QueenOwnedLaserEffects.statistics();
				tailsFinished = ownedAfter.started() - ownedBefore.started() == 3
						&& ownedAfter.playing() == 0 && ownedAfter.tails() == 0
						&& QueenBeamEffects.retentionStatistics().playing() == 0;
				stop = now;
				finished = true;
			}
		}

		private void observe(Minecraft minecraft, long now) {
			var level = minecraft.level;
			var player = minecraft.player;
			if (level == null || player == null) {
				throw new IllegalStateException("run_world_lost");
			}
			var window = minecraft.getWindow();
			require(GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED) != GLFW.GLFW_TRUE, "run_minimized");
			if (environment == null) {
				environment = new JsonObject();
				environment.addProperty("vsync", minecraft.options.enableVsync().get());
				environment.addProperty("configured_cap", minecraft.options.framerateLimit().get());
				environment.addProperty("actual_cap", minecraft.gameRenderer.getGameRenderState().framerateLimit);
				environment.addProperty("render_distance", minecraft.options.renderDistance().get());
				environment.addProperty("simulation_distance", minecraft.options.simulationDistance().get());
			} else {
				require(environment.get("vsync").getAsBoolean() == minecraft.options.enableVsync().get()
								&& environment.get("configured_cap").getAsInt() == minecraft.options.framerateLimit().get()
								&& environment.get("actual_cap").getAsInt() == minecraft.gameRenderer.getGameRenderState().framerateLimit
								&& environment.get("render_distance").getAsInt() == minecraft.options.renderDistance().get()
								&& environment.get("simulation_distance").getAsInt() == minecraft.options.simulationDistance().get(),
						"run_environment_changed");
			}
			if (target != null && variant != Variant.BLANK45) {
				var entity = level.getEntity(targetEntityId);
				require(entity == null || target.equals(entity.getUUID()), "run_client_target_identity");
				if (entity != null && entity.isAlive() && !entity.isInvisible()) {
					if (firstSeen == 0) {
						firstSeen = now;
					}
					lastSeen = now;
					if (player.hasLineOfSight(entity)) {
						visibleFrames++;
					}
				} else {
					require(firstSeen == 0, "run_client_target_lost");
				}
				require(firstSeen != 0 || now - start < 6_000_000_000L, "run_client_target_never_seen");
			}
		}

		private void observeLaser(LivingEntity entity) {
			var skills = IEntitySkillAccess.get(entity);
			require(skills != null, "run_server_skills_missing");
			long active = skills.activeSkills().stream().filter(runtime -> runtime.skill() instanceof LaserSkill).count();
			require(active <= 1, "run_server_laser_overlap");
			if (active > 0 && !laserActive) {
				require(currentCast != null, "run_unrequested_laser");
				currentCast.addProperty("active_ns", System.nanoTime());
				laserActive = true;
			} else if (active == 0 && laserActive) {
				currentCast.addProperty("ended_ns", System.nanoTime());
				completedCasts++;
				laserActive = false;
			}
		}

		private void dispatch(World world, int action, long due) {
			long requested = System.nanoTime();
			pending = pending.thenRunAsync(() -> {
				try {
					long actual = System.nanoTime();
					var row = new JsonObject();
					row.addProperty("action", action);
					row.addProperty("due_ns", due);
					row.addProperty("requested_ns", requested);
					row.addProperty("actual_ns", actual);
					require(actual - due <= 250_000_000L, "run_dispatch_late");
					if (action == 0 && variant != Variant.BLANK45) {
						var type = variant == Variant.COW25 ? EntityType.COW
								: AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get();
						var entity = type.create(world.level, EntitySpawnReason.COMMAND);
						require(entity != null, "run_spawn_failed");
						entity.setPos(world.origin);
						entity.setNoAi(true);
						entity.setInvulnerable(true);
						entity.setPersistenceRequired();
						entity.setYRot(variant == Variant.LASER3 ? 0 : 180);
						entity.setYHeadRot(variant == Variant.LASER3 ? 0 : 180);
						entity.yBodyRot = variant == Variant.LASER3 ? 0 : 180;
						require(world.level.addFreshEntity(entity), "run_spawn_rejected");
						target = entity.getUUID();
						targetEntityId = entity.getId();
						row.addProperty("target_uuid", target.toString());
					} else if (action > 0) {
						var entity = world.level.getEntity(target);
						require(entity instanceof LivingEntity && entity.isAlive(), "run_target_lost");
						var skills = IEntitySkillAccess.get((LivingEntity) entity);
						require(skills != null && !skills.hasActiveSkills(), "run_skill_overlap");
						require(completedCasts == action - 1, "run_previous_laser_not_completed");
						currentCast = new JsonObject();
						currentCast.addProperty("ordinal", action);
						laserTransitions.add(currentCast);
						var result = skills.cast(TheQueenOfHatredSkills.LASER.get(), null, world.origin.add(0, 0, 16));
						row.addProperty("status", result.status().name());
						require(result.started(), "run_cast_failed");
						observeLaser((LivingEntity) entity);
					}
					synchronized (actions) {
						actions.add(row);
					}
				} catch (RuntimeException | LinkageError problem) {
					failure = problem;
					throw problem;
				}
			}, Minecraft.getInstance().getSingleplayerServer());
		}

		private void cleanup() {
			if (cleanup != null) {
				return;
			}
			cleanup = prepared.handle((world, _) -> world).thenCompose(world -> {
				if (world == null) {
					return CompletableFuture.completedFuture(null);
				}
				return pending.handle((_, _) -> null).thenRunAsync(() -> {
					if (target != null) {
						var entity = world.level.getEntity(target);
						if (entity != null) {
							entity.discard();
						}
						require(world.level.getEntity(target) == null, "run_cleanup_failed");
					}
				}, world.level.getServer());
			});
		}

		private void archive(BenchClientContext context, boolean complete) throws Exception {
			if (archived) {
				return;
			}
			if (complete) {
				require(start > 0 && stop - start >= variant.seconds * 1_000_000_000L, "run_window_short");
				require(scheduled == (variant == Variant.LASER3 ? 4 : 1), "run_actions_incomplete");
				require(!pending.isCompletedExceptionally(), "run_actions_failed");
				if (variant != Variant.BLANK45) {
					require(firstSeen > 0 && lastSeen >= stop && targetSubmissions > 0 && lastSubmission >= firstSeen,
							"run_client_renderer_submission");
				}
				if (variant == Variant.LASER3) {
					require(completedCasts == 3 && !laserActive, "run_server_lasers_incomplete");
					require(ownedAfter != null && tailsFinished, "run_tails_unfinished");
					require(ownedAfter.started() - ownedBefore.started() == 3
							&& ownedAfter.errors() == ownedBefore.errors()
							&& ownedAfter.fallbacks() == ownedBefore.fallbacks()
							&& ownedAfter.overlaps() == ownedBefore.overlaps(), "run_owned_failures");
					var components = ownedAfter.components().stream()
							.filter(value -> value.ordinal() > ownedBefore.started()).toList();
					require(components.size() == 3 && components.stream().allMatch(value ->
							value.created().containsAll(PhotonOwnedLaser.COMPONENTS)
									&& value.activeObserved().containsAll(PhotonOwnedLaser.COMPONENTS)), "run_components_incomplete");
				}
			}
			var csv = new StringBuilder("head_ns,interval_ns,width,height,focused,iconified,maximized\n");
			for (var frame : frames) {
				csv.append(frame.head).append(',').append(frame.interval).append(',').append(frame.width)
						.append(',').append(frame.height).append(',').append(frame.focused).append(',')
						.append(frame.iconified).append(',').append(frame.maximized).append('\n');
			}
			var artifact = context.artifacts().write(label + "_frames.csv", "text/csv", csv.toString());
			var report = new JsonObject();
			report.addProperty("schema", "photon-run/1");
			report.addProperty("runId", runId);
			report.addProperty("batchId", batchId);
			report.addProperty("sourceCommit", commit);
			report.addProperty("sourceVariant", source);
			report.addProperty("scenario", variant.id());
			report.addProperty("pid", ProcessHandle.current().pid());
			report.addProperty("shader", shader);
			report.addProperty("dimensions", dimensions);
			report.addProperty("started_ns", start);
			report.addProperty("started_epoch_ms", startEpochMs);
			report.addProperty("clock_pair_uncertainty_ns", clockPairUncertaintyNs);
			report.addProperty("stopped_ns", stop);
			report.addProperty("frame_count", frames.size());
			report.addProperty("complete", complete);
			report.addProperty("pilot", pilot);
			report.addProperty("performance_matrix_eligible", complete && !pilot);
			report.addProperty("failure", failure == null ? null : failure.toString());
			report.addProperty("frames", artifact.toString());
			report.addProperty("interval_scope", "adjacent Minecraft.renderFrame HEAD; full window including tick, swap and limiter");
			report.addProperty("profilers_required", false);
			report.addProperty("gpu_ns_status", "unknown_not_sampled");
			report.addProperty("allocation_bytes_status", "unknown_not_sampled");
			report.addProperty("first_seen_ns", firstSeen);
			report.addProperty("last_seen_ns", lastSeen);
			report.addProperty("line_of_sight_frames", visibleFrames);
			report.addProperty("target_renderer_submissions", targetSubmissions);
			report.addProperty("last_renderer_submission_ns", lastSubmission);
			report.addProperty("renderer_scope", "EntityRenderDispatcher.submit HEAD; isolated target type, not GPU completion");
			report.add("server_laser_transitions", laserTransitions.deepCopy());
			report.addProperty("server_completed_casts", completedCasts);
			report.addProperty("tails_finished", tailsFinished);
			report.add("owned_before", new Gson().toJsonTree(ownedBefore));
			report.add("owned_after", new Gson().toJsonTree(ownedAfter));
			report.add("environment", environment);
			report.addProperty("loaded_class", PhotonRunMatrixCapture.class.getProtectionDomain().getCodeSource().getLocation().toString());
			var mods = new JsonArray();
			for (var mod : ModList.get().getMods()) {
				var item = new JsonObject();
				item.addProperty("id", mod.getModId());
				item.addProperty("version", mod.getVersion().toString());
				var file = ModList.get().getModFileById(mod.getModId());
				item.addProperty("file", file == null ? "unknown" : file.getFile().getFilePath().toString());
				mods.add(item);
			}
			report.add("loaded_mods", mods);
			report.add("vm_arguments", new Gson().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
			synchronized (actions) {
				report.add("actions", actions.deepCopy());
			}
			context.artifacts().write(label + "_result.json", "application/json", report.toString());
			archived = true;
		}
	}
}
