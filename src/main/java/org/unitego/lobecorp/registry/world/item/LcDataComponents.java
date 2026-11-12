package org.unitego.lobecorp.registry.world.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.item.component.WarpAnchor;

public interface LcDataComponents {
	DeferredRegister<DataComponentType<?>> REGISTER = Lobecorp.register(Registries.DATA_COMPONENT_TYPE);
	DeferredHolder<DataComponentType<?>, DataComponentType<WarpAnchor>> WARP_ANCHOR = register(
			"warp_anchor", WarpAnchor.CODEC, WarpAnchor.STREAM_CODEC);

	static void init(IEventBus bus) {
		REGISTER.register(bus);
	}

	static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
			String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		return REGISTER.register(name, () -> DataComponentType.<T>builder()
				.persistent(codec).networkSynchronized(streamCodec).build());
	}
}
