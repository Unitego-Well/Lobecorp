package org.unitego.lobecorp.registry.bench;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;

import org.unitego.lobecorp.client.bench.SweeperRenderScenario;
import org.unitego.lobecorp.client.bench.Sweeper30RenderScenario;
import org.unitego.lobecorp.client.bench.HordeEncounterPerformanceScenario;
import org.unitego.lobecorp.client.bench.HordeCameraDiagnosticScenario;
import org.unitego.lobecorp.client.bench.HordeTideAcceptanceScenario;
import org.unitego.lobecorp.client.bench.CorpseRenderScenario;
import org.unitego.lobecorp.client.bench.RandomCorpseRenderScenario;
import org.unitego.lobecorp.client.bench.PhotonLogicRegressionScenario;
import org.unitego.lobecorp.client.bench.PhotonWorkspaceContractScenario;
import org.unitego.lobecorp.client.bench.PhotonBridgeContractScenario;
import org.unitego.lobecorp.client.bench.PhotonMigrationLaserScenario;
import org.unitego.lobecorp.client.bench.PhotonWorkspaceReviewScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridQueenScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridLaserScenario;
import org.unitego.lobecorp.client.bench.PhotonHybridBlankScenario;
import org.unitego.lobecorp.client.bench.PhotonQuietControlScenario;
import org.unitego.lobecorp.client.bench.PhotonRunControlScenario;
import org.unitego.lobecorp.client.bench.PhotonRunMatrixCapture;
import org.unitego.lobecorp.client.bench.PhotonQuietMatrixCapture;
import org.unitego.lobecorp.client.bench.PhotonQuietVisualScenario;
import org.unitego.lobecorp.client.bench.PhotonGlobalContractScenario;
import org.unitego.lobecorp.client.bench.PhotonQueenEntryScenario;
import org.unitego.lobecorp.client.bench.PhotonEntryBlankScenario;
import org.unitego.lobecorp.client.bench.PhotonNativeControlScenario;
import org.unitego.lobecorp.client.bench.PhotonRepeatedEffectScenario;
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
		if (HordeCameraDiagnosticScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(HordeCameraDiagnosticScenario.ID, "开幕右摆帧耗时诊断",
					Set.of("client", "horde", "camera", "diagnostic"), Duration.ofMinutes(6)),
					_ -> new HordeCameraDiagnosticScenario());
			return;
		}
		var run = PhotonRunMatrixCapture.Variant.selected();
		if (run != null) {
			registrar.register(new ScenarioDescriptor(run.id(), "轻量 Run 矩阵",
					Set.of("client", "photon", "run"), Duration.ofSeconds(180)),
					_ -> new PhotonRunControlScenario(run));
			return;
		}
		if (PhotonGlobalContractScenario.ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(PhotonGlobalContractScenario.ID, "全局命名执行契约",
					Set.of("client", "photon", "contracts"), Duration.ofSeconds(600)), _ -> new PhotonGlobalContractScenario());
			return;
		}
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
		if (PhotonQuietMatrixCapture.Variant.selected() != null) {
			var quiet = PhotonQuietMatrixCapture.Variant.selected();
			registrar.register(new ScenarioDescriptor(quiet.id(), "安静对照：" + quiet.label(),
							Set.of("client", "photon", "quiet"), Duration.ofSeconds(600)),
					_ -> new PhotonQuietControlScenario(quiet));
			return;
		}
		if (PhotonQuietMatrixCapture.VISUAL_ID.equals(selectedProperty)) {
			registrar.register(new ScenarioDescriptor(selectedProperty, "安静诊断：真实激光与十五组成件视觉",
							Set.of("client", "photon", "quiet", "visual"), Duration.ofSeconds(600)),
					_ -> new PhotonQuietVisualScenario());
			return;
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




		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.CLIENT_SCENARIO, "Sweeper renderer workload",
						Set.of("client", "sweeper", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new SweeperRenderScenario());

		registrar.register(new ScenarioDescriptor(HordeEncounterPerformanceScenario.ID,
				"Formal midnight cleanup performance", Set.of("client", "horde", "performance"),
				HordeEncounterPerformanceScenario.TIMEOUT), _ -> new HordeEncounterPerformanceScenario());

		registrar.register(new ScenarioDescriptor(
						HordeTideAcceptanceScenario.ID, "Seven-part tide visual acceptance capture",
						Set.of("client", "horde", "visual", "acceptance"), HordeTideAcceptanceScenario.PHASE_TIMEOUT),
				_ -> new HordeTideAcceptanceScenario());
		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.CORPSE_SCENARIO, "Static corpse renderer workload",
						Set.of("client", "corpse", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new CorpseRenderScenario());
		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.SWEEPER30_AI_SCENARIO, "Server sweepers with AI render workload",
						Set.of("client", "sweeper", "server", "ai", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new Sweeper30RenderScenario(false));
		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.SWEEPER30_NOAI_SCENARIO, "Server sweepers without AI render workload",
						Set.of("client", "sweeper", "server", "noai", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new Sweeper30RenderScenario(true));
		registrar.register(new ScenarioDescriptor(
						LobecorpBenchConfig.CORPSE_RANDOM100_SCENARIO, "Random mob corpse render workload",
						Set.of("client", "corpse", "random", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
				_ -> new RandomCorpseRenderScenario());
	}
}
