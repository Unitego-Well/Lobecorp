package org.unitego.lobecorp.client.bench;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sun.management.ThreadMXBean;
import org.unitego.lobecorp.util.photon.performance.PhotonPersistedMetadataUtil;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 独立 JVM 的声明数组微基准；不启动游戏，不构造配置对象，不改写借用数组。
/// 参数为尚不存在的输出目录；三个独立 JVM 由调用方分别启动。
public class PhotonPersistedMetadataBenchmark {
	private static final int SWEEPS = 200;
	private static final int WARMUP_GROUPS = 3;
	private static final int MEASUREMENT_GROUPS = 6;
	private static final long BUDGET_NS = 25_000_000_000L;
	private static final List<String> SEEDS = List.of(
			"com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig",
			"org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig",
			"com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef",
			"com.lowdragmc.lowdraglib2.math.Transform",
			"com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting",
			"com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting"
	);
	// 实例不捕获状态，重复 hit 复用同一 Operation；计数只发生在真正的反射调用前。
	private static final Operation<Method[]> METHOD_LOADER =
			arguments -> originalMethods((Class<?>) arguments[0]);
	private static final Operation<Field[]> FIELD_LOADER =
			arguments -> originalFields((Class<?>) arguments[0]);
	private static long methodCalls;
	private static long fieldCalls;
	private static volatile long blackhole;

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("参数：NEW_OUTPUT_DIRECTORY；目录必须尚不存在");
		}

		var output = Path.of(args[0]).toAbsolutePath().normalize();
		// createDirectory 原子拒绝已存在路径；不会创建或覆盖调用方已有输出。
		Files.createDirectory(output);
		var started = System.nanoTime();
		var classes = loadInputs();
		var allocation = allocationBean();
		var evidence = validate(classes);
		var report = environment(classes);
		report.put("equivalence", evidence);
		report.put("validationRawMethodCalls", methodCalls);
		report.put("validationRawFieldCalls", fieldCalls);
		report.put("allocationStatus", allocation == null ? "unknown" : "supported");
		report.put("threadId", Thread.currentThread().threadId());
		report.put("sweepsPerBlock", SWEEPS);
		report.put("warmupGroups", WARMUP_GROUPS);
		report.put("measurementGroups", MEASUREMENT_GROUPS);
		report.put("orderPerGroup", List.of("A", "B", "B", "A"));
		report.put("budgetNs", BUDGET_NS);
		report.put("retainedBytes", "unknown: no size agent");
		report.put("scope", "仅声明数组及顺序；不证明旧帧/实例语义、JUnit、真实 Mixin 集成或 FPS");
		var rows = new ArrayList<Row>();
		long expectedChecksum = expectedChecksum(classes);
		report.put("expectedChecksumPerSweep", expectedChecksum);
		report.put("variants", Map.of("A", "原 Class.getDeclaredMethods/Fields", "B", "helper cache，复用 stateless Operation"));
		runGroups(classes, allocation, rows, "warmup", WARMUP_GROUPS, started, expectedChecksum);
		runGroups(classes, allocation, rows, "measurement", MEASUREMENT_GROUPS, started, expectedChecksum);
		validateUnchanged(classes, evidence);
		report.put("postMeasurementEquivalence", true);
		report.put("rows", rows);
		report.put("blackhole", blackhole);
		report.put("budgetExceeded", System.nanoTime() - started >= BUDGET_NS);
		report.put("complete", rows.size() == (WARMUP_GROUPS + MEASUREMENT_GROUPS) * 4
				&& rows.stream().allMatch(row -> row.sweeps() == SWEEPS));
		report.put("totalElapsedNs", System.nanoTime() - started);

		var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		Files.writeString(output.resolve("results.json"), gson.toJson(report), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW);
		writeCsv(output.resolve("rounds.csv"), rows);
		System.out.println(output);
	}

	private static Class<?>[] loadInputs() throws ClassNotFoundException {
		var inputs = new LinkedHashSet<Class<?>>();
		var loader = PhotonPersistedMetadataBenchmark.class.getClassLoader();

		for (var name : SEEDS) {
			for (Class<?> type = Class.forName(name, false, loader); type != null; type = type.getSuperclass()) {
				inputs.add(type);
			}
		}

		return inputs.toArray(Class<?>[]::new);
	}

	private static Method[] originalMethods(Class<?> type) {
		methodCalls++;
		return type.getDeclaredMethods();
	}

	private static Field[] originalFields(Class<?> type) {
		fieldCalls++;
		return type.getDeclaredFields();
	}

	private static List<Equivalence> validate(Class<?>[] classes) {
		var evidence = new ArrayList<Equivalence>();

		for (var type : classes) {
			var methods = originalMethods(type);
			var fields = originalFields(type);
			var cachedMethods = PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER);
			var cachedFields = PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER);
			requireEqual(type, methods, cachedMethods);
			requireEqual(type, fields, cachedFields);
			evidence.add(new Equivalence(type.getName(), signatures(methods), signatures(cachedMethods),
					signatures(fields), signatures(cachedFields), true));
		}

		// 本入口要求新 JVM：首次校验应各含一次原路径和一次 cache miss。
		if (methodCalls != 2L * classes.length || fieldCalls != 2L * classes.length) {
			throw new IllegalStateException("helper 已预热或 loader 调用次数不符合独立 JVM 契约");
		}

		return List.copyOf(evidence);
	}

	private static void requireEqual(Class<?> type, Object[] original, Object[] cached) {
		if (!Arrays.equals(original, cached)) {
			throw new IllegalStateException("声明数组内容/顺序不等价：" + type.getName());
		}
	}

	private static List<String> signatures(Member[] members) {
		var signatures = new ArrayList<String>(members.length);

		for (var member : members) {
			signatures.add(member.toString());
		}

		return List.copyOf(signatures);
	}

	private static void validateUnchanged(Class<?>[] classes, List<Equivalence> evidence) {
		for (int i = 0; i < classes.length; i++) {
			var type = classes[i];
			var methods = originalMethods(type);
			var fields = originalFields(type);
			var cachedMethods = PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER);
			var cachedFields = PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER);
			requireEqual(type, methods, cachedMethods);
			requireEqual(type, fields, cachedFields);
			var before = evidence.get(i);

			if (!before.originalMethods().equals(signatures(methods))
					|| !before.originalFields().equals(signatures(fields))) {
				throw new IllegalStateException("测量后数组内容/顺序变化：" + type.getName());
			}
		}
	}

	private static ThreadMXBean allocationBean() {
		var bean = ManagementFactory.getThreadMXBean();

		if (!(bean instanceof ThreadMXBean allocation) || !allocation.isThreadAllocatedMemorySupported()) {
			return null;
		}

		try {
			if (!allocation.isThreadAllocatedMemoryEnabled()) {
				allocation.setThreadAllocatedMemoryEnabled(true);
			}

			return allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) >= 0 ? allocation : null;
		} catch (UnsupportedOperationException | SecurityException _) {
			return null;
		}
	}

	private static long allocated(ThreadMXBean bean) {
		return bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	private static long consume(Member[] members) {
		long sum = members.length;

		// 读取全部成员，两个分支使用相同工作负载；顺序通过非交换累加进入 checksum。
		for (var member : members) {
			sum = sum * 31 + member.hashCode();
		}

		return sum;
	}

	private static long expectedChecksum(Class<?>[] classes) {
		long sum = 0;

		for (var type : classes) {
			sum += consume(PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER));
			sum += consume(PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER));
		}

		return sum;
	}

	private static void runGroups(Class<?>[] classes, ThreadMXBean allocation, List<Row> rows,
	                              String phase, int groups, long started, long expectedChecksum) {
		for (int group = 0; group < groups; group++) {
			for (int position = 0; position < 4; position++) {
				if (System.nanoTime() - started >= BUDGET_NS) {
					return;
				}

				boolean cached = position == 1 || position == 2;
				rows.add(measure(classes, allocation, phase, group, position, cached, started, expectedChecksum));
			}
		}
	}

	private static Row measure(Class<?>[] classes, ThreadMXBean allocation, String phase, int group,
	                           int position, boolean cached, long started, long expectedChecksum) {
		long methodsBefore = methodCalls;
		long fieldsBefore = fieldCalls;
		long bytesBefore = allocated(allocation);
		long timeBefore = System.nanoTime();
		long checksum = 0;
		int sweeps = 0;

		for (; sweeps < SWEEPS; sweeps++) {
			if (System.nanoTime() - started >= BUDGET_NS) {
				break;
			}

			for (var type : classes) {
				var methods = cached ? PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER)
						: originalMethods(type);
				var fields = cached ? PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER)
						: originalFields(type);
				checksum += consume(methods);
				checksum += consume(fields);
			}
		}

		long elapsed = System.nanoTime() - timeBefore;
		long bytesAfter = allocated(allocation);
		blackhole = checksum;
		long rawMethods = methodCalls - methodsBefore;
		long rawFields = fieldCalls - fieldsBefore;
		long expectedCalls = cached ? 0 : (long) sweeps * classes.length;

		if (checksum != expectedChecksum * sweeps || rawMethods != expectedCalls || rawFields != expectedCalls) {
			throw new IllegalStateException("checksum 或原反射次数不符合预期：" + phase + "/" + group + "/" + position);
		}

		String bytes = bytesBefore < 0 || bytesAfter < bytesBefore ? "unknown"
				: Long.toString(bytesAfter - bytesBefore);
		return new Row(phase, group, position, cached ? "B" : "A", sweeps,
				(long) sweeps * classes.length * 2, rawMethods, rawFields, rawMethods + rawFields,
				bytes, elapsed, checksum);
	}

	private static Map<String, Object> environment(Class<?>[] classes) throws Exception {
		var result = new LinkedHashMap<String, Object>();
		var runtime = ManagementFactory.getRuntimeMXBean();
		result.put("pid", ProcessHandle.current().pid());
		result.put("jvmName", runtime.getVmName());
		result.put("jvmVersion", runtime.getVmVersion());
		result.put("jvmVendor", runtime.getVmVendor());
		result.put("javaVersion", System.getProperty("java.version"));
		result.put("jvmArguments", runtime.getInputArguments());
		result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
		result.put("seedClasses", SEEDS);
		result.put("compileTimeLockedVersions", Map.of("minecraft", "26.1.2", "neoforge", "26.1.2.100",
				"photon", "26.1.2.3", "ldlib2", "26.1.2.42"));
		result.put("versionScope", "锁定版本取自实现时 gradle/libs.versions.toml；实际运行来源见 inputs 的版本、codeSource 和 SHA");
		result.put("inputPolicy", "固定 seed，按出现顺序加入各父类，Class 身份去重；父类独立，不合并 metadata");
		var inputs = new ArrayList<Map<String, Object>>();

		for (var type : classes) {
			var input = new LinkedHashMap<String, Object>();
			input.put("class", type.getName());
			input.put("superclass", type.getSuperclass() == null ? "" : type.getSuperclass().getName());
			input.put("implementationVersion", type.getPackage().getImplementationVersion() == null
					? "unknown" : type.getPackage().getImplementationVersion());
			var source = type.getProtectionDomain().getCodeSource();
			input.put("codeSource", source == null ? "unknown" : source.getLocation().toExternalForm());
			input.put("classBytesSha256", classHash(type));
			inputs.add(input);
		}

		result.put("inputs", inputs);
		result.put("helperClass", PhotonPersistedMetadataUtil.class.getName());
		result.put("helperClassBytesSha256", classHash(PhotonPersistedMetadataUtil.class));
		var helperMembers = new LinkedHashMap<String, String>();

		for (var nested : PhotonPersistedMetadataUtil.class.getDeclaredClasses()) {
			helperMembers.put(nested.getName(), classHash(nested));
		}

		result.put("helperDeclaredClassBytesSha256", helperMembers);
		result.put("classHashScope", "加载类对应资源字节；非 Instrumentation 转换后字节");
		result.put("measurement", "ThreadMXBean 当前线程实际分配差值；nanoTime 墙钟；非 retained size/FPS");
		return result;
	}

	private static String classHash(Class<?> type) throws Exception {
		var resource = "/" + type.getName().replace('.', '/') + ".class";

		try (var input = type.getResourceAsStream(resource)) {
			if (input == null) {
				throw new IOException("缺少类字节资源：" + resource);
			}

			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
		}
	}

	private static void writeCsv(Path path, List<Row> rows) throws IOException {
		var csv = new StringBuilder("phase,group,position,variant,sweeps,operations,rawMethodCalls,rawFieldCalls,rawReflectionCalls,allocatedBytes,elapsedNs,checksum\n");

		for (var row : rows) {
			csv.append(row.phase()).append(',').append(row.group()).append(',').append(row.position()).append(',')
					.append(row.variant()).append(',').append(row.sweeps()).append(',').append(row.operations()).append(',')
					.append(row.rawMethodCalls()).append(',').append(row.rawFieldCalls()).append(',')
					.append(row.rawReflectionCalls()).append(',').append(row.allocatedBytes()).append(',')
					.append(row.elapsedNs()).append(',').append(row.checksum()).append('\n');
		}

		Files.writeString(path, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
	}

	private record Equivalence(String className, List<String> originalMethods, List<String> cachedMethods,
	                           List<String> originalFields, List<String> cachedFields, boolean contentAndOrderEqual) {
	}

	private record Row(String phase, int group, int position, String variant, int sweeps, long operations,
	                   long rawMethodCalls, long rawFieldCalls, long rawReflectionCalls,
	                   String allocatedBytes, long elapsedNs, long checksum) {
	}
}
