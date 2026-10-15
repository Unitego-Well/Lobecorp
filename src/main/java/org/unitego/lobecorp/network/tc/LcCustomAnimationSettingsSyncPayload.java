package org.unitego.lobecorp.network.tc;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.ILcCustomAnimatable;

import static org.unitego.lobecorp.Lobecorp.id;

public record LcCustomAnimationSettingsSyncPayload(
		int entityId,
		String controllerName,
		LcAnimationTransitionSettings settings
) implements IToClientPayload {
	/// 载荷类型的稳定标识，必须与收发两端的协议登记一致。
	public static final CustomPacketPayload.Type<LcCustomAnimationSettingsSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(id("custom_animation_settings_sync"));
	/// 载荷的二进制编解码契约，字段读写顺序必须保持一致。
	public static final StreamCodec<RegistryFriendlyByteBuf, LcCustomAnimationSettingsSyncPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, LcCustomAnimationSettingsSyncPayload::entityId,
					ByteBufCodecs.STRING_UTF8, LcCustomAnimationSettingsSyncPayload::controllerName,
					LcAnimationTransitionSettings.STREAM_CODEC, LcCustomAnimationSettingsSyncPayload::settings,
					LcCustomAnimationSettingsSyncPayload::new
			);

	public static void send(Entity entity, String controllerName, LcAnimationTransitionSettings settings) {
		PacketDistributor.sendToPlayersTrackingEntity(entity,
				new LcCustomAnimationSettingsSyncPayload(entity.getId(), controllerName, settings));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		Entity entity = player.level().getEntity(entityId);

		if (entity instanceof ILcCustomAnimatable animatable) {
			animatable.applyCustomAnimationSettings(controllerName, settings);
		}
	}
}
