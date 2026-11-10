package org.unitego.lobecorp.registry.client.horde;

import java.util.ArrayList;
import java.util.List;

/// 浪潮提供资源生命周期扩展点，不反向依赖具体玩法。
public class HordeClientHooks {
	private static final List<Hooks> hooks = new ArrayList<>();

	public static void register(Runnable prepare, Runnable resourcesReleased, Runnable sessionClosed, Runnable metrics) {
		hooks.add(new Hooks(prepare, resourcesReleased, sessionClosed, metrics));
	}

	public static void prepare() {
		hooks.forEach(value -> value.prepare().run());
	}

	public static void releaseResources() {
		hooks.forEach(value -> value.resourcesReleased().run());
	}

	public static void closeSession() {
		hooks.forEach(value -> value.sessionClosed().run());
	}

	public static void metrics() {
		hooks.forEach(value -> value.metrics().run());
	}

	private record Hooks(Runnable prepare, Runnable resourcesReleased, Runnable sessionClosed, Runnable metrics) {
	}
}
