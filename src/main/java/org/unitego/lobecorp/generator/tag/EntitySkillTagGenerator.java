package org.unitego.lobecorp.generator.tag;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.KeyTagProvider;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcRegistrieKeys;
import org.unitego.lobecorp.registry.LcRegistries;
import org.unitego.lobecorp.registry.world.entity.skill.SweeperSkills;
import org.unitego.lobecorp.registry.world.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.world.tag.LcEntitySkillTags;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class EntitySkillTagGenerator extends KeyTagProvider<IEntitySkill<?>> {
	public EntitySkillTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, LcRegistrieKeys.ENTITY_SKILL, lookupProvider.thenApply(EntitySkillTagGenerator::withSkills), Lobecorp.NAMESPACE);
	}

	private static HolderLookup.Provider withSkills(HolderLookup.Provider registries) {
		// 技能是模组静态注册表，数据包注册表查找未必包含它；使用已注册的同一实例校验标签成员。
		return HolderLookup.Provider.create(Stream.concat(
				registries.listRegistries().filter(lookup -> !lookup.key().equals(LcRegistrieKeys.ENTITY_SKILL)),
				Stream.of(LcRegistries.ENTITY_SKILL)));
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		tag(LcEntitySkillTags.DAMAGE)
				.add(TheQueenOfHatredSkills.SWEEP.getKey(),
						TheQueenOfHatredSkills.STAR_BEAM.getKey(),
						TheQueenOfHatredSkills.LASER.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey(),
						TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getKey());

		tag(LcEntitySkillTags.DEFENSE)
				.add(TheQueenOfHatredSkills.REPEL.getKey(),
						TheQueenOfHatredSkills.SWEEP.getKey(),
						TheQueenOfHatredSkills.DAMAGE_REDUCTION.getKey(),
						TheQueenOfHatredSkills.PURIFICATION.getKey());

		tag(LcEntitySkillTags.HEALING)
				.add(SweeperSkills.SWEEP.getKey());

		tag(LcEntitySkillTags.MAGIC)
				.add(TheQueenOfHatredSkills.STAR_BEAM.getKey(),
						TheQueenOfHatredSkills.LASER.getKey(),
						TheQueenOfHatredSkills.DAMAGE_REDUCTION.getKey(),
						TheQueenOfHatredSkills.PURIFICATION.getKey(),
						TheQueenOfHatredSkills.SLOWNESS.getKey(),
						TheQueenOfHatredSkills.MARK.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey(),
						TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getKey());

		tag(LcEntitySkillTags.MELEE)
				.addTag(LcEntitySkillTags.PURE_MELEE)
				.add(TheQueenOfHatredSkills.REPEL.getKey(),
						TheQueenOfHatredSkills.SWEEP.getKey());

		tag(LcEntitySkillTags.MOVEMENT)
				.add(SweeperSkills.LEAP.getKey());

		tag(LcEntitySkillTags.PROJECTILE)
				.add(TheQueenOfHatredSkills.SWEEP.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey());

		tag(LcEntitySkillTags.PURE_MELEE)
				.add(SweeperSkills.ATTACK.getKey(),
						SweeperSkills.LEAP.getKey(),
						TheQueenOfHatredSkills.ATTACK.getKey());

		tag(LcEntitySkillTags.RANGED)
				.addTag(LcEntitySkillTags.PROJECTILE)
				.add(TheQueenOfHatredSkills.STAR_BEAM.getKey(),
						TheQueenOfHatredSkills.LASER.getKey(),
						TheQueenOfHatredSkills.SLOWNESS.getKey(),
						TheQueenOfHatredSkills.MARK.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey(),
						TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getKey());

		tag(LcEntitySkillTags.SPELL)
				.add(TheQueenOfHatredSkills.STAR_BEAM.getKey(),
						TheQueenOfHatredSkills.LASER.getKey(),
						TheQueenOfHatredSkills.DAMAGE_REDUCTION.getKey(),
						TheQueenOfHatredSkills.PURIFICATION.getKey(),
						TheQueenOfHatredSkills.SLOWNESS.getKey(),
						TheQueenOfHatredSkills.MARK.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey(),
						TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getKey());

		tag(LcEntitySkillTags.STATIONARY)
				.addTag(LcEntitySkillTags.MELEE)
				.add(TheQueenOfHatredSkills.STAR_BEAM.getKey(),
						TheQueenOfHatredSkills.LASER.getKey(),
						TheQueenOfHatredSkills.DAMAGE_REDUCTION.getKey(),
						TheQueenOfHatredSkills.PURIFICATION.getKey(),
						TheQueenOfHatredSkills.SLOWNESS.getKey(),
						TheQueenOfHatredSkills.MARK.getKey(),
						TheQueenOfHatredSkills.STARFALL.getKey(),
						TheQueenOfHatredSkills.PILLAR_OF_LIGHT.getKey());
	}
}
