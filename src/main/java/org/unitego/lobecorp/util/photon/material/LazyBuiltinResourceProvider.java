package org.unitego.lobecorp.util.photon.material;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/// 延迟的只是默认值；完整 Map 一旦对外暴露，就继续使用父类原始可变 Map。
public class LazyBuiltinResourceProvider<T> extends BuiltinResourceProvider<T> {
	private final LazyBuiltinContents<IResourcePath, T> lazyContents;

	public LazyBuiltinResourceProvider(String name, ResourceInstance<T> instance,
	                                   Consumer<BuiltinResourceProvider<T>> initializer) {
		super(name, instance);
		lazyContents = new LazyBuiltinContents<>(contents, () -> {
			// 临时 provider 隔离失败的部分写入；原方法及其方法级注入仍正常执行。
			var staging = new BuiltinResourceProvider<>(name, instance);
			initializer.accept(staging);
			return staging.getContents();
		});
		PhotonMaterialPreparationUtil.providerCreated();
	}

	public boolean initialized() {
		return lazyContents.initialized();
	}

	private void ensureInitialized() {
		if (!lazyContents.initialized() && lazyContents.ensureInitialized()) {
			PhotonMaterialPreparationUtil.defaultGenerated();
		}
	}

	@Override
	public boolean hasResource(IResourcePath path) {
		if (!supportResourcePath(path)) {
			return false;
		}

		ensureInitialized();
		return super.hasResource(path);
	}

	@Override
	public T getResource(IResourcePath path) {
		if (!supportResourcePath(path)) {
			return null;
		}

		ensureInitialized();
		return super.getResource(path);
	}

	@Override
	public T getLoadedResource(IResourcePath path) {
		return super.getLoadedResource(path);
	}

	@Override
	public Map<IResourcePath, T> getContents() {
		ensureInitialized();
		return super.getContents();
	}

	@Override
	public @NotNull Iterator<Map.Entry<IResourcePath, T>> iterator() {
		ensureInitialized();
		return super.iterator();
	}

	@Override
	public boolean addResource(IResourcePath path, T resource) {
		if (resource == null || !supportResourcePath(path)) {
			return false;
		}

		ensureInitialized();
		return super.addResource(path, resource);
	}

	@Override
	public T removeResource(IResourcePath path) {
		if (!supportResourcePath(path)) {
			return null;
		}

		ensureInitialized();
		return super.removeResource(path);
	}
}
