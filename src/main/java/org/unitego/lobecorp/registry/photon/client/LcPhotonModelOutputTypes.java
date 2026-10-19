package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackType;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.Nullable;

public class LcPhotonModelOutputTypes {
	public static final String TYPE_NAME = "lobecorp_model_emitter";
	public static final String TRACK_NAME = "lobecorp_model_animation";
	@LDLRegisterClient(name = TRACK_NAME, registry = "photon:timeline_track")
	public static final TrackType MODEL_ANIMATION = new TrackType(PhotonModelClipTrack.class,
			PhotonModelClipTrack::new, new PhotonModelClipTrackEditor());
	private static final List<RuntimeBinding> BINDINGS = List.of(
			binding("enabled", "enabled", ConfigValueType.BOOL),
			binding("duration", "duration", ConfigValueType.INT),
			binding("startDelay", "start_delay", ConfigValueType.INT),
			binding("looping", "looping", ConfigValueType.BOOL),
			binding("paused", "pause", ConfigValueType.BOOL),
			binding("playbackSpeed", "playback_speed", ConfigValueType.FLOAT),
			binding("animationOffset", "animation_offset", ConfigValueType.FLOAT),
			binding("length", "length", ConfigValueType.FLOAT),
			binding("width", "width", ConfigValueType.FLOAT),
			binding("opacity", "opacity", ConfigValueType.FLOAT),
			binding("color", "color", ConfigValueType.COLOR),
			new RuntimeBinding("renderer.orderInLayer", "photon.emitter.config.renderer.orderInLayer", ConfigValueType.INT,
					object -> ((PhotonModelOutput) object).rendererRuntime().orderInLayer));

	@LDLRegisterClient(name = TYPE_NAME, registry = "photon:fx_object")
	public static final FXObjectType MODEL = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonModelOutput();
		}

		@Override
		public IGuiTexture icon() {
			return ParticleEmitter.ICON;
		}

		@Override
		public List<RuntimeBinding> runtimeBindings() {
			return BINDINGS;
		}

		@Override
		public List<AnimatedPropertyType> animatableProperties(FXObject target) {
			var result = new ArrayList<>(super.animatableProperties(target));

			if (target instanceof PhotonModelOutput model) {
				result.addAll(model.dynamicProperties());
			}

			return List.copyOf(result);
		}

		@Override
		public @Nullable RuntimeBinding resolveRuntimeBinding(FXObject target, String path) {
			var binding = super.resolveRuntimeBinding(target, path);
			return binding != null ? binding : target instanceof PhotonModelOutput model ? model.dynamicBinding(path) : null;
		}

		@Override
		public Codec<IFXObject> codec() {
			var base = super.codec();
			return CompoundTag.CODEC.comapFlatMap(tag -> {
				CompoundTag adapted;

				try {
					adapted = PhotonModelAssetUtil.adaptObject(tag);
				} catch (IOException failure) {
					return DataResult.error(failure::getMessage);
				}

				return base.parse(NbtOps.INSTANCE, adapted).map(object -> {
					((PhotonModelOutput) object).rememberSource(tag);
					return object;
				});
			}, object -> {
				var encoded = base.encodeStart(NbtOps.INSTANCE, object).getOrThrow();

				if (!(encoded instanceof CompoundTag data)) {
					throw new IllegalStateException("模型对象未编码为NBT复合标签。");
				}

				var model = (PhotonModelOutput) object;
				var preserved = model.source();

				if (model.config.resource == null) {
					preserved.getCompoundOrEmpty("config").remove("resource");
				}

				return preserved.merge(data);
			});
		}
	};

	private static RuntimeBinding binding(String path, String label, ConfigValueType type) {
		return new RuntimeBinding(path, "photon.lobecorp.model." + label, type,
				object -> ((PhotonModelOutput) object).slot(path));
	}
}
