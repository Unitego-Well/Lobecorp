package org.unitego.lobecorp;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.unitego.lobecorp.config.client.ClientConfig;
import org.unitego.lobecorp.config.server.ServerConfig;
import org.unitego.lobecorp.registry.world.LcAttachmentTypes;
import org.unitego.lobecorp.registry.world.item.LcDataComponents;
import org.unitego.lobecorp.registry.sound.LcSoundEvents;
import org.unitego.lobecorp.registry.animation.LcAnimationExampleDefinitions;
import org.unitego.lobecorp.registry.animation.CannedEnkephalinAnimationDefinitions;
import org.unitego.lobecorp.registry.animation.TheQueenOfHatredAnimationDefinitions;
import org.unitego.lobecorp.registry.world.block.LcBlocks;
import org.unitego.lobecorp.registry.world.effect.LcMobEffects;
import org.unitego.lobecorp.registry.world.encounter.LcEncounters;
import org.unitego.lobecorp.registry.world.entity.LcAttributes;
import org.unitego.lobecorp.registry.world.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.world.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.world.entity.ai.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.world.entity.ai.LcSensorTypes;
import org.unitego.lobecorp.registry.world.entity.skill.LcEntitySkills;
import org.unitego.lobecorp.registry.world.item.LcCreativeModeTabs;
import org.unitego.lobecorp.registry.world.item.LcItems;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.registry.world.tag.LcTags;

@Mod(Lobecorp.NAMESPACE)
public class Lobecorp {
	/// 模组命名空间。
	public static final String NAMESPACE = "lobecorp";
	public static final Logger LOGGER = LogUtils.getLogger();

	public Lobecorp(IEventBus iEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.specification(), ClientConfig.FILE_NAME);
		modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.specification(), ServerConfig.FILE_NAME);
		LcEncounters.init(iEventBus);

		LcAttachmentTypes.init(iEventBus);
		LcDataComponents.init(iEventBus);
		LcTags.init();
		LcAnimationExampleDefinitions.init();
		CannedEnkephalinAnimationDefinitions.init();
		TheQueenOfHatredAnimationDefinitions.init();
		LcMemoryModuleTypes.init(iEventBus);
		LcSensorTypes.init(iEventBus);
		LcMobEffects.init(iEventBus);
		LcParticleTypes.init(iEventBus);
		LcSoundEvents.init(iEventBus);
		LcAttributes.init(iEventBus);
		LcEntityDataSerializers.init(iEventBus);
		LcEntityTypes.init(iEventBus);
		LcEntitySkills.init(iEventBus);
		LcBlocks.init(iEventBus);
		LcItems.init(iEventBus);
		LcCreativeModeTabs.init(iEventBus);
	}

	/// 创建属于本模组命名空间的资源标识符。
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	/// 创建属于本模组命名空间的完整资源名称。
	public static String name(String path) {
		return id(path).toString();
	}

	/// 使用本模组命名空间创建指定注册表的资源键。
	public static <T> ResourceKey<T> createKey(ResourceKey<? extends Registry<T>> registry, String path) {
		return createKey(registry, id(path));
	}

	/// 使用完整资源标识符创建指定注册表的资源键。
	public static <T> ResourceKey<T> createKey(ResourceKey<? extends Registry<T>> registry, Identifier identifier) {
		return ResourceKey.create(registry, identifier);
	}

	/// 为原版注册表创建本模组的延迟注册器。
	public static <T> DeferredRegister<T> register(Registry<T> registry) {
		return DeferredRegister.create(registry, NAMESPACE);
	}

	/// 为资源键指定的注册表创建本模组延迟注册器。
	public static <T> DeferredRegister<T> register(ResourceKey<Registry<T>> registry) {
		return DeferredRegister.create(registry, NAMESPACE);
	}

	/// 创建属于本模组命名空间的网络载荷类型。
	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String identifier) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, identifier));
	}
}
