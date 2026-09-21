package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;

public interface LcAnimatable extends GeoAnimatable {
	void registerLcAnimationLayers(LcAnimationLayerRegistrar registrar);
}
