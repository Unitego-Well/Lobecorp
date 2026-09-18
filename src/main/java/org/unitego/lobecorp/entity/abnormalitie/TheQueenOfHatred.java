package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillHolder;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

import java.util.Collection;
import java.util.List;

public class TheQueenOfHatred extends PathfinderMob implements GeoEntity, EntitySkillHolder, IAbnormalitie {
	public TheQueenOfHatred(Level level) {
		this(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), level);
	}

	public TheQueenOfHatred(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return null;
	}

	@Override
	public Collection<IEntitySkill<TheQueenOfHatred>> skills() {
		return List.of();
	}
}
