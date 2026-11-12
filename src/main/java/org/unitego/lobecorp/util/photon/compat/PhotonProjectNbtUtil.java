package org.unitego.lobecorp.util.photon.compat;

import com.mojang.serialization.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.ValueInput;

import java.util.stream.Stream;

/// 使用公开ValueIO的整图Codec读取原树，不反射或暴露TagValueInput内部可变数据。
public class PhotonProjectNbtUtil {
	private static final MapCodec<CompoundTag> RAW = new MapCodec<>() {
		@Override
		public <T> RecordBuilder<T> encode(CompoundTag value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
			for (var entry : value.entrySet()) {
				prefix.add(ops.createString(entry.getKey()), NbtOps.INSTANCE.convertTo(ops, entry.getValue()));
			}
			return prefix;
		}

		@Override
		public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
			var value = ops.convertTo(NbtOps.INSTANCE, ops.createMap(input.entries()));
			return value instanceof CompoundTag compound ? DataResult.success(compound.copy()) : DataResult.error(() -> "工程原树不是复合标签。");
		}

		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Stream.empty();
		}
	};

	@SuppressWarnings("deprecation") // 当前公开ValueIO只有整图MapCodec入口能保留未声明键，不能用字段摘要代替。
	public static CompoundTag read(ValueInput input) {
		return input.read(RAW).orElseThrow(() -> new IllegalArgumentException("无法读取完整工程原树。"));
	}
}
