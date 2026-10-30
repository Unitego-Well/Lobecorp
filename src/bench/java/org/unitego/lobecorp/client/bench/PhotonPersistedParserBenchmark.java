package org.unitego.lobecorp.client.bench;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
import com.sun.management.ThreadMXBean;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.unitego.lobecorp.util.photon.performance.PhotonPersistedMetadataUtil;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 真实 parser 的 fixture 往返；独立加载器仅改写指定反射调用点，不加载游戏或 Codec 工厂。
public class PhotonPersistedParserBenchmark {
	private static final int COPIES = 100;
	private static final int WARMUP_GROUPS = 3;
	private static final int MEASUREMENT_GROUPS = 6;
	private static final long BUDGET_NS = 20_000_000_000L;
	private static final String PARSER = PersistedParser.class.getName();
	private static final String OWNER = Type.getInternalName(PhotonPersistedParserBenchmark.class);
	private static final List<String> FIXTURES = List.of(
			ParentFixture.class.getName(), ChildFixture.class.getName(),
			NestedFixture.class.getName(), FlatFixture.class.getName());
	private static final Operation<Method[]> METHOD_LOADER = args -> rawMethods((Class<?>) args[0]);
	private static final Operation<Field[]> FIELD_LOADER = args -> rawFields((Class<?>) args[0]);
	// 单线程入口在 encode/decode dispatch 前选择计数槽，递归原样归入入口。
	private static final long[][] RAW = new long[2][2];
	private static int entry;
	private static volatile long blackhole;

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("参数：NEW_OUTPUT_DIRECTORY，目录必须不存在");
		}

		var output = Path.of(args[0]).toAbsolutePath().normalize();
		Files.createDirectory(output);
		long started = System.nanoTime();
		if (AccessorRegistries.findByClassOrNull(int.class) == null) {
			AccessorRegistries.registerAccessor(
					PrimitiveAccessor.of(Codec.INT, ByteBufCodecs.INT, int.class, Integer.class), -1);
		}

		var a = new Variant(false);
		var b = new Variant(true);
		var allocation = allocationBean();
		var rows = new ArrayList<Row>();
		var report = environment(a, b);
		report.put("variants", Map.of("A", "真实 parser，仅 deserializeInternal 两调用使用 helper；非纯原始",
				"B", "真实 parser，serializeInternal + deserializeInternal 四调用使用 helper"));
		report.put("harness", "相同 Method.invoke dispatch；fresh fixture、Tag copy/toString/hash、状态与回调校验、计数均含在计量内；不是裸 parser 耗时");
		report.put("coldOrder", List.of("A", "B"));
		report.put("coldIsolation", "各 loader 独立 fixture Class 身份；共享 helper 按 Class 分缓存；不强制 GC");
		report.put("scope", "仅 fixture parser serialize→新实例→deserialize 往返含 dispatch；不含真实 FX 创建/render/FPS/净保留内存");
		report.put("copiesPerBlock", COPIES);
		report.put("warmupGroups", WARMUP_GROUPS);
		report.put("measurementGroups", MEASUREMENT_GROUPS);
		report.put("order", "偶数组 ABBA，奇数组 BAAB");
		report.put("budgetNs", BUDGET_NS);
		report.put("allocationUnknown", allocation == null);
		report.put("retainedBytes", "unknown");
		var coldA = measured(a, "cold", 0, 0, 1, allocation, started, null);
		rows.add(coldA);
		var coldB = measured(b, "cold", 0, 1, 1, allocation, started, null);
		rows.add(coldB);
		require(coldA.copies() == 1 && coldB.copies() == 1, "预算不足以完成 cold 对照");
		require(a.coldEvidence != null && a.coldEvidence.equals(b.coldEvidence), "cold A/B Tag、状态或回调不等价");
		report.put("coldEquivalence", a.coldEvidence);
		var expected = verify(a, b);
		report.put("preMeasurementEquivalence", expected);
		runGroups(a, b, rows, "warmup", WARMUP_GROUPS, allocation, started, expected);
		runGroups(a, b, rows, "measurement", MEASUREMENT_GROUPS, allocation, started, expected);
		require(expected.equals(verify(a, b)), "计量前后语义或输入变化");
		a.checkMetadata();
		b.checkMetadata();
		report.put("postMeasurementImmutable", true);
		report.put("rows", rows);
		report.put("blackhole", blackhole);
		report.put("budgetExceeded", System.nanoTime() - started >= BUDGET_NS);
		report.put("complete", rows.size() == 2 + 4 * (WARMUP_GROUPS + MEASUREMENT_GROUPS)
				&& rows.stream().allMatch(row -> row.copies() == (row.phase().equals("cold") ? 1 : COPIES)));
		report.put("totalElapsedNs", System.nanoTime() - started);
		Files.writeString(output.resolve("results.json"),
				new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		writeCsv(output.resolve("rounds.csv"), rows);
		System.out.println(output);
	}

	public static Method[] rawMethods(Class<?> type) {
		RAW[entry][0]++;
		return type.getDeclaredMethods();
	}

	public static Field[] rawFields(Class<?> type) {
		RAW[entry][1]++;
		return type.getDeclaredFields();
	}

	public static Method[] cachedMethods(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredMethods(type, METHOD_LOADER);
	}

	public static Field[] cachedFields(Class<?> type) {
		return PhotonPersistedMetadataUtil.declaredFields(type, FIELD_LOADER);
	}

	private static Evidence verify(Variant a, Variant b) throws Exception {
		var left = a.roundtrip();
		var right = b.roundtrip();
		require(left.evidence().equals(right.evidence()), "A/B Tag、值或回调顺序不等价");
		require(left.target() != right.target(), "A/B 根实例共享");
		var leftNested = left.target().nestedIdentity();
		var rightNested = right.target().nestedIdentity();
		require(leftNested != rightNested, "A/B nested 共享");
		left.target().changeNested();
		require(right.evidence().equals(evidence(right.tag(), right.target(), right.encodeEvents())), "实例隔离失败");
		var again = a.roundtrip();
		require(again.evidence().equals(right.evidence()), "fixture 源或新实例被污染");
		a.checkSource();
		b.checkSource();
		return right.evidence();
	}

	private static Evidence evidence(CompoundTag tag, IFixture target, List<String> encodeEvents) {
		return new Evidence(tag.toString(), target.state(), encodeEvents, List.copyOf(target.events()));
	}

	private static void runGroups(Variant a, Variant b, List<Row> rows, String phase, int groups,
	                              ThreadMXBean allocation, long started, Evidence expected) throws Exception {
		for (int group = 0; group < groups; group++) {
			for (int position = 0; position < 4; position++) {
				if (System.nanoTime() - started >= BUDGET_NS) {
					return;
				}

				boolean useB = (position == 1 || position == 2) ^ (group % 2 != 0);
				rows.add(measured(useB ? b : a, phase, group, position, COPIES, allocation, started, expected));
			}
		}
	}

	private static Row measured(Variant variant, String phase, int group, int position, int limit,
	                            ThreadMXBean allocation, long started, Evidence expected) throws Exception {
		var before = counts();
		long bytesBefore = allocated(allocation);
		long timeBefore = System.nanoTime();
		long checksum = 0;
		int copies = 0;
		for (; copies < limit && System.nanoTime() - started < BUDGET_NS; copies++) {
			var roundtrip = variant.roundtrip();
			if (phase.equals("cold")) {
				variant.coldEvidence = roundtrip.evidence();
			}
			checksum = checksum * 31 + roundtrip.tag().hashCode();
			checksum = checksum * 31 + roundtrip.target().state().hashCode();
			if (expected != null) {
				require(expected.equals(roundtrip.evidence()), "计量往返不等价");
			}
		}

		long elapsed = System.nanoTime() - timeBefore;
		long bytesAfter = allocated(allocation);
		blackhole = checksum;
		var after = counts();
		var raw = new long[4];
		for (int i = 0; i < raw.length; i++) {
			raw[i] = after[i] - before[i];
		}

		if (!phase.equals("cold") && copies > 0) {
			require(raw[2] == 0 && raw[3] == 0, "warm decode 仍有真实反射");
			require(variant.both ? raw[0] == 0 && raw[1] == 0
					: raw[0] > 0 && raw[0] == raw[1], "warm encode 原始反射次数异常");
		}

		String bytes = bytesBefore < 0 || bytesAfter < bytesBefore ? "unknown" : Long.toString(bytesAfter - bytesBefore);
		String perCopyBytes = bytes.equals("unknown") || copies == 0 ? "unknown"
				: Double.toString((double) (bytesAfter - bytesBefore) / copies);
		return new Row(phase, group, position, variant.both ? "B" : "A", copies,
				raw[0], raw[1], raw[2], raw[3], bytes, perCopyBytes, elapsed,
				copies == 0 ? "unknown" : Double.toString((double) elapsed / copies), checksum);
	}

	private static long[] counts() {
		return new long[]{RAW[0][0], RAW[0][1], RAW[1][0], RAW[1][1]};
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
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

			return allocated(allocation) < 0 ? null : allocation;
		} catch (UnsupportedOperationException | SecurityException _) {
			return null;
		}
	}

	private static long allocated(ThreadMXBean allocation) {
		return allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	private static Map<String, Object> environment(Variant a, Variant b) throws Exception {
		var result = new LinkedHashMap<String, Object>();
		var runtime = ManagementFactory.getRuntimeMXBean();
		result.put("pid", ProcessHandle.current().pid());
		result.put("startInstant", ProcessHandle.current().info().startInstant().map(Object::toString).orElse("unknown"));
		result.put("runtimeStartMillis", runtime.getStartTime());
		result.put("jvm", runtime.getVmName() + " " + runtime.getVmVendor() + " " + runtime.getVmVersion());
		result.put("javaVersion", System.getProperty("java.version"));
		result.put("jvmArguments", runtime.getInputArguments());
		result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
		result.put("threadId", Thread.currentThread().threadId());
		result.put("lockedVersions", Map.of("minecraft", "26.1.2", "neoforge", "26.1.2.100",
				"ldlib2", "26.1.2.42", "photon", "26.1.2.3"));
		var inputs = new LinkedHashMap<String, Object>();
		for (var type : List.of(PhotonPersistedParserBenchmark.class, PersistedParser.class,
				PhotonPersistedMetadataUtil.class, ParentFixture.class, ChildFixture.class, NestedFixture.class, FlatFixture.class)) {
			var codeSource = type.getProtectionDomain().getCodeSource();
			inputs.put(type.getName(), Map.of("classSha256", hash(bytes(type.getName())),
					"codeSource", codeSource == null ? "unknown" : codeSource.getLocation().toExternalForm(),
					"implementationVersion", String.valueOf(type.getPackage().getImplementationVersion())));
		}

		for (var nested : PhotonPersistedMetadataUtil.class.getDeclaredClasses()) {
			inputs.put(nested.getName(), Map.of("classSha256", hash(bytes(nested.getName()))));
		}

		result.put("inputs", inputs);
		var sourceHashes = new LinkedHashMap<String, String>();
		for (var file : List.of("src/bench/java/org/unitego/lobecorp/client/bench/PhotonPersistedParserBenchmark.java",
				"src/main/java/org/unitego/lobecorp/util/photon/performance/PhotonPersistedMetadataUtil.java",
				"gradle/libs.versions.toml")) {
			var path = Path.of(file).toAbsolutePath().normalize();
			sourceHashes.put(path.toString(), Files.isRegularFile(path) ? hash(Files.readAllBytes(path)) : "unknown");
		}

		result.put("sourceHashes", sourceHashes);
		result.put("sourceHashScope", "运行时磁盘源码，不声称与加载字节构建对应；真实 parser 来源另以资源字节 SHA 记录");
		result.put("transformedParserSha256", Map.of("A", a.loader.parserHash, "B", b.loader.parserHash));
		result.put("replacementSites", Map.of("A", a.loader.replacements, "B", b.loader.replacements));
		return result;
	}

	private static byte[] bytes(String name) throws IOException {
		var resource = "/" + name.replace('.', '/') + ".class";
		try (var input = PhotonPersistedParserBenchmark.class.getResourceAsStream(resource)) {
			if (input == null) {
				throw new IOException("缺少类字节：" + resource);
			}

			return input.readAllBytes();
		}
	}

	private static String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void writeCsv(Path path, List<Row> rows) throws IOException {
		var csv = new StringBuilder("phase,group,position,variant,copies,encodeRawMethods,encodeRawFields,decodeRawMethods,decodeRawFields,allocatedBytes,bytesPerRoundtrip,elapsedNs,nsPerRoundtrip,checksum\n");
		for (var r : rows) {
			csv.append(r.phase()).append(',').append(r.group()).append(',').append(r.position()).append(',')
					.append(r.variant()).append(',').append(r.copies()).append(',').append(r.encodeRawMethods()).append(',')
					.append(r.encodeRawFields()).append(',').append(r.decodeRawMethods()).append(',').append(r.decodeRawFields())
					.append(',').append(r.allocatedBytes()).append(',').append(r.bytesPerRoundtrip()).append(',')
					.append(r.elapsedNs()).append(',').append(r.nsPerRoundtrip()).append(',').append(r.checksum()).append('\n');
		}

		Files.writeString(path, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
	}

	private record Row(String phase, int group, int position, String variant, int copies,
	                   long encodeRawMethods, long encodeRawFields, long decodeRawMethods, long decodeRawFields,
	                   String allocatedBytes, String bytesPerRoundtrip, long elapsedNs, String nsPerRoundtrip,
	                   long checksum) {
	}

	private record Evidence(String tag, List<Integer> state, List<String> encodeEvents, List<String> decodeEvents) {
	}

	private record Roundtrip(CompoundTag tag, IFixture target, List<String> encodeEvents, Evidence evidence) {
	}

	private static class Variant {
		private final boolean both;
		private final ParserLoader loader;
		private final Method encode;
		private final Method decode;
		private final IFixture source;
		private final List<Integer> sourceState;
		private Evidence coldEvidence;
		private final Map<Class<?>, List<String>> metadata = new LinkedHashMap<>();

		private Variant(boolean both) throws Exception {
			this.both = both;
			loader = new ParserLoader(both);
			var parser = loader.loadClass(PARSER);
			require(parser != PersistedParser.class, "parser 未隔离");
			encode = parser.getMethod("serialize", DynamicOps.class, Object.class, HolderLookup.Provider.class);
			decode = parser.getMethod("deserialize", DynamicOps.class, Object.class, Object.class, HolderLookup.Provider.class);
			source = (IFixture) loader.loadClass(ChildFixture.class.getName()).getConstructor().newInstance();
			source.seed();
			sourceState = source.state();
			for (var name : FIXTURES) {
				var type = loader.loadClass(name);
				metadata.put(type, signatures(type));
			}
		}

		private CompoundTag encode(IFixture value) throws Exception {
			entry = 0;
			return (CompoundTag) ((DataResult<?>) invoke(encode, NbtOps.INSTANCE, value, null)).getOrThrow();
		}

		private Roundtrip roundtrip() throws Exception {
			source.events().clear();
			var tag = encode(source);
			var unchanged = tag.copy();
			var encodeEvents = List.copyOf(source.events());
			var target = source.fresh();
			var nested = target.nestedIdentity();
			var flat = target.flatIdentity();
			entry = 1;
			invoke(decode, NbtOps.INSTANCE, tag, target, null);
			require(tag.equals(unchanged), "decode 修改输入 Tag");
			require(nested == target.nestedIdentity() && flat == target.flatIdentity(), "final 子对象身份变化");
			require(nested != source.nestedIdentity() && flat != source.flatIdentity(), "源和目标共享子实例");
			require(sourceState.equals(source.state()), "serialize 修改源值");
			require(target.state().equals(List.of(11, 22, 33, 77, -1, 44, 66, -7, 88)), "fixture 值不符合原生语义");
			require(!tag.contains("ignored") && !tag.contains("skipped") && !tag.contains("flat")
					&& tag.contains("flatValue") && tag.contains("parent_hidden") && tag.contains("child_hidden"), "Tag 结构错误");
			require(encodeEvents.equals(List.of("beforeEncode", "skip:9", "additionalEncode", "afterEncode")), "encode 回调错误");
			require(target.events().equals(List.of("beforeDecode", "childHidden:11", "parentOnly:33",
					"childHidden:22", "additionalDecode", "afterDecode")), "decode 回调顺序错误");
			return new Roundtrip(tag, target, encodeEvents, evidence(tag, target, encodeEvents));
		}

		private void checkSource() throws Exception {
			require(sourceState.equals(source.state()), "源被污染");
			var first = encode(source);
			var second = encode(source);
			require(first.equals(second), "源 Tag 不稳定");
		}

		private void checkMetadata() {
			metadata.forEach((type, before) -> {
				require(before.equals(signatures(type)), "声明元数据内容或顺序变化");
				require(Arrays.equals(type.getDeclaredMethods(), cachedMethods(type)), "缓存 methods 被改写");
				require(Arrays.equals(type.getDeclaredFields(), cachedFields(type)), "缓存 fields 被改写");
			});
		}
	}

	private static List<String> signatures(Class<?> type) {
		var result = new ArrayList<String>();
		for (var method : type.getDeclaredMethods()) {
			result.add(method.toString());
		}

		for (var field : type.getDeclaredFields()) {
			result.add(field.toString());
		}

		return List.copyOf(result);
	}

	private static Object invoke(Method method, Object... arguments) throws Exception {
		try {
			return method.invoke(null, arguments);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof Exception cause) {
				throw cause;
			}

			throw new IllegalStateException("parser 失败", exception.getCause());
		}
	}

	public interface IFixture {
		void seed();

		IFixture fresh();

		List<Integer> state();

		List<String> events();

		Object nestedIdentity();

		Object flatIdentity();

		void changeNested();
	}

	public static class ParentFixture implements IPersistedSerializable {
		@Persisted(key = "parent_hidden")
		public int hidden;
		@Persisted
		public int parentOnly;
		public final List<String> events = new ArrayList<>();

		@ConfigSetter(field = "parentOnly")
		public void parentOnly(int value) {
			events.add("parentOnly:" + value);
		}
	}

	public static class ChildFixture extends ParentFixture implements IFixture {
		@Persisted(key = "child_hidden")
		public int hidden;
		@Configurable
		public int configOnly;
		@Configurable(persisted = false)
		public int ignored = -1;
		@Persisted(subPersisted = true)
		public final NestedFixture nested = new NestedFixture();
		@Persisted(subPersisted = true, subFlattenPersisted = true)
		public final FlatFixture flat = new FlatFixture();
		@Persisted
		public int skipped = -7;
		public CompoundTag additional = new CompoundTag();

		@Override
		public void seed() {
			super.hidden = 11;
			hidden = 22;
			parentOnly = 33;
			configOnly = 77;
			nested.nestedValue = 44;
			flat.flatValue = 66;
			skipped = 9;
			additional.putInt("marker", 88);
		}

		@Override
		public IFixture fresh() {
			return new ChildFixture();
		}

		@Override
		public List<Integer> state() {
			return List.of(super.hidden, hidden, parentOnly, configOnly, ignored, nested.nestedValue,
					flat.flatValue, skipped, additional.getIntOr("marker", -1));
		}

		@Override
		public List<String> events() {
			return events;
		}

		@Override
		public Object nestedIdentity() {
			return nested;
		}

		@Override
		public Object flatIdentity() {
			return flat;
		}

		@Override
		public void changeNested() {
			nested.nestedValue = 999;
		}

		@SkipPersistedValue(field = "skipped")
		public boolean skip(int value) {
			events.add("skip:" + value);
			return value == 9;
		}

		@ConfigSetter(field = "hidden")
		public void childHidden(int value) {
			events.add("childHidden:" + value);
		}

		@Override
		public void beforeSerialize() {
			events.add("beforeEncode");
		}

		@Override
		public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
			events.add("additionalEncode");
			return additional.copy();
		}

		@Override
		public void afterSerialize() {
			events.add("afterEncode");
		}

		@Override
		public void beforeDeserialize() {
			events.add("beforeDecode");
		}

		@Override
		public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
			events.add("additionalDecode");
			additional = ((CompoundTag) tag).copy();
		}

		@Override
		public void afterDeserialize() {
			events.add("afterDecode");
		}
	}

	public static class NestedFixture {
		@Persisted
		public int nestedValue;
	}

	public static class FlatFixture {
		@Persisted
		public int flatValue;
	}

	private static class ParserLoader extends ClassLoader {
		private final boolean both;
		private final Map<String, List<Integer>> replacements = new LinkedHashMap<>();
		private String parserHash;

		private ParserLoader(boolean both) {
			super(PhotonPersistedParserBenchmark.class.getClassLoader());
			this.both = both;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.equals(PARSER) && !FIXTURES.contains(name)) {
				return super.loadClass(name, resolve);
			}

			synchronized (getClassLoadingLock(name)) {
				var loaded = findLoadedClass(name);
				if (loaded == null) {
					try {
						var bytecode = bytes(name);
						if (name.equals(PARSER)) {
							bytecode = transform(bytecode);
							parserHash = hash(bytecode);
						}

						loaded = defineClass(name, bytecode, 0, bytecode.length);
					} catch (Exception exception) {
						throw new ClassNotFoundException(name, exception);
					}
				}

				if (resolve) {
					resolveClass(loaded);
				}

				return loaded;
			}
		}

		private byte[] transform(byte[] original) {
			var writer = new ClassWriter(0);
			var sites = new LinkedHashMap<String, int[]>();
			new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
					var delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
					if (!method.equals("serializeInternal") && !method.equals("deserializeInternal")) {
						return delegate;
					}

					require(!sites.containsKey(method), "目标方法重载不符合预期");
					var counts = new int[2];
					sites.put(method, counts);
					boolean cached = both || method.equals("deserializeInternal");
					return new MethodVisitor(Opcodes.ASM9, delegate) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
							if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")) {
								if (name.equals("getDeclaredMethods") && desc.equals("()[Ljava/lang/reflect/Method;")) {
									counts[0]++;
									super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, cached ? "cachedMethods" : "rawMethods",
											"(Ljava/lang/Class;)[Ljava/lang/reflect/Method;", false);
									return;
								}

								if (name.equals("getDeclaredFields") && desc.equals("()[Ljava/lang/reflect/Field;")) {
									counts[1]++;
									super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, cached ? "cachedFields" : "rawFields",
											"(Ljava/lang/Class;)[Ljava/lang/reflect/Field;", false);
									return;
								}
							}

							super.visitMethodInsn(opcode, owner, name, desc, isInterface);
						}
					};
				}
			}, 0);
			require(sites.size() == 2, "目标方法缺失");
			sites.forEach((name, count) -> {
				require(count[0] == 1 && count[1] == 1, name + " 必须恰好一个 methods + 一个 fields");
				replacements.put(name, List.of(count[0], count[1], both || name.equals("deserializeInternal") ? 2 : 0));
			});
			return writer.toByteArray();
		}
	}
}
