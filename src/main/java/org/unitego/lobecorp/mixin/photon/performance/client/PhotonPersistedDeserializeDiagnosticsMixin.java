package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.field.ManagedKey;
import com.lowdragmc.lowdraglib2.syncdata.ref.IRef;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = PersistedParser.class)
public abstract class PhotonPersistedDeserializeDiagnosticsMixin {
	@WrapMethod(method = "deserializeInternal")
	private static <T> void lobecorp$class(boolean root, MapLike<T> map, DynamicOps<T> op,
	                                       Map<String, Method> setters, Class<?> clazz, Object object, HolderLookup.Provider provider, Operation<Void> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(root, map, op, setters, clazz, object, provider);
			return;
		}

		var scope = PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.CLASS_DESERIALIZE,
				clazz == null ? "null" : clazz.getName(), "", root);
		PhotonFXLoadDiagnosticsUtil.call(scope, original, root, map, op, setters, clazz, object, provider);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;"))
	private static Method[] lobecorp$methods(Class<?> clazz, Operation<Method[]> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(clazz);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.DECLARED_METHODS, clazz.getName(), ""), original, clazz);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;"))
	private static Field[] lobecorp$fields(Class<?> clazz, Operation<Field[]> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(clazz);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.DECLARED_FIELDS, clazz.getName(), ""), original, clazz);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/ManagedFieldUtils;createKey(Ljava/lang/reflect/Field;)Lcom/lowdragmc/lowdraglib2/syncdata/field/ManagedKey;"))
	private static ManagedKey lobecorp$key(Field field, Operation<ManagedKey> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(field);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.FIELD_KEY, field.getDeclaringClass().getName(), field.getName()), original, field);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/field/ManagedKey;createRef(Ljava/lang/Object;)Lcom/lowdragmc/lowdraglib2/syncdata/ref/IRef;"))
	private static IRef<?> lobecorp$ref(ManagedKey key, Object instance, Operation<IRef<?>> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(key, instance);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.FIELD_REF, field.getDeclaringClass().getName(), field.getName()), original, key, instance);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/ref/IRef;writePersisted(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"))
	private static void lobecorp$write(IRef<?> ref, DynamicOps<?> op, Object payload, Operation<Void> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(ref, op, payload);
			return;
		}

		PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.FIELD_WRITE, field.getDeclaringClass().getName(), field.getName()), original, ref, op, payload);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Ljava/lang/reflect/Field;get(Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object lobecorp$read(Field field, Object obj, Operation<Object> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(field, obj);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.FIELD_READ, field.getDeclaringClass().getName(), field.getName()), original, field, obj);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Ljava/lang/reflect/Method;invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object lobecorp$setter(Method setter, Object obj, Object[] args, Operation<Object> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(setter, obj, args);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.SETTER, field.getDeclaringClass().getName(), field.getName()), original, setter, obj, args);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/IPersistedSerializable;beforeDeserialize()V"))
	private static void lobecorp$before(IPersistedSerializable object, Operation<Void> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(object);
			return;
		}

		PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.BEFORE_DESERIALIZE, "", ""), original, object);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/IPersistedSerializable;deserializeAdditionalNBT(Lnet/minecraft/nbt/Tag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
	private static void lobecorp$additional(IPersistedSerializable object, Tag tag, HolderLookup.Provider provider, Operation<Void> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(object, tag, provider);
			return;
		}

		PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.ADDITIONAL_NBT, "", ""), original, object, tag, provider);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/syncdata/IPersistedSerializable;afterDeserialize()V"))
	private static void lobecorp$after(IPersistedSerializable object, Operation<Void> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(object);
			return;
		}

		PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.AFTER_DESERIALIZE, "", ""), original, object);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lnet/neoforged/neoforge/common/util/ValueIOSerializable;deserialize(Lnet/minecraft/world/level/storage/ValueInput;)V"))
	private static void lobecorp$value(ValueIOSerializable object, ValueInput input, Operation<Void> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(object, input);
			return;
		}

		PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.VALUE_DESERIALIZE, field.getDeclaringClass().getName(), field.getName()), original, object, input);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE",
			target = "Lcom/mojang/serialization/DataResult;ifSuccess(Ljava/util/function/Consumer;)Lcom/mojang/serialization/DataResult;"))
	private static <T> DataResult<T> lobecorp$subMap(DataResult<T> result, Consumer<? super T> consumer, Operation<DataResult<T>> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(result, consumer);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.SUB_CONFIG, field.getDeclaringClass().getName(), field.getName()), original, result, consumer);
	}

	@WrapOperation(method = "deserializeInternal", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lcom/lowdragmc/lowdraglib2/utils/PersistedParser;deserializeInternal(ZLcom/mojang/serialization/MapLike;Lcom/mojang/serialization/DynamicOps;Ljava/util/Map;Ljava/lang/Class;Ljava/lang/Object;Lnet/minecraft/core/HolderLookup$Provider;)V"))
	private static <T> void lobecorp$subFlat(boolean root, MapLike<T> map, DynamicOps<T> op,
	                                         Map<String, Method> setters, Class<?> clazz, Object object, HolderLookup.Provider provider,
	                                         Operation<Void> original, @Local(name = "field") Field field) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			original.call(root, map, op, setters, clazz, object, provider);
			return;
		}

		var scope = PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.SUB_CONFIG, field.getDeclaringClass().getName(), field.getName());
		PhotonFXLoadDiagnosticsUtil.call(scope, original, root, map, op, setters, clazz, object, provider);
	}
}
