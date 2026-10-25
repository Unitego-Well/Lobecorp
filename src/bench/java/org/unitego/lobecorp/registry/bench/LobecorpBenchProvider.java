package org.unitego.lobecorp.registry.bench;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;

import org.unitego.lobecorp.client.bench.SweeperRenderScenario;
import org.unitego.lobecorp.client.bench.PhotonModelRenderScenario;
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
	    if (System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-")) {
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
