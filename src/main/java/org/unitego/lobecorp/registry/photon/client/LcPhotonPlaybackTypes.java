package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackType;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrackEditor;

import java.io.IOException;

public class LcPhotonPlaybackTypes {
	@LDLRegisterClient(name = "lobecorp_child_animation", registry = "photon:timeline_track")
	public static final TrackType CHILD = new TrackType(PhotonChildTrack.class, PhotonChildTrack::new, new PhotonChildTrackEditor());
	@LDLRegisterClient(name = "lobecorp_animation_call", registry = "photon:timeline_track")
	public static final TrackType CALL = new TrackType(PhotonCallTrack.class, PhotonCallTrack::new, new PhotonChildTrackEditor());
	@LDLRegisterClient(name = "lobecorp_animation_controller", registry = "photon:fx_object")
	public static final FXObjectType CONTROLLER = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonNamedController();
		}

		@Override
		public IGuiTexture icon() {
			return IGuiTexture.EMPTY;
		}

		@Override
		public Codec<IFXObject> codec() {
			var base = super.codec();
			return CompoundTag.CODEC.comapFlatMap(tag -> {
				try {
					if (tag.getIntOr("version", 0) != 0 || !(tag.get("definition") instanceof CompoundTag definition)) {
						return DataResult.error(() -> "命名控制器对象版本或定义类型无效。");
					}

					PhotonNamedCodecUtil.read(definition);
				} catch (IOException invalid) {
					return DataResult.error(invalid::getMessage);
				}

				return base.parse(NbtOps.INSTANCE, tag).map(object -> {
					((PhotonNamedController) object).rememberSource(tag);
					return object;
				});
			}, object -> {
				var encoded = base.encodeStart(NbtOps.INSTANCE, object).getOrThrow();

				if (!(encoded instanceof CompoundTag data)) {
					throw new IllegalStateException("命名控制器未编码为复合标签。");
				}

				var controller = (PhotonNamedController) object;
				var preserved = controller.source();
				preserved.remove("definition");
				return preserved.merge(data);
			});
		}
	};
}
