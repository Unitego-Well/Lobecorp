package org.unitego.lobecorp.world.entity.skill.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;

/// 不作为 Minecraft 实体注册、由实体技能创建的服务端效果实例。
public abstract class EntitySkillEffect {
	private final ServerLevel level;
	private final int lifetimeTicks;
	@Nullable
	private final Entity source;
	@Nullable
	private final EntitySkillRuntime<?> boundSkillRuntime;
	private final boolean removeWhenSourceUnavailable;
	private Vec3 position;
	private long createdGameTime;
	private int ageTicks;
	private boolean removed;

	protected EntitySkillEffect(ServerLevel level, Vec3 position, int lifetimeTicks, @Nullable Entity source,
	                            boolean removeWhenSourceUnavailable, @Nullable EntitySkillRuntime<?> skillRuntime,
	                            boolean removeWhenSkillRuntimeEnds) {
		if (lifetimeTicks == 0 || lifetimeTicks < -1) {
			throw new IllegalArgumentException("lifetimeTicks must be positive or -1");
		}
		if (removeWhenSourceUnavailable && source == null) {
			throw new IllegalArgumentException("a source is required when source-bound cleanup is enabled");
		}
		if (removeWhenSkillRuntimeEnds && skillRuntime == null) {
			throw new IllegalArgumentException("a skill runtime is required when skill-bound cleanup is enabled");
		}
		this.level = level;
		this.position = position;
		this.lifetimeTicks = lifetimeTicks;
		this.source = source;
		this.removeWhenSourceUnavailable = removeWhenSourceUnavailable;
		this.boundSkillRuntime = removeWhenSkillRuntimeEnds ? skillRuntime : null;
	}

	public ServerLevel level() {
		return level;
	}

	public Vec3 position() {
		return position;
	}

	public void setPosition(Vec3 position) {
		this.position = position;
	}

	public int ageTicks() {
		return ageTicks;
	}

	public void remove() {
		EntitySkillEffectManager.remove(this);
	}

	@Nullable
	public Entity source() {
		return source;
	}

	public boolean removeWhenSourceUnavailable() {
		return removeWhenSourceUnavailable;
	}

	@Nullable
	public EntitySkillRuntime<?> boundSkillRuntime() {
		return boundSkillRuntime;
	}

	public long createdGameTime() {
		return createdGameTime;
	}

	public void markAdded() {
		createdGameTime = level.getGameTime();
	}

	public void advanceTick() {
		ageTicks++;
		tickEffect();
	}

	public boolean isExpired() {
		return lifetimeTicks > 0 && ageTicks >= lifetimeTicks;
	}

	public boolean isRemoved() {
		return removed;
	}

	public void markRemoved() {
		removed = true;
		onRemoved();
	}

	public boolean hasUnavailableBoundSource() {
		return removeWhenSourceUnavailable
				&& (source == null || source.isRemoved() || !source.isAlive() || source.level() != level);
	}

	protected abstract void tickEffect();

	protected void onRemoved() {
	}
}
