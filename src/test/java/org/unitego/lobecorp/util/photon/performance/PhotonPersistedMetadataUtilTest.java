package org.unitego.lobecorp.util.photon.performance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// 仅验证缓存与传入的 Operation；不加载 Mixin，不代替真实 parser／Codec 集成验收。
public class PhotonPersistedMetadataUtilTest {
	@Test
	public void methodsAndFieldsUseOnlyTheirFirstSuccessfulLoader() {
		var methodLoads = new AtomicInteger();
		var fieldLoads = new AtomicInteger();
		var methods = Independent.class.getDeclaredMethods();
		var fields = Independent.class.getDeclaredFields();
		Operation<Method[]> methodLoader = args -> {
			assertEquals(1, args.length);
			assertSame(Independent.class, args[0]);
			methodLoads.incrementAndGet();
			return methods;
		};
		Operation<Field[]> fieldLoader = args -> {
			assertEquals(1, args.length);
			assertSame(Independent.class, args[0]);
			fieldLoads.incrementAndGet();
			return fields;
		};

		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(Independent.class, methodLoader));
		assertEquals(0, fieldLoads.get());
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(Independent.class,
				_ -> fail("命中不得调用本次 methods loader")));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(Independent.class, fieldLoader));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(Independent.class,
				_ -> fail("命中不得调用本次 fields loader")));
		assertEquals(1, methodLoads.get());
		assertEquals(1, fieldLoads.get());
	}

	@Test
	public void fieldsMayLoadBeforeMethodsWithoutCoupling() {
		var fields = FieldsFirst.class.getDeclaredFields();
		var methods = FieldsFirst.class.getDeclaredMethods();

		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(FieldsFirst.class, _ -> fields));
		var failure = new SecurityException("methods 尚未缓存");
		assertSame(failure, assertThrows(SecurityException.class,
				() -> PhotonPersistedMetadataUtil.declaredMethods(FieldsFirst.class, _ -> {
					throw failure;
				})));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(FieldsFirst.class,
				_ -> fail("methods 失败不得影响 fields")));
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(FieldsFirst.class, _ -> methods));
	}

	@Test
	public void methodsFailurePropagatesUnchangedAndRetriesWithCurrentLoader() {
		var failure = new NoClassDefFoundError("无法解析方法签名");
		var attempts = new AtomicInteger();
		assertSame(failure, assertThrows(NoClassDefFoundError.class,
				() -> PhotonPersistedMetadataUtil.declaredMethods(MethodFailure.class, _ -> {
					attempts.incrementAndGet();
					throw failure;
				})));

		var methods = MethodFailure.class.getDeclaredMethods();
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(MethodFailure.class, args -> {
			assertSame(MethodFailure.class, args[0]);
			attempts.incrementAndGet();
			return methods;
		}));
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(MethodFailure.class,
				_ -> fail("成功后不得重试旧加载器或新加载器")));
		assertEquals(2, attempts.get());
	}

	@Test
	public void fieldsFailurePropagatesUnchangedAndRetriesWithCurrentLoader() {
		var failure = new SecurityException("无法访问字段");
		var attempts = new AtomicInteger();
		assertSame(failure, assertThrows(SecurityException.class,
				() -> PhotonPersistedMetadataUtil.declaredFields(FieldFailure.class, _ -> {
					attempts.incrementAndGet();
					throw failure;
				})));

		var fields = FieldFailure.class.getDeclaredFields();
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(FieldFailure.class, args -> {
			assertSame(FieldFailure.class, args[0]);
			attempts.incrementAndGet();
			return fields;
		}));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(FieldFailure.class,
				_ -> fail("成功后不得再调用 fields loader")));
		assertEquals(2, attempts.get());
	}

	@Test
	public void declarationArraysKeepOriginalOrderAndHiddenFieldsSeparate() throws Exception {
		var parentMethods = Parent.class.getDeclaredMethods();
		var childMethods = Child.class.getDeclaredMethods();
		var parentFields = Parent.class.getDeclaredFields();
		var childFields = Child.class.getDeclaredFields();

		var cachedParentMethods = PhotonPersistedMetadataUtil.declaredMethods(Parent.class, _ -> parentMethods);
		var cachedChildMethods = PhotonPersistedMetadataUtil.declaredMethods(Child.class, _ -> childMethods);
		var cachedParentFields = PhotonPersistedMetadataUtil.declaredFields(Parent.class, _ -> parentFields);
		var cachedChildFields = PhotonPersistedMetadataUtil.declaredFields(Child.class, _ -> childFields);
		assertSame(parentMethods, cachedParentMethods);
		assertSame(childMethods, cachedChildMethods);
		assertSame(parentFields, cachedParentFields);
		assertSame(childFields, cachedChildFields);
		assertArrayEquals(Parent.class.getDeclaredMethods(), cachedParentMethods);
		assertArrayEquals(Child.class.getDeclaredMethods(), cachedChildMethods);
		assertArrayEquals(Parent.class.getDeclaredFields(), cachedParentFields);
		assertArrayEquals(Child.class.getDeclaredFields(), cachedChildFields);
		assertNotSame(cachedParentFields, cachedChildFields);
		assertEquals(Parent.class, Parent.class.getDeclaredField("hidden").getDeclaringClass());
		assertEquals(Child.class, Child.class.getDeclaredField("hidden").getDeclaringClass());
		assertEquals(parentFields.length, cachedParentFields.length);
		assertEquals(childFields.length, cachedChildFields.length);
	}

	@Test
	public void emptyArraysAreSuccessfulCachedValues() {
		var methods = Empty.class.getDeclaredMethods();
		var fields = Empty.class.getDeclaredFields();
		assertEquals(0, methods.length);
		assertEquals(0, fields.length);
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(Empty.class, _ -> methods));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(Empty.class, _ -> fields));
		assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(Empty.class,
				_ -> fail("空 methods 也是命中")));
		assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(Empty.class,
				_ -> fail("空 fields 也是命中")));
	}

	@Test
	public void concurrentFirstAccessLoadsEachArrayOnce() throws Exception {
		var workers = 12;
		var ready = new CountDownLatch(workers);
		var start = new CountDownLatch(1);
		var methodLoads = new AtomicInteger();
		var fieldLoads = new AtomicInteger();
		var methods = Concurrent.class.getDeclaredMethods();
		var fields = Concurrent.class.getDeclaredFields();
		Operation<Method[]> methodLoader = args -> {
			assertSame(Concurrent.class, args[0]);
			methodLoads.incrementAndGet();
			return methods;
		};
		Operation<Field[]> fieldLoader = args -> {
			assertSame(Concurrent.class, args[0]);
			fieldLoads.incrementAndGet();
			return fields;
		};
		var executor = Executors.newFixedThreadPool(workers);
		try {
			var futures = new ArrayList<Future<?>>();
			for (var i = 0; i < workers; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					assertTrue(start.await(5, TimeUnit.SECONDS));
					for (var j = 0; j < 100; j++) {
						assertSame(methods, PhotonPersistedMetadataUtil.declaredMethods(Concurrent.class, methodLoader));
						assertSame(fields, PhotonPersistedMetadataUtil.declaredFields(Concurrent.class, fieldLoader));
					}
					return null;
				}));
			}

			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			for (var future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
			assertEquals(1, methodLoads.get());
			assertEquals(1, fieldLoads.get());
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private static class Independent {
		private int field;

		private void method() {
		}
	}

	private static class FieldsFirst {
	}

	private static class MethodFailure {
	}

	private static class FieldFailure {
	}

	private static class Parent {
		private int hidden;
		private String parentOnly;

		private void parentMethod() {
		}
	}

	private static class Child extends Parent {
		private int hidden;
		private long childOnly;

		private void childMethod() {
		}
	}

	private static class Empty {
	}

	private static class Concurrent {
		private int field;

		private void method() {
		}
	}
}
