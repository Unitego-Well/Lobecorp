package org.unitego.lobecorp.util.photon.creation;

import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import org.unitego.lobecorp.mixin.photon.creation.client.FXObjectLeaseAccessor;
import org.unitego.lobecorp.mixin.photon.creation.client.ParticleEmitterLeaseAccessor;

/// 仅补足原生 reset 未恢复的私有字段；配置、TRS 与 named 状态仍由主线完整恢复。
public class PhotonLeasePrivateResetUtil {
	public static void resetTiming(FXObject object) {
		if (!(object instanceof FXObjectLeaseAccessor accessor)) {
			throw new IllegalStateException("FXObjectLeaseAccessor is not registered");
		}

		accessor.lobecorp$setLeaseLastTick(0);
		accessor.lobecorp$setLeaseLastTickTime(0);
		accessor.lobecorp$setLeaseDeltaTime(0);
	}

	/// 应在配置恢复后调用，让下一次 runtime() 按配置重新创建运行覆盖层。
	public static void resetParticleRuntime(ParticleEmitter emitter) {
		if (!(emitter instanceof ParticleEmitterLeaseAccessor accessor)) {
			throw new IllegalStateException("ParticleEmitterLeaseAccessor is not registered");
		}

		accessor.lobecorp$setLeaseRuntime(null);
	}
}
