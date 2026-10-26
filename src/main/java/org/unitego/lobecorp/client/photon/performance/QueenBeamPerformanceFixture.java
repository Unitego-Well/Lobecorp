package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.registry.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 真实技能的开发夹具；创建、施放和清理只在所属服务器线程执行，不改技能生命周期。
public class QueenBeamPerformanceFixture {
	/// 目标与女皇保持十六格，沿用既有单束采样距离。
	public static final double TARGET_DISTANCE = 16;
	/// 只改变夹具靶的生命上限，技能原有伤害计算保持不变。
	public static final double TARGET_HEALTH = 1024;
	/// 多束夹具沿 X 轴按六格排列，平行布局方向固定为正 Z。
	private static final double LANE_SPACING = 6;
	/// 交汇靶区按一格分开，避免独占 Husk 在相同位置互推，最远目标仍在二十格射程内。
	private static final double CONVERGING_TARGET_START = 14;
	private static final double CONVERGING_TARGET_SPACING = 1;
	private static final String FIXTURE_TAG = "laser_profile_fixture";
	private static final Path BENCH_CLIENT_DIRECTORY = Path.of("build", "modBench", "runs", "default", "client");
	private final MinecraftServer ownerServer;
	private final ResourceKey<Level> dimension;
	private final List<Member> members;
	private final boolean converging;

	public record Member(UUID queenUuid, UUID targetUuid, int queenId, double targetHealth, Vec3 targetSpawn) {
	}

	public record CastResult(UUID queenUuid, int queenId, String status, boolean started) {
	}

	private record Pair(TheQueenOfHatred queen, LivingEntity target, IEntitySkillAccess skills, Vec3 targetSpawn) {
	}

	private QueenBeamPerformanceFixture(MinecraftServer ownerServer, ResourceKey<Level> dimension, List<Member> members, boolean converging) {
		this.ownerServer = ownerServer;
		this.dimension = dimension;
		this.members = List.copyOf(members);
		this.converging = converging;
	}

	/// 只用于显式 Photon Bench 的隔离世界；旧真实激光 Trial 不调用，不冻结其原世界。
	public static void prepareBenchWorld(MinecraftServer server) {
		requireServerThread(server);
		var worldId = System.getProperty("modBench.client.worldId", "");
		var preset = System.getProperty("modBench.client.worldPreset", "normal");
		var expectedName = preset.equals("normal") ? worldId : worldId + "-" + preset;

		if (worldId.isBlank() || !System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-")
				|| !server.getWorldData().getLevelName().equals(expectedName)) {
			throw new IllegalStateException("profile_fixture_requires_isolated_bench_world");
		}

		requireBenchWorldPath(server, expectedName);
		var level = server.overworld();
		var clock = level.dimensionType().defaultClock().orElseThrow();

		if (!server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON)) {
			throw new IllegalStateException("profile_fixture_noon_marker_missing");
		}

