package org.unitego.lobecorp.util.photon.creation;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.ManagedFieldUtils;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.EnumAccessor;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.IDirectAccessor;
import com.lowdragmc.lowdraglib2.syncdata.accessor.IAccessor;
import com.lowdragmc.lowdraglib2.syncdata.var.ManagedHolderVar;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ExtraCodecs;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.PrimitiveAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.field.ManagedKey;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/// 只编译持久化配置初始化指令；不保存原型，也不复制运行时私有对象图。
public class PhotonStatePlanUtil {
	private static final Set<Class<?>> IMMUTABLE_TYPES = Set.of(
			String.class, Boolean.class, Byte.class, Short.class, Integer.class,
			Long.class, Float.class, Double.class, Character.class, UUID.class, Identifier.class);
	private static final Set<Codec<?>> DIRECT_CODECS = Set.of(
			Codec.INT, Codec.LONG, Codec.FLOAT, Codec.DOUBLE, Codec.BOOL, Codec.BYTE, Codec.SHORT,
			Codec.STRING, LDLibExtraCodecs.CHAR, LDLibExtraCodecs.UUID, Identifier.CODEC,
			ExtraCodecs.NBT, CompoundTag.CODEC, ExtraCodecs.VECTOR3F, ExtraCodecs.VECTOR4F,
			ExtraCodecs.QUATERNIONF, LDLibExtraCodecs.VECTOR2F, LDLibExtraCodecs.VECTOR2I,
			LDLibExtraCodecs.VECTOR3I);
	private static final Set<String> JOML_TYPES = Set.of(
			"org.joml.Vector2f", "org.joml.Vector3f", "org.joml.Vector4f",
			"org.joml.Vector2d", "org.joml.Vector3d", "org.joml.Vector4d",
			"org.joml.Vector2i", "org.joml.Vector3i", "org.joml.Vector4i",
			"org.joml.Quaternionf", "org.joml.Quaterniond",
			"org.joml.Matrix2f", "org.joml.Matrix3f", "org.joml.Matrix4f",
			"org.joml.Matrix2d", "org.joml.Matrix3d", "org.joml.Matrix4d",
			"org.joml.Matrix3x2f", "org.joml.Matrix4x3f",
			"org.joml.Matrix3x2d", "org.joml.Matrix4x3d");

	private static final Map<Class<?>, MethodHandle> JOML_COPIERS = compileJomlCopiers();

	/// constructor 必须只创建独立对象，不得捕获原型、场景或执行器。
	/// excludedFields 按根对象 Java 字段名排除，不按序列化别名匹配。
	public static IStatePlan compile(Object canonicalPrototype, CompoundTag canonicalTag,
	                                 Supplier<?> constructor, Set<String> excludedFields) {
		return compilePlan(canonicalPrototype, canonicalTag, constructor, excludedFields, true);
	}

	/// 根层只处理原型根类型的注解字段与标准回调，由调用方承接根层自定义序列化数据。
	/// transfer 允许该根类型的实例向子类投影，只覆盖已编译字段，不覆盖子类新增字段或自定义序列化。
	public static IStatePlan compileFields(Object canonicalPrototype, CompoundTag canonicalTag,
	                                       Supplier<?> constructor, Set<String> excludedFields) {
		return compilePlan(canonicalPrototype, canonicalTag, constructor, excludedFields, false);
	}

	private static IStatePlan compilePlan(Object canonicalPrototype, CompoundTag canonicalTag,
	                                      Supplier<?> constructor, Set<String> excludedFields, boolean customRoot) {
		Objects.requireNonNull(canonicalPrototype);
		Objects.requireNonNull(canonicalTag);
		Objects.requireNonNull(constructor);
		var excluded = Set.copyOf(excludedFields);
		var type = canonicalPrototype.getClass();
		var schema = compileSchema(type, canonicalPrototype, canonicalTag.copy(), excluded,
				new IdentityHashMap<>(), type.getName(), customRoot, new TransferDiagnostics());
		return new StatePlan(type, constructor, schema, !customRoot);
	}

