package org.unitego.lobecorp.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.unitego.lobecorp.animation.LcCustomAnimatable;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;

import static org.unitego.lobecorp.Lobecorp.id;

public record LcCustomAnimationSettingsSyncPayload(
		int entityId,
		String controllerName,
		LcAnimationTransitionSettings settings
) implements ToPayload {
	public static final CustomPacketPayload.Type<LcCustomAnimationSettingsSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(id("custom_animation_settings_sync"));
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
	public void work(IPayloadContext context) {
		Entity entity = context.player().level().getEntity(entityId);
		if (entity instanceof LcCustomAnimatable animatable) {
			animatable.applyCustomAnimationSettings(controllerName, settings);
		}
	}
}
