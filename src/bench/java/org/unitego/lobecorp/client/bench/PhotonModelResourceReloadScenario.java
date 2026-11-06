package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Frame;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/// 主测量前执行真实资源重载诊断；继承单次真实激光、OUTPUT 截图与 Spark 生命周期，不作性能 A/B。
public class PhotonModelResourceReloadScenario extends PhotonModelRenderScenario {
	public static final String ID = "lobecorp.photon-01-resource-reload";
	private static final String LABEL = "photon-01-resource-reload";
	private static final String MODEL_PATH = "models/photon/the_queen_of_hatred/beam/";
	private static final List<String> MODELS = List.of("laser_body", "dust_column", "reverse_muzzle", "source_dust");
	/// 与基类第一次预热相同的 tick 数；重载后另等稳定帧，避免把重载恢复纳入主采集。
	private static final int POST_RELOAD_WARMUP_TICKS = 100;
	/// 真实重载（含覆盖层退场）最多等待两分钟；不在客户端线程阻塞 future。
	private static final long RELOAD_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(120);
	private final PhotonBenchFocusAudit reloadFocusAudit = new PhotonBenchFocusAudit();
	private final List<ModelBefore> before = new ArrayList<>();
	private final JsonObject report = new JsonObject();
	private final JsonArray events = new JsonArray();
	private final JsonArray models = new JsonArray();
	private Throwable scenarioFailure;
	private boolean baseWarmupComplete;
	private boolean reloadVerified;
	private boolean diagnosticComplete;
	private CompletableFuture<Void> reload;
	private long reloadRequestedNs;
	private int postReloadTicks;

	private record Sample(Input input, Frame frame, String fingerprint) {
	}

	private record ModelBefore(PhotonModelOutputConfig first, PhotonModelOutputConfig second,
	                           PhotonModelPoseSampler sampler, PhotonModelSourceUtil.Loaded loaded,
	                           JsonObject source, List<Sample> samples, JsonObject evidence) {
	}

	public PhotonModelResourceReloadScenario() {
		super(LABEL, 0, 0, 16, 1, true, true);
		report.addProperty("scenario", ID);
		report.addProperty("diagnostic_before_measure", true);
		report.addProperty("strict_performance_ab", false);
		report.addProperty("measurement_scope", "inherited_single_real_cast_after_reload_and_second_warmup");
		report.addProperty("spark_owner", "PhotonModelRenderScenario");
		report.add("events", events);
		report.add("models", models);
	}

	@Override
	@SuppressWarnings("resource") // Minecraft 由 Bench 上下文借用，场景不关闭游戏宿主。
	public BenchClientStepResult warmup(BenchClientContext context) {
		if (scenarioFailure != null) {
			return failWithSpark(context, scenarioFailure);
		}

		if (!baseWarmupComplete) {
			var result = super.warmup(context);

			if (result != BenchClientStepResult.COMPLETE) {
				return result;
			}

			baseWarmupComplete = true;
			mark("base_warmup_complete");
		}

		try {
			reloadFocusAudit.audit(false, reloadVerified ? "resource_reload_rewarm" : "resource_reload_wait");

			if (reload == null) {
				captureBefore();
				reloadRequestedNs = System.nanoTime();
				mark("reload_requested");
				reload = context.minecraft().reloadResourcePacks();
				return BenchClientStepResult.CONTINUE;
			}

			if (!reloadVerified) {
				if (System.nanoTime() - reloadRequestedNs > RELOAD_TIMEOUT_NS) {
					throw new IllegalStateException("真实资源重载及覆盖层退场等待超时。");
				}

				if (!reload.isDone()) {
					return BenchClientStepResult.CONTINUE;
				}

				reload.join();

				if (context.minecraft().getOverlay() != null) {
					return BenchClientStepResult.CONTINUE;
				}

				mark("reload_completed");
				verifyAfter();
				reloadVerified = true;
				mark("reload_verified");
			}

			if (!diagnosticComplete) {
				if (++postReloadTicks < POST_RELOAD_WARMUP_TICKS
						|| !context.environment().isFrameStable(LobecorpBenchConfig.STABLE_FRAMES)) {
					return BenchClientStepResult.CONTINUE;
				}

				diagnosticComplete = true;
				mark("post_reload_warmup_complete");
			}

			return BenchClientStepResult.COMPLETE;
		} catch (IOException failure) {
			return failDiagnostic(context, new IllegalStateException("RESOURCE 重载诊断读取失败。", failure));
		} catch (RuntimeException | AssertionError failure) {
			return failDiagnostic(context, failure);
		}
	}

