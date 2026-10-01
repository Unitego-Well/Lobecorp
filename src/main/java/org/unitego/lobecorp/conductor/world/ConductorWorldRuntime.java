package org.unitego.lobecorp.conductor.world;

import org.unitego.lobecorp.conductor.data.ConductorDirectory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 指挥家世界级票据、客户端基线和技能订阅运行状态。
public class ConductorWorldRuntime {
	public final Map<UUID, ClientView> clients = new HashMap<>();
	public final Map<UUID, Set<UUID>> requestedAbilities = new HashMap<>();
	protected final Map<UUID, ConductorChunkLoading.Location> tickets = new HashMap<>();
	protected final Map<UUID, ConductorView.State> views = new HashMap<>();

	public record ClientView(ConductorDirectory directory, long revision) {
	}
}
