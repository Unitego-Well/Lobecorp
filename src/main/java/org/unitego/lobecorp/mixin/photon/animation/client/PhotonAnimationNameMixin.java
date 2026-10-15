package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.accessors.IConfiguratorAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationNameUtil;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(ConfiguratorParser.class)
public abstract class PhotonAnimationNameMixin {
	@WrapOperation(method = "createFieldConfigurator", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/configurator/accessors/IConfiguratorAccessor;create(Ljava/lang/String;Ljava/util/function/Supplier;Ljava/util/function/Consumer;ZLjava/lang/reflect/Field;Ljava/lang/Object;)Lcom/lowdragmc/lowdraglib2/configurator/ui/Configurator;"))
	private static <T> Configurator lobecorp$lockObjectName(IConfiguratorAccessor<T> accessor, String name,
	                                                        Supplier<T> supplier, Consumer<T> consumer, boolean forceUpdate,
	                                                        Field field, Object owner, Operation<Configurator> original) {
		if (!PhotonAnimationNameUtil.locksName(field, owner)) {
			return original.call(accessor, name, supplier, consumer, forceUpdate, field, owner);
		}

		var configurator = original.call(accessor, name, supplier, (Consumer<T>) _ -> {
		}, forceUpdate, field, owner);
		configurator.setActive(false);
		return configurator;
	}
}
