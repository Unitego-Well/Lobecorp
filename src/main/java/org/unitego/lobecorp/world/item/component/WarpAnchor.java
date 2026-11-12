package org.unitego.lobecorp.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/// 迁跃锚的完整物品状态；返回点可变不受固定锚点编辑权限影响。
public record WarpAnchor(Optional<Destination> anchor, Optional<Destination> returnPoint,
                         boolean anchorEditable, boolean clearing, Optional<Destination> chargeOrigin) {
	public static final WarpAnchor EMPTY = new WarpAnchor(Optional.empty(), Optional.empty(), true, false, Optional.empty());
	public static final Codec<WarpAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Destination.CODEC.optionalFieldOf("anchor").forGetter(WarpAnchor::anchor),
			Destination.CODEC.optionalFieldOf("return_point").forGetter(WarpAnchor::returnPoint),
			Codec.BOOL.optionalFieldOf("anchor_editable", true).forGetter(WarpAnchor::anchorEditable),
			Codec.BOOL.optionalFieldOf("clearing", false).forGetter(WarpAnchor::clearing),
			Destination.CODEC.optionalFieldOf("charge_origin").forGetter(WarpAnchor::chargeOrigin)
	).apply(instance, WarpAnchor::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, WarpAnchor> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public WarpAnchor beginCharge(Entity user, boolean clear) {
		var origin = !clear && (anchor.isPresent() || returnPoint.isPresent())
				? Optional.of(Destination.capture(user)) : Optional.<Destination>empty();
		return new WarpAnchor(anchor, returnPoint, anchorEditable, clear, origin);
	}

	public WarpAnchor finishCharge() {
		return new WarpAnchor(anchor, returnPoint, anchorEditable, false, Optional.empty());
	}

	public boolean hasMoved(Entity user) {
		return chargeOrigin.map(origin -> !origin.dimension().equals(user.level().dimension())
				|| origin.location().map(location -> location.position().distanceToSqr(user.position()) > 1.0E-6).orElse(true))
				.orElse(false);
	}

	public WarpAnchor withAnchor(Destination destination) {
		return new WarpAnchor(Optional.of(destination), returnPoint, anchorEditable, clearing, chargeOrigin);
	}

	public WarpAnchor withReturnPoint(Destination destination) {
		return new WarpAnchor(anchor, Optional.of(destination), anchorEditable, clearing, chargeOrigin);
	}

	public WarpAnchor withAnchorEditable(boolean editable) {
		return new WarpAnchor(anchor, returnPoint, editable, clearing, chargeOrigin);
	}

	public WarpAnchor withClearing(boolean value) {
		return new WarpAnchor(anchor, returnPoint, anchorEditable, value, chargeOrigin);
	}

	public WarpAnchor clearReturnPoint() {
		return new WarpAnchor(anchor, Optional.empty(), anchorEditable, clearing, chargeOrigin);
	}

	public WarpAnchor clearAnchor() {
		return new WarpAnchor(Optional.empty(), returnPoint, anchorEditable, clearing, chargeOrigin);
	}

	/// 省略location表示传送时按出发位置的X/Z解析目标维度地表；固定点显式保存位置和视角。
	public record Destination(ResourceKey<Level> dimension, Optional<Location> location) {
		public static final Codec<Destination> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Destination::dimension),
				Location.CODEC.optionalFieldOf("location").forGetter(Destination::location)
		).apply(instance, Destination::new));

		public static Destination surface(ResourceKey<Level> dimension) {
			return new Destination(dimension, Optional.empty());
		}

		public static Destination capture(Entity entity) {
			return new Destination(entity.level().dimension(), Optional.of(new Location(entity.position(), entity.getYRot(), entity.getXRot())));
		}
	}

	public record Location(Vec3 position, float yaw, float pitch) {
		public static final Codec<Location> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Vec3.CODEC.fieldOf("position").forGetter(Location::position),
				Codec.FLOAT.fieldOf("yaw").forGetter(Location::yaw),
				Codec.FLOAT.fieldOf("pitch").forGetter(Location::pitch)
		).apply(instance, Location::new));
	}
}
