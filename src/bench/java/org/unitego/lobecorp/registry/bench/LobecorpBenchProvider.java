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
import org.unitego.lobecorp.client.bench.HordeRenderScenario;
import org.unitego.lobecorp.client.bench.HordeTideAcceptanceScenario;
import org.unitego.lobecorp.client.bench.CorpseRenderScenario;
import org.unitego.lobecorp.client.bench.RandomCorpseRenderScenario;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;
import org.unitego.lobecorp.world.entity.bench.SweeperAiScenario;

import java.util.Set;

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
        registrar.register(new ScenarioDescriptor(
                        LobecorpBenchConfig.CLIENT_SCENARIO, "Sweeper renderer workload",
                        Set.of("client", "sweeper", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
                _ -> new SweeperRenderScenario());
	    registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.HORDE_SCENARIO, "GPU horde workload",
			    Set.of("client", "horde", "gpu"), LobecorpBenchConfig.HORDE_PHASE_TIMEOUT), _ -> new HordeRenderScenario());
        registrar.register(new ScenarioDescriptor(HordeTideAcceptanceScenario.ID, "Seven-part tide visual acceptance capture",
                Set.of("client", "horde", "visual", "acceptance"), HordeTideAcceptanceScenario.PHASE_TIMEOUT), _ -> new HordeTideAcceptanceScenario());
	    registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.CORPSE_SCENARIO, "Static corpse renderer workload",
			    Set.of("client", "corpse", "render"), LobecorpBenchConfig.PHASE_TIMEOUT), _ -> new CorpseRenderScenario());
	    registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.SWEEPER30_AI_SCENARIO,
			    "Server sweepers with AI render workload",
			    Set.of("client", "sweeper", "server", "ai", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
			    _ -> new Sweeper30RenderScenario(false));
	    registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.SWEEPER30_NOAI_SCENARIO,
			    "Server sweepers without AI render workload",
			    Set.of("client", "sweeper", "server", "noai", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
			    _ -> new Sweeper30RenderScenario(true));
	    registrar.register(new ScenarioDescriptor(LobecorpBenchConfig.CORPSE_RANDOM100_SCENARIO,
			    "Random mob corpse render workload",
			    Set.of("client", "corpse", "random", "render"), LobecorpBenchConfig.PHASE_TIMEOUT),
			    _ -> new RandomCorpseRenderScenario());
    }
}
