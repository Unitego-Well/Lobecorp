package org.unitego.lobecorp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.api.IDamageSourceExpand;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Mixin(DamageSource.class)
public abstract class DamageSourceMixin implements IDamageSourceExpand {
	@Shadow
	@Final
	private Holder<DamageType> type;

	@Unique
	private final Set<TagKey<DamageType>> lobecorp$modifiableTags = new HashSet<>();

	/// 需要从原始及动态伤害标签中排除的标签。
	@Unique
	private final Set<TagKey<DamageType>> lobecorp$eliminateTags = new HashSet<>();

	@Override
	public Set<TagKey<DamageType>> lobecorp$getModifiableTags() {
		Set<TagKey<DamageType>> hashSet = new HashSet<>(lobecorp$modifiableTags);
		hashSet.removeAll(lobecorp$eliminateTags);
		return Collections.unmodifiableSet(hashSet);
	}

	@Override
	public Set<TagKey<DamageType>> lobecorp$getAllTags() {
		Set<TagKey<DamageType>> hashSet = new HashSet<>(lobecorp$modifiableTags);
		hashSet.addAll(type.tags().toList());
		hashSet.removeAll(lobecorp$eliminateTags);
		return Collections.unmodifiableSet(hashSet);
	}

	@Override
	public boolean lobecorp$remove(TagKey<DamageType> tag) {
		// 非短路运算确保两个标签集合均完成更新。
		return (!lobecorp$modifiableTags.isEmpty() && lobecorp$modifiableTags.remove(tag)) | lobecorp$eliminateTags.add(tag);
	}

	@Override
	public boolean lobecorp$add(TagKey<DamageType> tag) {
		// 非短路运算确保两个标签集合均完成更新。
		return lobecorp$modifiableTags.add(tag) | lobecorp$eliminateTags.remove(tag);
	}

	@WrapMethod(method = "is(Lnet/minecraft/tags/TagKey;)Z")
	private boolean lobecorp$is(TagKey<DamageType> tag, Operation<Boolean> original) {
		return !lobecorp$eliminateTags.contains(tag) && ((!lobecorp$modifiableTags.isEmpty() && lobecorp$modifiableTags.contains(tag)) || original.call(tag));
	}
}
