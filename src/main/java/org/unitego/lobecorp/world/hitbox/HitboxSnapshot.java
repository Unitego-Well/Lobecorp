package org.unitego.lobecorp.world.hitbox;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.entity.skill.effect.EntitySkillEffectDebugInfo;
import org.unitego.lobecorp.world.hitbox.geometry.*;

/// 客户端调试渲染需要的完整判断框实例快照。
///
/// @param id             Level 内实例编号
/// @param size           当前类型化尺寸
/// @param position       当前世界中心
/// @param rotation       当前欧拉旋转
/// @param purpose        判断框的逻辑用途
/// @param active         是否处于伤害阶段
/// @param remainingTicks 服务端剩余寿命
public record HitboxSnapshot(int id, IHitboxSize size, Vec3 position, Vec3 rotation, HitboxPurpose purpose,
                             boolean active,
                             int remainingTicks, @Nullable EntitySkillEffectDebugInfo entitySkillEffectDebugInfo) {
	/// 判断框网络编码遇到未知尺寸类型时的错误前缀。
	private static final String UNSUPPORTED_SIZE_PREFIX = "Unsupported hitbox size: ";

	public static final StreamCodec<RegistryFriendlyByteBuf, HitboxSnapshot> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull HitboxSnapshot decode(@NonNull RegistryFriendlyByteBuf buffer) {
			int id = buffer.readVarInt();
			HitboxShapeType shapeType = HitboxShapeType.values()[buffer.readVarInt()];
			IHitboxSize size = decodeSize(buffer, shapeType);
			Vec3 position = Vec3.STREAM_CODEC.decode(buffer);
			Vec3 rotation = Vec3.STREAM_CODEC.decode(buffer);
			HitboxPurpose purpose = HitboxPurpose.values()[buffer.readVarInt()];
			boolean active = buffer.readBoolean();
			int remainingTicks = buffer.readVarInt();
			EntitySkillEffectDebugInfo entitySkillEffectDebugInfo = buffer.readBoolean()
					? new EntitySkillEffectDebugInfo(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt())
					: null;
			return new HitboxSnapshot(id, size, position, rotation, purpose, active, remainingTicks,
					entitySkillEffectDebugInfo);
		}

		@Override
		public void encode(@NonNull RegistryFriendlyByteBuf buffer, @NonNull HitboxSnapshot snapshot) {
			buffer.writeVarInt(snapshot.id);
			buffer.writeVarInt(snapshot.size.type().ordinal());
			encodeSize(buffer, snapshot.size);
			Vec3.STREAM_CODEC.encode(buffer, snapshot.position);
			Vec3.STREAM_CODEC.encode(buffer, snapshot.rotation);
			buffer.writeVarInt(snapshot.purpose.ordinal());
			buffer.writeBoolean(snapshot.active);
			buffer.writeVarInt(snapshot.remainingTicks);
			buffer.writeBoolean(snapshot.entitySkillEffectDebugInfo != null);
			if (snapshot.entitySkillEffectDebugInfo != null) {
				buffer.writeUtf(snapshot.entitySkillEffectDebugInfo.name());
				buffer.writeUtf(snapshot.entitySkillEffectDebugInfo.owner());
				buffer.writeUtf(snapshot.entitySkillEffectDebugInfo.skill());
				buffer.writeVarInt(snapshot.entitySkillEffectDebugInfo.lifetimeTicks());
			}
		}
	};

	/// 创建服务端实例的当前可见快照。
	///
	/// @param instance 服务端实例
	/// @return 不包含模板、效果、过滤器或命中历史的快照
	public static HitboxSnapshot of(HitboxInstance instance) {
		return new HitboxSnapshot(instance.id(), instance.size(), instance.position(), instance.rotation(),
				instance.template().purpose(),
				instance.isActive(), instance.remainingTicks(), instance.entitySkillEffectDebugInfo());
	}

	private static IHitboxSize decodeSize(RegistryFriendlyByteBuf buffer, HitboxShapeType type) {
		return switch (type) {
			case SPHERE -> new SphereSize(buffer.readDouble());
			case CYLINDER -> new CylinderSize(buffer.readDouble(), buffer.readDouble());
			case BOX -> new BoxSize(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
			case SECTOR_CYLINDER ->
					new SectorCylinderSize(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
			case CONE -> new ConeSize(buffer.readDouble(), buffer.readDouble());
			case ELLIPSOID -> new EllipsoidSize(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
			case RING_CYLINDER -> new RingCylinderSize(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
			case BOX_RING_CYLINDER ->
					new BoxRingCylinderSize(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
		};
	}

	private static void encodeSize(RegistryFriendlyByteBuf buffer, IHitboxSize size) {
		switch (size) {
			case SphereSize(double radius) -> buffer.writeDouble(radius);
			case CylinderSize(double radius, double height) -> {
				buffer.writeDouble(radius);
				buffer.writeDouble(height);
			}

			case BoxSize(double width, double height, double depth) -> {
				buffer.writeDouble(width);
				buffer.writeDouble(height);
				buffer.writeDouble(depth);
			}

			case SectorCylinderSize(double radius, double height, double angleDegrees) -> {
				buffer.writeDouble(radius);
				buffer.writeDouble(height);
				buffer.writeDouble(angleDegrees);
			}

			case ConeSize(double length, double angleDegrees) -> {
				buffer.writeDouble(length);
				buffer.writeDouble(angleDegrees);
			}

			case EllipsoidSize(double radiusX, double radiusY, double radiusZ) -> {
				buffer.writeDouble(radiusX);
				buffer.writeDouble(radiusY);
				buffer.writeDouble(radiusZ);
			}

			case RingCylinderSize(double radius, double height, double thickness) -> {
				buffer.writeDouble(radius);
				buffer.writeDouble(height);
				buffer.writeDouble(thickness);
			}

			case BoxRingCylinderSize(double width, double height, double depth, double thickness) -> {
				buffer.writeDouble(width);
				buffer.writeDouble(height);
				buffer.writeDouble(depth);
				buffer.writeDouble(thickness);
			}

			default -> throw new IllegalStateException(UNSUPPORTED_SIZE_PREFIX + size.getClass().getName());
		}
	}
}