	public static IStatePlan compile(Object canonicalPrototype, CompoundTag canonicalTag, Supplier<?> constructor) {
		return compile(canonicalPrototype, canonicalTag, constructor, Set.of());
	}

	public interface IStatePlan {
		Object create();

		void apply(Object target);

		/// 将调用时的注解字段复制到独占目标；不读取冻结配置。失败字段会报告路径并抛出异常。
		void transfer(Object source, Object target);

		int fallbackFields();

		List<String> fallbackReasons();

		int assignmentCount();
	}

	private static Schema compileSchema(Class<?> type, Object prototype, CompoundTag tag, Set<String> excluded,
	                                    IdentityHashMap<Object, Boolean> visiting, String path, boolean customRoot, TransferDiagnostics diagnostics) {
		var fields = new ArrayList<Field>();
		var setters = new HashMap<String, Method>();
		collect(type, fields, setters);
		var selected = fields.stream().filter(field -> !excluded.contains(field.getName())).toList();
		var filtered = tag.copy();
		for (var field : fields) {
			if (excluded.contains(field.getName())) {
				removeFieldTag(filtered, field);
			}
		}

		if (customRoot && (customDeserialize(type) || selected.isEmpty() && fields.isEmpty())
				|| visiting.containsKey(prototype)) {
			return nativeSchema(type, filtered, selected, path, "custom/empty/recursive serialization", diagnostics);
		}

		visiting.put(prototype, Boolean.TRUE);
		try {
			var steps = new ArrayList<Step>();
			var transfers = new ArrayList<TransferStep>();
			var reasons = new ArrayList<String>();
			var assignments = 0;
			for (var field : selected) {
				var setter = setters.get(field.getName());
				var access = new FieldAccess(getter(field), setter == null ? null : methodHandle(setter),
						isSubgroup(field) ? null : ManagedFieldUtils.createKey(field));
				var fieldPath = path + "." + field.getName();
				var result = compileField(field, access, prototype, tag, visiting, fieldPath, diagnostics);
				transfers.add(compileTransfer(field, access, result.subgroup(), prototype, visiting, fieldPath, diagnostics));
				steps.add(result.step());
				reasons.addAll(result.reasons());
				assignments += result.assignments();
			}

			var additional = tag.get("_additional");
			var privateAdditional = additional == null ? null : additional.copy();
			if (privateAdditional != null) {
				reasons.add(path + "._additional: native additional-data callback");
			}
			return new Schema(List.copyOf(steps), List.copyOf(transfers), List.copyOf(reasons), assignments,
					true, privateAdditional, diagnostics, path, hasAdditionalSerializer(type), type);
		} finally {
			visiting.remove(prototype);
		}
	}

