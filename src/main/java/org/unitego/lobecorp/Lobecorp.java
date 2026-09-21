package org.unitego.lobecorp;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.unitego.lobecorp.registry.LcAttachmentTypes;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity_skill.LcEntitySkills;
import org.unitego.lobecorp.registry.item.LcCreativeModeTabs;
import org.unitego.lobecorp.registry.item.LcItems;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.registry.tag.LcTags;

@Mod(Lobecorp.NAMESPACE)
public class Lobecorp {
	/// 模组命名空间。
	public static final String NAMESPACE = "lobecorp";
	/// 模组日志记录器。
	public static final Logger LOGGER = LogUtils.getLogger();

	public Lobecorp(IEventBus iEventBus, ModContainer modContainer) {
		LOGGER.info("Unitego.");

		LcAttachmentTypes.init(iEventBus);
		LcTags.init();
		LcMemoryModuleTypes.init(iEventBus);
		LcSensorTypes.init(iEventBus);
		LcMobEffects.init(iEventBus);
		LcParticleTypes.init(iEventBus);
		LcAttributes.init(iEventBus);
		LcEntityDataSerializers.init(iEventBus);
		LcEntityTypes.init(iEventBus);
		LcEntitySkills.init(iEventBus);
		LcItems.init(iEventBus);
		LcCreativeModeTabs.init(iEventBus);
	}

	/// 创建属于本模组命名空间的资源标识符。
	///
	/// @param path 命名空间内的资源路径
	/// @return 本模组资源标识符
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	/// 创建属于本模组命名空间的完整资源名称。
	///
	/// @param path 命名空间内的资源路径
	/// @return 字符串形式的完整资源名称
	public static String name(String path) {
		return id(path).toString();
	}

	/// 为原版注册表创建本模组的延迟注册器。
	///
	/// @param registry 目标注册表
	/// @param <T> 注册对象类型
	/// @return 绑定本模组命名空间的延迟注册器
	public static <T> DeferredRegister<T> register(Registry<T> registry) {
		return DeferredRegister.create(registry, NAMESPACE);
	}

	/// 为资源键指定的注册表创建本模组延迟注册器。
	///
	/// @param registry 目标注册表资源键
	/// @param <T> 注册对象类型
	/// @return 绑定本模组命名空间的延迟注册器
	public static <T> DeferredRegister<T> register(ResourceKey<Registry<T>> registry) {
		return DeferredRegister.create(registry, NAMESPACE);
	}

	/// 创建属于本模组命名空间的网络载荷类型。
	///
	/// @param identifier 载荷资源路径
	/// @param <T> 载荷类型
	/// @return 网络载荷类型
	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String identifier) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, identifier));
	}
}
