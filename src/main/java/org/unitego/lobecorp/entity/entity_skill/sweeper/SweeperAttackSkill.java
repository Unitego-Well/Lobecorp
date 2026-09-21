package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.entity.util.EntityUtil;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.hitbox.SectorCylinderSize;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫 3 段攻击技能：attack → attack2 → attack3 → attack 循环。
/// <p>
/// 每段由前摇 + 后摇组成（共 20 tick，与 1s 动画对齐）。
/// 连段自动衔接：每段后摇结束由 {@link Sweeper#getAttackCombo()} 进位，
/// 战斗行为持续施放即可自动打出下一段。
public class SweeperAttackSkill extends SweeperSkill {
	/// 一套连击（3 段）完成后进入的冷却
	private static final int COMBO_COOLDOWN = 2 * TICKS_PER_SECOND;
	/// 普通攻击的总连击段数
	private static final int COMBO_LENGTH = 3;
	/// 第一段攻击相对基础攻击伤害增加的倍率
	private static final float FIRST_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第二段攻击相对基础攻击伤害增加的倍率
	private static final float SECOND_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第三段攻击相对基础攻击伤害增加的倍率
	private static final float THIRD_ATTACK_DAMAGE_MODIFIER = 1.5F;
	/// 普通攻击距离，同时作为扇形判断框半径
	private static final double ATTACK_RANGE = 2.0;
	/// 普通攻击扇形的完整角度，与女皇横扫一致
	private static final double ATTACK_ANGLE_DEGREES = 120.0;
	/// 普通攻击扇形的高度，与女皇横扫一致
	private static final double ATTACK_HEIGHT = 3.0;
	/// 每段普通攻击最多命中的目标数量
	private static final int MAXIMUM_TARGET_COUNT = 2;
	/// 本次攻击创建的判断框编号
	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	/// 判断框使用的连击段数
	private static final TypedDataKey<Integer> HITBOX_COMBO = TypedDataKey.create();
	/// 判断框所属的技能运行态
	private static final TypedDataKey<EntitySkillRuntime<Sweeper>> HITBOX_RUNTIME = TypedDataKey.create();
	/// 普通攻击扇形判断框共享模板
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new SectorCylinderSize(ATTACK_RANGE, ATTACK_HEIGHT, ATTACK_ANGLE_DEGREES),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof Sweeper entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				Integer combo = context.instance().getData(HITBOX_COMBO);
				if (combo == null || !entity.doHurtTarget(context.level(), target, getAttackDamageModifier(combo))) {
					return false;
				}
				EntitySkillRuntime<Sweeper> runtime = context.instance().getData(HITBOX_RUNTIME);
				if (runtime != null) {
					runtime.markSuccessful();
				}
				SimpleParticleType strikeParticle = getStrikeParticle(combo);
				EntityUtil.getHitPosOnAABB(entity, target).ifPresent(hitPos ->
						context.level().sendParticles(strikeParticle, hitPos.x, hitPos.y, hitPos.z,
								1, 0, 0, 0, 0));
				return true;
			}
	);

	public SweeperAttackSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		if (target == null) {
			return false;
		}
		if (!target.isAlive()) {
			return false;
		}
		if (!entity.isValidTarget(target)) {
			return false;
		}
		if (!entity.hasLineOfSight(target)) {
			return false;
		}
		if (!isWithinAttackRange(entity, target)) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.ATTACK);
		int combo = entity.getAttackCombo() % COMBO_LENGTH;
		SweeperAnim animation = SweeperAnim.values()[SweeperAnim.ATTACK1.ordinal() + combo];
		entity.playActionAnimation(animation);
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + durationTicks() + recoveryTicks();
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime);
			hitbox.follow(entity, new Vec3(0.0, ATTACK_HEIGHT / 2.0, 0.0), true);
			hitbox.appendTargetFilter(entity::isValidTarget);
			hitbox.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.ONCE, 0, 1, MAXIMUM_TARGET_COUNT));
			hitbox.setData(HITBOX_COMBO, combo);
			hitbox.setData(HITBOX_RUNTIME, runtime);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LivingEntity target = getTarget(runtime);
		if (target == null) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!target.isAlive()) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!entity.isValidTarget(target)) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!isWithinAttackRange(entity, target)) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}

		entity.swing(InteractionHand.MAIN_HAND);
		HitboxInstance hitbox = getHitbox(level, runtime);
		if (hitbox != null) {
			hitbox.activate();
		}
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {

	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {

	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopActionAnimation();
		entity.removeEntityState(SweeperStates.ATTACK);
		removeHitbox(entity, runtime);
		if (!runtime.isSuccessful()) {
			return;
		}
		int combo = (entity.getAttackCombo() + 1) % COMBO_LENGTH;
		entity.setAttackCombo(combo);
		if (combo == 0) {
			EntitySkillManager.setCooldown(entity, this, COMBO_COOLDOWN);
		}
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopActionAnimation();
		entity.removeEntityState(SweeperStates.ATTACK);
		removeHitbox(entity, runtime);
	}

	private HitboxInstance getHitbox(ServerLevel level, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.getData(HITBOX_ID);
		return id == null ? null : HitboxManager.get(level, id);
	}

	private void removeHitbox(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (id != null && entity.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}

	private static SimpleParticleType getStrikeParticle(int combo) {
		return switch (combo) {
			case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
			case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
			case 2 -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
			default -> throw new IllegalStateException();
		};
	}

	private static float getAttackDamageModifier(int combo) {
		return switch (combo) {
			case 0 -> FIRST_ATTACK_DAMAGE_MODIFIER;
			case 1 -> SECOND_ATTACK_DAMAGE_MODIFIER;
			case 2 -> THIRD_ATTACK_DAMAGE_MODIFIER;
			default -> throw new IllegalStateException();
		};
	}

	private static boolean isWithinAttackRange(Sweeper entity, LivingEntity target) {
		return entity.distanceToSqr(target) <= ATTACK_RANGE * ATTACK_RANGE;
	}
}
