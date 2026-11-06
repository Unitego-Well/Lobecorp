package org.unitego.lobecorp.util.photon.performance;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.PrimitiveAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.SkipPersistedValue;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 使用依赖中真实 parser 对照，仅替换编码和解码的四处枚举指令，不重写原生算法。
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhotonPersistedMetadataRegressionTest {
	private static final int[][] RAW_ENUMERATIONS = new int[2][2];
	private static Method cachedDeserialize;
	private static Method cachedSerialize;
	private static Method nativeDeserialize;
	private static Method nativeSerialize;

	@BeforeAll
	public static void prepareNativeParser() throws Exception {
		if (AccessorRegistries.findByClassOrNull(int.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.INT, ByteBufCodecs.INT, int.class, Integer.class), -1);
		}

		var parser = new ParserLoader(true).loadClass(PersistedParser.class.getName());
		var nativeParser = new ParserLoader(false).loadClass(PersistedParser.class.getName());
		cachedSerialize = parser.getMethod("serialize", DynamicOps.class, Object.class, HolderLookup.Provider.class);
		nativeSerialize = nativeParser.getMethod("serialize", DynamicOps.class, Object.class, HolderLookup.Provider.class);
		nativeDeserialize = nativeParser.getMethod("deserialize", DynamicOps.class, Object.class, Object.class, HolderLookup.Provider.class);
		cachedDeserialize = parser.getMethod("deserialize", DynamicOps.class, Object.class, Object.class, HolderLookup.Provider.class);
		assertNotSame(PersistedParser.class, parser);
	}

	@Test
	@Order(1)
	public void nativeParserMatchesColdAndWarmCacheWithIndependentInstances() throws Throwable {
		var input = input();
		var unchanged = input.copy();
		var reference = new ChildFixture();
		PersistedParser.deserialize(NbtOps.INSTANCE, input, reference, null);
		assertRestored(reference);
		var referenceEvents = List.copyOf(reference.events);

		for (var i = 0; i < 2; i++) {
			var cached = new ChildFixture();
			var nestedIdentity = cached.nested;
			var configurableIdentity = cached.configured;
			var flatIdentity = cached.flat;
			deserializeCached(input, cached);

			assertRestored(cached);
			assertEquals(referenceEvents, cached.events);
			assertSame(nestedIdentity, cached.nested);
			assertSame(configurableIdentity, cached.configured);
			assertSame(flatIdentity, cached.flat);
			assertNotSame(reference.nested, cached.nested);
			assertNotSame(reference.configured, cached.configured);
			assertNotSame(reference.flat, cached.flat);
			var expected = PersistedParser.serialize(NbtOps.INSTANCE, reference, null).getOrThrow();
			var actual = serializeCached(cached);
			assertEquals(expected, actual);
			assertFalse(((CompoundTag) actual).contains("ignored"));
			assertFalse(((CompoundTag) actual).contains("flat"));
			assertTrue(((CompoundTag) actual).contains("flatValue"));
			assertFalse(((CompoundTag) actual).contains("staticValue"));
			assertFalse(((CompoundTag) actual).contains("unannotated"));

			var fromNative = new ChildFixture();
			var fromCached = new ChildFixture();
			deserializeCached((CompoundTag) expected, fromNative);
			PersistedParser.deserialize(NbtOps.INSTANCE, actual, fromCached, null);
			assertRestored(fromNative);
			assertRestored(fromCached);
			assertEquals(expected, serializeCached(fromNative));
			assertEquals(actual, PersistedParser.serialize(NbtOps.INSTANCE, fromCached, null).getOrThrow());

			cached.nested.nestedValue = 999;
			assertEquals(44, reference.nested.nestedValue);
			assertEquals(unchanged, input);
		}
	}

	@Test
	@Order(2)
	public void nativeSetterExceptionAndCallbackPositionMatch() {
		var input = input();
		input.putInt("child_hidden", -999);
		var reference = new ChildFixture();
		var cached = new ChildFixture();
		var expected = assertThrows(RuntimeException.class,
				() -> PersistedParser.deserialize(NbtOps.INSTANCE, input, reference, null));
		var actual = assertThrows(RuntimeException.class, () -> deserializeCached(input, cached));

		assertEquals(expected.getClass(), actual.getClass());
		assertEquals(expected.getCause().getClass(), actual.getCause().getClass());
		assertEquals(expected.getCause().getCause().getClass(), actual.getCause().getCause().getClass());
		assertEquals(expected.getCause().getCause().getMessage(), actual.getCause().getCause().getMessage());
		assertEquals(reference.events, cached.events);
		assertFalse(cached.events.contains("additional"));
		assertFalse(cached.events.contains("after"));
		assertEquals(-999, cached.hidden);
	}

	@Test
	@Order(3)
	public void rootBeforeExceptionPropagatesWithoutFieldOrAdditionalCallbacks() {
		var reference = new ChildFixture();
		var cached = new ChildFixture();
		reference.failBefore = true;
		cached.failBefore = true;
		var expected = assertThrows(IllegalStateException.class,
				() -> PersistedParser.deserialize(NbtOps.INSTANCE, input(), reference, null));
		var actual = assertThrows(IllegalStateException.class, () -> deserializeCached(input(), cached));

		assertEquals(expected.getMessage(), actual.getMessage());
		assertEquals(List.of("before"), cached.events);
		assertEquals(reference.events, cached.events);
		assertEquals(0, cached.hidden);
		assertEquals(0, cached.nested.nestedValue);
	}

	@Test
	public void skipTrueOmitsBothHiddenFieldsWithoutChangingOtherPayload() throws Throwable {
		var reference = new ChildFixture();
		var cached = new ChildFixture();
		PersistedParser.deserialize(NbtOps.INSTANCE, input(), reference, null);
		deserializeCached(input(), cached);
		reference.skipHidden = cached.skipHidden = true;
		var expected = PersistedParser.serialize(NbtOps.INSTANCE, reference, null).getOrThrow();
		var actual = (CompoundTag) serializeCached(cached);
		assertEquals(expected, actual);
		assertFalse(actual.contains("parent_hidden"));
		assertFalse(actual.contains("child_hidden"));
		assertFalse(actual.contains("wrongConfigKey"));
		assertEquals(77, actual.getIntOr("configOnly", -1));
	}

	@Test
	public void decodeAdditionalAndAfterFailuresPreserveOrderAndTypes() {
		for (var failAdditional : List.of(true, false)) {
			var reference = new ChildFixture();
			var cached = new ChildFixture();
			reference.failDecodeAdditional = cached.failDecodeAdditional = failAdditional;
			reference.failDecodeAfter = cached.failDecodeAfter = !failAdditional;
			var expected = assertThrows(IllegalStateException.class,
					() -> PersistedParser.deserialize(NbtOps.INSTANCE, input(), reference, null));
			var actual = assertThrows(IllegalStateException.class, () -> deserializeCached(input(), cached));
			assertFailureChain(expected, actual);
			assertEquals(reference.events, cached.events);
			assertEquals(failAdditional ? "additional" : "after", cached.events.getLast());
			assertEquals(22, cached.hidden);
			assertEquals(44, cached.nested.nestedValue);
		}
	}

	@Test
	@Order(0)
	public void nativeEnumerationCountsCoverColdWarmEncodeDecodeAndSharedCache() throws Throwable {
		assertEnumerationCounts(nativeSerialize, new EncodeCountFixture(), 0, 1);
		assertEnumerationCounts(nativeSerialize, new EncodeCountFixture(), 0, 1);
		assertEnumerationCounts(cachedSerialize, new EncodeCountFixture(), 0, 1);
		assertEnumerationCounts(cachedSerialize, new EncodeCountFixture(), 0, 0);
		assertEnumerationCounts(nativeDeserialize, new DecodeCountFixture(), 1, 1);
		assertEnumerationCounts(nativeDeserialize, new DecodeCountFixture(), 1, 1);
		assertEnumerationCounts(cachedDeserialize, new DecodeCountFixture(), 1, 1);
		assertEnumerationCounts(cachedDeserialize, new DecodeCountFixture(), 1, 0);
		assertEnumerationCounts(cachedDeserialize, new EncodeCountFixture(), 1, 0);
		assertEnumerationCounts(cachedSerialize, new DecodeCountFixture(), 0, 0);
	}

	private static void assertEnumerationCounts(Method parser, Object fixture, int direction, int expected) throws Throwable {
		for (var counts : RAW_ENUMERATIONS) {
			counts[0] = 0;
			counts[1] = 0;
		}

		if (direction == 0) {
			var encoded = (DataResult<?>) invokeParser(parser, NbtOps.INSTANCE, fixture, null);
			var expectedPayload = new CompoundTag();
			expectedPayload.putInt("value", 17);
			assertEquals(expectedPayload, encoded.getOrThrow());
		} else {
			var input = new CompoundTag();
			input.putInt("value", 17);
			switch (fixture) {
				case EncodeCountFixture value -> value.value = -1;
				case DecodeCountFixture value -> value.value = -1;
				default -> throw new IllegalArgumentException("unexpected fixture");
			}

			invokeParser(parser, NbtOps.INSTANCE, input, fixture, null);
			var restored = switch (fixture) {
				case EncodeCountFixture value -> value.value;
				case DecodeCountFixture value -> value.value;
				default -> throw new IllegalArgumentException("unexpected fixture");
			};
			assertEquals(17, restored);
		}

		for (var entry = 0; entry < 2; entry++) {
			assertEquals(expected, RAW_ENUMERATIONS[direction][entry], parser + " enumeration " + entry);
			assertEquals(0, RAW_ENUMERATIONS[1 - direction][entry], "other entry must remain idle");
		}
	}

	@Test
	public void encodeSkipOrderCallbacksAndFailuresMatchNativeParser() throws Throwable {
		for (var failure = 0; failure < 5; failure++) {
			var reference = new ChildFixture();
			var cached = new ChildFixture();
			PersistedParser.deserialize(NbtOps.INSTANCE, input(), reference, null);
			deserializeCached(input(), cached);
			reference.events.clear();
			cached.events.clear();
			reference.failEncodeBefore = cached.failEncodeBefore = failure == 1;
			reference.failSkip = cached.failSkip = failure == 2;
			reference.failAdditional = cached.failAdditional = failure == 3;
			reference.failEncodeAfter = cached.failEncodeAfter = failure == 4;

			if (failure == 0) {
				var expected = PersistedParser.serialize(NbtOps.INSTANCE, reference, null).getOrThrow();
				assertEquals(expected, serializeCached(cached));
				assertEquals(List.of("encodeBefore", "childSkip:11", "childSkip:22", "encodeAdditional", "encodeAfter"), cached.events);
			} else {
				var expected = assertThrows(RuntimeException.class,
						() -> PersistedParser.serialize(NbtOps.INSTANCE, reference, null));
				var actual = assertThrows(RuntimeException.class, () -> serializeCached(cached));
				assertFailureChain(expected, actual);
				var expectedEvents = switch (failure) {
					case 1 -> List.of("encodeBefore");
					case 2 -> List.of("encodeBefore", "childSkip:11");
					case 3 -> List.of("encodeBefore", "childSkip:11", "childSkip:22", "encodeAdditional");
					default ->
							List.of("encodeBefore", "childSkip:11", "childSkip:22", "encodeAdditional", "encodeAfter");
				};
				assertEquals(expectedEvents, cached.events);
			}

			assertEquals(reference.events, cached.events);
			assertFalse(cached.events.stream().anyMatch(event -> event.startsWith("parentSkip:")));
		}
	}

	private static void assertFailureChain(Throwable expected, Throwable actual) {
		while (expected != null) {
			assertEquals(expected.getClass(), actual.getClass());
			assertEquals(expected.getMessage(), actual.getMessage());
			expected = expected.getCause();
			actual = actual.getCause();
		}

		assertNull(actual);
	}

	@Test
	public void productionWrapOperationsCoverBothParserEntriesAtOrder900() throws IOException {
		var path = "/org/unitego/lobecorp/mixin/photon/performance/client/PhotonPersistedMetadataMixin.class";
		try (var input = PhotonPersistedMetadataRegressionTest.class.getResourceAsStream(path)) {
			if (input == null) {
				throw new IOException("缺少生产 Mixin 字节码: " + path);
			}

			var targets = new ArrayList<String>();
			new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
							if (!descriptor.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) {
								return null;
							}

							return new AnnotationVisitor(Opcodes.ASM9) {
								private final List<String> entries = new ArrayList<>();
								private int order;
								private String target;

								@Override
								public void visit(String name, Object value) {
									if (name.equals("order")) {
										order = (Integer) value;
									}
								}

								@Override
								public AnnotationVisitor visitArray(String name) {
									return switch (name) {
										case "method" -> new AnnotationVisitor(Opcodes.ASM9) {
											@Override
											public void visit(String name, Object value) {
												entries.add((String) value);
											}
										};
										case "at" -> new AnnotationVisitor(Opcodes.ASM9) {
											@Override
											public AnnotationVisitor visitAnnotation(String name, String descriptor) {
												return new AnnotationVisitor(Opcodes.ASM9) {
													@Override
													public void visit(String name, Object value) {
														if (name.equals("target")) {
															target = (String) value;
														}
													}
												};
											}
										};
										default -> null;
									};
								}

								@Override
								public void visitEnd() {
									assertEquals(List.of("serializeInternal", "deserializeInternal"), entries);
									assertEquals(900, order);
									var expectedTarget = switch (name) {
										case "lobecorp$cachedDeclaredMethods" ->
												"Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;";
										case "lobecorp$cachedDeclaredFields" ->
												"Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;";
										default -> throw new AssertionError("unexpected production handler: " + name);
									};
									assertEquals(expectedTarget, target);
									targets.add(target);
								}
							};
						}
					};
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			assertEquals(List.of("Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;",
					"Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;"), targets);
		}
	}

	public static Method[] encodeMethods(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredMethods(type, args -> rawEncodeMethods((Class<?>) args[0]));
	}

	public static Field[] encodeFields(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredFields(type, args -> rawEncodeFields((Class<?>) args[0]));
	}

	public static Method[] decodeMethods(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredMethods(type, args -> rawDecodeMethods((Class<?>) args[0]));
	}

	public static Field[] decodeFields(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredFields(type, args -> rawDecodeFields((Class<?>) args[0]));
	}

	public static Method[] rawEncodeMethods(Class<?> type) {
		RAW_ENUMERATIONS[0][0]++;
		return type.getDeclaredMethods();
	}

	public static Field[] rawEncodeFields(Class<?> type) {
		RAW_ENUMERATIONS[0][1]++;
		return type.getDeclaredFields();
	}

	public static Method[] rawDecodeMethods(Class<?> type) {
		RAW_ENUMERATIONS[1][0]++;
		return type.getDeclaredMethods();
	}

	public static Field[] rawDecodeFields(Class<?> type) {
		RAW_ENUMERATIONS[1][1]++;
		return type.getDeclaredFields();
	}

	@SuppressWarnings("unchecked")
	private static Tag serializeCached(Object fixture) throws Throwable {
		return ((DataResult<Tag>) invokeParser(cachedSerialize, NbtOps.INSTANCE, fixture, null)).getOrThrow();
	}

	private static Object invokeParser(Method method, Object... arguments) throws Throwable {
		try {
			return method.invoke(null, arguments);
		} catch (InvocationTargetException exception) {
			throw exception.getCause();
		}
	}

	private static void deserializeCached(CompoundTag input, ChildFixture fixture) throws Throwable {
		try {
			cachedDeserialize.invoke(null, NbtOps.INSTANCE, input, fixture, null);
		} catch (InvocationTargetException exception) {
			throw exception.getCause();
		}
	}

	private static CompoundTag input() {
		var input = new CompoundTag();
		input.putInt("parent_hidden", 11);
		input.putInt("child_hidden", 22);
		input.putInt("parentOnly", 33);
		input.putInt("configOnly", 77);
		input.putInt("ignored", 123);
		var nested = new CompoundTag();
		nested.putInt("nestedValue", 44);
		input.put("nested", nested);
		var configured = new CompoundTag();
		configured.putInt("nestedValue", 55);
		input.put("configured", configured);
		input.putInt("flatValue", 66);
		var additional = new CompoundTag();
		additional.putInt("marker", 88);
		input.put("_additional", additional);
		return input;
	}

	private static void assertRestored(ChildFixture fixture) {
		assertEquals(11, ((ParentFixture) fixture).hidden);
		assertEquals(22, fixture.hidden);
		assertEquals(33, ((ParentFixture) fixture).parentOnly);
		assertEquals(77, fixture.configOnly);
		assertEquals(-1, fixture.ignored);
		assertEquals(91, ChildFixture.staticValue);
		assertEquals(92, fixture.unannotated);
		assertEquals(44, fixture.nested.nestedValue);
		assertEquals(55, fixture.configured.nestedValue);
		assertEquals(66, fixture.flat.flatValue);
		assertEquals(List.of("childHidden:11", "childHidden:22"),
				fixture.events.stream().filter(event -> event.startsWith("childHidden:")).toList());
		assertTrue(fixture.events.contains("parentOnly:33"));
		assertTrue(fixture.events.indexOf("parentOnly:33") < fixture.events.indexOf("childHidden:22"));
		assertFalse(fixture.events.stream().anyMatch(event -> event.startsWith("parentHidden:")));
		assertEquals("before", fixture.events.getFirst());
		assertEquals(List.of("additional", "after"), fixture.events.subList(fixture.events.size() - 2, fixture.events.size()));
		assertEquals(1, fixture.events.stream().filter("before"::equals).count());
		assertEquals(1, fixture.events.stream().filter("additional"::equals).count());
		assertEquals(1, fixture.events.stream().filter("after"::equals).count());
		assertEquals(88, fixture.additional.getIntOr("marker", -1));
	}

	public static class ParentFixture implements IPersistedSerializable {
		@Persisted(key = "parent_hidden")
		private int hidden;
		@Persisted
		private int parentOnly;
		protected final List<String> events = new ArrayList<>();

		@SkipPersistedValue(field = "hidden")
		private boolean parentSkip(int value) {
			events.add("parentSkip:" + value);
			return true;
		}

		@ConfigSetter(field = "hidden")
		private void parentHidden(int value) {
			events.add("parentHidden:" + value);
		}

		@ConfigSetter(field = "parentOnly")
		private void parentOnly(int value) {
			events.add("parentOnly:" + value);
		}
	}

	public static class ChildFixture extends ParentFixture {
		@Persisted(key = "child_hidden")
		private int hidden;
		@Persisted(key = "wrongConfigKey")
		@Configurable
		private int configOnly;
		@Persisted
		@Configurable(persisted = false)
		private int ignored = -1;
		@Persisted(subPersisted = true)
		private final NestedFixture nested = new NestedFixture();
		@Configurable(subConfigurable = true)
		private final NestedFixture configured = new NestedFixture();
		@Persisted(subPersisted = true, subFlattenPersisted = true)
		private final FlatFixture flat = new FlatFixture();
		private CompoundTag additional = new CompoundTag();
		private boolean failBefore;
		private boolean failEncodeBefore;
		private boolean failSkip;
		private boolean failAdditional;
		private boolean failEncodeAfter;
		@Persisted
		private static int staticValue = 91;
		private int unannotated = 92;
		private boolean skipHidden;
		private boolean failDecodeAdditional;
		private boolean failDecodeAfter;

		@ConfigSetter(field = "hidden")
		private void childHidden(int value) {
			events.add("childHidden:" + value);
			if (value == -999) {
				throw new IllegalArgumentException("setter failure");
			}
		}

		@SkipPersistedValue(field = "hidden")
		private boolean childSkip(int value) {
			events.add("childSkip:" + value);
			if (failSkip) {
				throw new IllegalArgumentException("skip failure");
			}

			return skipHidden;
		}

		@Override
		public void beforeSerialize() {
			events.add("encodeBefore");
			if (failEncodeBefore) {
				throw new IllegalStateException("encode before failure");
			}
		}

		@Override
		public void afterSerialize() {
			events.add("encodeAfter");
			if (failEncodeAfter) {
				throw new IllegalStateException("encode after failure");
			}
		}

		@Override
		public void beforeDeserialize() {
			events.add("before");
			if (failBefore) {
				throw new IllegalStateException("before failure");
			}
		}

		@Override
		public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
			events.add("additional");
			if (failDecodeAdditional) {
				throw new IllegalStateException("decode additional failure");
			}

			additional = ((CompoundTag) tag).copy();
		}

		@Override
		public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
			events.add("encodeAdditional");
			if (failAdditional) {
				throw new IllegalStateException("encode additional failure");
			}

			return additional.copy();
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
			if (failDecodeAfter) {
				throw new IllegalStateException("decode after failure");
			}
		}
	}

	public static class EncodeCountFixture {
		@Persisted
		private int value = 17;
	}

	public static class DecodeCountFixture {
		@Persisted
		private int value = 17;
	}

	public static class NestedFixture {
		@Persisted
		private int nestedValue;
	}

	public static class FlatFixture {
		@Persisted
		private int flatValue;
	}

	/// 两种独立加载器使用同一原生算法，分别计数直接枚举和生产 helper 的实际冷加载。
	private static class ParserLoader extends ClassLoader {
		private final boolean cached;

		private ParserLoader(boolean cached) {
			super(PhotonPersistedMetadataRegressionTest.class.getClassLoader());
			this.cached = cached;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.equals(PersistedParser.class.getName())) {
				return super.loadClass(name, resolve);
			}

			synchronized (getClassLoadingLock(name)) {
				var loaded = findLoadedClass(name);
				if (loaded == null) {
					try {
						var bytes = parserBytes();
						loaded = defineClass(name, bytes, 0, bytes.length);
					} catch (IOException exception) {
						throw new ClassNotFoundException(name, exception);
					}
				}

				if (resolve) {
					resolveClass(loaded);
				}
				return loaded;
			}
		}

		private String bridge(int direction, String kind) {
			return (cached ? (direction == 0 ? "encode" : "decode") : (direction == 0 ? "rawEncode" : "rawDecode")) + kind;
		}

		private byte[] parserBytes() throws IOException {
			var path = "/" + Type.getInternalName(PersistedParser.class) + ".class";
			try (var input = PersistedParser.class.getResourceAsStream(path)) {
				if (input == null) {
					throw new IOException("缺少真实 parser 字节码: " + path);
				}

				var reader = new ClassReader(input);
				var writer = new ClassWriter(0);
				var replacements = new int[2][2];
				reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
					@Override
					public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
						var delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
						var direction = switch (name) {
							case "serializeInternal" -> 0;
							case "deserializeInternal" -> 1;
							default -> -1;
						};
						if (direction < 0) {
							return delegate;
						}

						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
								if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")) {
									if (name.equals("getDeclaredMethods") && descriptor.equals("()[Ljava/lang/reflect/Method;")) {
										replacements[direction][0]++;
										super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(PhotonPersistedMetadataRegressionTest.class),
												bridge(direction, "Methods"), "(Ljava/lang/Class;)[Ljava/lang/reflect/Method;", false);
										return;
									}
									if (name.equals("getDeclaredFields") && descriptor.equals("()[Ljava/lang/reflect/Field;")) {
										replacements[direction][1]++;
										super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(PhotonPersistedMetadataRegressionTest.class),
												bridge(direction, "Fields"), "(Ljava/lang/Class;)[Ljava/lang/reflect/Field;", false);
										return;
									}
								}

								super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
							}
						};
					}
				}, 0);
				for (var direction = 0; direction < 2; direction++) {
					assertEquals(1, replacements[direction][0], "methods direction " + direction);
					assertEquals(1, replacements[direction][1], "fields direction " + direction);
				}
				return writer.toByteArray();
			}
		}
	}
}
