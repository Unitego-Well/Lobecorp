package org.unitego.lobecorp.config.photon.playback;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;

public class PhotonSpawnShape implements IPersistedSerializable {
	public enum Type implements StringRepresentable {
		POINT, BOX, SPHERE, CIRCLE;

		@Override
		public String getSerializedName() {
			return PhotonSpawnConfig.ENUM_PREFIX + name();
		}
	}

	@Persisted
	public Type type = Type.POINT;
	@Persisted
	public Vector3f size = new Vector3f(1);
	@Persisted
	public Vector3f offset = new Vector3f();
}
