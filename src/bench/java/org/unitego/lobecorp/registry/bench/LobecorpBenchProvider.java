package org.unitego.lobecorp.registry.bench;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;

import org.unitego.lobecorp.client.bench.PhotonLogicRegressionScenario;
import org.unitego.lobecorp.client.bench.PhotonWorkspaceContractScenario;
import org.unitego.lobecorp.client.bench.PhotonBridgeContractScenario;
import org.unitego.lobecorp.client.bench.PhotonMigrationLaserScenario;
import org.unitego.lobecorp.client.bench.PhotonWorkspaceReviewScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridQueenScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridLaserScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridBlankScenario;
import org.unitego.lobecorp.client.bench.PhotonQueenEntryScenario;
import org.unitego.lobecorp.client.bench.PhotonEntryBlankScenario;
import org.unitego.lobecorp.client.bench.SweeperRenderScenario;
import org.unitego.lobecorp.client.bench.PhotonModelRenderScenario;
import org.unitego.lobecorp.client.bench.PhotonNativeControlScenario;
import org.unitego.lobecorp.client.bench.PhotonRepeatedEffectScenario;
import org.unitego.lobecorp.client.bench.PhotonModelResourceReloadScenario;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.world.entity.bench.SweeperAiScenario;

import java.util.Set;
import java.time.Duration;

public class LobecorpBenchProvider implements BenchServerProvider, BenchClientProvider {
	@Override
	public String id() {
		return LobecorpBenchConfig.PROVIDER_ID;
	}

	@Override
	public BenchCompatibility compatibility() {
		return BenchApiVersion.currentCompatibility();
	}

	@Override
	public void registerServer(BenchServerRegistrar registrar) {
		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.SERVER_SCENARIO, "Sweeper AI workload",
						Set.of("server", "sweeper", "ai"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new SweeperAiScenario());
	}

