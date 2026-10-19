package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;

import java.io.IOException;
import java.util.List;

public class LcPhotonModelOutputTypes {
	public static final String TYPE_NAME = "lobecorp_model_emitter";
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

				return ((PhotonModelOutput) object).source().merge(data);
			});
		}
	};

	private static RuntimeBinding binding(String path, String label, ConfigValueType type) {
		return new RuntimeBinding(path, "photon.lobecorp.model." + label, type,
				object -> ((PhotonModelOutput) object).slot(path));
	}
}
