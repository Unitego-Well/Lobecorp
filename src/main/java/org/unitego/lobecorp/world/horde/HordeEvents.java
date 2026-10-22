package org.unitego.lobecorp.world.horde;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.world.horde.definition.MidnightCleanup;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/// 群体事件的唯一业务入口；客户端不调用参战分配。
public class HordeEvents {
	private static final Map<Identifier, IHordeEventProfile> PROFILES = new LinkedHashMap<>();

	static {
		register(new MidnightCleanup());
	}

	public static void register(IHordeEventProfile profile) {
		if (PROFILES.putIfAbsent(profile.id(), profile) != null) {
			throw new IllegalArgumentException("重复的群体事件定义：" + profile.id());
		}
	}

	public static @Nullable IHordeEventProfile profile(Identifier id) {
		return PROFILES.get(id);
	}

	public static HordeStartResult start(ServerLevel level, Identifier profileId, HordeStartRequest request) {
		var profile = profile(profileId);
		if (profile == null) {
			return new HordeStartResult(null, "未知的群体事件定义");
		}
		try {
			var anchors = new ArrayList<Vec3>();
			anchors.add(request.start());
			anchors.addAll(request.waypoints());
			anchors.add(request.end());
			if (anchors.size() > 128 || anchors.stream().anyMatch(point -> !Double.isFinite(point.lengthSqr())
					|| !level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(point)))) {
				return new HordeStartResult(null, "线路坐标无效或超出世界边界");
			}
			var configured = profile.defaults();
			var rules = configured.withOverrides(request.durationTicks() <= 0 ? configured.duration() : request.durationTicks(),
					request.width() <= 0 ? configured.width() : request.width());
			if (rules.duration() < 20 || rules.width() < 1 || rules.width() > 512) {
				return new HordeStartResult(null, "时长或宽度超出配置范围");
			}
			var loop = HordeSamplingUtil.loop(anchors, rules.width());
			if (loop.stream().anyMatch(point -> !level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(point)))) {
				return new HordeStartResult(null, "闭环线路超出世界边界");
			}
			var route = HordeRoute.create(loop);
			if (!route.prepare(level, rules)) {
				return new HordeStartResult(null, "线路所需区块尚未加载或无法准备完整地面采样");
			}
			var id = UUID.randomUUID();
			var event = new HordeEvent(id, profileId, level.dimension(),
					rules, route);
			var data = HordeSavedData.get(level.getServer());
			data.events().put(id, event);
			data.setDirty();
			return new HordeStartResult(id, "群体事件已启动");
		} catch (IllegalArgumentException exception) {
			return new HordeStartResult(null, exception.getMessage());
		}
	}

	public static HordeStopResult stop(MinecraftServer server, UUID id, HordeStopMode mode) {
		var data = HordeSavedData.get(server);
		var event = data.events().get(id);
		if (event == null) {
			return new HordeStopResult(false, "事件不存在");
		}
		event.stop();
		HordeRuntime.get(server).stop(event, mode);
		if (mode == HordeStopMode.CLEAR) {
			data.events().remove(id);
		}
		data.setDirty();
		return new HordeStopResult(true, mode == HordeStopMode.CLEAR ? "事件已清除，保留已有尸体" : "事件进入收尾");
	}

	public static @Nullable HordeEventSnapshot snapshot(MinecraftServer server, UUID id) {
		var event = HordeSavedData.get(server).events().get(id);
		return event == null ? null : new HordeEventSnapshot(id, event.save());
	}
}
