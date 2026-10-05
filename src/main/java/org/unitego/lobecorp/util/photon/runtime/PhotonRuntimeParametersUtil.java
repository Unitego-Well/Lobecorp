package org.unitego.lobecorp.util.photon.runtime;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;

/**
 * 仅在客户端线程操作独立 FXRuntime；同一参数不应同时由 Timeline 写入。
 */
public class PhotonRuntimeParametersUtil {
	/**
	 * 本地空间光柱长度，沿资源原有端点方向；保留 Photon 的方块与实体截断。
	 */
	public static boolean setBeamLength(FXRuntime runtime, String name, float length) {
		requireNonNegative(length);
		if (!(runtime.findObject(name) instanceof BeamEmitter emitter)) {
			return false;
		}
		((PhotonBeamLengthAccess) emitter.runtime()).lobecorp$setBeamLength(length);
		return true;
	}

	public static boolean clearBeamLength(FXRuntime runtime, String name) {
		if (!(runtime.findObject(name) instanceof BeamEmitter emitter)) {
			return false;
		}
		((PhotonBeamLengthAccess) emitter.runtime()).lobecorp$setBeamLength(null);
		return true;
	}

	/**
	 * 覆盖当前光柱宽度，不改写资源配置。
	 */
	public static boolean setBeamWidth(FXRuntime runtime, String name, float width) {
		requireNonNegative(width);
		if (!(runtime.findObject(name) instanceof BeamEmitter emitter)) {
			return false;
		}
		emitter.runtime().width.set(NumberFunction.constant(width));
		return true;
	}

	public static boolean clearBeamWidth(FXRuntime runtime, String name) {
		if (!(runtime.findObject(name) instanceof BeamEmitter emitter)) {
			return false;
		}
		emitter.runtime().width.clear();
		return true;
	}

	/**
	 * 起始大小只影响之后生成的粒子，已生成粒子仍保留自己的初始大小。
	 */
	public static boolean setParticleStartSize(FXRuntime runtime, String name, float x, float y, float z) {
		requireNonNegative(x);
		requireNonNegative(y);
		requireNonNegative(z);
		if (!(runtime.findObject(name) instanceof ParticleEmitter emitter)) {
			return false;
		}
		emitter.runtime().startSize.set(new NumberFunction3(x, y, z));
		return true;
	}

	public static boolean clearParticleStartSize(FXRuntime runtime, String name) {
		if (!(runtime.findObject(name) instanceof ParticleEmitter emitter)) {
			return false;
		}
		emitter.runtime().startSize.clear();
		return true;
	}

	/**
	 * 对象本地缩放会同时影响其子对象；Local 粒子跟随，World 粒子不保证跟随。
	 */
	public static boolean setObjectScale(FXRuntime runtime, String name, float x, float y, float z) {
		requireNonNegative(x);
		requireNonNegative(y);
		requireNonNegative(z);
		var object = runtime.findObject(name);
		if (object == null) {
			return false;
		}
		object.updateScale(new Vector3f(x, y, z));
		return true;
	}

	private static void requireNonNegative(float value) {
		if (!Float.isFinite(value) || value < 0) {
			throw new IllegalArgumentException();
		}
	}
}