	private BenchClientStepResult failDiagnostic(BenchClientContext context, Throwable failure) {
		scenarioFailure = failure;
		report.addProperty("failure", failure.toString());
		mark("diagnostic_failed");
		return failWithSpark(context, failure);
	}

	private void captureBefore() throws IOException {
		mark("before_snapshot_started");

		for (var name : MODELS) {
			var first = config(name);
			var second = config(name);
			second.width = 0.5f;
			second.opacity = 0.25f;
			var evidence = new JsonObject();
			evidence.addProperty("resource", Objects.requireNonNull(first.resource).toString());
			models.add(evidence);
			var sampler = PhotonModelSourceUtil.prepare(first);
			var consumer = PhotonModelSourceUtil.prepare(second);
			evidence.addProperty("before_sampler_identity", System.identityHashCode(sampler));
			evidence.addProperty("before_consumer_identity", System.identityHashCode(consumer));
			require(sampler == consumer, name + " 不同 config 没有共享 RESOURCE sampler。");
			var loaded = PhotonModelSourceUtil.load(first);
			require(loaded.definition().equals(sampler.prepared().definition()), name + " 准备定义与实际加载不同。");
			var source = loaded.definition().source();
			evidence.add("source_json_before", source.deepCopy());
			evidence.addProperty("resource_sha256_before", loaded.sha256());
			evidence.addProperty("same_source_shared_before", true);
			var samples = samples(sampler, consumer);
			evidence.add("samples_before", sampleEvidence(samples));
			before.add(new ModelBefore(first, second, sampler, loaded, source, samples, evidence));
		}

		mark("before_snapshot_complete");
	}

	private void verifyAfter() throws IOException {
		for (var model : before) {
			var resource = Objects.requireNonNull(model.first().resource).toString();
			var sampler = PhotonModelSourceUtil.prepare(model.first());
			var consumer = PhotonModelSourceUtil.prepare(model.second());
			var evidence = model.evidence();
			evidence.addProperty("after_sampler_identity", System.identityHashCode(sampler));
			evidence.addProperty("after_consumer_identity", System.identityHashCode(consumer));
			require(sampler != model.sampler(), resource + " 真实重载后仍返回旧 sampler。");
			require(sampler == consumer, resource + " 重载后两个消费者没有共享新 sampler。");
			var loaded = PhotonModelSourceUtil.load(model.first());
			evidence.addProperty("resource_sha256_after", loaded.sha256());
			evidence.add("source_json_after", loaded.definition().source());
			require(model.loaded().sha256().equals(loaded.sha256()), resource + " 原资源组合指纹改变。");
			require(model.loaded().snapshot().equals(loaded.snapshot()), resource + " 原始快照改变。");
			require(model.source().equals(loaded.definition().source()), resource + " 原 JSON 改变。");
			require(model.loaded().definition().equals(loaded.definition()), resource + " 完整模型定义改变。");
			require(model.loaded().definition().equals(sampler.prepared().definition()), resource + " 新准备定义改变。");
			require(model.loaded().definition().equals(model.sampler().prepared().definition()), resource + " 旧定义被重载改写。");

			for (var sample : model.samples()) {
				require(sample.fingerprint().equals(fingerprint(sample.frame())), resource + " 已保存采样帧被改写。");
				assertFrame(sample.frame(), sampler.sample(sample.input(), false));
			}

			var afterSamples = samples(sampler, consumer);
			evidence.add("samples_after", sampleEvidence(afterSamples));
			require(model.source().equals(model.sampler().prepared().definition().source()), resource + " 采样修改原 JSON。");
			require(model.source().equals(sampler.prepared().definition().source()), resource + " 新采样修改原 JSON。");
			evidence.addProperty("sampler_replaced", true);
			evidence.addProperty("same_source_shared_after", true);
			evidence.addProperty("definition_snapshot_fingerprint_equal", true);
			evidence.addProperty("same_input_frames_equal", true);
			evidence.addProperty("consumer_frames_independent", true);
		}
	}

