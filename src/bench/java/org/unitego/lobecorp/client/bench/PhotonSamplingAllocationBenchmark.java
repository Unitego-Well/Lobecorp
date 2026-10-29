package org.unitego.lobecorp.client.bench;

import com.sun.management.ThreadMXBean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector2;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/// 同进程纯 CPU 采样对照；输出数值校验、当前线程分配和指定直接工作区保留量。
public class PhotonSamplingAllocationBenchmark {
	private static final String SAMPLER = "org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler";
	private static final String BASE = "build/agent-backups/20261029-first-optimization";
	private static final String BASELINE_SHA = "d40ca49fa15d66ac8af4c476cd659f16d9d57867872730319e0a54fec0ec370d";
	private static final List<String> FIXTURES = List.of("dust_column.json", "laser_body.json",
			"reverse_muzzle.json", "source_dust.json");
	private static final List<String> WORKSPACE_FIELDS = List.of("pivot", "local", "pivoted",
			"parts", "boneWorld", "boneSkin", "morphWeights");
	private static final List<String> POSITION_FIELDS = List.of("positionValues", "positionEpochs");
	private static volatile Object consumed;

	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			throw new IllegalArgumentException("args: projectRoot outputDirectory warmupCycles measurementCycles rounds");
		}

		var root = Path.of(args[0]).toRealPath();
		var output = Path.of(args[1]).toAbsolutePath().normalize();
		var micro = root.resolve(BASE + "/micro").toRealPath();
		if (!output.startsWith(micro) || output.equals(micro)
				|| !output.getParent().toRealPath().startsWith(micro)) {
			throw new IllegalArgumentException("输出必须是 micro 内尚不存在的直接运行目录");
		}

		int warmup = positive(args[2]);
		int cycles = positive(args[3]);
		int rounds = positive(args[4]);
		require(rounds >= 2, "交错测量至少两轮");
		var bean = allocationBean();
		var sizeMethod = Class.forName("org.unitego.lobecorp.client.bench.agent.WorkspaceSizeAgent")
				.getMethod("getObjectSize", Object.class);
		var oldJar = root.resolve(BASE + "/baseline/sampler-before.jar").toRealPath();
		require(sha(Files.readAllBytes(oldJar)).equals(BASELINE_SHA), "旧二进制 SHA 不符");
		var current = root.resolve("build/classes/java/main").toRealPath();
		Files.createDirectory(output);

		try (var report = new PrintWriter(Files.newBufferedWriter(output.resolve("report.txt")));
		     var inputs = new PrintWriter(Files.newBufferedWriter(output.resolve("inputs.tsv")));
		     var hashes = new PrintWriter(Files.newBufferedWriter(output.resolve("equivalence.tsv")));
		     var allocations = new PrintWriter(Files.newBufferedWriter(output.resolve("allocations.csv")));
		     var oldLoader = new SamplerLoader(oldJar.toUri().toURL());
		     var newLoader = new SamplerLoader(current.toUri().toURL())) {
			report.println("java=" + System.getProperty("java.runtime.version"));
			report.println("vm=" + System.getProperty("java.vm.name"));
			report.println("vmArgs=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
			report.println("thread=" + Thread.currentThread().threadId());
			report.println("allocationSupported=true enabled=" + bean.isThreadAllocatedMemoryEnabled());
			report.println("baseline=" + oldJar + " sha256=" + BASELINE_SHA);
			report.println("current=" + current);
			report.println("warmupCycles=" + warmup + " measurementCycles=" + cycles + " rounds=" + rounds);
			report.println("measure=false; reflection + volatile consumption included; no overhead subtraction");
			report.println("仅离线微基准；不是游戏 FPS、整个 FX 加载、总堆、资源缓存、GPU 或 ThreadLocalMap 大小");
			inputs.println("fixture\tindex\ttime\tclip\tloop\tlength\twidth\tanchor\topacity\tmorphWeights\tpartOverrides\tboneOverrides");
			hashes.println("fixture\tinput\tsha256\tbytes\tattributesEqual");
			allocations.println("fixture,round,order,side,samples,totalBytes,bytesPerSample,elapsedNs,nsPerSample");
			fingerprintClasses(current, report);
			verifyFixtureInventory(root);
			var cases = prepareCases(root, oldLoader, newLoader, report, inputs, hashes);
			report.println("equivalence=PASS (raw float bits, ordered complete attributes; retained frames checked)");
			report.flush();
			inputs.flush();
			hashes.flush();

			// 先预热全部模型，两侧使用相同序列；记录和散列不进入测量区间。
			for (var model : cases) {
				for (int i = 0; i < warmup; i++) {
					if ((i & 1) == 0) {
						cycle(model.oldSide, 1);
						cycle(model.newSide, 1);
					} else {
						cycle(model.newSide, 1);
						cycle(model.oldSide, 1);
					}
				}
			}

			for (int round = 0; round < rounds; round++) {
				for (var model : cases) {
					if ((round & 1) == 0) {
						measure(model.name, round, 0, "old", model.oldSide, cycles, bean, allocations);
						measure(model.name, round, 1, "new", model.newSide, cycles, bean, allocations);
					} else {
						measure(model.name, round, 0, "new", model.newSide, cycles, bean, allocations);
						measure(model.name, round, 1, "old", model.oldSide, cycles, bean, allocations);
					}
				}
				allocations.flush();
			}

			for (var model : cases) {
				report.println("totals fixture=" + model.name);
				totals("old", model.oldSide, report);
				totals("new", model.newSide, report);
				retainedMemory(model, sizeMethod, report);
				require(sha(Files.readAllBytes(root.resolve("src/test/resources/photon-model").resolve(model.name)))
						.equals(model.fixtureSha), "fixture SHA 运行期间改变");
				checkRetained(model.oldSide);
				checkRetained(model.newSide);
			}
			verifyFixtureInventory(root);
			report.println("consumedFrameType=" + consumed.getClass().getName());
			report.println("status=COMPLETE; 验证序列的相邻帧及跨预热/测量保留的首帧复核通过；fixture 未写入");
			require(!report.checkError() && !inputs.checkError() && !hashes.checkError()
					&& !allocations.checkError(), "报告写入失败");
		} finally {
			consumed = null;
		}

		System.out.println("Benchmark reports: " + output);
	}

	private static List<ModelCase> prepareCases(Path root, SamplerLoader oldLoader, SamplerLoader newLoader,
	                                            PrintWriter report, PrintWriter inputs, PrintWriter hashes) throws Exception {
		var cases = new ArrayList<ModelCase>();

		for (var name : FIXTURES) {
			var bytes = Files.readAllBytes(root.resolve("src/test/resources/photon-model").resolve(name));
			report.println("fixture=" + name + " sha256=" + sha(bytes) + " bytes=" + bytes.length);
			var definition = PhotonModelCodec.read(new String(bytes, StandardCharsets.UTF_8));
			var prepared = PhotonModelTopologyUtil.prepare(definition);
			var specs = specifications(definition);
			var oldSide = new Side(oldLoader, prepared, specs);
			var newSide = new Side(newLoader, prepared, specs);
			var model = new ModelCase(name, sha(bytes), prepared, specs, oldSide, newSide);
			cases.add(model);
			report.println("model=" + name + " vertices=" + definition.vertices().size()
					+ " groups=" + prepared.groups().size() + " parts=" + definition.parts().size()
					+ " bones=" + definition.bones().size() + " morphs=" + definition.morphs().size()
					+ " inputCount=" + specs.size());
			for (int i = 0; i < specs.size(); i++) {
				var s = specs.get(i);
				inputs.println(name + "\t" + i + "\t" + s.time + "\t" + s.clip + "\t" + s.loop
						+ "\t" + s.length + "\t" + s.width + "\t" + s.anchor + "\t" + s.opacity
						+ "\t{}\t{}\t{}");
			}
			validate(model, hashes);
		}

		return List.copyOf(cases);
	}

	private static int positive(String value) {
		int result = Integer.parseInt(value);
		require(result > 0, "cycles/rounds 必须大于零");
		return result;
	}

	private static ThreadMXBean allocationBean() {
		if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)) {
			throw new IllegalStateException("ThreadMXBean 分配统计不可用");
		}

		require(bean.isThreadAllocatedMemorySupported(), "线程分配统计不受支持");
		if (!bean.isThreadAllocatedMemoryEnabled()) {
			bean.setThreadAllocatedMemoryEnabled(true);
		}
		require(bean.isThreadAllocatedMemoryEnabled()
				&& bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) >= 0, "线程分配统计不可读取");
		return bean;
	}

	private static void verifyFixtureInventory(Path root) throws Exception {
		try (var paths = Files.list(root.resolve("src/test/resources/photon-model"))) {
			var found = paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).sorted().toList();
			require(found.equals(FIXTURES), "fixture 实际清单不等于四个指定模型：" + found);
		}
	}

	private static void fingerprintClasses(Path current, PrintWriter report) throws Exception {
		var folder = current.resolve(SAMPLER.substring(0, SAMPLER.lastIndexOf('.')).replace('.', '/'));
		try (var files = Files.list(folder)) {
			var selected = files.filter(path -> {
				var name = path.getFileName().toString();
				return name.equals("PhotonModelPoseSampler.class")
						|| name.startsWith("PhotonModelPoseSampler$") && name.endsWith(".class");
			}).sorted().toList();
			require(!selected.isEmpty(), "当前 sampler 尚未编译");
			for (var file : selected) {
				report.println("currentClass=" + file.getFileName() + " sha256=" + sha(Files.readAllBytes(file))
						+ " modified=" + Files.getLastModifiedTime(file));
			}
		}
	}

	private record Spec(float time, String clip, boolean loop, float length, float width, Anchor anchor,
	                    float opacity) {
	}

	private static List<Spec> specifications(PhotonModelDefinition definition) {
		var result = new ArrayList<Spec>();
		addDimensions(result, 0, "", false, definition);

		for (var clip : definition.clips()) {
			var times = new TreeSet<>(List.of(0f, clip.duration() * 0.125f, clip.duration() * 0.5f,
					clip.duration(), clip.duration() * 1.25f));
			clip.partTracks().values().forEach(keys -> keys.forEach(key -> times.add(key.time())));
			clip.boneTracks().values().forEach(keys -> keys.forEach(key -> times.add(key.time())));
			clip.morphTracks().values().forEach(keys -> keys.forEach(key -> times.add(key.time())));
			clip.opacityKeys().forEach(key -> times.add(key.time()));
			for (float time : times) {
				addDimensions(result, time, clip.id(), false, definition);
				addDimensions(result, time, clip.id(), true, definition);
			}
		}

		return List.copyOf(result);
	}

	private static void addDimensions(List<Spec> result, float time, String clip, boolean loop, PhotonModelDefinition d) {
		result.add(new Spec(time, clip, loop, d.referenceLength(), d.referenceWidth(), Anchor.NONE, 1));
		result.add(new Spec(time, clip, loop, d.referenceLength() * 1.75f, d.referenceWidth() * 0.65f, Anchor.START, 0.37f));
		result.add(new Spec(time, clip, loop, d.referenceLength() * 0.4f, d.referenceWidth() * 1.5f, Anchor.END, 0));
		result.add(new Spec(time, clip, loop, 0, 0, Anchor.NONE, 1));
	}

	private record ModelCase(String name, String fixtureSha, PhotonModelTopologyUtil.Prepared prepared,
	                         List<Spec> specs,
	                         Side oldSide, Side newSide) {
	}

	private static class SamplerLoader extends URLClassLoader {
		private SamplerLoader(URL source) {
			super(new URL[]{source}, PhotonSamplingAllocationBenchmark.class.getClassLoader());
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.equals(SAMPLER) && !name.startsWith(SAMPLER + "$")) {
				return super.loadClass(name, resolve);
			}

			synchronized (getClassLoadingLock(name)) {
				var type = findLoadedClass(name);
				if (type == null) {
					// 缺少旧内部类时直接失败，禁止回退混入父加载器的新实现。
					type = findClass(name);
				}
				if (resolve) {
					resolveClass(type);
				}
				return type;
			}
		}
	}

	private static class Side {
		private final Object sampler;
		private final Method sample;
		private final Object[][] arguments;
		private final PhotonModelTopologyUtil.Prepared prepared;
		private Object retained;
		private byte[] retainedSnapshot;
		private long samples;
		private long measuredBytes;
		private long measuredNanos;
		private long measuredSamples;

		private Side(SamplerLoader loader, PhotonModelTopologyUtil.Prepared prepared, List<Spec> specs) throws Exception {
			var type = loader.loadClass(SAMPLER);
			var input = loader.loadClass(SAMPLER + "$Input");
			require(type.getClassLoader() == loader && input.getClassLoader() == loader, "加载器隔离失败");
			this.prepared = prepared;
			sampler = type.getConstructor(PhotonModelTopologyUtil.Prepared.class).newInstance(prepared);
			sample = type.getMethod("sample", input, boolean.class);
			Constructor<?> constructor = input.getConstructor(float.class, String.class, boolean.class, float.class,
					float.class, Anchor.class, float.class, Map.class);
			arguments = new Object[specs.size()][];
			for (int i = 0; i < specs.size(); i++) {
				var s = specs.get(i);
				var value = constructor.newInstance(s.time, s.clip, s.loop, s.length, s.width, s.anchor, s.opacity, Map.of());
				arguments[i] = new Object[]{value, false};
			}
		}

		private Object sample(int index) throws Exception {
			var frame = sample.invoke(sampler, arguments[index]);
			samples++;
			consumed = frame;
			return frame;
		}
	}

	private static void validate(ModelCase model, PrintWriter hashes) throws Exception {
		Object previousOld = null;
		Object previousNew = null;
		byte[] previousSnapshot = null;
		for (int i = 0; i < model.specs.size(); i++) {
			var oldFrame = model.oldSide.sample(i);
			var newFrame = model.newSide.sample(i);
			var oldBytes = snapshot(oldFrame, model.prepared);
			var newBytes = snapshot(newFrame, model.prepared);
			require(Arrays.equals(oldBytes, newBytes), "属性不等价 " + model.name + " input=" + i
					+ " firstDifferentByte=" + Arrays.mismatch(oldBytes, newBytes));
			hashes.println(model.name + "\t" + i + "\t" + sha(oldBytes) + "\t" + oldBytes.length + "\ttrue");
			if (previousOld != null) {
				require(Arrays.equals(previousSnapshot, snapshot(previousOld, model.prepared)), "旧帧被后次调用污染");
				require(Arrays.equals(previousSnapshot, snapshot(previousNew, model.prepared)), "新帧被后次调用污染");
			}
			if (i == 0) {
				model.oldSide.retained = oldFrame;
				model.newSide.retained = newFrame;
				model.oldSide.retainedSnapshot = oldBytes;
				model.newSide.retainedSnapshot = newBytes;
			}
			previousOld = oldFrame;
			previousNew = newFrame;
			previousSnapshot = oldBytes;
		}
		model.oldSide.sample(0);
		model.newSide.sample(0);
		require(Arrays.equals(previousSnapshot, snapshot(previousOld, model.prepared)), "旧末帧被后次调用污染");
		require(Arrays.equals(previousSnapshot, snapshot(previousNew, model.prepared)), "新末帧被后次调用污染");
		checkRetained(model.oldSide);
		checkRetained(model.newSide);
	}

	private static void checkRetained(Side side) throws Exception {
		require(Arrays.equals(side.retainedSnapshot, snapshot(side.retained, side.prepared)), "长期保留帧发生变化");
	}

	private static Object property(Object value, String name) throws Exception {
		return value.getClass().getMethod(name).invoke(value);
	}

	private static byte[] snapshot(Object frame, PhotonModelTopologyUtil.Prepared prepared) throws Exception {
		require(property(frame, "prepared") == prepared, "Prepared 未共享");
		require(property(frame, "timings") == null, "measure=false 却返回 timings");
		var bytes = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(bytes)) {
			number(out, property(frame, "opacity"));
			vector(out, property(frame, "min"));
			vector(out, property(frame, "max"));
			var groups = (List<?>) property(frame, "groups");
			require(groups.size() == prepared.groups().size(), "组数改变");
			out.writeInt(groups.size());
			for (int i = 0; i < groups.size(); i++) {
				var group = groups.get(i);
				var topology = prepared.groups().get(i);
				out.writeInt(i);
				out.writeInt(topology.part());
				out.writeUTF(topology.material());
				number(out, property(group, "opacity"));
				var vertices = (List<?>) property(group, "vertices");
				require(vertices.size() == topology.corners().size(), "组顶点数改变");
				out.writeInt(vertices.size());
				for (var vertex : vertices) {
					vector(out, property(vertex, "position"));
					vector(out, property(vertex, "normal"));
					var uv = (Vector2) property(vertex, "uv");
					number(out, uv.x());
					number(out, uv.y());
				}
			}
		}
		return bytes.toByteArray();
	}

	private static void vector(DataOutputStream out, Object value) throws Exception {
		var v = (Vector3) value;
		number(out, v.x());
		number(out, v.y());
		number(out, v.z());
	}

	private static void number(DataOutputStream out, Object number) throws Exception {
		float value = ((Number) number).floatValue();
		require(Float.isFinite(value), "输出包含非有限数值");
		out.writeInt(Float.floatToRawIntBits(value));
	}

	private static void cycle(Side side, int cycles) throws Exception {
		for (int cycle = 0; cycle < cycles; cycle++) {
			for (int index = 0; index < side.arguments.length; index++) {
				side.sample(index);
			}
		}
	}

	private static void measure(String fixture, int round, int order, String label, Side side, int cycles,
	                            ThreadMXBean bean, PrintWriter out) throws Exception {
		long thread = Thread.currentThread().threadId();
		long before = bean.getThreadAllocatedBytes(thread);
		long start = System.nanoTime();
		cycle(side, cycles);
		long elapsed = System.nanoTime() - start;
		long after = bean.getThreadAllocatedBytes(thread);
		require(before >= 0 && after >= before, "分配计数不可用或倒退");
		long count = Math.multiplyExact((long) cycles, side.arguments.length);
		long allocated = after - before;
		side.measuredBytes += allocated;
		side.measuredNanos += elapsed;
		side.measuredSamples += count;
		out.println(fixture + "," + round + "," + order + "," + label + "," + count + "," + allocated
				+ "," + (double) allocated / count + "," + elapsed + "," + (double) elapsed / count);
	}

	private static void totals(String label, Side side, PrintWriter report) {
		report.println(label + " samples=" + side.measuredSamples + " totalBytes=" + side.measuredBytes
				+ " bytesPerSample=" + (double) side.measuredBytes / side.measuredSamples
				+ " elapsedNs=" + side.measuredNanos + " nsPerSample=" + (double) side.measuredNanos / side.measuredSamples);
	}

	private record Footprint(long bytes, int objects, List<String> unknown) {
		private String display() {
			return (unknown.isEmpty() ? Long.toString(bytes) : "unknown; knownBytes=" + bytes)
					+ " objects=" + objects + " unknown=" + unknown;
		}
	}

	private static void retainedMemory(ModelCase model, Method size, PrintWriter report) throws Exception {
		var oldScratch = scratch(model.oldSide, report);
		var newScratch = scratch(model.newSide, report);
		report.println("memory fixture=" + model.name);
		if (oldScratch == null || newScratch == null) {
			report.println("scratchShallow / selectedWorkspaceDelta=unknown");
			return;
		}

		long oldShallow = (Long) size.invoke(null, oldScratch);
		long newShallow = (Long) size.invoke(null, newScratch);
		var oldWork = workspace(oldScratch, WORKSPACE_FIELDS, size, false);
		var newWork = workspace(newScratch, WORKSPACE_FIELDS, size, true);
		report.println("scratchShallow old=" + oldShallow + " new=" + newShallow + " delta=" + (newShallow - oldShallow));
		report.println("selectedWorkspace old=" + oldWork.display() + " new=" + newWork.display());
		if (oldWork.unknown.isEmpty() && newWork.unknown.isEmpty()) {
			long delta = newWork.bytes - oldWork.bytes;
			report.println("selectedWorkspaceDelta=" + delta + " includingScratchShallowDelta="
					+ (delta + newShallow - oldShallow));
		} else {
			report.println("selectedWorkspaceDelta=unknown");
		}
		report.println("positionCacheSeparate old=" + workspace(oldScratch, POSITION_FIELDS, size, false).display()
				+ " new=" + workspace(newScratch, POSITION_FIELDS, size, true).display());
	}

	private static Object scratch(Side side, PrintWriter report) {
		require(side.samples > 0, "未采样，禁止初始化 scratch");
		try {
			var field = side.sampler.getClass().getDeclaredField("scratch");
			if (!field.trySetAccessible()) {
				report.println("scratch=unknown: inaccessible");
				return null;
			}
			// 已在本线程成功完成采样，get 仅取得已有槽位；不创建或遍历其他线程。
			return ((ThreadLocal<?>) field.get(side.sampler)).get();
		} catch (ReflectiveOperationException | RuntimeException e) {
			report.println("scratch=unknown: " + e);
			return null;
		}
	}

	private static Footprint workspace(Object scratch, List<String> names, Method size, boolean required) {
		var seen = new IdentityHashMap<Object, Boolean>();
		var unknown = new ArrayList<String>();
		long bytes = 0;
		for (var name : names) {
			try {
				var field = scratch.getClass().getDeclaredField(name);
				if (Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible()) {
					unknown.add(name + ": inaccessible/static");
					continue;
				}
				bytes += directSize(field.get(scratch), size, seen, unknown);
			} catch (NoSuchFieldException e) {
				if (required) {
					unknown.add(name + ": absent");
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				unknown.add(name + ": " + e);
			}
		}
		return new Footprint(bytes, seen.size(), List.copyOf(unknown));
	}

	private static long directSize(Object value, Method size, IdentityHashMap<Object, Boolean> seen,
	                               List<String> unknown) throws ReflectiveOperationException {
		if (value == null || seen.put(value, Boolean.TRUE) != null) {
			return 0;
		}

		var type = value.getClass();
		long bytes = (Long) size.invoke(null, value);
		if (type.isArray()) {
			if (!type.getComponentType().isPrimitive()) {
				for (int i = 0; i < Array.getLength(value); i++) {
					bytes += directSize(Array.get(value, i), size, seen, unknown);
				}
			}
		} else if (value instanceof Matrix4f || value instanceof Vector3f || value instanceof Vector3) {
			for (var field : type.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
					unknown.add(type.getName() + "." + field.getName() + ": 非直接基本类型字段");
				}
			}
		} else {
			unknown.add(type.getName() + ": 未知直接工作对象，仅记录 shallow");
		}
		return bytes;
	}

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
