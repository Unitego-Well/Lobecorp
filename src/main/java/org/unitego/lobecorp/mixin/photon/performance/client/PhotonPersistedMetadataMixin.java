package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonPersistedMetadataUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = PersistedParser.class)
public abstract class PhotonPersistedMetadataMixin {
	// 先于默认 order=1000 的诊断包装应用，使诊断仍观察每次访问（包含缓存命中）。
	@WrapOperation(method = {"serializeInternal", "deserializeInternal"}, order = 900, at = @At(value = "INVOKE",
			target = "Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;"))
	private static Method[] lobecorp$cachedDeclaredMethods(Class<?> clazz, Operation<Method[]> original) {
		return PhotonPersistedMetadataUtil.declaredMethods(clazz, original);
	}

	@WrapOperation(method = {"serializeInternal", "deserializeInternal"}, order = 900, at = @At(value = "INVOKE",
			target = "Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;"))
	private static Field[] lobecorp$cachedDeclaredFields(Class<?> clazz, Operation<Field[]> original) {
		return PhotonPersistedMetadataUtil.declaredFields(clazz, original);
	}
}
