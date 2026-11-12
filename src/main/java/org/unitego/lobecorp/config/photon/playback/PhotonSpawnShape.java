package org.unitego.lobecorp.config.photon.playback;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/// 发射器发射器的形状、尺寸与局部偏移；字段由 Photon 持久化。
public class PhotonSpawnShape implements IPersistedSerializable {
	@Persisted
	public Type type = Type.POINT;
	@Persisted
	public Vector3f size = new Vector3f(1);
	@Persisted
	public Vector3f offset = new Vector3f();
	public enum Type implements StringRepresentable {
		POINT, BOX, SPHERE, CIRCLE;

		@Override
		public @NotNull String getSerializedName() {
			return PhotonSpawnConfig.ENUM_PREFIX + name();
		}
	}
}
