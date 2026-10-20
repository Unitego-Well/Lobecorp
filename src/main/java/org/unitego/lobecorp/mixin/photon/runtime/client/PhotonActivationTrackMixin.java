package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.playback.PhotonActivationTrackAccess;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.util.photon.playback.PhotonActivationCodecUtil;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Mixin(Track.class)
public abstract class PhotonActivationTrackMixin implements PhotonActivationTrackAccess {
	@Unique
	private PhotonActivationOptions lobecorp$options = PhotonActivationOptions.NATIVE;
	@Unique
	private @Nullable CompoundTag lobecorp$activationSource;
	@Unique
	private CompoundTag lobecorp$trackSource = new CompoundTag();
	@Unique
	private final Map<Clip, CompoundTag> lobecorp$clipSources = new IdentityHashMap<>();

	@Override
	public PhotonActivationOptions lobecorp$activationOptions() {
		return lobecorp$options;
	}

	@Override
	public @Nullable CompoundTag lobecorp$activationSource() {
		return lobecorp$activationSource == null ? null : lobecorp$activationSource.copy();
	}

	@Override
	public void lobecorp$activationSource(@Nullable CompoundTag source) {
		if (!((Object) this instanceof ActivatorTrack)) {
			throw new IllegalArgumentException("只有激活轨道可以配置激活行为。");
		}

		var options = source == null ? PhotonActivationOptions.NATIVE : PhotonActivationOptions.read(source);
		lobecorp$options = options;
		lobecorp$activationSource = source == null ? null : source.copy();
	}

	@Override
	public void lobecorp$activationOptions(PhotonActivationOptions options) {
		if (!((Object) this instanceof ActivatorTrack)) {
			throw new IllegalArgumentException("只有激活轨道可以配置激活行为。");
		}

		lobecorp$options = Objects.requireNonNull(options);
		lobecorp$activationSource = options.merge(lobecorp$activationSource == null ? new CompoundTag() : lobecorp$activationSource);
	}

	@Override
	public void lobecorp$copyActivationData(CompoundTag source, List<CompoundTag> clips) {
		var target = (Track) (Object) this;
		if (!(target instanceof ActivatorTrack) || target.clips().size() != clips.size()) {
			throw new IllegalArgumentException("激活轨道复制结构不一致。");
		}

		lobecorp$activationSource(source.get(PhotonActivationOptions.TAG) instanceof CompoundTag tag ? tag : null);
		lobecorp$trackSource = source.copy();
		lobecorp$clipSources.clear();
		for (int index = 0; index < clips.size(); index++) {
			lobecorp$clipSources.put(target.clips().get(index), clips.get(index).copy());
		}
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void lobecorp$copyActivation(CallbackInfoReturnable<Track> cir) {
		if (!((Object) this instanceof ActivatorTrack)) {
			return;
		}
		var source = lobecorp$trackSource.copy();
		source.remove(PhotonActivationOptions.TAG);
		if (lobecorp$activationSource != null) {
			source.put(PhotonActivationOptions.TAG, lobecorp$activationSource.copy());
		}

		var clips = new ArrayList<CompoundTag>();
		for (var clip : ((Track) (Object) this).clips()) {
			clips.add(lobecorp$clipSources.getOrDefault(clip, new CompoundTag()));
		}

		((PhotonActivationTrackAccess) cir.getReturnValue()).lobecorp$copyActivationData(source, clips);
	}

	@Inject(method = "readData", at = @At("RETURN"))
	private void lobecorp$readActivation(HolderLookup.Provider provider, CompoundTag data, CallbackInfo ci) {
		if (!((Object) this instanceof ActivatorTrack)) {
			return;
		}

		lobecorp$trackSource = data.copy();
		lobecorp$clipSources.clear();
		var clips = ((Track) (Object) this).clips();
		int index = 0;
		for (var raw : data.getListOrEmpty("clips")) {
			if (raw instanceof CompoundTag source && index < clips.size()) {
				lobecorp$clipSources.put(clips.get(index++), source.copy());
			}
		}

		if (!data.contains(PhotonActivationOptions.TAG)) {
			lobecorp$options = PhotonActivationOptions.NATIVE;
			lobecorp$activationSource = null;
			return;
		}

		if (!(data.get(PhotonActivationOptions.TAG) instanceof CompoundTag source)) {
			throw new IllegalArgumentException("激活行为必须为NBT复合标签。");
		}

		lobecorp$options = PhotonActivationOptions.read(source);
		lobecorp$activationSource = source.copy();
	}

	@Inject(method = "writeData", at = @At("RETURN"), cancellable = true)
	private void lobecorp$writeActivation(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
		if (!((Object) this instanceof ActivatorTrack)) {
			return;
		}
		var current = cir.getReturnValue();
		var clips = ((Track) (Object) this).clips();
		var saved = current.getListOrEmpty("clips");
		for (int index = 0; index < clips.size() && index < saved.size(); index++) {
			var source = lobecorp$clipSources.get(clips.get(index));
			if (source != null && saved.get(index) instanceof CompoundTag value) {
				saved.set(index, PhotonActivationCodecUtil.clip(source, value));
			}
		}

		if (lobecorp$activationSource != null) {
			current.put(PhotonActivationOptions.TAG, lobecorp$activationSource.copy());
		}
		cir.setReturnValue(PhotonActivationCodecUtil.track(lobecorp$trackSource, current));
	}
}
