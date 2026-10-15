package org.unitego.lobecorp.network.tc.attachment;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.attachment.LcAnchorReference;
import org.unitego.lobecorp.attachment.LcAttachmentSnapshot;
import org.unitego.lobecorp.attachment.LcRenderAnchorReference;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.client.attachment.LcClientAttachments;
import org.unitego.lobecorp.network.tc.IToClientPayload;
import org.unitego.lobecorp.world.item.animation.LcAttachedItemSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 全量帧仅替换服务端同步分区；物品描述覆盖本次更新的绑定，缺席表示解除该描述。
public record LcAttachmentStatePayload(boolean full, long serverTick, List<LcAttachmentSnapshot> updates,
                                       Set<UUID> removed,
                                       List<LcAttachedItemSnapshot> items) implements IToClientPayload {
	public static final Type<LcAttachmentStatePayload> TYPE = new Type<>(Lobecorp.id("attachment_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LcAttachmentStatePayload> STREAM_CODEC =
			StreamCodec.of(LcAttachmentStatePayload::encode, LcAttachmentStatePayload::decode);

	public LcAttachmentStatePayload {
		updates = List.copyOf(updates);
		removed = Set.copyOf(removed);
		items = List.copyOf(items);
	}

	private static void encode(RegistryFriendlyByteBuf buffer, LcAttachmentStatePayload payload) {
		buffer.writeBoolean(payload.full());
		buffer.writeLong(payload.serverTick());
		buffer.writeVarInt(payload.updates().size());
		payload.updates().forEach(snapshot -> writeSnapshot(buffer, snapshot));
		buffer.writeVarInt(payload.removed().size());
		payload.removed().forEach(buffer::writeUUID);
		buffer.writeVarInt(payload.items().size());
		payload.items().forEach(item -> writeItem(buffer, item));
	}

	private static LcAttachmentStatePayload decode(RegistryFriendlyByteBuf buffer) {
		var full = buffer.readBoolean();
		var tick = buffer.readLong();
		List<LcAttachmentSnapshot> updates = new ArrayList<>();
		var updateCount = buffer.readVarInt();
		for (int index = 0; index < updateCount; index++) {
			updates.add(readSnapshot(buffer));
		}

		Set<UUID> removed = new LinkedHashSet<>();
		var removedCount = buffer.readVarInt();
		for (int index = 0; index < removedCount; index++) {
			removed.add(buffer.readUUID());
		}

		List<LcAttachedItemSnapshot> items = new ArrayList<>();
		var itemCount = buffer.readVarInt();
		for (int index = 0; index < itemCount; index++) {
			items.add(readItem(buffer));
		}

		return new LcAttachmentStatePayload(full, tick, updates, removed, items);
	}

	private static void writeSnapshot(RegistryFriendlyByteBuf buffer, LcAttachmentSnapshot snapshot) {
		buffer.writeUUID(snapshot.id());
		var anchor = snapshot.anchor();
		buffer.writeEnum(anchor.kind());
		writeUuid(buffer, anchor.sourceId());
		buffer.writeVarInt(anchor.entityId());
		writeString(buffer, anchor.bone());
		writeString(buffer, anchor.locator());
		writeTransform(buffer, snapshot.offset());
		writeTransform(buffer, snapshot.worldTransform());
		var render = snapshot.renderAnchor();
		buffer.writeBoolean(render != null);
		if (render != null) {
			Identifier.STREAM_CODEC.encode(buffer, render.provider());
			buffer.writeUUID(render.sourceId());
			buffer.writeVarInt(render.entityId());
			buffer.writeUtf(render.node());
		}

		buffer.writeLong(snapshot.revision());
		buffer.writeBoolean(snapshot.preserveWorldTransform());
		writeTransform(buffer, snapshot.renderOffset());
	}

	private static LcAttachmentSnapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
		var id = buffer.readUUID();
		var anchor = new LcAnchorReference(buffer.readEnum(LcAnchorReference.Kind.class), readUuid(buffer),
				buffer.readVarInt(), readString(buffer), readString(buffer));
		var offset = readTransform(buffer);
		var world = readTransform(buffer);
		var render = buffer.readBoolean() ? new LcRenderAnchorReference(Identifier.STREAM_CODEC.decode(buffer),
				buffer.readUUID(), buffer.readVarInt(), buffer.readUtf()) : null;
		return new LcAttachmentSnapshot(id, anchor, offset, world, render, buffer.readLong(), buffer.readBoolean(),
				readTransform(buffer));
	}

	private static void writeItem(RegistryFriendlyByteBuf buffer, LcAttachedItemSnapshot item) {
		buffer.writeUUID(item.bindingId());
		ItemStack.STREAM_CODEC.encode(buffer, item.stack());
		buffer.writeEnum(item.displayContext());
		writeUuid(buffer, item.animationId());
		writeString(buffer, item.bone());
		LcAnimationTransitionSettings.STREAM_CODEC.encode(buffer, item.transitions());
		buffer.writeLong(item.startedTick());
		buffer.writeLong(item.stoppingTick());
		buffer.writeLong(item.serverTick());
	}

	private static LcAttachedItemSnapshot readItem(RegistryFriendlyByteBuf buffer) {
		return new LcAttachedItemSnapshot(buffer.readUUID(), ItemStack.STREAM_CODEC.decode(buffer),
				buffer.readEnum(ItemDisplayContext.class), readUuid(buffer), readString(buffer),
				LcAnimationTransitionSettings.STREAM_CODEC.decode(buffer), buffer.readLong(), buffer.readLong(),
				buffer.readLong());
	}

	private static void writeTransform(RegistryFriendlyByteBuf buffer, LcTransform transform) {
		writeVector(buffer, transform.position());
		var rotation = transform.rotation();
		buffer.writeFloat(rotation.x);
		buffer.writeFloat(rotation.y);
		buffer.writeFloat(rotation.z);
		buffer.writeFloat(rotation.w);
		writeVector(buffer, transform.scale());
	}

	private static LcTransform readTransform(RegistryFriendlyByteBuf buffer) {
		var position = readVector(buffer);
		var rotation = new Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
		return new LcTransform(position, rotation, readVector(buffer));
	}

	private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
		buffer.writeDouble(vector.x);
		buffer.writeDouble(vector.y);
		buffer.writeDouble(vector.z);
	}

	private static Vec3 readVector(RegistryFriendlyByteBuf buffer) {
		return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
	}

	private static void writeUuid(RegistryFriendlyByteBuf buffer, @Nullable UUID value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeUUID(value);
		}
	}

	private static @Nullable UUID readUuid(RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readUUID() : null;
	}

	private static void writeString(RegistryFriendlyByteBuf buffer, @Nullable String value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeUtf(value);
		}
	}

	private static @Nullable String readString(RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readUtf() : null;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		LcClientAttachments.accept(full, serverTick, updates, removed, items);
	}
}
