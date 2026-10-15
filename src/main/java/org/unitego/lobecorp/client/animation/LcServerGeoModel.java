package org.unitego.lobecorp.client.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;

import java.util.function.Supplier;

/// 新接入的渲染器从服务器基础内容读取模型，本地标准资源包可以覆盖视觉。
public class LcServerGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
	private final Supplier<LcAnimationContent> content;
	private final Identifier texture;

	public LcServerGeoModel(Supplier<LcAnimationContent> content, Identifier texture) {
		this.content = content;
		this.texture = texture;
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return content.get().modelId();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return texture;
	}

	@Override
	public Identifier getAnimationResource(T animatable) {
		return content.get().animationId();
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public BakedGeoModel getBakedModel(Identifier location) {
		return LcClientAnimationResources.model(content.get());
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public @Nullable Animation getBakedAnimation(T animatable, String name) {
		return LcClientAnimationResources.animation(content.get(), name);
	}
}
