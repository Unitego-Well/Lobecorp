package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/// 正常播放只依赖此类型查询编辑器预览绑定，避免链接编辑器 UI 与工具实现。
public class PhotonRuntimePreviewUtil {
	private static final Bindings<FXRuntime, Preview> PREVIEWS = new Bindings<>();

	/// 编辑器预览行为；实现由编辑器工具持有，弱表不延长其生命周期。
	public interface Preview {
		public void applyOverrides(FXRuntime runtime);

		public boolean previewVisible(FXRuntime runtime, IFXObject object);
	}

	/// 弱键弱值绑定核心；不持有 runtime、预览所有者或场景的强引用。
	public static class Bindings<K, V> {
		private final Map<K, WeakReference<V>> values = new WeakHashMap<>();

		public void put(K key, V value) {
			values.put(key, new WeakReference<>(value));
		}

		public @Nullable V get(K key) {
			var reference = values.get(key);
			return reference == null ? null : reference.get();
		}

		public boolean remove(K key, V expected) {
			var reference = values.get(key);

			if (reference == null || reference.get() != expected) {
				return false;
			}

			values.remove(key);
			return true;
		}

		public @Nullable WeakReference<V> reference(K key) {
			return values.get(key);
		}
	}

	public static void bind(FXRuntime runtime, Preview preview) {
		PREVIEWS.put(runtime, preview);
	}

	public static void unbind(FXRuntime runtime, Preview expected) {
		PREVIEWS.remove(runtime, expected);
	}

	public static void apply(FXRuntime runtime) {
		var preview = PREVIEWS.get(runtime);

		if (preview != null) {
			preview.applyOverrides(runtime);
		}
	}

	public static boolean visible(FXRuntime runtime, IFXObject object) {
		var preview = PREVIEWS.get(runtime);
		return preview == null || preview.previewVisible(runtime, object);
	}

	public static boolean visible(IFXObject object) {
		if (!(object.getScene() instanceof FXRuntime runtime)) {
			return true;
		}

		return visible(runtime, object);
	}
}
