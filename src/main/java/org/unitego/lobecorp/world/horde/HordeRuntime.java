package org.unitego.lobecorp.world.horde;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.util.horde.HordeCleanupUtil;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.server.HordeEventConfig;
import org.unitego.lobecorp.util.entity.EntityGrabUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.world.entity.combat.CombatData;
import org.unitego.lobecorp.world.entity.combat.ICombatant;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;
import org.unitego.lobecorp.world.horde.network.HordeSync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/// 服务端事件调度器；内存占用不随累计虚拟影像数量增长。
@SuppressWarnings("resource")
public class HordeRuntime {
	private static final Map<MinecraftServer, HordeRuntime> RUNTIMES = new WeakHashMap<>();
	private final MinecraftServer server;
	private final Map<UUID, Mob> loaded = new LinkedHashMap<>();
	private final Map<String, SpawnRequest> requests = new LinkedHashMap<>();
	private final Map<UUID, Long> blockedSince = new HashMap<>();
	private final Map<String, HordeTargetSpatialState> spatial = new HashMap<>();
	private final List<List<HordeTargetAssignment>> regionGroups = new ArrayList<>();
	private boolean regionsDirty;
	// 只限制归档清理；目标有效性、成员与战斗计时仍逐tick检查。
	private static final int CHECK_INTERVAL = 20;
	private final HordeSync sync = new HordeSync();
	private final MidnightCleanupOpening opening = new MidnightCleanupOpening();
	private boolean stopping;
	private long ticksNanos;
	private long spawned;
	private long scanned;

	private HordeRuntime(MinecraftServer server) {
		this.server = server;
	}

	public static HordeRuntime get(MinecraftServer server) {
		return RUNTIMES.computeIfAbsent(server, HordeRuntime::new);
	}

	public void skipOpening(ServerPlayer player, UUID eventId) {
		opening.skip(player, eventId);
	}

	public static void blockUpdated(ServerLevel level, BlockPos position) {
		var runtime = RUNTIMES.get(level.getServer());
		if (runtime == null) {
			return;
		}
		for (var event : HordeSavedData.get(level.getServer()).events().values()) {
			if (event.dimension().equals(level.dimension())) {
				event.route().invalidate(position);
			}
		}
	}

	public static void stopping(MinecraftServer server) {
		get(server).stopping = true;
	}

	public static void clear(MinecraftServer server) {
		var runtime = RUNTIMES.remove(server);
		if (runtime != null) {
			runtime.opening.clear();
		}
	}

