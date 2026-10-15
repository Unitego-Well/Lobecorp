package org.unitego.lobecorp.registry;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;
import org.unitego.lobecorp.world.entity.state.EntityState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface LcStreamCodecs {
	/// 实体状态列表的同步数量上限，单位为条目，防止不受限的载荷分配。
	int MAX_ENTITY_STATES = 64;

	StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = ByteBufCodecs.fromCodecTrusted(CompoundTag.CODEC);
	StreamCodec<RegistryFriendlyByteBuf, Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = ByteBufCodecs.optional(EntityType.STREAM_CODEC);
	/// 单个实体状态的编解码契约，保留状态标识及互斥分组。
	StreamCodec<ByteBuf, EntityState> ENTITY_STATE = StreamCodec.composite(
			Identifier.STREAM_CODEC, EntityState::id,
			ByteBufCodecs.optional(Identifier.STREAM_CODEC), state -> Optional.ofNullable(state.exclusiveGroup()),
			(id, exclusiveGroup) -> new EntityState(id, exclusiveGroup.orElse(null))
	);
	/// 实体状态列表的同步编解码契约，保留原有列表顺序。
	StreamCodec<ByteBuf, List<EntityState>> ENTITY_STATES = new StreamCodec<>() {
		@Override
		public @NonNull List<EntityState> decode(@NonNull ByteBuf buffer) {
			int count = ByteBufCodecs.VAR_INT.decode(buffer);

			if (count < 0 || count > MAX_ENTITY_STATES) {
				throw new DecoderException("Entity state count " + count + " exceeds maximum " + MAX_ENTITY_STATES);
			}

			List<EntityState> states = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				states.add(ENTITY_STATE.decode(buffer));
			}

			return List.copyOf(states);
		}

		@Override
		public void encode(@NonNull ByteBuf buffer, @NonNull List<EntityState> states) {
			if (states.size() > MAX_ENTITY_STATES) {
				throw new EncoderException("Entity state count " + states.size() + " exceeds maximum " + MAX_ENTITY_STATES);
			}

			ByteBufCodecs.VAR_INT.encode(buffer, states.size());
			states.forEach(state -> ENTITY_STATE.encode(buffer, state));
		}
	};
}
