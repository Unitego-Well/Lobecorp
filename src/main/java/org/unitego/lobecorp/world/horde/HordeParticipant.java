package org.unitego.lobecorp.world.horde;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.registry.LcAttachmentTypes;

import java.util.UUID;

/// 仅正常实体与其尸体持有的事件来源；不表示虚拟个体身份。
public record HordeParticipant(String eventId, String targetId, long createdAt, long fadeAt, int fadeTicks) {
	public static final HordeParticipant EMPTY = new HordeParticipant("", "", 0, -1, 5);
	public static final Codec<HordeParticipant> CODEC = CompoundTag.CODEC.xmap(HordeParticipant::load, HordeParticipant::save);
	public static final StreamCodec<ByteBuf, HordeParticipant> STREAM_CODEC =
			ByteBufCodecs.COMPOUND_TAG.map(HordeParticipant::load, HordeParticipant::save);

	public boolean present() {
		return !eventId.isEmpty();
	}

	public boolean fading() {
		return fadeAt >= 0;
	}

	public UUID eventUuid() {
		return UUID.fromString(eventId);
	}

	public UUID targetUuid() {
		return UUID.fromString(targetId);
	}

	public static HordeParticipant get(Entity entity) {
		return entity.getData(LcAttachmentTypes.HORDE_PARTICIPANT);
	}

	public static boolean isEvent(Entity entity) {
		return entity.hasData(LcAttachmentTypes.HORDE_PARTICIPANT) && get(entity).present();
	}

	public static void set(Entity entity, HordeParticipant participant) {
		entity.setData(LcAttachmentTypes.HORDE_PARTICIPANT, participant);
	}

	public float alpha(double tick) {
		return (float) (fading() ? Math.clamp(1 - (tick - fadeAt) / fadeTicks, 0, 1)
				: Math.clamp((tick - createdAt) / fadeTicks, 0, 1));
	}

	public CompoundTag save() {
		var tag = new CompoundTag();
		tag.putString("event", eventId);
		tag.putString("target", targetId);
		tag.putLong("created", createdAt);
		tag.putLong("fade", fadeAt);
		tag.putInt("fade_ticks", fadeTicks);
		return tag;
	}

	public static HordeParticipant load(CompoundTag tag) {
		return new HordeParticipant(tag.getStringOr("event", ""), tag.getStringOr("target", ""),
				tag.getLongOr("created", 0), tag.getLongOr("fade", -1), Math.max(1, tag.getIntOr("fade_ticks", 5)));
	}
}