	public static @Nullable LivingEntity assignedTarget(Mob mob) {
		var participant = HordeParticipant.get(mob);
		if (!participant.present() || participant.fading() || !(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity target = level.getEntity(participant.targetUuid());
		return target instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	public boolean join(Mob mob) {
		if (!HordeParticipant.isEvent(mob)) {
			return true;
		}
		var state = HordeParticipant.get(mob);
		var data = HordeSavedData.get(server);
		var event = data.events().get(state.eventUuid());
		boolean registered = event != null && event.id().equals(data.memberEvents().get(mob.getUUID()))
				&& data.targets().values().stream().anyMatch(target -> target.members.contains(mob.getUUID()));
		if (!mob.isAlive()) {
			return true;
		}
		if (event == null || event.finished() || !registered || state.fading()) {
			return false;
		}
		loaded.put(mob.getUUID(), mob);
		var target = assignedTarget(mob);
		if (target != null) {
			bind(mob, target, event);
		}
		return true;
	}

	public void leave(Entity entity) {
		if (!(entity instanceof Mob mob) || !HordeParticipant.isEvent(mob)) {
			return;
		}
		loaded.remove(mob.getUUID());
		blockedSince.remove(mob.getUUID());
		if (stopping || !mob.isAlive() || entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
			if (!stopping) {
				CombatData.get(server).remove(mob.getUUID());
			}
			return;
		}
		CombatData.get(server).remove(mob.getUUID());
		release(mob);
	}

	public void death(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || !HordeParticipant.isEvent(mob)) {
			return;
		}
		var target = assignment(mob);
		if (target != null && target.members.remove(mob.getUUID())) {
			target.death();
			HordeSavedData.get(server).setDirty();
		}
		loaded.remove(mob.getUUID());
		blockedSince.remove(mob.getUUID());
		HordeSavedData.get(server).memberEvents().remove(mob.getUUID());
	}

	public void engage(LivingEntity entity, @Nullable Entity other) {
		if (!(entity instanceof Mob mob) || !HordeParticipant.isEvent(mob)) {
			return;
		}
		var state = HordeParticipant.get(mob);
		if (state.fading() || other == null || !state.targetId().equals(other.getUUID().toString())) {
			return;
		}
		var allocation = assignment(mob);
		if (allocation != null) {
			allocation.engaged = true;
			blockedSince.remove(mob.getUUID());
		}
	}

	public void open(HordeEvent event) {
		opening.start(server, event);
	}

	public void tick() {
		var data = HordeSavedData.get(server);
		opening.tick(server, data);
		if (data.events().isEmpty() && loaded.isEmpty()) {
			sync.tick(server, data, this);
			prune(data);
			return;
		}
		long began = HordeEventConfig.STATISTICS.get() ? System.nanoTime() : 0;
		data.advance();
		var active = new ArrayList<>(data.events().values());
		if (!active.isEmpty()) {
			java.util.Collections.rotate(active, -(int) (data.clock() % active.size()));
		}
		for (var event : active) {
			var profile = HordeEvents.profile(event.profile());
			ServerLevel level = server.getLevel(event.dimension());
			if (level == null || profile == null) {
				stop(event, HordeStopMode.CLEAR);
				data.events().remove(event.id());
				Lobecorp.LOGGER.warn("群体事件 {} 的维度或定义已失效", event.id());
				continue;
			}
			event.advance();
			if (event.route().refresh(level)) {
				event.changed();
			}
			if (event.finished()) {
				stop(event, HordeStopMode.GRACEFUL);
			}
			if (event.finished()) {
				data.events().remove(event.id());
				continue;
			}
		}
		scan(data);
		refreshSpatial(data);
		refreshQuotas(data);
		tickParticipants(data);
		clearDebris(data);
		tickTargets(data);
		updateRegions(data);
		createRequests(data);
		processRequests(data);
		sync.tick(server, data, this);
		prune(data);
		if (began != 0) {
			ticksNanos = System.nanoTime() - began;
		}
	}

	private void tickParticipants(HordeSavedData data) {
		for (Mob mob : List.copyOf(loaded.values())) {
			if (mob.isRemoved() || !mob.isAlive()) {
				loaded.remove(mob.getUUID());
				continue;
			}
			var participant = HordeParticipant.get(mob);
			if (participant.fading()) {
				if (mob.level().getGameTime() - participant.fadeAt() >= participant.fadeTicks()) {
					mob.discard();
				}
				continue;
			}
			var event = data.events().get(participant.eventUuid());
			if (event == null || event.finished()) {
				fade(mob);
				continue;
			}
			var allocation = assignment(mob);
			var target = assignedTarget(mob);
			if (allocation == null || target == null) {
				transferOrFade(mob, allocation, event, true);
				continue;
			}

			if (!event.covers(target.position())) {
				if (allocation.outsideSince < 0) {
					allocation.outsideSince = data.clock();
				}
				if (data.clock() - allocation.outsideSince >= event.rules().outsideTicks()) {
					transferOrFade(mob, allocation, event, true);
				}
				continue;
			}
			allocation.outsideSince = -1;
			if (EntitySkillUtil.hasActiveSkills(mob)
					|| mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
				blockedSince.remove(mob.getUUID());
			} else {
				long began = blockedSince.computeIfAbsent(mob.getUUID(), _ -> data.clock());
				if (data.clock() - began >= event.rules().unreachableTicks()) {
					allocation.retryUntil = data.clock() + allocation.rules.retryTicks();
					transferOrFade(mob, allocation, event, false);
				}
			}
		}
	}

	private void refreshSpatial(HordeSavedData data) {
		if (spatial.keySet().removeIf(key -> !data.targets().containsKey(key))) {
			regionsDirty = true;
		}
		for (var allocation : data.targets().values()) {
			var target = targetEntity(allocation);
			if (target == null) {
				continue;
			}
			var state = spatial.computeIfAbsent(allocation.key, _ -> new HordeTargetSpatialState(allocation.target));
			boolean near = target.level().players().stream().anyMatch(player ->
					HordeTargetSpatialState.near(player.distanceToSqr(target)));
			if (state.due(data.clock(), near)) {
				regionsDirty |= state.refresh(data.clock(), target.position(), target.getBoundingBox(), allocation.rules.regionMargin());
				allocation.position = state.position();
			}
		}
	}

	private void scan(HordeSavedData data) {
		for (var level : server.getAllLevels()) {
			if (data.events().values().stream().noneMatch(event -> event.dimension().equals(level.dimension()))) {
				continue;
			}

			var seen = new HashSet<UUID>();
			int chunks = server.getPlayerList().getSimulationDistance();
			for (var player : level.players()) {
				var center = player.chunkPosition();
				boolean fullScan = HordeTargetSpatialState.fullScanDue(data.clock());
				var area = fullScan
						? new AABB((center.x() - chunks) * 16d, level.getMinY(), (center.z() - chunks) * 16d,
						(center.x() + chunks + 1) * 16d, level.getMaxY(), (center.z() + chunks + 1) * 16d)
						: player.getBoundingBox().inflate(HordeTargetSpatialState.NEAR_DISTANCE);
				for (var target : level.getEntitiesOfClass(LivingEntity.class, area)) {
					if (!seen.add(target.getUUID())) {
						continue;
					}

					for (var event : data.events().values()) {
						if (!event.canSpawnAt(target.position()) || !event.dimension().equals(level.dimension())) {
							continue;
						}

						var profile = HordeEvents.profile(event.profile());
						if (profile == null || !profile.acceptsTarget(new HordeTargetContext(level, target.position(), profile.faction()), target)) {
							continue;
						}

						String domain = domain(event, profile);
						String key = domain + "|" + target.getUUID();
						var allocation = data.targets().computeIfAbsent(key,
								_ -> new HordeTargetAssignment(key, domain, target.getUUID(), target.position(), event.rules()));
						allocation.seenAt = data.clock();
						scanned++;
					}
				}
			}
		}
	}

	private void clearDebris(HordeSavedData data) {
		for (var level : server.getAllLevels()) {
			if (data.events().values().stream().noneMatch(event -> event.dimension().equals(level.dimension()))) {
				continue;
			}

			var seen = new HashSet<UUID>();
			int chunks = server.getPlayerList().getSimulationDistance();
			for (var player : level.players()) {
				var center = player.chunkPosition();
				var area = new AABB((center.x() - chunks) * 16d, level.getMinY(), (center.z() - chunks) * 16d,
						(center.x() + chunks + 1) * 16d, level.getMaxY(), (center.z() + chunks + 1) * 16d);
				for (var entity : level.getEntities((Entity) null, area, HordeRuntime::isDebris)) {
					if (!seen.add(entity.getUUID())) {
						continue;
					}

					boolean covered = data.events().values().stream().anyMatch(event ->
							event.dimension().equals(level.dimension()) && !event.finished() && event.covers(entity.position()));
					if (!covered || protectedByCombat(data, level, entity.getBoundingBox())) {
						continue;
					}
					if (entity instanceof EntityCorpse<?> corpse
							&& !HordeCleanupUtil.mayClearCorpse(level.getGameTime(), corpse.eventCleanupAt(), false)) {
						continue;
					}
					HordeCleanupUtil.discard(entity);
				}
			}
		}
	}

	private static boolean isDebris(Entity entity) {
		return entity instanceof ItemEntity || entity instanceof EntityCorpse<?> corpse
				&& (corpse.eventCleanupAt() >= 0 || HordeParticipant.isEvent(corpse));
	}

	public static boolean withinActiveWave(ServerLevel level, Vec3 point) {
		return HordeSavedData.get(level.getServer()).events().values().stream().anyMatch(event ->
				event.dimension().equals(level.dimension()) && !event.finished() && event.covers(point));
	}

	public static boolean protectedFromCleanup(ServerLevel level, AABB bounds) {
		return get(level.getServer()).protectedByCombat(HordeSavedData.get(level.getServer()), level, bounds);
	}

	private boolean protectedByCombat(HordeSavedData data, ServerLevel level, AABB bounds) {
		for (var allocation : data.targets().values()) {
			var target = targetEntity(allocation);
			if (target == null || target.level() != level) {
				continue;
			}

			if (target.getBoundingBox().inflate(allocation.rules.regionMargin()).intersects(bounds)) {
				return true;
			}

			for (var id : allocation.members) {
				var member = loaded.get(id);
				if (member != null && member.level() == level
						&& member.getBoundingBox().inflate(allocation.rules.regionMargin()).intersects(bounds)) {
					return true;
				}
			}
		}

		return false;
	}

	private void refreshQuotas(HordeSavedData data) {
		var retained = new HashMap<UUID, Integer>();
		for (var allocation : data.targets().values().stream()
				.sorted(Comparator.comparing(target -> target.key)).toList()) {
			var target = targetEntity(allocation);
			if (target == null) {
				continue;
			}
			allocation.maximumQuota = HordeRules.maximumQuota(target.getMaxHealth());
			allocation.quota = Math.min(allocation.quota, allocation.maximumQuota);
			int available = Math.max(0, allocation.maximumQuota - retained.getOrDefault(allocation.target, 0));
			if (allocation.occupied() > available) {
				var request = requests.get(allocation.key);
				if (request != null) {
					cancel(request);
				}
				for (var excess : allocation.members.stream().sorted().skip(available).toList()) {
					var mob = loaded.get(excess);
					if (mob != null) {
						fade(mob);
					}
				}
			}
			retained.merge(allocation.target, allocation.occupied(), Integer::sum);
		}
	}

	private int occupied(HordeTargetAssignment target, HordeSavedData data) {
		return data.targets().values().stream().filter(candidate -> candidate.target.equals(target.target))
				.mapToInt(HordeTargetAssignment::occupied).sum();
	}

	private void tickTargets(HordeSavedData data) {
		for (var allocation : data.targets().values()) {

			boolean present = allocation.members.stream().anyMatch(loaded::containsKey);
			if (allocation.engaged && present && allocation.retryUntil <= data.clock()) {
				allocation.fightTicks++;
				if (allocation.fightTicks >= allocation.rules.reinforceTicks()) {
					allocation.quota = (int) Math.min(allocation.maximumQuota, (long) allocation.quota + 1);
					allocation.fightTicks = 0;
				}
			}
		}
	}

	private void createRequests(HordeSavedData data) {
		var targets = data.targets().values().stream()
				.filter(target -> target.retryUntil <= data.clock()
						&& target.missing() > 0 && targetEntity(target) != null)
				.sorted(Comparator.comparingInt(HordeTargetAssignment::occupied).thenComparing(target -> target.key))
				.toList();
		for (var target : targets) {
			var selected = provider(target, data);
			if (selected == null) {
				continue;
			}
			String key = target.key;
			if (!requests.containsKey(key) && occupied(target, data) < target.maximumQuota) {
				target.reservations++;
				requests.put(key, new SpawnRequest(selected.id(), target));
			}
		}
	}

	private @Nullable HordeEvent provider(HordeTargetAssignment target, HordeSavedData data) {
		var living = targetEntity(target);
		if (living == null) {
			return null;
		}
		var position = living.position();
		return data.events().values().stream().filter(event -> event.canSpawnAt(position)).filter(event -> {
			var profile = HordeEvents.profile(event.profile());
			return profile != null && domain(event, profile).equals(target.domain)
					&& living.level().dimension().equals(event.dimension()) && event.covers(position);
		}).min(Comparator.comparingDouble((HordeEvent event) -> event.route().point(event.station(position),
						0).distanceToSqr(position))
				.thenComparing(HordeEvent::id)).orElse(null);
	}

	private void processRequests(HordeSavedData data) {
		var work = new HashMap<ServerLevel, Integer>();
		var creates = new HashMap<ServerLevel, Integer>();
		var livingCounts = new HashMap<ServerLevel, Long>();
		var sorted = new ArrayList<>(requests.values());
		sorted.sort(Comparator.comparingInt((SpawnRequest request) -> request.target.occupied())
				.thenComparingLong(request -> request.lastServed));
		for (var request : sorted) {
			var event = data.events().get(request.event);
			var level = event == null ? null : server.getLevel(event.dimension());
			var target = targetEntity(request.target);
			var profile = event == null ? null : HordeEvents.profile(event.profile());
			if (event == null || level == null || target == null || target.level() != level
					|| profile == null || !domain(event, profile).equals(request.target.domain)
					|| !event.canSpawnAt(target.position())
					|| !profile.acceptsTarget(new HordeTargetContext(level, target.position(), profile.faction()), target)
					|| request.target.occupied() > Math.min(request.target.quota, request.target.maximumQuota)
					|| occupied(request.target, data) > HordeRules.maximumQuota(target.getMaxHealth())) {
				cancel(request);
				continue;
			}
			if (work.getOrDefault(level, 0) >= HordeEventConfig.WORK_PER_TICK.get()
					|| creates.getOrDefault(level, 0) >= HordeEventConfig.CREATES_PER_TICK.get()) {
				request.pause();
				continue;
			}
			long livingCount = livingCounts.computeIfAbsent(level, current -> loaded.values().stream()
					.filter(mob -> mob.level() == current && mob.isAlive() && !HordeParticipant.get(mob).fading()).count());
			if (livingCount >= HordeEventConfig.SOFT_BUDGET.get() && request.target.occupied() > 1
					&& creates.getOrDefault(level, 0) >= Math.max(1, HordeEventConfig.CREATES_PER_TICK.get() / 2)) {
				request.pause();
				continue;
			}
			request.lastServed = data.clock();
			if (request.startedAt < 0) {
				request.startedAt = data.clock();
			}
			if (data.clock() - request.startedAt >= event.rules().unreachableTicks()) {
				request.target.retryUntil = data.clock() + event.rules().retryTicks();
				cancel(request);
				continue;
			}
			work.merge(level, 1, Integer::sum);
			var state = spatial.get(request.target.key);
			if (state == null) {
				continue;
			}
			var position = HordeSpawnPlanner.candidate(level, event, target, state.region(), request.attempt++, profile.dimensions());
			if (request.fallback != null && !HordeSpawnPlanner.valid(level, request.fallback, target.position(), profile.dimensions())) {
				request.fallback = null;
			}
			if (position == null) {
				continue;
			}
			boolean hidden = HordeSpawnPlanner.hidden(level, position, profile.dimensions());
			if (request.fallback == null || hidden) {
				request.fallback = position;
			}
			if (!hidden && request.attempt < 4) {
				continue;
			}
			Mob mob;
			// 创建及其事件回调可能改变成员；后续请求必须重新统计。
			livingCounts.clear();
			try {
				mob = profile.createParticipant(level,
						new HordeSpawnContext(event.id(), target, request.fallback));
			} catch (RuntimeException exception) {
				Lobecorp.LOGGER.warn("停止无法创建战斗实体的群体事件 {}", event.id(), exception);
				stop(event, HordeStopMode.CLEAR);
				data.events().remove(event.id());
				continue;
			}
			mob.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
			if (!level.noCollision(mob) || !profile.acceptsTarget(
					new HordeTargetContext(level, mob.position(), profile.faction()), target)
					|| mob instanceof ICombatant && !CombatUtil.wantsTarget(mob, target)) {
				continue;
			}
			mob.setPersistenceRequired();
			HordeParticipant.set(mob, new HordeParticipant(event.id().toString(), target.getUUID().toString(),
					level.getGameTime(), -1, event.rules().fadeTicks()));
			request.target.members.add(mob.getUUID());
			loaded.put(mob.getUUID(), mob);
			bind(mob, target, event);
			if (level.addFreshEntity(mob)) {
				CombatData combat = CombatData.get(server);
				if (combat.faction(profile.faction()) == null) {
					combat.putFaction(profile.faction(), Set.of());
				}
				combat.assign(mob.getUUID(), profile.faction(), event.id().toString());
				creates.merge(level, 1, Integer::sum);
				spawned++;
				cancel(request);
			} else {
				request.target.members.remove(mob.getUUID());
				loaded.remove(mob.getUUID());
				data.memberEvents().remove(mob.getUUID());
			}
		}
	}

	private void cancel(SpawnRequest request) {
		if (requests.remove(request.target.key, request)) {
			request.target.reservations = Math.max(0, request.target.reservations - 1);
		}
	}

	private void bind(Mob mob, LivingEntity target, HordeEvent event) {
		HordeSavedData.get(server).memberEvents().put(mob.getUUID(), event.id());
		var state = HordeParticipant.get(mob);
		HordeParticipant.set(mob, new HordeParticipant(event.id().toString(), target.getUUID().toString(),
				state.createdAt(), -1, event.rules().fadeTicks()));
		mob.setTarget(target);
		mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		blockedSince.put(mob.getUUID(), HordeSavedData.get(server).clock());
	}

	private void transferOrFade(Mob mob, @Nullable HordeTargetAssignment previous, HordeEvent event, boolean allowExcess) {
		var data = HordeSavedData.get(server);
		var next = previous == null ? null : data.targets().values().stream()
				.filter(candidate -> candidate != previous && candidate.domain.equals(previous.domain)
						&& candidate.region.equals(previous.region) && candidate.retryUntil <= data.clock()
						&& occupied(candidate, data) < candidate.maximumQuota
						&& (allowExcess || candidate.missing() > 0))
				.filter(candidate -> {
					var target = targetEntity(candidate);
					var profile = HordeEvents.profile(event.profile());
					var state = spatial.get(candidate.key);
					return target != null && target.level() == mob.level() && event.covers(target.position())
							&& profile != null && target.level() instanceof ServerLevel level
							&& profile.acceptsTarget(new HordeTargetContext(level, mob.position(), profile.faction()), target)
							&& state != null && state.region().containsHorizontal(mob.position())
							&& level.noCollision(mob)
							&& HordeSpawnPlanner.reachable(level, mob.position(), target.position(), mob.getDimensions(mob.getPose()));
				}).min(Comparator.comparingInt((HordeTargetAssignment candidate) -> candidate.missing() > 0 ? 0 : 1)
						.thenComparingDouble(candidate -> mob.distanceToSqr(candidate.position)))
				.orElse(null);
		release(mob);
		if (next == null) {
			fade(mob);
			return;
		}
		var target = targetEntity(next);
		if (target == null) {
			fade(mob);
			return;
		}
		EntitySkillUtil.cancelSkill(mob);
		EntityGrabUtil.clear(mob);
		mob.getNavigation().stop();
		next.members.add(mob.getUUID());
		bind(mob, target, event);
		data.setDirty();
	}

	private void release(Mob mob) {
		HordeSavedData.get(server).memberEvents().remove(mob.getUUID());
		var allocation = assignment(mob);
		if (allocation != null) {
			allocation.members.remove(mob.getUUID());
		}
		blockedSince.remove(mob.getUUID());
		HordeSavedData.get(server).setDirty();
	}

	public void fade(Mob mob) {
		var state = HordeParticipant.get(mob);
		if (!state.present() || state.fading() || !mob.isAlive()) {
			return;
		}
		release(mob);
		EntitySkillUtil.cancelSkill(mob);
		EntitySkillUtil.clearRuntimeState(mob);
		EntityGrabUtil.clear(mob);
		mob.getNavigation().stop();
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.setTarget(null);
		mob.setNoAi(true);
		mob.noPhysics = true;
		mob.setInvulnerable(true);
		mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		HordeParticipant.set(mob, new HordeParticipant(state.eventId(), state.targetId(), state.createdAt(),
				mob.level().getGameTime(), state.fadeTicks()));
	}

	public void stop(HordeEvent event, HordeStopMode mode) {
		if (mode == HordeStopMode.CLEAR || event.finished()) {
			opening.finish(server, event);
		}
		for (var request : List.copyOf(requests.values())) {
			if (request.event.equals(event.id())) {
				cancel(request);
			}
		}
		for (Mob mob : List.copyOf(loaded.values())) {
			var participant = HordeParticipant.get(mob);
			if (participant.eventId().equals(event.id().toString()) && mob.isAlive()) {
				fade(mob);
				if (mode == HordeStopMode.CLEAR) {
					mob.discard();
				}
			}
		}
		var data = HordeSavedData.get(server);
		var retired = data.memberEvents().entrySet().stream().filter(entry -> entry.getValue().equals(event.id()))
				.map(Map.Entry::getKey).toList();
		for (UUID member : retired) {
			data.targets().values().forEach(target -> target.members.remove(member));
			data.memberEvents().remove(member);
		}
	}

	private @Nullable HordeTargetAssignment assignment(Mob mob) {
		var state = HordeParticipant.get(mob);
		var data = HordeSavedData.get(server);
		var event = data.events().get(state.eventUuid());
		var profile = event == null ? null : HordeEvents.profile(event.profile());
		if (profile != null) {
			var indexed = data.targets().get(domain(event, profile) + "|" + state.targetId());
			if (indexed != null && indexed.members.contains(mob.getUUID())) {
				return indexed;
			}
		}
		return data.targets().values().stream()
				.filter(target -> target.target.toString().equals(state.targetId()) && target.members.contains(mob.getUUID()))
				.findFirst().orElse(null);
	}

	private @Nullable LivingEntity targetEntity(HordeTargetAssignment target) {
		for (var level : server.getAllLevels()) {
			var entity = level.getEntity(target.target);
			if (entity instanceof LivingEntity living && living.isAlive()) {
				return living;
			}
		}
		return null;
	}

	private static String domain(HordeEvent event, IHordeEventProfile profile) {
		return event.dimension().identifier() + "|" + profile.id() + "|" + profile.faction();
	}

	private void updateRegions(HordeSavedData data) {
		if (regionsDirty) {
			rebuildRegionGroups(data);
			regionsDirty = false;
		}
		for (var group : regionGroups) {
			var start = group.getFirst();
			UUID leader = group.stream().map(target -> target.target).min(UUID::compareTo).orElse(start.target);
			UUID region = HordeTargetAssignment.regionId(start.domain, leader);
			for (var member : group) {
				boolean merging = group.stream().anyMatch(other -> other.region.equals(region));
				if (member.region.equals(region) || merging) {
					member.region = region;
					member.splitSince = -1;
				} else if (member.splitSince < 0) {
					member.splitSince = data.clock();
				} else if (data.clock() - member.splitSince >= member.rules.splitTicks()) {
					member.region = region;
					member.splitSince = -1;
				}
			}
		}
	}

	private void rebuildRegionGroups(HordeSavedData data) {
		regionGroups.clear();
		var targets = new ArrayList<>(data.targets().values());
		var byCell = new HashMap<String, List<HordeTargetAssignment>>();
		double cellSize = 32;
		for (var target : targets) {
			String cell = target.domain + ":" + (int) Math.floor(target.position.x / cellSize)
					+ ":" + (int) Math.floor(target.position.z / cellSize);
			byCell.computeIfAbsent(cell, _ -> new ArrayList<>()).add(target);
		}
		var visited = new HashSet<String>();
		for (var start : targets) {
			if (!visited.add(start.key)) {
				continue;
			}
			var group = new ArrayList<HordeTargetAssignment>();
			group.add(start);
			for (int i = 0; i < group.size(); i++) {
				var current = group.get(i);
				int x = (int) Math.floor(current.position.x / cellSize);
				int z = (int) Math.floor(current.position.z / cellSize);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (var other : byCell.getOrDefault(current.domain + ":" + (x + dx) + ":" + (z + dz), List.of())) {
							double radius = current.rules.regionMargin() + other.rules.regionMargin() + 2;
							if (!visited.contains(other.key) && current.position.distanceToSqr(other.position) <= radius * radius) {
								visited.add(other.key);
								group.add(other);
							}
						}
					}
				}
			}
			regionGroups.add(group);
		}
	}

