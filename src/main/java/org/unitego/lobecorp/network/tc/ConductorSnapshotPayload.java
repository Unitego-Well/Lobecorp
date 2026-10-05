package org.unitego.lobecorp.network.tc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.conductor.ability.ConductorAbility;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.data.ConductorDirectory;
import org.unitego.lobecorp.util.conductor.ConductorUtil;
import org.unitego.lobecorp.conductor.world.ConductorWorldRuntime;

import java.util.*;

/// 服务端到客户端的指挥家目录和能力快照载荷。
public record ConductorSnapshotPayload(boolean full, long baseRevision, long revision,
                                       List<String> removedTeams, List<String> removedUnits, ConductorDirectory data,
                                       List<AbilityCooldown> abilityCooldowns,
                                       List<UnitAbilities> unitAbilities) implements ToClientPayload {
	public static final Type<ConductorSnapshotPayload> TYPE = Lobecorp.type("conductor_snapshot");
	public static final StreamCodec<RegistryFriendlyByteBuf, ConductorSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull ConductorSnapshotPayload decode(@NonNull RegistryFriendlyByteBuf buffer) {
			return new ConductorSnapshotPayload(buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(),
					KEYS_CODEC.decode(buffer), KEYS_CODEC.decode(buffer), DATA_CODEC.decode(buffer), COOLDOWNS_CODEC.decode(buffer),
					ABILITIES_CODEC.decode(buffer));
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull ConductorSnapshotPayload payload) {
			buffer.writeBoolean(payload.full);
			buffer.writeVarLong(payload.baseRevision);
			buffer.writeVarLong(payload.revision);
			KEYS_CODEC.encode(buffer, payload.removedTeams);
			KEYS_CODEC.encode(buffer, payload.removedUnits);
			DATA_CODEC.encode(buffer, payload.data);
			COOLDOWNS_CODEC.encode(buffer, payload.abilityCooldowns);
			ABILITIES_CODEC.encode(buffer, payload.unitAbilities);
		}
	};
	private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> KEYS_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(Codec.STRING.listOf());
	private static final StreamCodec<RegistryFriendlyByteBuf, ConductorDirectory> DATA_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(ConductorDirectory.CODEC);
	private static final StreamCodec<RegistryFriendlyByteBuf, List<AbilityCooldown>> COOLDOWNS_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(AbilityCooldown.CODEC.listOf());
	private static final StreamCodec<RegistryFriendlyByteBuf, List<UnitAbilities>> ABILITIES_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(UnitAbilities.CODEC.listOf());

	public static void send(ServerPlayer player, ConductorData data) {
		send(player, data, List.of());
	}

	public static void send(ServerPlayer player, ConductorData data, List<UUID> requested) {
		Set<UUID> subscriptions = data.runtime.requestedAbilities.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
		requested.stream().filter(uuid -> data.unit(uuid) != null).forEach(subscriptions::add);
		PacketDistributor.sendToPlayer(player, create(player, data, true));
	}

	public static void sendAll(MinecraftServer server, ConductorData data) {
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
			PacketDistributor.sendToPlayer(player, create(player, data, false));
		}
		data.runtime.clients.keySet().retainAll(online);
		data.runtime.requestedAbilities.keySet().retainAll(online);
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		ConductorClient.receive(this);
	}

	private static ConductorSnapshotPayload create(ServerPlayer player, ConductorData data, boolean full) {
		MinecraftServer server = player.level().getServer();
		Set<UUID> requested = data.runtime.requestedAbilities.getOrDefault(player.getUUID(), Set.of());
		List<AbilityCooldown> cooldowns = new ArrayList<>();
		List<UnitAbilities> abilities = new ArrayList<>();
		for (var entry : data.units().entrySet()) {
			if (!requested.contains(UUID.fromString(entry.getKey())))
				continue;
			ConductorData.Unit unit = entry.getValue();
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(unit.dimension())));
			Entity entity = level == null ? null : level.getEntity(UUID.fromString(entry.getKey()));
			if (!(entity instanceof Mob mob)) {
				continue;
			}
			data.remember(mob);
			if (ConductorUtil.get(mob) == null)
				continue;
			List<ConductorAbility> available = List.copyOf(ConductorUtil.abilities(mob));
			abilities.add(new UnitAbilities(mob.getUUID(), available.stream().map(ConductorAbility::id).toList()));
			for (ConductorAbility ability : available) {
				int remaining = ability.cooldownTicks(mob);
				if (remaining > 0) {
					cooldowns.add(new AbilityCooldown(mob.getUUID(), ability.id(), remaining,
							Math.max(remaining, ability.totalCooldownTicks(mob))));
				}
			}
		}
		ConductorDirectory current = new ConductorDirectory(data.teams(), data.units(), data.memberInfo());
		ConductorWorldRuntime.ClientView previous = data.runtime.clients.get(player.getUUID());
		full |= previous == null;
		ConductorDirectory before = previous == null ? ConductorDirectory.empty() : previous.directory();
		List<String> removedTeams = before.teams().keySet().stream().filter(key -> !current.teams().containsKey(key)).toList();
		List<String> removedUnits = before.units().keySet().stream().filter(key -> !current.units().containsKey(key)).toList();
		ConductorSnapshotPayload payload = new ConductorSnapshotPayload(full, previous == null ? 0L : previous.revision(),
				data.revision(), removedTeams, removedUnits, full ? current : current.changesFrom(before),
				List.copyOf(cooldowns), List.copyOf(abilities));
		data.runtime.clients.put(player.getUUID(), new ConductorWorldRuntime.ClientView(current, data.revision()));
		return payload;
	}

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record UnitAbilities(UUID unit, List<Identifier> abilities) {
		private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
		private static final Codec<Identifier> IDENTIFIER_CODEC = Codec.STRING.xmap(Identifier::parse, Identifier::toString);

		public static final Codec<UnitAbilities> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUID_CODEC.fieldOf("unit").forGetter(UnitAbilities::unit),
				IDENTIFIER_CODEC.listOf().fieldOf("abilities").forGetter(UnitAbilities::abilities)
		).apply(instance, UnitAbilities::new));
	}

	public record AbilityCooldown(UUID unit, Identifier ability, int ticks, int totalTicks) {
		private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
		private static final Codec<Identifier> IDENTIFIER_CODEC = Codec.STRING.xmap(Identifier::parse, Identifier::toString);

		public static final Codec<AbilityCooldown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUID_CODEC.fieldOf("unit").forGetter(AbilityCooldown::unit),
				IDENTIFIER_CODEC.fieldOf("ability").forGetter(AbilityCooldown::ability),
				Codec.INT.fieldOf("ticks").forGetter(AbilityCooldown::ticks),
				Codec.INT.fieldOf("total_ticks").forGetter(AbilityCooldown::totalTicks)
		).apply(instance, AbilityCooldown::new));
	}
}
