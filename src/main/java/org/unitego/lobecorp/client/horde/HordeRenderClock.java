package org.unitego.lobecorp.client.horde;

/// 仅平滑事件公共时钟，不创建逐单位CPU运动状态。
public class HordeRenderClock {
	private static final double CORRECTION_TICKS = 20;
	private static final double MAX_CORRECTION_RATE = 0.25;
	private static final double MAX_EXTRAPOLATION_TICKS = 40;
	private double base;
	private double correction;
	private double received;
	private boolean initialized;

	public void synchronize(double serverTick, double clientTick) {
		double current = initialized ? time(clientTick) : serverTick;
		base = current;
		correction = serverTick - current;
		received = clientTick;
		initialized = true;
	}

	public double time(double clientTick) {
		double elapsed = Math.clamp(clientTick - received, 0, MAX_EXTRAPOLATION_TICKS);
		double duration = Math.max(CORRECTION_TICKS, Math.abs(correction) / MAX_CORRECTION_RATE);
		return base + elapsed - correction * Math.expm1(-elapsed / duration);
	}
}
