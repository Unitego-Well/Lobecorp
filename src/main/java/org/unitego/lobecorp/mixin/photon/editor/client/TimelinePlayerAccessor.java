package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Track;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(TimelinePlayer.class)
public interface TimelinePlayerAccessor {
	@Accessor("effect")
	@Nullable
	IEffectExecutor lobecorp$effect();

	@Accessor("effect")
	void lobecorp$effect(@Nullable IEffectExecutor effect);

	@Accessor("lastEvalTime")
	double lobecorp$lastEvaluationTime();

	@Invoker("applyAudio")
	void lobecorp$audio(List<Track> tracks, double time);

	@Invoker("applyPostProcess")
	void lobecorp$postProcess(double time);
}