	private static void collect(Class<?> type, List<Field> fields, Map<String, Method> setters) {
		if (type == null || type == Object.class) {
			return;
		}

		for (var method : type.getDeclaredMethods()) {
			var annotation = method.getAnnotation(ConfigSetter.class);
			if (annotation != null) {
				setters.putIfAbsent(annotation.field(), method);
			}
		}

		collect(type.getSuperclass(), fields, setters);
		for (var field : type.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && PersistedParser.persistedKey(field) != null) {
				fields.add(field);
			}
		}
	}

	private static FieldPlan compileField(Field field, FieldAccess access, Object prototype, CompoundTag tag,
	                                      IdentityHashMap<Object, Boolean> visiting, String path, TransferDiagnostics diagnostics) {
		var key = Objects.requireNonNull(PersistedParser.persistedKey(field));
		var data = tag.get(key);
		var getter = access.getter();
		var setterHandle = access.setter();
		if (isSubgroup(field)) {
			var subTag = data instanceof CompoundTag compound ? compound
					: data == null && PersistedParser.isPersistedFlat(field) ? tag : null;
			if (subTag == null) {
				return new FieldPlan(_ -> {
				}, List.of(path + ": missing/non-map subgroup; native no-op"), 0);
			}

			var child = invoke(getter, prototype);
			if (child == null) {
				var privateTag = subTag.copy();
				var declaredType = field.getType();
				var parser = nativeParser();
				return new FieldPlan(target -> {
					var value = invoke(getter, target);
					if (value != null) {
						nativeApply(parser, declaredType, value, privateTag.copy());
					}
				}, List.of(path + ": native subgroup; prototype has no configuration instance"), 0);
			}

			// 原生子组按声明类型遍历，ValueIOSerializable 则动态分派。
			var schema = child instanceof ValueIOSerializable && customDeserialize(child.getClass())
					? nativeSchema(child.getClass(), subTag, List.of(), path, "custom subgroup deserialize", diagnostics)
					: compileSchema(child instanceof ValueIOSerializable ? child.getClass() : field.getType(),
					child, subTag, Set.of(), visiting, path, true, diagnostics);
			return new FieldPlan(target -> {
				var value = invoke(getter, target);
				if (value != null) {
					schema.apply(value);
				}
			}, schema.reasons(), schema.assignments(), schema);
		}

		if (data == null) {
			return new FieldPlan(_ -> {
			}, List.of(path + ": missing tag; native no-op"), 0);
		}

		var managed = access.key();
		var accessor = managed.getFieldAccessor();
		var accessorClass = accessor.getClass();
		var direct = standardDirect(accessor);
		var factory = direct ? valueFactory(decodeDirect((IDirectAccessor<?>) accessor, managed, data.copy())) : null;
		if (!Modifier.isFinal(field.getModifiers()) && !managed.isReadOnlyManaged()
				&& direct && factory != null) {
			var writer = writer(field);
			return new FieldPlan(target -> {
				invoke(writer, target, factory.get());
				callSetter(setterHandle, getter, target);
			}, List.of(), 1);
		}

		var privateTag = data.copy();
		var reason = path + ": native field accessor " + accessorClass.getName()
				+ (Modifier.isFinal(field.getModifiers()) ? " (final leaf)" : "")
				+ (factory == null ? " (unsupported value/copy semantics)" : "");
		return new FieldPlan(target -> {
			managed.createRef(target).writePersisted(NbtOps.INSTANCE, privateTag.copy());
			callSetter(setterHandle, getter, target);
		}, List.of(reason), 0);
	}

	private static TransferStep compileTransfer(Field field, FieldAccess access, Schema subgroup,
	                                            Object prototype, IdentityHashMap<Object, Boolean> visiting,
	                                            String path, TransferDiagnostics diagnostics) {
		if (isSubgroup(field)) {
			var child = invoke(access.getter(), prototype);
			var schema = subgroup;
			if (schema == null && child != null) {
				var type = child instanceof ValueIOSerializable ? child.getClass() : field.getType();
				schema = compileSchema(type, child, new CompoundTag(), Set.of(), visiting, path, true, diagnostics);
			}
			return compileSubgroupTransfer(field.getType(), access.getter(), schema, path, diagnostics);
		}

		var key = access.key();
		IAccessor<?> accessor;
		try {
			accessor = key.getFieldAccessor();
		} catch (IllegalArgumentException exception) {
			var reason = "transfer has no native field accessor: " + exception.getMessage();
			diagnostics.record(path, reason);
			return (_, _) -> {
				throw new IllegalStateException(path + ": " + reason);
			};
		}
		var direct = !Modifier.isFinal(field.getModifiers()) && !key.isReadOnlyManaged() && standardDirect(accessor);
		var writer = direct ? writer(field) : null;
		return (source, target) -> {
			var value = invoke(access.getter(), source);
			if (direct && knownLiveValue(value)) {
				invoke(writer, target, copyLiveValue(value));
				callSetter(access.setter(), access.getter(), target);
				return;
			}

			diagnostics.record(path, "transfer native field readPersisted/writePersisted (" + accessor.getClass().getName() + ")");
			try {
				var payload = Objects.requireNonNull(key.createRef(source).readPersisted(NbtOps.INSTANCE),
						"native field serialization returned no value");
				key.createRef(target).writePersisted(NbtOps.INSTANCE, payload.copy());
				callSetter(access.setter(), access.getter(), target);
			} catch (RuntimeException exception) {
				throw new IllegalStateException(path + ": native live field transfer failed", exception);
			}
		};
	}

	private static TransferStep compileSubgroupTransfer(Class<?> declaredType, MethodHandle getter, Schema schema,
	                                                    String path, TransferDiagnostics diagnostics) {
		var parser = nativeParser();
		var serializer = nativeSerializer();
		var directTransfer = schema != null && schema.callbacks() && !customSerialize(schema.type());
		return (source, target) -> {
			var from = invoke(getter, source);
			var to = invoke(getter, target);
			if (from == null && to == null) {
				return;
			}
			if (from == null || to == null || from == to) {
				diagnostics.record(path, "transfer cannot replace missing or shared subgroup instances");
				throw new IllegalStateException(path + ": subgroup transfer requires distinct non-null constructor objects");
			}

			if (from instanceof ValueIOSerializable && from.getClass() != to.getClass()) {
				diagnostics.record(path, "transfer cannot replace polymorphic subgroup constructor type");
				throw new IllegalStateException(path + ": incompatible live subgroup types");
			}
			if (directTransfer && schema.type().isInstance(from) && schema.type().isInstance(to)
					&& (!(from instanceof ValueIOSerializable) || from.getClass() == schema.type())) {
				schema.transfer(from, to);
			} else {
				diagnostics.record(path, "transfer native subgroup serialization; no root wrapper encoding");
				var payload = nativeSubgroupTag(serializer, declaredType, from);
				nativeApply(parser, declaredType, to, payload);
			}
		};
	}

	private static MethodHandle nativeSerializer() {
		try {
			var method = PersistedParser.class.getDeclaredMethod("serializeInternal", boolean.class,
					RecordBuilder.class, DynamicOps.class, Map.class, Class.class, Object.class, HolderLookup.Provider.class);
			method.setAccessible(true);
			return MethodHandles.lookup().unreflect(method);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unsupported native persisted serializer", exception);
		}
	}

	private static CompoundTag nativeSubgroupTag(MethodHandle serializer, Class<?> declaredType, Object source) {
		var provider = Platform.getFrozenRegistry();
		if (source instanceof ValueIOSerializable serializable) {
			try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
				var output = TagValueOutput.createWithContext(reporter, provider);
				serializable.serialize(output);
				return output.buildResult().copy();
			}
		}

		var builder = NbtOps.INSTANCE.mapBuilder();
		try {
			serializer.invoke(true, builder, NbtOps.INSTANCE, new HashMap<String, Method>(),
					declaredType, source, provider);
			return ((CompoundTag) builder.build(NbtOps.INSTANCE.empty()).getOrThrow()).copy();
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException("Native subgroup serialization failed", exception);
		}
	}

	private static Map<Class<?>, MethodHandle> compileJomlCopiers() {
		var result = new HashMap<Class<?>, MethodHandle>();
		for (var name : JOML_TYPES) {
			try {
				var type = Class.forName(name, false, PhotonStatePlanUtil.class.getClassLoader());
				for (var constructor : type.getConstructors()) {
					var parameters = constructor.getParameterTypes();
					if (parameters.length == 1 && parameters[0].isInterface() && parameters[0].isAssignableFrom(type)) {
						result.put(type, MethodHandles.lookup().unreflectConstructor(constructor));
						break;
					}
				}
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Cannot prepare JOML copier: " + name, exception);
			}
		}
		return Map.copyOf(result);
	}

	private static boolean knownLiveValue(Object value) {
		return value == null || IMMUTABLE_TYPES.contains(value.getClass()) || value instanceof Enum<?>
				|| value instanceof Tag || JOML_COPIERS.containsKey(value.getClass());
	}

	private static Object copyLiveValue(Object value) {
		if (value == null || IMMUTABLE_TYPES.contains(value.getClass()) || value instanceof Enum<?>) {
			return value;
		}
		if (value instanceof Tag tag) {
			return tag.copy();
		}
		return invoke(Objects.requireNonNull(JOML_COPIERS.get(value.getClass())), value);
	}

	private static boolean standardDirect(IAccessor<?> accessor) {
		if (accessor.getClass() == EnumAccessor.class) {
			return true;
		}
		if (accessor.getClass() == PrimitiveAccessor.class && accessor instanceof PrimitiveAccessor<?> primitive) {
			return DIRECT_CODECS.contains(primitive.getCodec());
		}
		return accessor.getClass() == CustomDirectAccessor.class
				&& accessor instanceof CustomDirectAccessor<?> custom && DIRECT_CODECS.contains(custom.getCodec());
	}

	@SuppressWarnings("unchecked")
	private static <T> Object decodeDirect(IDirectAccessor<T> accessor, ManagedKey key, Tag data) {
		if (!key.getRawField().getType().isPrimitive() && LDLibExtraCodecs.isEmptyOrStringNull(NbtOps.INSTANCE, data)) {
			return null;
		}

		var holder = ManagedHolderVar.ofType((Class<T>) key.getClazzType());
		accessor.writeDirectVar(NbtOps.INSTANCE, holder, data);
		return holder.value();
	}

	private static boolean isSubgroup(Field field) {
		var configurable = field.getAnnotation(Configurable.class);
		return configurable != null ? configurable.subConfigurable()
				: field.getAnnotation(Persisted.class).subPersisted();
	}

	private static void removeFieldTag(CompoundTag tag, Field field) {
		tag.remove(Objects.requireNonNull(PersistedParser.persistedKey(field)));
		if (PersistedParser.isPersistedFlat(field)) {
			var fields = new ArrayList<Field>();
			collect(field.getType(), fields, new HashMap<>());
			for (var child : fields) {
				removeFieldTag(tag, child);
			}
		}
	}

	private static boolean hasAdditionalSerializer(Class<?> type) {
		if (!IPersistedSerializable.class.isAssignableFrom(type)) {
			return false;
		}
		try {
			return type.getMethod("serializeAdditionalNBT", HolderLookup.Provider.class).getDeclaringClass()
					!= IPersistedSerializable.class;
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Missing additional serializer: " + type.getName(), exception);
		}
	}

	private static boolean customSerialize(Class<?> type) {
		if (!ValueIOSerializable.class.isAssignableFrom(type)) {
			return false;
		}

		try {
			return type.getMethod("serialize", ValueOutput.class).getDeclaringClass() != IPersistedSerializable.class;
		} catch (NoSuchMethodException exception) {
			throw new IllegalArgumentException("Missing serialize on " + type.getName(), exception);
		}
	}

	private static boolean customDeserialize(Class<?> type) {
		if (!ValueIOSerializable.class.isAssignableFrom(type)) {
			return false;
		}

		try {
			return type.getMethod("deserialize", ValueInput.class).getDeclaringClass() != IPersistedSerializable.class;
		} catch (NoSuchMethodException exception) {
			throw new IllegalArgumentException("Missing deserialize on " + type.getName(), exception);
		}
	}

	private static Schema nativeSchema(Class<?> type, CompoundTag tag, List<Field> fields,
	                                   String path, String reason, TransferDiagnostics diagnostics) {
		var reasons = new ArrayList<String>();
		for (var field : fields) {
			reasons.add(path + "." + field.getName() + ": native subtree (" + reason + ")");
		}
		if (reasons.isEmpty()) {
			reasons.add(path + ": native subtree (" + reason + ")");
		}

		var privateTag = tag.copy();
		var parser = nativeParser();
		return new Schema(List.of(target -> nativeApply(parser, type, target, privateTag.copy())),
				List.of((_, _) -> {
					diagnostics.record(path, "transfer unsupported for whole custom/empty/recursive root; use compileFields and an explicit adapter");
					throw new IllegalStateException(path + ": cannot transfer whole custom serialization without a root wrapper");
				}), List.copyOf(reasons), 0, false, null, diagnostics, path, false, type);
	}

	private static MethodHandle nativeParser() {
		try {
			var method = PersistedParser.class.getDeclaredMethod("deserializeInternal",
					boolean.class, MapLike.class, DynamicOps.class, Map.class, Class.class,
					Object.class, HolderLookup.Provider.class);
			method.setAccessible(true);
			return MethodHandles.lookup().unreflect(method);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unsupported native persisted parser", exception);
		}
	}

	private static void nativeApply(MethodHandle parser, Class<?> declaredType, Object target, CompoundTag tag) {
		var provider = Platform.getFrozenRegistry();
		if (target instanceof ValueIOSerializable serializable) {
			try (var reporter = new ProblemReporter.ScopedCollector(LDLib2.LOGGER)) {
				serializable.deserialize(TagValueInput.create(reporter, provider, tag));
			}
		} else {
			try {
				// 原生子组遍历声明类型，不能改用公开入口重新遍历目标的派生类型。
				parser.invoke(true, NbtOps.INSTANCE.getMap(tag).getOrThrow(), NbtOps.INSTANCE,
						new HashMap<String, Method>(), declaredType, target, provider);
			} catch (RuntimeException | Error exception) {
				throw exception;
			} catch (Throwable exception) {
				throw new IllegalStateException("Native state-plan fallback failed", exception);
			}
		}
	}

	private static Supplier<?> valueFactory(Object value) {
		if (value == null) {
			return null;
		}

		var type = value.getClass();
		if (IMMUTABLE_TYPES.contains(type) || value instanceof Enum<?>) {
			return () -> value;
		}

		if (value instanceof Tag tag) {
			var privateTag = tag.copy();
			return privateTag::copy;
		}

		var copier = JOML_COPIERS.get(type);
		if (copier != null) {
			var privateValue = invoke(copier, value);
			return () -> invoke(copier, privateValue);
		}

		// 容器、数组、NumberFunction 和其他 accessor 的身份/解码语义由原生字段路径保留。
		return null;
	}

	private static MethodHandle getter(Field field) {
		try {
			field.setAccessible(true);
			return MethodHandles.lookup().unreflectGetter(field);
		} catch (IllegalAccessException exception) {
			throw new IllegalArgumentException("Cannot read " + field, exception);
		}
	}

	private static MethodHandle writer(Field field) {
		try {
			field.setAccessible(true);
			return MethodHandles.lookup().unreflectSetter(field);
		} catch (IllegalAccessException exception) {
			throw new IllegalArgumentException("Cannot write " + field, exception);
		}
	}

	private static MethodHandle methodHandle(Method method) {
		try {
			method.setAccessible(true);
			var handle = MethodHandles.lookup().unreflect(method);
			return Modifier.isStatic(method.getModifiers()) ? MethodHandles.dropArguments(handle, 0, Object.class) : handle;
		} catch (IllegalAccessException exception) {
			throw new IllegalArgumentException("Cannot invoke " + method, exception);
		}
	}

	private static void callSetter(MethodHandle setter, MethodHandle getter, Object target) {
		if (setter != null) {
			invoke(setter, target, invoke(getter, target));
		}
	}

	private static Object invoke(MethodHandle handle, Object argument) {
		try {
			return handle.invoke(argument);
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException("State-plan instruction failed", exception);
		}
	}

	private static void invoke(MethodHandle handle, Object target, Object value) {
		try {
			handle.invoke(target, value);
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException("State-plan instruction failed", exception);
		}
	}

	private interface Step {
		void apply(Object target);
	}

	private interface TransferStep {
		void transfer(Object source, Object target);
	}

	private record FieldAccess(MethodHandle getter, MethodHandle setter, ManagedKey key) {
	}

	private record FieldPlan(Step step, List<String> reasons, int assignments, Schema subgroup) {
		private FieldPlan(Step step, List<String> reasons, int assignments) {
			this(step, reasons, assignments, null);
		}
	}

	private record Schema(List<Step> steps, List<TransferStep> transfers, List<String> reasons, int assignments,
	                      boolean callbacks, Tag additional, TransferDiagnostics diagnostics, String path,
	                      boolean liveAdditional, Class<?> type) {
		private void transfer(Object source, Object target) {
			if (callbacks && target instanceof IPersistedSerializable serializable) {
				serializable.beforeDeserialize();
			}
			for (var transfer : transfers) {
				transfer.transfer(source, target);
			}
			if (callbacks && target instanceof IPersistedSerializable serializable) {
				if (liveAdditional && source instanceof IPersistedSerializable from) {
					var provider = Platform.getFrozenRegistry();
					var current = from.serializeAdditionalNBT(provider);
					if (current != null && current != EndTag.INSTANCE) {
						diagnostics.record(path + "._additional", "transfer native live additional-data callback");
						serializable.deserializeAdditionalNBT(current.copy(), provider);
					}
				}
				serializable.afterDeserialize();
			}
		}

		private void apply(Object target) {
			if (callbacks && target instanceof IPersistedSerializable serializable) {
				serializable.beforeDeserialize();
			}

			for (var step : steps) {
				step.apply(target);
			}

			if (callbacks && target instanceof IPersistedSerializable serializable) {
				if (additional != null) {
					serializable.deserializeAdditionalNBT(additional.copy(), Platform.getFrozenRegistry());
				}
				serializable.afterDeserialize();
			}
		}
	}

	private static class TransferDiagnostics {
		private final Map<String, String> fallbacks = new ConcurrentHashMap<>();

		private void record(String path, String reason) {
			fallbacks.put(path, path + ": " + reason);
		}

		private List<String> reasons(List<String> frozen) {
			var merged = new LinkedHashMap<String, String>();
			for (var reason : frozen) {
				merged.put(reason.substring(0, reason.indexOf(':')), reason);
			}
			fallbacks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
					merged.merge(entry.getKey(), entry.getValue(), (left, right) -> left + "; " + right));
			return List.copyOf(merged.values());
		}
	}

	private record StatePlan(Class<?> type, Supplier<?> constructor, Schema schema,
	                         boolean rootProjection) implements IStatePlan {
		@Override
		public Object create() {
			var target = Objects.requireNonNull(constructor.get(), "State-plan constructor returned null");
			apply(target);
			return target;
		}

		@Override
		public void apply(Object target) {
			if (!type.isInstance(target)) {
				throw new IllegalArgumentException("Expected " + type.getName());
			}
			schema.apply(target);
		}

		@Override
		public void transfer(Object source, Object target) {
			if (!type.isInstance(source) || !type.isInstance(target) || source == target
					|| !rootProjection && (source.getClass() != type || target.getClass() != type)) {
				throw new IllegalArgumentException("Transfer requires distinct " + type.getName() + " instances");
			}
			schema.transfer(source, target);
		}

		@Override
		public int fallbackFields() {
			return fallbackReasons().size();
		}

		@Override
		public List<String> fallbackReasons() {
			return schema.diagnostics().reasons(schema.reasons());
		}

		@Override
		public int assignmentCount() {
			return schema.assignments();
		}
	}
}
