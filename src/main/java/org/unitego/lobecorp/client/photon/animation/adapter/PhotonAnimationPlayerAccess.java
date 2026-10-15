package org.unitego.lobecorp.client.photon.animation.adapter;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnnecessaryModifier")
public interface PhotonAnimationPlayerAccess {
	public void lobecorp$evaluate(double time);

	public void lobecorp$evaluateAt(double time);

	public void lobecorp$frameAt(double time);

	public double lobecorp$sampleTime(double fallback);

	public boolean lobecorp$audioEnabled();

	public void lobecorp$localTime(double time);

	public double lobecorp$localTime();

	public double lobecorp$lastEvalTime();

	public void lobecorp$lastEvalTime(double time);

	public void lobecorp$duration(double duration);

	public void lobecorp$lastSignalTick(double time);

	public void lobecorp$animation(@Nullable String animation);

	public @Nullable String lobecorp$animation();
}
