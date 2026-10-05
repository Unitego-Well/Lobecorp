package org.unitego.lobecorp.conductor.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.config.ConductorRules;


/// 处理指挥家对指定位置的远程或近战攻击节奏。
public class ConductorPointAttack {

	private static State state(Mob mob) {
		ConductorUnitRuntime runtime = ConductorUnitRuntime.get(mob);
		if (runtime.pointAttack == null)
			runtime.pointAttack = new State();
		return runtime.pointAttack;
	}

	public static boolean supports(Mob mob) {
		if (mob instanceof Breeze) {
			return true;
		}
		if (mob instanceof AbstractSkeleton && !mob.isHolding(stack -> stack.getItem() instanceof BowItem)) {
			return false;
		}
		return mob instanceof RangedAttackMob;
	}

	public static void tick(ServerLevel level, Mob mob, Vec3 position) {
		State state = state(mob);
		if (mob instanceof Breeze breeze) {
			tickBreeze(level, breeze, position, state);
			return;
		}
		if (!(mob instanceof RangedAttackMob ranged)) {
			return;
		}
		InteractionHand crossbowHand = ProjectileUtil.getWeaponHoldingHand(mob,
				item -> item instanceof CrossbowItem);
		ItemStack crossbow = mob.getItemInHand(crossbowHand);
		if (mob instanceof CrossbowAttackMob crossbowMob && crossbow.getItem() instanceof CrossbowItem crossbowItem) {
			if (state.weapon != crossbow || state.hand != crossbowHand) {
				stop(mob);
				state = state(mob);
				state.weapon = crossbow;
				state.hand = crossbowHand;
			}
			tickCrossbow(level, mob, crossbowMob, crossbowItem, position, state);
			return;
		}
		InteractionHand bowHand = ProjectileUtil.getWeaponHoldingHand(mob, item -> item instanceof BowItem);
		ItemStack bow = mob.getItemInHand(bowHand);
		if (bow.getItem() instanceof BowItem) {
			mob.setAggressive(true);
			if (state.weapon != bow || state.hand != bowHand) {
				stop(mob);
				state = state(mob);
				state.weapon = bow;
				state.hand = bowHand;
			}
			if (mob.tickCount < state.readyAt) {
				return;
			}
			if (!mob.isUsingItem()) {
				mob.startUsingItem(bowHand);
			} else if (mob.getTicksUsingItem() >= ConductorRules.BOW_DRAW_TICKS) {
				int pullTime = mob.getTicksUsingItem();
				mob.stopUsingItem();
				ranged.performRangedAttack(point(level, position), BowItem.getPowerForTime(pullTime));
				state.readyAt = mob.tickCount + ConductorRules.POINT_ATTACK_INTERVAL_TICKS;
			}
			return;
		}
		if (mob instanceof AbstractSkeleton) {
			return;
		}
		if (mob.tickCount >= state.readyAt) {
			ranged.performRangedAttack(point(level, position), ConductorRules.POINT_ATTACK_POWER);
			state.readyAt = mob.tickCount + ConductorRules.POINT_ATTACK_INTERVAL_TICKS;
		}
	}

	private static void tickCrossbow(ServerLevel level, Mob mob, CrossbowAttackMob shooter,
	                                 CrossbowItem crossbowItem, Vec3 position, State state) {
		if (!CrossbowItem.isCharged(state.weapon)) {
			if (!mob.isUsingItem()) {
				mob.startUsingItem(state.hand);
				shooter.setChargingCrossbow(true);
			} else if (mob.getTicksUsingItem() >= CrossbowItem.getChargeDuration(state.weapon, mob)) {
				mob.releaseUsingItem();
				shooter.setChargingCrossbow(false);
				state.readyAt = mob.tickCount + ConductorRules.CROSSBOW_READY_DELAY_TICKS
						+ mob.getRandom().nextInt(ConductorRules.CROSSBOW_READY_RANDOM_TICKS);
			}
		} else if (mob.tickCount >= state.readyAt) {
			crossbowItem.performShooting(level, mob, state.hand, state.weapon,
					ConductorRules.CROSSBOW_POINT_ATTACK_POWER,
					ConductorRules.POINT_ATTACK_UNCERTAINTY_BASE
							- level.getDifficulty().getId() * ConductorRules.POINT_ATTACK_UNCERTAINTY_PER_DIFFICULTY,
					point(level, position));
			shooter.onCrossbowAttackPerformed();
			state.readyAt = mob.tickCount + ConductorRules.POINT_ATTACK_INTERVAL_TICKS;
		}
	}

	private static void tickBreeze(ServerLevel level, Breeze breeze, Vec3 position, State state) {
		if (state.shootAt != 0) {
			if (breeze.tickCount < state.shootAt) {
				return;
			}
			Vec3 direction = position.subtract(breeze.getX(), breeze.getFiringYPosition(), breeze.getZ());
			Projectile.spawnProjectileUsingShoot(new BreezeWindCharge(breeze, level), level, ItemStack.EMPTY,
					direction.x, direction.y, direction.z, ConductorRules.BREEZE_PROJECTILE_SPEED,
					ConductorRules.BREEZE_UNCERTAINTY_BASE
							- level.getDifficulty().getId() * ConductorRules.BREEZE_UNCERTAINTY_PER_DIFFICULTY);
			breeze.playSound(SoundEvents.BREEZE_SHOOT, ConductorRules.BREEZE_SHOOT_VOLUME, 1.0F);
			breeze.setPose(Pose.STANDING);
			state.shootAt = 0;
			state.readyAt = breeze.tickCount + ConductorRules.BREEZE_RECOVER_TICKS + ConductorRules.BREEZE_SHOOT_COOLDOWN_TICKS;
		} else if (breeze.tickCount >= state.readyAt) {
			breeze.setPose(Pose.SHOOTING);
			breeze.playSound(SoundEvents.BREEZE_INHALE, 1.0F, 1.0F);
			state.shootAt = breeze.tickCount + ConductorRules.BREEZE_CHARGE_TICKS;
		}
	}

	private static ArmorStand point(ServerLevel level, Vec3 position) {
		return new ArmorStand(level, position.x, position.y, position.z);
	}

	public static void stop(Mob mob) {
		stop(mob, false);
	}

	public static void stop(Mob mob, boolean attackingEntity) {
		State state = ConductorUnitRuntime.get(mob).pointAttack;
		ConductorUnitRuntime.get(mob).pointAttack = null;
		if (state == null) {
			return;
		}
		if (mob.isUsingItem() && mob.getUseItem() == state.weapon) {
			mob.stopUsingItem();
		}
		if (state.weapon.getItem() instanceof BowItem && !attackingEntity) {
			mob.setAggressive(false);
		}
		if (mob instanceof CrossbowAttackMob shooter) {
			shooter.setChargingCrossbow(false);
		}
		if (mob instanceof Breeze breeze && breeze.getPose() == Pose.SHOOTING) {
			breeze.setPose(Pose.STANDING);
		}
	}

	protected static class State {
		private ItemStack weapon = ItemStack.EMPTY;
		private InteractionHand hand = InteractionHand.MAIN_HAND;
		private int readyAt;
		private int shootAt;
	}
}
