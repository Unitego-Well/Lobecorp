package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 一个客户端 Photon 播放实例；locator 首次可用后才发射，时长与模拟速度独立。
 */
public class PhotonGeoEffect implements IEffectExecutor {
	/**
	 * 不设置外部截止时间，沿用资源自身生命周期。
	 */
	public static final int UNLIMITED_DURATION = -1;
	private final FXRuntime runtime;
	private final Level level;
	private final Object source;
	private final String locator;
	private boolean started;
	private boolean stopped;
	private boolean forceStopped;
	private Vec3 sourcePosition;
	private float simulationSpeed = 1;
	private int durationTicks = UNLIMITED_DURATION;
	private long startedAt;

	public PhotonGeoEffect(FX fx, Level level, Object source, String locator, Vec3 position) {
		this.runtime = fx.createRuntime();
		this.level = level;
		this.source = source;
		this.locator = locator;
		this.sourcePosition = source instanceof Entity entity ? entity.position() : position;
		this.runtime.root.updatePos(position.toVector3f());
	}

	@Override
	public Level getLevel() {
		return this.level;
	}

	public String locator() {
		return this.locator;
	}

	/**
	 * 返回独立实例，可通过 findObject/runtime() 覆盖发射器参数，不修改共享 FX 资源。
	 */
	public FXRuntime runtime() {
		return this.runtime;
	}

	/**
	 * 只调整发射器模拟倍率；Photon Timeline 的主时钟仍按游戏 tick 推进。
	 */
	public void setSimulationSpeed(float speed) {
		if (!Float.isFinite(speed) || speed < 0) {
			throw new IllegalArgumentException();
		}
		this.simulationSpeed = speed;
		this.runtime.root.setSelfTimeScale(speed);
	}

	/**
	 * 从实际发射时刻计算总时长；修改时长不会重置已播放时间。
	 */
	public void setDurationTicks(int ticks) {
		if (ticks < 0 && ticks != UNLIMITED_DURATION) {
			throw new IllegalArgumentException();
		}
		this.durationTicks = ticks;
	}

	public boolean isAlive() {
		if (this.forceStopped || this.started && this.runtime.getHost() != null
				&& this.runtime.getHost().generation() != this.runtime.getGenerationAtEmit()) {
			return false;
		}
		if (!this.started) {
			return !this.stopped;
		}
		return !this.runtime.isFinished() && (this.stopped || this.runtime.isValid());
	}

	public boolean isStopped() {
		return this.stopped;
	}

	/**
	 * false 停止发射并保留余粒子，true 立即清除；允许将柔和结束升级为立即清除。
	 */
	public void stop(boolean force) {
		if (!this.stopped || force) {
			this.stopped = true;
			this.forceStopped |= force;
			this.runtime.root.setSelfTimeScale(1);
			this.runtime.destroy(force);
		}
	}

	public void updateAnchor(PhotonAnchorTransform anchor) {
		if (this.stopped) {
			return;
		}
		this.runtime.root.updatePos(anchor.position());
		this.runtime.root.updateRotation(anchor.rotation());
		this.runtime.root.updateScale(anchor.scale());
		if (this.source instanceof Entity entity) {
			this.sourcePosition = entity.position();
		}
		start();
	}

	public void start() {
		if (!this.started && !this.stopped) {
			if (this.durationTicks == 0) {
				stop(true);
				return;
			}
			this.started = true;
			this.startedAt = this.level.getGameTime();
			this.runtime.emit(this);
			this.runtime.root.setSelfTimeScale(this.simulationSpeed);
		}
	}

	@SuppressWarnings("resource")
	public void tick() {
		if (Minecraft.getInstance().level != this.level
				|| this.source instanceof Entity entity && (entity.isRemoved() || !entity.isAlive() || entity.level() != this.level)
				|| this.source instanceof BlockEntity blockEntity && (blockEntity.isRemoved() || blockEntity.getLevel() != this.level)) {
			stop(true);
			return;
		}
		if (!this.started || this.stopped) {
			return;
		}
		if (this.durationTicks != UNLIMITED_DURATION && this.level.getGameTime() - this.startedAt >= this.durationTicks) {
			stop(false);
		} else if (this.source instanceof Entity entity) {
			Vec3 currentPosition = entity.position();
			if (this.locator.isBlank()) {
				this.runtime.root.updatePos(currentPosition.toVector3f());
			} else {
				this.runtime.root.updatePos(new Vector3f(this.runtime.root.transform().position())
						.add(currentPosition.subtract(this.sourcePosition).toVector3f()));
			}
			this.sourcePosition = currentPosition;
		}
	}
}
