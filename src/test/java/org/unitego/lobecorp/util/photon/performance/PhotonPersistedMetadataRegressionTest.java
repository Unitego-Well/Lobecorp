package org.unitego.lobecorp.util.photon.performance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.PrimitiveAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 使用依赖中真实 parser 对照，只替换两处反射指令；不加载生产 Mixin 或 Codec 工厂的 FML 宿主。
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhotonPersistedMetadataRegressionTest {
	private static final Operation<Method[]> METHOD_LOADER = args -> ((Class<?>) args[0]).getDeclaredMethods();
	private static final Operation<Field[]> FIELD_LOADER = args -> ((Class<?>) args[0]).getDeclaredFields();
	private static Method cachedDeserialize;

	@BeforeAll
	public static void prepareNativeParser() throws Exception {
		if (AccessorRegistries.findByClassOrNull(int.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.INT, ByteBufCodecs.INT, int.class, Integer.class), -1);
		}

		var parser = new ParserLoader().loadClass(PersistedParser.class.getName());
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

		for (var i = 0; i < 2; i++) {
			var cached = new ChildFixture();
			var nestedIdentity = cached.nested;
			var configurableIdentity = cached.configured;
			var flatIdentity = cached.flat;
			deserializeCached(input, cached);

			assertRestored(cached);
			assertEquals(reference.events, cached.events);
			assertSame(nestedIdentity, cached.nested);
			assertSame(configurableIdentity, cached.configured);
			assertSame(flatIdentity, cached.flat);
			assertNotSame(reference.nested, cached.nested);
			assertNotSame(reference.configured, cached.configured);
			assertNotSame(reference.flat, cached.flat);
			var expected = PersistedParser.serialize(NbtOps.INSTANCE, reference, null).getOrThrow();
			var actual = PersistedParser.serialize(NbtOps.INSTANCE, cached, null).getOrThrow();
			assertEquals(expected, actual);
			assertFalse(((CompoundTag) actual).contains("ignored"));
			assertFalse(((CompoundTag) actual).contains("flat"));
			assertTrue(((CompoundTag) actual).contains("flatValue"));

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

	public static Method[] cachedMethods(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER);
	}

	public static Field[] cachedFields(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER);
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
		@Configurable
		private int configOnly;
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

		@ConfigSetter(field = "hidden")
		private void childHidden(int value) {
			events.add("childHidden:" + value);
			if (value == -999) {
				throw new IllegalArgumentException("setter failure");
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
			additional = ((CompoundTag) tag).copy();
		}

		@Override
		public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
			return additional.copy();
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
		}
	}

	public static class NestedFixture {
		@Persisted
		private int nestedValue;
	}

	public static class FlatFixture {
		@Persisted
		private int flatValue;
	}

	/// 仅在独立加载器中修改真实 parser 的两个指令，不重写反序列化算法。
	private static class ParserLoader extends ClassLoader {
		private ParserLoader() {
			super(PhotonPersistedMetadataRegressionTest.class.getClassLoader());
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

		private byte[] parserBytes() throws IOException {
			var path = "/" + Type.getInternalName(PersistedParser.class) + ".class";
			try (var input = PersistedParser.class.getResourceAsStream(path)) {
				if (input == null) {
					throw new IOException("缺少真实 parser 字节码: " + path);
				}

				var reader = new ClassReader(input);
				var writer = new ClassWriter(0);
				var replacements = new int[2];
				reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
					@Override
					public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
						var delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
						if (!name.equals("deserializeInternal")) {
							return delegate;
						}

						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
								if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")) {
									if (name.equals("getDeclaredMethods") && descriptor.equals("()[Ljava/lang/reflect/Method;")) {
										replacements[0]++;
										super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(PhotonPersistedMetadataRegressionTest.class),
												"cachedMethods", "(Ljava/lang/Class;)[Ljava/lang/reflect/Method;", false);
										return;
									}
									if (name.equals("getDeclaredFields") && descriptor.equals("()[Ljava/lang/reflect/Field;")) {
										replacements[1]++;
										super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(PhotonPersistedMetadataRegressionTest.class),
												"cachedFields", "(Ljava/lang/Class;)[Ljava/lang/reflect/Field;", false);
										return;
									}
								}

								super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
							}
						};
					}
				}, 0);
				assertEquals(1, replacements[0]);
				assertEquals(1, replacements[1]);
				return writer.toByteArray();
			}
		}
	}
}
