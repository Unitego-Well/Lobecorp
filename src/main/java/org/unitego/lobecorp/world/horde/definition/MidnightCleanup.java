package org.unitego.lobecorp.world.horde.definition;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityDimensions;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;
import net.minecraft.world.entity.EntitySpawnReason;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.combat.CombatData;
import org.unitego.lobecorp.world.entity.ordeal.indigo.IIndigoOrdeal;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperAi;
import org.unitego.lobecorp.world.horde.IHordeEventProfile;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.HordeTargetContext;
import org.unitego.lobecorp.world.horde.HordeSpawnContext;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;
import net.neoforged.neoforge.event.EventHooks;

public class MidnightCleanup implements IHordeEventProfile {
	public static final Identifier ID = Lobecorp.id("midnight_cleanup");
	public static final String FACTION = "horde_indigo";

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public String faction() {
		return FACTION;
	}

	@Override
	public HordeRules defaults() {
		return HordeRules.configured();
	}

	@Override
	public double movementSpeed() {
		return SweeperAi.runningGroundSpeed();
	}

	@Override
	public boolean acceptsTarget(HordeTargetContext context, LivingEntity target) {
		if (!CombatUtil.canTargetInLevel(context.level(), target) || target instanceof EntityCorpse<?>) {
			return false;
		}
		if (CombatUtil.isIndiscriminate(target)) {
			return true;
		}
		if (target instanceof IIndigoOrdeal || target.is(LcEntityTypeTags.ORDEAL_INDIGO)) {
			return false;
		}
		var affiliation = CombatData.get(context.level().getServer()).affiliation(target.getUUID());
		return affiliation == null || !affiliation.faction().equals(context.faction());
	}

	@Override
	public Mob createParticipant(ServerLevel level, HordeSpawnContext context) {
		var sweeper = OrdealEntityTypes.SWEEPER.get().create(level, EntitySpawnReason.COMMAND);
		if (sweeper == null) {
			throw new IllegalStateException("清道夫实体类型无法创建");
		}
		sweeper.setPos(context.position());
		EventHooks.finalizeMobSpawn(sweeper, level, level.getCurrentDifficultyAt(sweeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		sweeper.setHealth(sweeper.getMaxHealth());
		return sweeper;
	}

	@Override
	public HordeVisualDefinition visuals() {
		return new HordeVisualDefinition(Lobecorp.id("entity/sweeper_a"),
				Lobecorp.id("entity/sweeper_a"), "run",
				Lobecorp.id("textures/entity/sweeper_a.png"));
	}

	@Override
	public EntityDimensions dimensions() {
		return OrdealEntityTypes.SWEEPER.get().getDimensions();
	}

	@Override
	public boolean startsCombat(IEntitySkill<?> skill) {
		return skill == SweeperSkills.ATTACK.get() || skill == SweeperSkills.LEAP.get();
	}
}
