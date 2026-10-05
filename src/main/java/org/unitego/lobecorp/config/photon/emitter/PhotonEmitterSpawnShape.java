package org.unitego.lobecorp.config.photon.emitter;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import org.jetbrains.annotations.NotNull;
import net.minecraft.util.StringRepresentable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import org.joml.Vector3f;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;

/**
 * 实例发射的本地空间分布；不创建载体粒子。
 */
public class PhotonEmitterSpawnShape implements IConfigurable, IPersistedSerializable {
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SHAPE_TYPE)
	public Type type = Type.POINT;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SHAPE_SIZE)
	@ConfigNumber(range = {0, Float.MAX_VALUE})
	public Vector3f size = new Vector3f(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SHAPE_OFFSET)
	public Vector3f offset = new Vector3f();

	public enum Type implements StringRepresentable {
		POINT, BOX, SPHERE, CIRCLE;

		@Override
		public @NotNull String getSerializedName() {
			return PhotonEmitterSpawnerTextUtil.enumKey(this);
		}
	}
}
