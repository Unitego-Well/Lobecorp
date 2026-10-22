package org.unitego.lobecorp.registry.bench;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;

import org.unitego.lobecorp.client.bench.SweeperRenderScenario;
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
	}
}