	@Override
	public void registerClient(BenchClientRegistrar registrar) {
		var selectedProperty = System.getProperty("modBench.scenarios", "");
		if (PhotonBridgeContractScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonBridgeContractScenario.ID, "标准MCP导出与关闭契约",
					Set.of("client", "photon", "bridge", "logic"), Duration.ofSeconds(600)), _ -> new PhotonBridgeContractScenario());
			return;
		}
		if (PhotonWorkspaceContractScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonWorkspaceContractScenario.ID, "组合状态、依赖与冻结回读契约",
					Set.of("client", "photon", "workspace", "logic"), Duration.ofSeconds(600)), _ -> new PhotonWorkspaceContractScenario());
			return;
		}
		if (PhotonLogicRegressionScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonLogicRegressionScenario.ID, "Photon独立逻辑回归",
					Set.of("client", "photon", "logic"), Duration.ofSeconds(360)), _ -> new PhotonLogicRegressionScenario());
			return;
		}
		if (PhotonWorkspaceReviewScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonWorkspaceReviewScenario.ID, "工作区审查：自然排空、子池退休与文件导出",
					Set.of("client", "photon", "review", "lifecycle"), Duration.ofSeconds(240)), _ -> new PhotonWorkspaceReviewScenario());
		}
		if (PhotonMigrationLaserScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonMigrationLaserScenario.ID, "迁移回归：45秒三次真实激光",
					Set.of("client", "photon", "migration", "laser"), Duration.ofSeconds(360)), _ -> new PhotonMigrationLaserScenario());
		}
		if (PhotonHybridQueenScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonHybridQueenScenario.ID, "混合：进入后25秒单女皇",
					Set.of("client", "photon", "hybrid", "entry"), Duration.ofSeconds(240)), _ -> new PhotonHybridQueenScenario());
		}
		if (PhotonHybridLaserScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonHybridLaserScenario.ID, "混合：45秒单女皇三次真实激光",
					Set.of("client", "photon", "hybrid", "laser"), Duration.ofSeconds(240)), _ -> new PhotonHybridLaserScenario());
		}
		if (PhotonHybridBlankScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonHybridBlankScenario.ID, "混合：45秒空白，含前25秒基线",
					Set.of("client", "photon", "hybrid", "blank"), Duration.ofSeconds(240)), _ -> new PhotonHybridBlankScenario());
		}

		if (PhotonQueenEntryScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonQueenEntryScenario.ID,
							"进入世界首帧起25秒，第5秒添加静止女皇，无施法无靶",
							Set.of("client", "photon", "queen", "entry"), Duration.ofSeconds(180)),
					_ -> new PhotonQueenEntryScenario());
		}

		if (PhotonEntryBlankScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonEntryBlankScenario.ID,
							"进入世界首帧起25秒空白对照，不添加女皇",
							Set.of("client", "photon", "blank", "entry"), Duration.ofSeconds(180)),
					_ -> new PhotonEntryBlankScenario());
		}

		if (LobecorpBenchConfig.PHOTON_REPEAT_PERFORMANCE_SCENARIO.equals(System.getProperty("modBench.scenarios", ""))) {
			registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.PHOTON_REPEAT_PERFORMANCE_SCENARIO,
							"三次特效，开始间隔五秒，共用四十秒窗口，无截图",
							Set.of("client", "photon", "repeat", "performance"), Duration.ofSeconds(300)),
					_ -> new PhotonRepeatedEffectScenario());
		}

		if (LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO.equals(System.getProperty("modBench.scenarios", ""))) {
			registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO,
							"首次释放性能采集：20秒单次真实施放，无截图验收",
							Set.of("client", "photon", "first-cast", "performance"), Duration.ofSeconds(120)),
					_ -> System.getProperty(LobecorpBenchConfig.PHOTON_CONTROL_FX_PROPERTY, "").isBlank()
							? new PhotonModelRenderScenario("photon-02-first-cast-performance", 0, 0, 16, 1, true, true,
							PhotonModelRenderScenario.EvidenceMode.PERFORMANCE)
							: new PhotonNativeControlScenario());
		}

		if (System.getProperty("modBench.scenarios", "").contains(PhotonModelResourceReloadScenario.ID)) {
			registrar.register(new ScenarioDescriptor(PhotonModelResourceReloadScenario.ID, "资源重载诊断：单次真实激光，非性能 A/B",
							Set.of("client", "photon", "resource-reload"), Duration.ofSeconds(240)),
					_ -> new PhotonModelResourceReloadScenario());
		}

		if (System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-")) {
			registrar.register(new ScenarioDescriptor("lobecorp.photon-00-first-cast", "首次释放诊断：只施放一次",
							Set.of("client", "photon", "first-cast"), Duration.ofSeconds(120)),
					_ -> new PhotonModelRenderScenario("photon-00-first-cast", 0, 0, 16, 1, true, true));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-00-empty", "模型诊断：00-empty",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-00-empty", 0, false, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-10-static-one", "模型诊断：10-static-one",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-10-static-one", 1, false, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-20-animated-one", "模型诊断：20-animated-one",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-20-animated-one", 1, true, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-30-static-sixteen", "模型诊断：30-static-sixteen",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-30-static-sixteen", 16, false, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-40-animated-sixteen", "模型诊断：40-animated-sixteen",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-40-animated-sixteen", 16, true, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-50-cold-sixteen", "模型诊断：50-cold-sixteen",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-50-cold-sixteen", 16, true, true));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-60-static-repeat", "模型诊断：60-static-repeat",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-60-static-repeat", 16, false, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-70-empty-repeat", "模型诊断：70-empty-repeat",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-70-empty-repeat", 0, false, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-80-static-thirtytwo", "模型诊断：32静态",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-80-static-thirtytwo", 32, 0, 16, 0, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-90-animated-thirtytwo", "模型诊断：32动画",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-90-animated-thirtytwo", 0, 32, 16, 0, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-91-mixed-thirtytwo-laser", "极端诊断：E2",
							Set.of("client", "photon", "extreme"), Duration.ofSeconds(180)),
					_ -> new PhotonModelRenderScenario("photon-91-mixed-thirtytwo-laser", 16, 16, 16, 1, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-92-mixed-sixtyfour-four-lasers", "极端诊断：E3",
							Set.of("client", "photon", "extreme"), Duration.ofSeconds(180)),
					_ -> new PhotonModelRenderScenario("photon-92-mixed-sixtyfour-four-lasers", 32, 32, 16, 4, false));
			registrar.register(new ScenarioDescriptor("lobecorp.photon-99-empty-extreme-repeat", "模型诊断：极端后空基线",
							Set.of("client", "photon", "model"), LobecorpBenchConfig.PHASE_TIMEOUT),
					_ -> new PhotonModelRenderScenario("photon-99-empty-extreme-repeat", 0, false, false));
		}

		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.CLIENT_SCENARIO, "Sweeper renderer workload",
						Set.of("client", "sweeper", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new SweeperRenderScenario());
	}
}
