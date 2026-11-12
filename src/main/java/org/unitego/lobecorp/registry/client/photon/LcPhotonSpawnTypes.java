package org.unitego.lobecorp.registry.client.photon;

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
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEmitterSpawnerTranslationKeys;
import org.unitego.lobecorp.util.photon.playback.PhotonSpawnConfigUtil;

import java.util.List;

public class LcPhotonSpawnTypes {
	public static final String TYPE_NAME = "lobecorp_emitter_spawner";
	@LDLRegisterClient(name = TYPE_NAME, registry = "photon:fx_object")
	public static final FXObjectType SPAWNER = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonChildSpawner();
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
				try {
					PhotonSpawnConfigUtil.validate(tag);
				} catch (RuntimeException invalid) {
					return DataResult.error(invalid::getMessage);
				}

				return base.parse(NbtOps.INSTANCE, tag).map(object -> {
					((PhotonChildSpawner) object).rememberSource(tag);
					return object;
				});
			}, object -> {
				var encoded = base.encodeStart(NbtOps.INSTANCE, object).getOrThrow();

				if (!(encoded instanceof CompoundTag data)) {
					throw new IllegalStateException("子发射对象未编码为复合标签。");
				}

				var spawner = (PhotonChildSpawner) object;
				var preserved = spawner.source();

				if (spawner.config.fxLocation == null) {
					preserved.getCompoundOrEmpty("config").remove("fxLocation");
				}

				return preserved.merge(data);
			});
		}
	};
	private static final List<RuntimeBinding> BINDINGS = List.of(
			binding("enabled", ConfigValueType.BOOL),
			binding("duration", ConfigValueType.INT),
			binding("looping", ConfigValueType.BOOL),
			binding("startDelay", ConfigValueType.INT),
			binding("interval", ConfigValueType.INT),
			binding("count", ConfigValueType.NUMBER_FUNCTION),
			binding("rate", ConfigValueType.NUMBER_FUNCTION),
			binding("probability", ConfigValueType.NUMBER_FUNCTION),
			binding("maxInstances", ConfigValueType.INT),
			binding("launchSpeed", ConfigValueType.NUMBER_FUNCTION),
			binding("parameters.lifetime", ConfigValueType.NUMBER_FUNCTION),
			binding("parameters.size", ConfigValueType.NUMBER_FUNCTION3),
			binding("parameters.color", ConfigValueType.COLOR),
			binding("parameters.speed", ConfigValueType.NUMBER_FUNCTION),
			binding("parameters.beamLength", ConfigValueType.NUMBER_FUNCTION),
			binding("parameters.beamWidth", ConfigValueType.NUMBER_FUNCTION),
			binding("parameters.playbackSpeed", ConfigValueType.FLOAT));

	private static RuntimeBinding binding(String path, ConfigValueType type) {
		return new RuntimeBinding(path, PhotonEmitterSpawnerTranslationKeys.key(path), type,
				object -> ((PhotonChildSpawner) object).slot(path));
	}
}
