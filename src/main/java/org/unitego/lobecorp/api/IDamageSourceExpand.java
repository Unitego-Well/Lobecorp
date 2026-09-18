package org.unitego.lobecorp.api;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.Set;

public interface IDamageSourceExpand {
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
