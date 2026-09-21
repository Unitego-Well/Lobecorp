package org.unitego.lobecorp.hitbox;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/// 单个服务端或客户端 Level 的非持久判断框数据。
public class HitboxLevelData {
	private final Map<Integer, HitboxInstance> instances = new LinkedHashMap<>();
	private final Map<Integer, ClientEntry> clientEntries = new LinkedHashMap<>();
	private int nextId = 1;

	int add(HitboxInstance instance) {
		int id = nextId++;
		instance.setId(id);
		instances.put(id, instance);
		return id;
	}

	HitboxInstance get(int id) {
		return instances.get(id);
	}

	HitboxInstance remove(int id) {
		return instances.remove(id);
	}

	Collection<HitboxInstance> instances() {
		return instances.values();
	}

	/// 新建或覆盖客户端可见快照。
	///
	/// @param snapshot 最新服务端快照
	/// @param gameTime 客户端收到快照时的游戏时间
	public void upsertClient(HitboxSnapshot snapshot, long gameTime) {
		clientEntries.put(snapshot.id(), new ClientEntry(snapshot, gameTime));
	}

	/// 移除客户端可见实例。
	///
	/// @param id Level 内实例编号
	public void removeClient(int id) {
		clientEntries.remove(id);
	}

	/// 清除不再被服务端更新的客户端镜像，防止离开跟踪范围后残留。
	///
	/// @param gameTime 当前客户端游戏时间
	public void pruneClient(long gameTime) {
		clientEntries.values().removeIf(entry -> entry.lastUpdateGameTime() < gameTime - 1);
	}

	/// @return 当前客户端调试渲染快照
	public Collection<HitboxSnapshot> clientSnapshots() {
		return clientEntries.values().stream().map(ClientEntry::snapshot).toList();
	}

	private record ClientEntry(HitboxSnapshot snapshot, long lastUpdateGameTime) {
	}
}
