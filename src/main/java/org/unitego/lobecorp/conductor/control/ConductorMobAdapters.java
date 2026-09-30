package org.unitego.lobecorp.conductor.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.control.ConductorCapabilities.MobControl;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;

/// 不同实体类型的指挥命令适配实现。
public class ConductorMobAdapters {
	public static class GhastControl extends MobControl {
		public GhastControl(Ghast mob) {
			super(mob);
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level) {
				ConductorData.Unit command = ConductorData.get(level.getServer()).unit(mob.getUUID());
				if (command != null && command.order() != ConductorData.OrderType.MOVE
						&& command.order() != ConductorData.OrderType.RETURN) clearNavigation();
			}
			super.tick();
		}

		private void clearNavigation() {
			ConductorUnitRuntime runtime = ConductorUnitRuntime.get(mob);
			if (runtime.flyingNavigation != null) runtime.flyingNavigation.stop();
			runtime.flyingNavigation = null;
		}

		@Override
		public void advanceMovement(ConductorData data, ConductorData.Unit command) {
			ConductorUnitRuntime runtime = ConductorUnitRuntime.get(mob);
			if (runtime.flyingNavigation == null) runtime.flyingNavigation = new FlyingPathNavigation(mob, mob.level());
			FlyingPathNavigation navigation = runtime.flyingNavigation;
			if (ConductorMovement.advance(mob, navigation, data, command)) navigation.tick();
			else navigation.stop();
		}

		@Override
		public void movementStopped() {
			clearNavigation();
			mob.setDeltaMovement(Vec3.ZERO);
		}
	}

	public static class BreezeControl extends MobControl {
		public BreezeControl(Breeze mob) {
			super(mob);
		}

		@Override
		public void stop() {
			if (mob.getPose() == Pose.SLIDING) mob.setPose(Pose.STANDING);
			super.stop();
		}

		@Override
		public void movementArrived() {
			mob.setPose(Pose.STANDING);
		}
	}

	public static class QueenControl extends MobControl {
		public QueenControl(TheQueenOfHatred mob) {
			super(mob);
		}

		@Override
		public void prepareMovement() {
			((TheQueenOfHatred) mob).cancelConductorSitting();
		}
	}

	public static class DragonControl extends MobControl {
		public DragonControl(EnderDragon mob) {
			super(mob);
		}

		@Override
		public void advanceMovement(ConductorData data, ConductorData.Unit command) {
			if (!ConductorMovement.advance(mob, mob.getNavigation(), data, command)) return;
			EnderDragon dragon = (EnderDragon) mob;
			if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) {
				dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
			}
		}
	}
}
