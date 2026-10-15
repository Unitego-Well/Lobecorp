package org.unitego.lobecorp.world.entity.control;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/// 实体的非持久化抓取关系；客户端只接收移动锁，锚点历史和持有目标留在服务端。
public class EntityGrabState {
	/// 抓取关系沿用附件同步；不传递服务端脱离阈值、历史位置或持有目标集合。
	public static final StreamCodec<ByteBuf, EntityGrabState> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public @NonNull EntityGrabState decode(ByteBuf buffer) {
			var state = new EntityGrabState();
			if (buffer.readBoolean()) {
				state.relation = new Relation(UUIDUtil.STREAM_CODEC.decode(buffer),
						Vec3.STREAM_CODEC.decode(buffer), 0.0, Vec3.ZERO);
			}

			return state;
		}

		@Override
		public void encode(ByteBuf buffer, EntityGrabState state) {
			Relation relation = state.relation;
			buffer.writeBoolean(relation != null);
			if (relation != null) {
				UUIDUtil.STREAM_CODEC.encode(buffer, relation.grabberId());
				Vec3.STREAM_CODEC.encode(buffer, relation.offset());
			}
		}
	};

	@Nullable
	private Relation relation;
	private final Set<UUID> heldTargets = new LinkedHashSet<>();

	@Nullable
	public Relation relation() {
		return relation;
	}

	public void setRelation(@Nullable Relation relation) {
		this.relation = relation;
	}

	public Set<UUID> heldTargets() {
		return Set.copyOf(heldTargets);
	}

	public void addHeldTarget(UUID target) {
		heldTargets.add(target);
	}

	public void removeHeldTarget(UUID target) {
		heldTargets.remove(target);
	}

	/// offset 为抓取者脚部位置上的世界轴位移，距离平方与历史锚点只用于服务端脱离判断。
	public record Relation(UUID grabberId, Vec3 offset, double escapeDistanceSquared, Vec3 fixedPosition) {
		public Relation at(Vec3 position) {
			return new Relation(grabberId, offset, escapeDistanceSquared, position);
		}
	}
}
