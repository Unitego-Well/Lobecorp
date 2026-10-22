package org.unitego.lobecorp.world.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
	private final Map<UUID, Integer> scanCursors = new HashMap<>();
	private final Map<UUID, Long> scanServed = new HashMap<>();
	private final Map<UUID, CompoundTag> regionFrames = new HashMap<>();
	private final Map<UUID, Long> closingRegions = new HashMap<>();
	private final HordeSync sync = new HordeSync();
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

	public static void stopping(MinecraftServer server) {
		get(server).stopping = true;
	}

	public static void clear(MinecraftServer server) {
		RUNTIMES.remove(server);
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
		if (event == null || !event.running() || !registered || state.fading()) {
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

	public void tick() {
		var data = HordeSavedData.get(server);
		if (data.events().isEmpty() && loaded.isEmpty()) {
			return;
		}
		long began = HordeEventConfig.STATISTICS.get() ? System.nanoTime() : 0;
		data.advance();
		var active = new ArrayList<>(data.events().values());
		if (!active.isEmpty()) {
			java.util.Collections.rotate(active, -(int) (data.clock() % active.size()));
		}
		var routeBudgets = new HashMap<ServerLevel, Integer>();
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
			if (event.tick() == event.stopAt() || event.finished()) {
				stop(event, HordeStopMode.GRACEFUL);
			}
			if (event.finished()) {
				data.events().remove(event.id());
				scanCursors.remove(event.id());
				continue;
			}
			int routeWork = routeBudgets.getOrDefault(level, 0);
			if (routeWork < HordeEventConfig.ROUTE_WORK.get()) {
				if (event.route().refine(level, event.rules(), event.tick(), 1)) {
					event.changed();
				}
				routeBudgets.put(level, routeWork + 1);
			}
		}
		tickParticipants(data);
		scan(data);
		tickTargets(data);
		if (data.clock() % 20 == 0) {
			updateRegions(data);
		}
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
			if (event == null || !event.running()) {
				fade(mob);
				continue;
			}
			var allocation = assignment(mob);
			var target = assignedTarget(mob);
			if (allocation == null || target == null) {
				transferOrFade(mob, allocation, event, true);
				continue;
			}
			allocation.position = target.position();
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

	private void scan(HordeSavedData data) {
		var events = data.events().values().stream().filter(HordeEvent::running)
				.sorted(Comparator.comparingLong(event -> scanServed.getOrDefault(event.id(), Long.MIN_VALUE))).toList();
		var budgets = new HashMap<ServerLevel, Integer>();
		for (var event : events) {
			if (Math.floorMod(data.clock() + event.id().getLeastSignificantBits(), event.rules().scanTicks()) != 0) {
				continue;
			}
			var level = server.getLevel(event.dimension());
			var profile = HordeEvents.profile(event.profile());
			if (level == null || profile == null) {
				continue;
			}
			int first = event.route().segment(event.tail());
			int last = event.route().segment(event.head());
			int cursor = scanCursors.getOrDefault(event.id(), first);
			int available = HordeEventConfig.SCAN_WORK.get() - budgets.getOrDefault(level, 0);
			if (available <= 0) {
				continue;
			}
			int count = Math.min(available, last - first + 1);
			budgets.merge(level, count, Integer::sum);
			scanServed.put(event.id(), data.clock());
			for (int i = 0; i < count; i++) {
				int index = first + Math.floorMod(cursor++ - first, Math.max(1, last - first + 1));
				var routeTag = event.route().slice(index, 1).getListOrEmpty("nodes");
				var node = HordeRoute.Node.load((CompoundTag) routeTag.getFirst());
				var center = node.at(event.tick(), event.rules().transitionTicks());
				BlockPos block = BlockPos.containing(center);
				if (level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) == null) {
					continue;
				}
				double radius = event.rules().width() / 2 + event.rules().margin();
				var bounds = new AABB(center, center).inflate(radius, 16, radius);
				for (var target : level.getEntitiesOfClass(LivingEntity.class, bounds,
						living -> profile.acceptsTarget(new HordeTargetContext(level, center, profile.faction()), living)
								&& event.covers(living.position()))) {
					String domain = domain(event, profile);
					String key = domain + "|" + target.getUUID();
					var allocation = data.targets().computeIfAbsent(key,
							_ -> new HordeTargetAssignment(key, domain, target.getUUID(), target.position(), event.rules()));
					allocation.position = target.position();
					allocation.seenAt = data.clock();
					scanned++;
				}
			}
			scanCursors.put(event.id(), cursor);
		}
	}

	private void tickTargets(HordeSavedData data) {
		for (var allocation : data.targets().values()) {
			LivingEntity target = targetEntity(allocation);
			if (target == null) {
				continue;
			}
			allocation.position = target.position();
			boolean present = allocation.members.stream().anyMatch(loaded::containsKey);
			if (allocation.engaged && present && allocation.retryUntil <= data.clock()) {
				allocation.fightTicks++;
				if (allocation.fightTicks >= allocation.rules.reinforceTicks()) {
					allocation.quota = Math.min(allocation.rules.maximumQuota(), allocation.quota + 1);
					allocation.fightTicks = 0;
				}
			}
		}
	}

	private void createRequests(HordeSavedData data) {
		var targets = data.targets().values().stream()
				.filter(target -> target.retryUntil <= data.clock() && target.missing() > 0 && targetEntity(target) != null)
				.sorted(Comparator.comparingInt(HordeTargetAssignment::occupied).thenComparing(target -> target.key))
				.toList();
		for (var target : targets) {
			var selected = provider(target, data);
			if (selected == null) {
				continue;
			}
			String key = target.key;
			if (!requests.containsKey(key)) {
				target.reservations++;
				requests.put(key, new SpawnRequest(selected.id(), target));
			}
		}
	}

	private @Nullable HordeEvent provider(HordeTargetAssignment target, HordeSavedData data) {
		return data.events().values().stream().filter(HordeEvent::running).filter(event -> {
			var profile = HordeEvents.profile(event.profile());
			return profile != null && domain(event, profile).equals(target.domain) && event.covers(target.position);
		}).min(Comparator.comparingDouble((HordeEvent event) -> event.route().point(event.station(target.position),
						0, event.tick(), event.rules().transitionTicks()).distanceToSqr(target.position))
				.thenComparing(HordeEvent::id)).orElse(null);
	}

	private void processRequests(HordeSavedData data) {
		var work = new HashMap<ServerLevel, Integer>();
		var creates = new HashMap<ServerLevel, Integer>();
		var sorted = new ArrayList<>(requests.values());
		sorted.sort(Comparator.comparingInt((SpawnRequest request) -> request.target.occupied())
				.thenComparingLong(request -> request.lastServed));
		for (var request : sorted) {
			var event = data.events().get(request.event);
			var level = event == null ? null : server.getLevel(event.dimension());
			var target = targetEntity(request.target);
			if (event == null || !event.running() || level == null || target == null
					|| request.target.occupied() > request.target.quota) {
				cancel(request);
				continue;
			}
			if (work.getOrDefault(level, 0) >= HordeEventConfig.WORK_PER_TICK.get()
					|| creates.getOrDefault(level, 0) >= HordeEventConfig.CREATES_PER_TICK.get()) {
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
			long livingCount = loaded.values().stream().filter(mob -> mob.level() == level && mob.isAlive()
					&& !HordeParticipant.get(mob).fading()).count();
			if (livingCount >= HordeEventConfig.SOFT_BUDGET.get() && request.target.occupied() > 1
					&& creates.getOrDefault(level, 0) >= Math.max(1, HordeEventConfig.CREATES_PER_TICK.get() / 2)) {
				continue;
			}
			work.merge(level, 1, Integer::sum);
			var profile = HordeEvents.profile(event.profile());
			if (profile == null) {
				cancel(request);
				continue;
			}
			var position = HordeSpawnPlanner.candidate(level, event, target, request.attempt++, profile.dimensions());
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
			try {
				mob = profile.createParticipant(level,
						new HordeSpawnContext(event.id(), target, request.fallback, event.seed()));
			} catch (RuntimeException exception) {
				Lobecorp.LOGGER.warn("停止无法创建战斗实体的群体事件 {}", event.id(), exception);
				stop(event, HordeStopMode.CLEAR);
				data.events().remove(event.id());
				continue;
			}
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
						&& (allowExcess || candidate.missing() > 0))
				.filter(candidate -> {
					var target = targetEntity(candidate);
					return target != null && event.covers(target.position());
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
		scanCursors.remove(event.id());
		scanServed.remove(event.id());
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
		return HordeSavedData.get(server).targets().values().stream()
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
		publishRegions(data);
	}

	private void publishRegions(HordeSavedData data) {
		var boxes = new HashMap<UUID, AABB>();
		var domains = new HashMap<UUID, String>();
		for (var target : data.targets().values()) {
			if (target.members.isEmpty() && target.reservations == 0) {
				continue;
			}
			LivingEntity living = targetEntity(target);
			AABB box = living == null ? new AABB(target.position, target.position) : living.getBoundingBox();
			for (UUID id : target.members) {
				Mob member = loaded.get(id);
				if (member != null) {
					box = box.minmax(member.getBoundingBox());
				}
			}
			box = box.inflate(target.rules.regionMargin());
			boxes.merge(target.region, box, AABB::minmax);
			domains.put(target.region, target.domain);
		}
		for (var entry : boxes.entrySet()) {
			AABB box = entry.getValue();
			var tag = new CompoundTag();
			tag.putString("id", entry.getKey().toString());
			tag.putString("domain", domains.get(entry.getKey()));
			tag.putDouble("x", box.getCenter().x);
			tag.putDouble("y", box.getCenter().y);
			tag.putDouble("z", box.getCenter().z);
			tag.putDouble("rx", box.getXsize() / 2);
			tag.putDouble("rz", box.getZsize() / 2);
			tag.putLong("changed", data.clock());
			tag.putBoolean("active", true);
			regionFrames.put(entry.getKey(), tag);
			closingRegions.remove(entry.getKey());
		}
		for (var id : List.copyOf(regionFrames.keySet())) {
			if (!boxes.containsKey(id)) {
				long ended = closingRegions.computeIfAbsent(id, _ -> data.clock());
				if (data.clock() - ended > 200) {
					regionFrames.remove(id);
					closingRegions.remove(id);
				} else {
					regionFrames.get(id).putBoolean("active", false);
					regionFrames.get(id).putLong("changed", ended);
				}
			}
		}
	}

	public ListTag regions(HordeEvent event) {
		var result = new ListTag();
		var profile = HordeEvents.profile(event.profile());
		if (profile != null) {
			String domain = domain(event, profile);
			regionFrames.values().stream().filter(tag -> tag.getStringOr("domain", "").equals(domain))
					.forEach(tag -> result.add(tag.copy()));
		}
		return result;
	}

	private void prune(HordeSavedData data) {
		var domains = data.events().values().stream().map(event -> {
			var profile = HordeEvents.profile(event.profile());
			return profile == null ? "" : domain(event, profile);
		}).collect(java.util.stream.Collectors.toSet());
		data.targets().values().removeIf(target -> !domains.contains(target.domain) && target.members.isEmpty());
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

		private SpawnRequest(UUID event, HordeTargetAssignment target) {
			this.event = event;
			this.target = target;
		}
	}
}
