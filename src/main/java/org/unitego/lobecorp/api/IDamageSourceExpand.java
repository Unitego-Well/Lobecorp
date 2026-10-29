package org.unitego.lobecorp.api;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.Set;
import java.util.EnumSet;
import java.util.Objects;

/// 通过 Interface Injection 暴露伤害类型标签读写；默认实现由 `DamageSourceMixin` 覆盖。
public interface IDamageSourceExpand {
	/// 单色设置只清理四色标签，其他原版及动态标签保留。
	default void lobecorp$setLcDamageType(LcDamageType type) {
		Objects.requireNonNull(type);

		for (LcDamageType candidate : LcDamageType.values()) {
			lobecorp$remove(candidate.getTag());
		}

		lobecorp$add(type.getTag());
	}

	default void lobecorp$addLcDamageType(LcDamageType type) {
		lobecorp$add(type.getTag());
	}

	default void lobecorp$removeLcDamageType(LcDamageType type) {
		lobecorp$remove(type.getTag());
	}

	default Set<LcDamageType> lobecorp$getLcDamageTypes() {
		var result = EnumSet.noneOf(LcDamageType.class);
		Set<TagKey<DamageType>> tags = lobecorp$getAllTags();
		for (LcDamageType type : LcDamageType.values()) {
			if (tags.contains(type.getTag())) {
				result.add(type);
			}
		}

		return Set.copyOf(result);
	}

	/// 获取可修改的标签的集合
	default Set<TagKey<DamageType>> lobecorp$getModifiableTags() {
		throw new NoMixinException();
	}

	/// 获取所有标签包括可修改的标签集合
	default Set<TagKey<DamageType>> lobecorp$getAllTags() {
		throw new NoMixinException();
	}

	/// 移除指定标签
	///
	/// @return 返回是否成功
	default boolean lobecorp$remove(TagKey<DamageType> tag) {
		throw new NoMixinException();
	}

	/// 添加指定标签
	///
	/// @return 添加是否成功
	default boolean lobecorp$add(TagKey<DamageType> tag) {
		throw new NoMixinException();
	}

	/// 快速添加无视无敌帧的标签
	default void addBypassesCooldow() {
		lobecorp$add(DamageTypeTags.BYPASSES_COOLDOWN);
	}
}