	private void prune(HordeSavedData data) {
		if (!data.events().isEmpty() && data.clock() % CHECK_INTERVAL != 0) {
			return;
		}
		var domains = data.events().values().stream().map(event -> {
			var profile = HordeEvents.profile(event.profile());
			return profile == null ? "" : domain(event, profile);
		}).collect(java.util.stream.Collectors.toSet());
		boolean removed = data.targets().values().removeIf(target -> {
			if (!target.members.isEmpty() || target.reservations != 0) {
				return false;
			}
			var living = targetEntity(target);
			if (!domains.contains(target.domain) || living == null) {
				return true;
			}

			if (provider(target, data) != null) {
				target.outsideSince = -1;
				return false;
			}
			if (target.outsideSince < 0) {
				target.outsideSince = data.clock();
			}
			return data.clock() - target.outsideSince >= target.rules.outsideTicks();
		});
		if (removed) {
			data.setDirty();
		}
	}

	public String statistics() {
		return "events=" + HordeSavedData.get(server).events().size() + ", participants=" + loaded.size()
				+ ", queued=" + requests.size() + ", created=" + spawned + ", scanned=" + scanned
				+ ", lastTickMs=" + ticksNanos / 1_000_000d;
	}

	private static class SpawnRequest {
		private final UUID event;
		private final HordeTargetAssignment target;
		private long startedAt = -1;
		private long lastServed;
		private int attempt;
		private Vec3 fallback;

		private void pause() {
			if (startedAt >= 0) {
				startedAt++;
			}
		}

		private SpawnRequest(UUID event, HordeTargetAssignment target) {
			this.event = event;
			this.target = target;
		}
	}
}
