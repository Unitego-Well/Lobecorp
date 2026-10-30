package org.unitego.lobecorp.util.photon.material;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PhotonMaterialPreparationUtil {
	public static final String EAGER_BUILTIN_PROPERTY = "lobecorp.photon.bench.eagerShaderBuiltin";
	private static final AtomicLong PROVIDERS = new AtomicLong();
	private static final AtomicLong GENERATED = new AtomicLong();
	private static final AtomicLong FILE_REQUESTS_WITHOUT_DEFAULT = new AtomicLong();

	public static void providerCreated() {
		PROVIDERS.incrementAndGet();
	}

	public static void defaultGenerated() {
		GENERATED.incrementAndGet();
	}

	public static Counters counters() {
		return new Counters(PROVIDERS.get(), GENERATED.get(), FILE_REQUESTS_WITHOUT_DEFAULT.get());
	}

	public static boolean supports(ResourceInstance<?> instance) {
		return instance.resource.getClass() == ShaderGraphResource.class && !Boolean.getBoolean(EAGER_BUILTIN_PROPERTY);
	}

	public static <T> BuiltinResourceProvider<T> create(String name, ResourceInstance<T> instance) {
		return new LazyBuiltinResourceProvider<>(name, instance, instance.resource::buildBuiltin);
	}

	/// 在原查询成功返回后计数，包含 direct/global/pack 及暖缓存命中，不声称全是 pack miss。
	public static void recordFileRequest(ResourceInstance<?> instance, IResourcePath path, Object result) {
		if (!(path instanceof FilePath) || result == null || !supports(instance)) {
			return;
		}

		for (var provider : instance.getBuiltinProviders().getOrDefault(BuiltinResourceProvider.TYPE,
				List.of())) {
			if (provider instanceof LazyBuiltinResourceProvider<?> lazy && !lazy.initialized()) {
				FILE_REQUESTS_WITHOUT_DEFAULT.incrementAndGet();
				return;
			}
		}
	}

	public record Counters(long providersCreated, long defaultsGenerated, long fileRequestsWithoutDefault) {
	}
}