		var rules = level.getGameRules();
		rules.set(GameRules.ADVANCE_TIME, false, server);
		rules.set(GameRules.ADVANCE_WEATHER, false, server);
		rules.set(GameRules.SPAWN_MOBS, false, server);
		server.setWeatherParameters(0, 0, false, false);
		// Bench 默认和平难度会自动移除 Husk；仅在已核对的隔离世界保留真实技能靶。
		server.setDifficulty(Difficulty.NORMAL, true);
	}

	private static void requireBenchWorldPath(MinecraftServer server, String worldName) {
		try {
			var worldId = Path.of(worldName);

			if (worldId.isAbsolute() || worldId.getRoot() != null || worldId.getNameCount() != 1
					|| !worldId.getFileName().toString().equals(worldName) || worldName.equals(".") || worldName.equals("..")) {
				throw new IllegalStateException("profile_fixture_bench_world_id_invalid");
			}

			var gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
			var realGameDirectory = gameDirectory.toRealPath();

			if (!gameDirectory.endsWith(BENCH_CLIENT_DIRECTORY) || !realGameDirectory.endsWith(BENCH_CLIENT_DIRECTORY)) {
				throw new IllegalStateException("profile_fixture_bench_directory_invalid");
			}

			var saves = realGameDirectory.resolve("saves");
			var realSaves = saves.toRealPath();
			var expectedWorld = realSaves.resolve(worldId);
			var actualWorld = server.getWorldPath(LevelResource.ROOT).toRealPath();

			if (!realSaves.equals(saves) || !realSaves.equals(actualWorld.getParent())
					|| !actualWorld.equals(expectedWorld) || !actualWorld.equals(expectedWorld.toRealPath())) {
				throw new IllegalStateException("profile_fixture_bench_world_path_invalid");
			}
		} catch (IOException | InvalidPathException failure) {
			throw new IllegalStateException("profile_fixture_bench_world_path_unavailable", failure);
		}
	}

	/// 原点使用调用者已确认的地面高度；单束不加横向偏移，多束固定为四个独占靶。
	public static QueenBeamPerformanceFixture create(MinecraftServer server, UUID playerId, Vec3 origin, int count) {
		return create(server, playerId, origin, count, false);
	}

	/// 四条真实光束朝前方十四至十七格的靶区汇聚；像素重叠须由实际采样另行确认。
	public static QueenBeamPerformanceFixture createConverging(MinecraftServer server, UUID playerId, Vec3 origin, int count) {
		if (count != 4) {
			throw new IllegalArgumentException("profile_fixture_converging_count_invalid");
		}

		return create(server, playerId, origin, count, true);
	}

	private static QueenBeamPerformanceFixture create(MinecraftServer server, UUID playerId, Vec3 origin, int count, boolean converging) {
		requireServerThread(server);

		if (count != 1 && count != 4 || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) {
			throw new IllegalArgumentException("profile_fixture_configuration_invalid");
		}

		var player = server.getPlayerList().getPlayer(playerId);

		if (player == null) {
			throw new IllegalStateException("profile_player_missing");
		}

		var level = player.level();
		var members = new ArrayList<Member>();

		try {
			for (int index = 0; index < count; index++) {
				double offset = count == 1 ? 0 : (index - (count - 1) / 2.0) * LANE_SPACING;
				double targetX = converging ? origin.x : origin.x + offset;
				double targetZ = origin.z + (converging ? CONVERGING_TARGET_START + index * CONVERGING_TARGET_SPACING : TARGET_DISTANCE);
				var targetPosition = new Vec3(targetX, origin.y, targetZ);
				var queen = new TheQueenOfHatred(level);
				queen.setPos(origin.x + offset, origin.y, origin.z);
				float yaw = converging ? EntityFacingUtil.yaw(targetPosition.subtract(queen.position()), 0) : 0;
				queen.setYRot(yaw);
				queen.setYHeadRot(yaw);
				queen.yBodyRot = yaw;
				queen.setNoAi(true);
				queen.setPersistenceRequired();
				queen.addTag(FIXTURE_TAG);
				var target = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);

				if (target == null) {
					throw new IllegalStateException("profile_target_missing");
				}

				target.setPos(targetPosition);
				target.setNoAi(true);
				target.setPersistenceRequired();
				target.addTag(FIXTURE_TAG);
				var health = target.getAttribute(Attributes.MAX_HEALTH);

				if (health == null) {
					throw new IllegalStateException("profile_target_health_missing");
				}

				health.setBaseValue(TARGET_HEALTH);
				target.setHealth((float) TARGET_HEALTH);
				members.add(new Member(queen.getUUID(), target.getUUID(), queen.getId(), target.getMaxHealth(), targetPosition));

				if (!level.addFreshEntity(target) || !level.addFreshEntity(queen)) {
					throw new IllegalStateException("profile_fixture_spawn_failed");
				}

				CombatUtil.recordAttack(queen, target);

				if (!queen.isHatedTarget(target)) {
					throw new IllegalStateException("profile_target_not_hostile");
				}
			}
		} catch (RuntimeException failure) {
			try {
				clearMembers(level, members);
			} catch (RuntimeException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}

			throw failure;
		}

		return new QueenBeamPerformanceFixture(server, level.dimension(), members, converging);
	}

	public List<Member> members() {
		return members;
	}

	/// 调用公共施法能力；交汇靶仅施放前回位，施放期间保留真实击退，不覆盖伤害、冷却或动画。
	public List<CastResult> cast(MinecraftServer server) {
		requireOwner(server);
		var level = server.getLevel(dimension);

		if (level == null) {
			throw new IllegalStateException("profile_fixture_level_missing");
		}

		var pairs = new ArrayList<Pair>();

		for (var member : members) {
			if (!(level.getEntity(member.queenUuid()) instanceof TheQueenOfHatred queen)
					|| !(level.getEntity(member.targetUuid()) instanceof LivingEntity target)
					|| !queen.entityTags().contains(FIXTURE_TAG) || !target.entityTags().contains(FIXTURE_TAG)) {
				throw new IllegalStateException("profile_fixture_removed");
			}

			var access = IEntitySkillAccess.get(queen);

			if (access == null) {
				throw new IllegalStateException("profile_skill_capability_missing");
			}

			pairs.add(new Pair(queen, target, access, member.targetSpawn()));
		}

		if (converging) {
			for (var pair : pairs) {
				pair.target().setPos(pair.targetSpawn());
				pair.target().setDeltaMovement(Vec3.ZERO);
			}
		}

		var results = new ArrayList<CastResult>();

		for (var pair : pairs) {
			var queen = pair.queen();
			var target = pair.target();
			target.setHealth(target.getMaxHealth());
			CombatUtil.recordAttack(queen, target);
			var result = pair.skills().cast(TheQueenOfHatredSkills.LASER.get(), target, target.position());
			results.add(new CastResult(queen.getUUID(), queen.getId(), result.status().name(), result.started()));
		}

		return List.copyOf(results);
	}

	/// 同步清理完成后才返回；调用者可在 server.execute 的 future 中确认完成。
	public void clear(MinecraftServer server) {
		requireOwner(server);
		var level = server.getLevel(dimension);

		if (level != null) {
			clearMembers(level, members);
		}
	}

	private static void clearMembers(ServerLevel level, List<Member> members) {
		RuntimeException problem = null;

		for (var member : members) {
			if (level.getEntity(member.queenUuid()) instanceof TheQueenOfHatred queen && queen.entityTags().contains(FIXTURE_TAG)) {
				try {
					var access = IEntitySkillAccess.get(queen);

					if (access != null) {
						access.cancelAll(true);
					}
				} catch (RuntimeException failure) {
					if (problem == null) {
						problem = failure;
					} else {
						problem.addSuppressed(failure);
					}
				} finally {
					queen.discard();
				}
			}

			var target = level.getEntity(member.targetUuid());

			if (target != null && target.entityTags().contains(FIXTURE_TAG)) {
				target.discard();
			}
		}

		if (problem != null) {
			throw problem;
		}
	}

	/// 只清理带夹具 tag 的遗留对象；调用前世界路径已由 prepareBenchWorld 校验，归属不明实体不删除。
	public static int discardOrphanedFixtures(ServerLevel level) {
		int removed = 0;

		for (var entity : level.getAllEntities()) {
			if (entity.entityTags().contains(FIXTURE_TAG)) {
				entity.discard();
				removed++;
			}
		}

		return removed;
	}

	/// 统计夹具原点附近实体；带 tag 的计数与归属不明样本分开，供报告核对背景实体来源。
	public static JsonObject census(ServerLevel level, Vec3 center, double radius) {
		var result = new JsonObject();
		var counts = new JsonObject();
		var foreign = new JsonArray();
		double limit = radius * radius;
		int total = 0;
		int tagged = 0;
		int foreignCount = 0;

		for (var entity : level.getAllEntities()) {
			if (entity.position().distanceToSqr(center) > limit) {
				continue;
			}

			total++;
			String type = EntityType.getKey(entity.getType()).toString();
			counts.addProperty(type, counts.has(type) ? counts.get(type).getAsInt() + 1 : 1);

			if (entity.entityTags().contains(FIXTURE_TAG)) {
				tagged++;
				continue;
			}

			foreignCount++;

			if (foreign.size() < 50) {
				var item = new JsonObject();
				item.addProperty("type", type);
				item.addProperty("uuid", entity.getUUID().toString());
				item.addProperty("x", entity.getX());
				item.addProperty("y", entity.getY());
				item.addProperty("z", entity.getZ());
				foreign.add(item);
			}
		}

		result.addProperty("radius", radius);
		result.addProperty("total_near_origin", total);
		result.add("counts", counts);
		result.addProperty("fixture_tagged", tagged);
		result.addProperty("foreign", foreignCount);
		result.add("foreign_sample", foreign);
		return result;
	}

	private void requireOwner(MinecraftServer server) {
		requireServerThread(server);

		if (server != ownerServer) {
			throw new IllegalStateException("profile_fixture_server_changed");
		}
	}

	private static void requireServerThread(MinecraftServer server) {
		if (!server.isSameThread()) {
			throw new IllegalStateException("profile_fixture_requires_server_thread");
		}
	}
}
