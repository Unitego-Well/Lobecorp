package org.unitego.lobecorp.debug;

/// 客户端向本地集成服务器单向传递的调试运行时选项。
public class LcDebugRuntimeOptions {
	private static volatile boolean controlledByClient;
	private static volatile boolean monitorTickTimes;

	private LcDebugRuntimeOptions() {
	}

	public static void setMonitorTickTimesFromClient(boolean enabled) {
		controlledByClient = true;
		monitorTickTimes = enabled;
	}

	public static boolean monitorTickTimes(boolean configuredDefault) {
		return controlledByClient ? monitorTickTimes : configuredDefault;
	}
}
