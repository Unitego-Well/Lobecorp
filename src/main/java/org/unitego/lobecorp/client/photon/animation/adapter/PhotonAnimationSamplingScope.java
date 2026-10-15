package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.jetbrains.annotations.Nullable;

/// 在命名动画的原生求值调用内收集属性；作用域外保持 Photon 原调用。
public class PhotonAnimationSamplingScope implements AutoCloseable {
	@SuppressWarnings("UnnecessaryModifier") // 项目规范要求显式可见性。
	public interface Handler {
		public void beginPass();

		public void sample(AnimatedProperty property, FXObject target, double time, Runnable original);

		public void commit();
	}

	/// 当前线程正在执行的动画采样作用域。
	private static final ThreadLocal<PhotonAnimationSamplingScope> CURRENT = new ThreadLocal<>();
	private final @Nullable PhotonAnimationSamplingScope previous;
	private final Handler handler;

	public PhotonAnimationSamplingScope(Handler handler) {
		this.handler = handler;
		previous = CURRENT.get();
		CURRENT.set(this);
	}

	public static void apply(AnimatedProperty property, FXObject target, double time, Runnable original) {
		var scope = CURRENT.get();

		if (scope == null) {
			original.run();
			return;
		}

		scope.handler.sample(property, target, time, original);
	}

	public static void beginPass() {
		var scope = CURRENT.get();

		if (scope != null) {
			scope.handler.beginPass();
		}
	}

	public static void commit() {
		var scope = CURRENT.get();

		if (scope != null) {
			scope.handler.commit();
		}
	}

	@Override
	public void close() {
		if (previous == null) {
			CURRENT.remove();
			return;
		}

		CURRENT.set(previous);
	}
}