	private static List<Sample> samples(PhotonModelPoseSampler first, PhotonModelPoseSampler second) {
		var definition = first.prepared().definition();
		var inputs = new ArrayList<Input>();
		inputs.add(input(definition, "", 0));

		for (var clip : definition.clips()) {
			inputs.add(input(definition, clip.id(), 0));
			inputs.add(input(definition, clip.id(), clip.duration() * 0.5f));
		}

		var saved = new ArrayList<Sample>();

		for (var input : inputs) {
			var a = first.sample(input, false);
			var aHash = fingerprint(a);
			var different = new Input(input.time() + 1, input.clip(), input.loop(), input.length() * 1.5f,
					input.width() * 0.5f, Anchor.END, 0.25f, Map.of());
			var b = second.sample(different, false);
			var bHash = fingerprint(b);
			require(!aHash.equals(bHash), "不同消费者 Input 没有产生可区分的帧。");
			require(aHash.equals(fingerprint(a)), "消费者 B 改写了消费者 A 的帧。");
			assertFrame(a, first.sample(input, false));
			require(bHash.equals(fingerprint(b)), "消费者 A 再次采样改写了消费者 B 的帧。");
			assertFrame(b, second.sample(different, false));
			require(aHash.equals(fingerprint(a)), "消费者 B 再次采样改写了消费者 A 的帧。");
			saved.add(new Sample(input, a, aHash));
			saved.add(new Sample(different, b, bHash));
		}

		// 连续切换所有片段后再次复核，覆盖跨片段 scratch 复用。
		for (var sample : saved) {
			require(sample.fingerprint().equals(fingerprint(sample.frame())), "后续片段采样改写已保存帧。");
		}

		return List.copyOf(saved);
	}

	private static Input input(PhotonModelDefinition definition, String clip, float time) {
		return new Input(time, clip, true, definition.referenceLength(), definition.referenceWidth(),
				Anchor.START, 1, Map.of());
	}

	private static PhotonModelOutputConfig config(String name) {
		var config = new PhotonModelOutputConfig();
		config.source = PhotonModelOutputConfig.Source.RESOURCE;
		config.resource = Identifier.fromNamespaceAndPath("lobecorp", MODEL_PATH + name + ".obj");
		return config;
	}

	private static void assertFrame(Frame expected, Frame actual) {
		require(expected.groups().equals(actual.groups()) && Float.compare(expected.opacity(), actual.opacity()) == 0
						&& expected.min().equals(actual.min()) && expected.max().equals(actual.max()),
				"同一 Input 的顶点、法线、UV、分组透明度或边界不等价。");
	}

	private static String fingerprint(Frame frame) {
		var value = frame.groups() + "|" + frame.opacity() + "|" + frame.min() + "|" + frame.max();

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static JsonArray sampleEvidence(List<Sample> samples) {
		var result = new JsonArray();

		for (var sample : samples) {
			var item = new JsonObject();
			item.addProperty("input", sample.input().toString());
			item.addProperty("frame_sha256", sample.fingerprint());
			result.add(item);
		}

		return result;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private void mark(String event) {
		var item = new JsonObject();
		item.addProperty("event", event);
		item.addProperty("observed_ns", System.nanoTime());
		events.add(item);
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		try {
			require(diagnosticComplete && before.size() == MODELS.size(), "资源重载诊断未完整结束。");
			super.verify(context);
		} catch (Exception | AssertionError failure) {
			scenarioFailure = failure;
			throw failure;
		} finally {
			archiveReport(context, "verify");
		}
	}

	@Override
	public void teardown(BenchClientContext context) {
		try {
			super.teardown(context);
		} catch (RuntimeException | Error failure) {
			if (scenarioFailure != null && scenarioFailure != failure) {
				scenarioFailure.addSuppressed(failure);
			} else {
				scenarioFailure = failure;
				throw failure;
			}
		} finally {
			archiveReport(context, "teardown");
		}
	}

	private void archiveReport(BenchClientContext context, String phase) {
		try {
			mark(phase);
			report.addProperty("archive_phase", phase);
			report.addProperty("base_warmup_complete", baseWarmupComplete);
			report.addProperty("reload_future_done", reload != null && reload.isDone());
			report.addProperty("reload_verified", reloadVerified);
			report.addProperty("diagnostic_complete", diagnosticComplete);
			report.addProperty("post_reload_warmup_ticks", postReloadTicks);
			report.add("reload_focus_audit", reloadFocusAudit.snapshot());

			if (scenarioFailure != null) {
				report.addProperty("failure", scenarioFailure.toString());
			}

			var file = phase.equals("verify") ? LABEL + "-verify.json" : LABEL + ".json";
			context.artifacts().write(file, "application/json", report.toString());
		} catch (Exception failure) {
			if (scenarioFailure != null) {
				scenarioFailure.addSuppressed(failure);
			} else {
				var archiveFailure = new IllegalStateException("资源重载诊断 JSON 归档失败。", failure);
				scenarioFailure = archiveFailure;
				throw archiveFailure;
			}
		}
	}
}
