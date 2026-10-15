package org.unitego.lobecorp.network.tc.animation;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.behavior.LcRootMotion;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.attachment.LcTransform;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.animation.runtime.LcAnimationSnapshot;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.network.tc.IToClientPayload;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;
import java.util.UUID;

/// 数值帧只引用已分块传输的内容版本，不重复携带模型与动画文本。
public record LcAnimationStatePayload(UUID instanceId, int ownerEntityId, Identifier definitionId,
                                      String contentVersion, String animationName, double timeSeconds,
                                      double speed, boolean paused, boolean stopped, LcSkeletalPose pose,
                                      Map<String, Double> parameters, LcTransform worldTransform,
                                      @Nullable String rootBone,
                                      Set<LcRootMotion.Axis> rootAxes, @Nullable LcPlayerAnimationPolicy playerPolicy,
                                      long serverTick, long playbackRevision, long startedTick, long stoppedTick,
                                      LcAnimationTermination termination,
                                      LcSkeletalPose poseOverrides) implements IToClientPayload {
	public static final Type<LcAnimationStatePayload> TYPE = new Type<>(Lobecorp.id("animation_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LcAnimationStatePayload> STREAM_CODEC =
			StreamCodec.of(LcAnimationStatePayload::encode, LcAnimationStatePayload::decode);

	public LcAnimationStatePayload {
		parameters = Map.copyOf(parameters);
		rootAxes = Set.copyOf(rootAxes);
	}

	public static LcAnimationStatePayload of(LcAnimationSnapshot snapshot) {
		return new LcAnimationStatePayload(snapshot.instanceId(), snapshot.ownerEntityId(), snapshot.definitionId(),
				snapshot.content().version(), snapshot.animationName(), snapshot.timeSeconds(), snapshot.speed(),
				snapshot.paused(), snapshot.stopped(), snapshot.pose(), snapshot.parameters(),
				snapshot.worldTransform(), snapshot.rootBone(), snapshot.rootAxes(),
				snapshot.playerPolicy(), snapshot.serverTick(), snapshot.playbackRevision(), snapshot.startedTick(),
				snapshot.stoppedTick(), snapshot.termination(), snapshot.poseOverrides());
	}

	public LcAnimationSnapshot snapshot(LcAnimationContent content) {
		return new LcAnimationSnapshot(instanceId, ownerEntityId, definitionId, content, animationName,
				timeSeconds, speed, paused, stopped, pose, parameters, worldTransform, rootBone, rootAxes,
				playerPolicy, serverTick, playbackRevision, startedTick, stoppedTick, termination, poseOverrides);
	}

	private static void encode(RegistryFriendlyByteBuf buffer, LcAnimationStatePayload payload) {
		buffer.writeUUID(payload.instanceId());
		buffer.writeVarInt(payload.ownerEntityId());
		Identifier.STREAM_CODEC.encode(buffer, payload.definitionId());
		buffer.writeUtf(payload.contentVersion());
		buffer.writeUtf(payload.animationName());
		buffer.writeDouble(payload.timeSeconds());
		buffer.writeDouble(payload.speed());
		buffer.writeBoolean(payload.paused());
		buffer.writeBoolean(payload.stopped());
		writePose(buffer, payload.pose());
		buffer.writeVarInt(payload.parameters().size());
		payload.parameters().forEach((name, value) -> {
			buffer.writeUtf(name);
			buffer.writeDouble(value);
		});
		writeTransform(buffer, payload.worldTransform());
		writeNullable(buffer, payload.rootBone());
		buffer.writeVarInt(payload.rootAxes().size());
		for (var axis : payload.rootAxes()) {
			buffer.writeEnum(axis);
		}

		writePolicy(buffer, payload.playerPolicy());
		buffer.writeLong(payload.serverTick());
		buffer.writeLong(payload.playbackRevision());
		buffer.writeLong(payload.startedTick());
		buffer.writeLong(payload.stoppedTick());
		buffer.writeEnum(payload.termination());
		writePose(buffer, payload.poseOverrides());
	}

	private static LcAnimationStatePayload decode(RegistryFriendlyByteBuf buffer) {
		var id = buffer.readUUID();
		var owner = buffer.readVarInt();
		var definition = Identifier.STREAM_CODEC.decode(buffer);
		var version = buffer.readUtf();
		var animation = buffer.readUtf();
		var time = buffer.readDouble();
		var speed = buffer.readDouble();
		var paused = buffer.readBoolean();
		var stopped = buffer.readBoolean();
		var pose = readPose(buffer);
		Map<String, Double> parameters = new LinkedHashMap<>();
		var parameterCount = buffer.readVarInt();
		for (int index = 0; index < parameterCount; index++) {
			parameters.put(buffer.readUtf(), buffer.readDouble());
		}

		var world = readTransform(buffer);
		var rootBone = readNullable(buffer);
		Set<LcRootMotion.Axis> axes = EnumSet.noneOf(LcRootMotion.Axis.class);
		var axisCount = buffer.readVarInt();
		for (int index = 0; index < axisCount; index++) {
			axes.add(buffer.readEnum(LcRootMotion.Axis.class));
		}

		var policy = readPolicy(buffer);
		var serverTick = buffer.readLong();
		var revision = buffer.readLong();
		var startedTick = buffer.readLong();
		var stoppedTick = buffer.readLong();
		var termination = buffer.readEnum(LcAnimationTermination.class);
		var overrides = readPose(buffer);

		return new LcAnimationStatePayload(id, owner, definition, version, animation, time, speed, paused,
				stopped, pose, parameters, world, rootBone, axes, policy, serverTick, revision,
				startedTick, stoppedTick, termination, overrides);
	}

	private static void writePolicy(RegistryFriendlyByteBuf buffer, @Nullable LcPlayerAnimationPolicy policy) {
		buffer.writeBoolean(policy != null);
		if (policy == null) {
			return;
		}

		buffer.writeVarInt(policy.sources().size());
		policy.sources().forEach((source, weight) -> {
			Identifier.STREAM_CODEC.encode(buffer, source);
			buffer.writeDouble(weight);
		});
		buffer.writeVarInt(policy.bones().size());
		policy.bones().forEach(buffer::writeUtf);
		buffer.writeVarInt(policy.channels().size());
		policy.channels().forEach(buffer::writeEnum);
		buffer.writeDouble(policy.weight());
		buffer.writeVarInt(policy.priority());
		LcAnimationTransitionSettings.STREAM_CODEC.encode(buffer, policy.transitions());
		buffer.writeBoolean(policy.firstPersonArms());
	}

	private static @Nullable LcPlayerAnimationPolicy readPolicy(RegistryFriendlyByteBuf buffer) {
		if (!buffer.readBoolean()) {
			return null;
		}

		Map<Identifier, Double> sources = new LinkedHashMap<>();
		var sourceCount = buffer.readVarInt();
		for (int index = 0; index < sourceCount; index++) {
			sources.put(Identifier.STREAM_CODEC.decode(buffer), buffer.readDouble());
		}

		Set<String> bones = new LinkedHashSet<>();
		var boneCount = buffer.readVarInt();
		for (int index = 0; index < boneCount; index++) {
			bones.add(buffer.readUtf());
		}

		Set<LcPlayerAnimationPolicy.Channel> channels = EnumSet.noneOf(LcPlayerAnimationPolicy.Channel.class);
		var channelCount = buffer.readVarInt();
		for (int index = 0; index < channelCount; index++) {
			channels.add(buffer.readEnum(LcPlayerAnimationPolicy.Channel.class));
		}

		return new LcPlayerAnimationPolicy(sources, bones, channels, buffer.readDouble(), buffer.readVarInt(),
				LcAnimationTransitionSettings.STREAM_CODEC.decode(buffer), buffer.readBoolean());
	}

	private static void writePose(RegistryFriendlyByteBuf buffer, LcSkeletalPose pose) {
		buffer.writeVarInt(pose.bones().size());
		pose.bones().forEach((name, transform) -> {
			buffer.writeUtf(name);
			writeTransform(buffer, transform);
		});
		buffer.writeVarInt(pose.eulerRotations().size());
		pose.eulerRotations().forEach((name, angles) -> {
			buffer.writeUtf(name);
			writeVector(buffer, angles);
		});
	}

	private static LcSkeletalPose readPose(RegistryFriendlyByteBuf buffer) {
		Map<String, LcTransform> bones = new LinkedHashMap<>();
		var boneCount = buffer.readVarInt();
		for (int index = 0; index < boneCount; index++) {
			bones.put(buffer.readUtf(), readTransform(buffer));
		}

		Map<String, Vec3> rotations = new LinkedHashMap<>();
		var rotationCount = buffer.readVarInt();
		for (int index = 0; index < rotationCount; index++) {
			rotations.put(buffer.readUtf(), readVector(buffer));
		}

		return new LcSkeletalPose(bones, rotations);
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

	private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 value) {
		buffer.writeDouble(value.x);
		buffer.writeDouble(value.y);
		buffer.writeDouble(value.z);
	}

	private static Vec3 readVector(RegistryFriendlyByteBuf buffer) {
		return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
	}

	private static void writeNullable(RegistryFriendlyByteBuf buffer, @Nullable String value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeUtf(value);
		}
	}

	private static @Nullable String readNullable(RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readUtf() : null;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@Override
	public void work(IPayloadContext context, AbstractClientPlayer player) {
		LcClientAnimations.accept(this);
	}
}
