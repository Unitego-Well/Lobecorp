package org.unitego.lobecorp.animation.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 本次采样的查询与变量上下文；不修改 GeckoLib 的全局变量表。
public class LcAnimationContext implements ILcAnimationQueries {
	private static final String UNKNOWN_QUERY = "No server animation query provider: ";
	private static final String READ_ONLY_QUERY = "Animation query is not writable: ";
	private static final List<String> STANDARD_QUERIES = List.of("q.anim_time", "q.delta_time", "q.life_time",
			"q.is_alive", "q.is_on_ground", "q.ground_speed", "q.health", "q.max_health", "q.body_y_rotation",
			"q.head_y_rotation", "q.head_x_rotation");
	private final LcAnimationInstance instance;
	private final double animationTime;
	private final double deltaSeconds;
	private final Map<String, Double> variables;
	private LcSkeletalPose pose = LcSkeletalPose.empty();

	public LcAnimationContext(LcAnimationInstance instance, double animationTime, double deltaSeconds,
	                          Map<String, Double> variables) {
		this.instance = instance;
		this.animationTime = animationTime;
		this.deltaSeconds = deltaSeconds;
		this.variables = variables;
	}

	public LcAnimationInstance instance() {
		return instance;
	}

	public ServerLevel level() {
		return instance.level();
	}

	@Nullable
	public Entity sourceEntity() {
		return instance.sourceEntity();
	}

	public double animationTime() {
		return animationTime;
	}

	public double elapsedTime() {
		return instance.timeSeconds();
	}

	public double deltaSeconds() {
		return deltaSeconds;
	}

	public LcSkeletalPose pose() {
		return pose;
	}

	public void usePose(LcSkeletalPose sampledPose) {
		pose = sampledPose;
	}

	public Map<String, Double> parameters() {
		return Map.copyOf(variables);
	}

	/// 查询提供器必须是纯查询；快照会再次求值标准查询和显式注册查询，以同步动态数值。
	public Map<String, Double> snapshotParameters() {
		Map<String, Double> values = new LinkedHashMap<>();
		variables.forEach((name, value) -> values.put(normalize(name), value));
		STANDARD_QUERIES.forEach(name -> values.put(name, get(name)));
		instance.definition().queries().keySet().forEach(name -> values.put(normalize(name), get(name)));
		return Map.copyOf(values);
	}

	@Override
	public double get(String name) {
		String query = normalize(name);
		var provider = instance.definition().queries().get(query);

		if (provider == null) {
			for (var entry : instance.definition().queries().entrySet()) {
				if (normalize(entry.getKey()).equals(query)) {
					provider = entry.getValue();
					break;
				}
			}
		}

		if (provider != null) {
			return provider.applyAsDouble(this);
		}

		if (query.startsWith("v.") || query.startsWith("t.")) {
			var value = variables.get(query);

			if (value != null) {
				return value;
			}

			for (var entry : variables.entrySet()) {
				if (normalize(entry.getKey()).equals(query)) {
					return entry.getValue();
				}
			}

			return variables.getOrDefault(query.substring(2), 0.0);
		}

		Entity entity = sourceEntity();
		return switch (query) {
			case "q.anim_time" -> animationTime;
			case "q.delta_time" -> deltaSeconds;
			case "q.life_time" ->
					entity == null ? elapsedTime() : entity.tickCount / LcAnimationInstance.TICKS_PER_SECOND;
			case "q.is_alive" -> entity != null && entity.isAlive() ? 1 : 0;
			case "q.is_on_ground" -> entity != null && entity.onGround() ? 1 : 0;
			case "q.ground_speed" -> entity == null ? 0
					: entity.getDeltaMovement().horizontalDistance() * LcAnimationInstance.TICKS_PER_SECOND;
			case "q.health" -> entity instanceof LivingEntity living ? living.getHealth() : 0;
			case "q.max_health" -> entity instanceof LivingEntity living ? living.getMaxHealth() : 0;
			case "q.body_y_rotation" -> entity instanceof LivingEntity living ? living.yBodyRot : 0;
			case "q.head_y_rotation" -> entity instanceof LivingEntity living ? living.getYHeadRot() : 0;
			case "q.head_x_rotation" -> entity == null ? 0 : entity.getXRot();
			default -> throw new IllegalArgumentException(UNKNOWN_QUERY + name);
		};
	}

	@Override
	public void set(String name, double value) {
		String query = normalize(name);

		if (!query.startsWith("v.") && !query.startsWith("t.")) {
			throw new IllegalArgumentException(READ_ONLY_QUERY + name);
		}

		variables.put(query, value);
	}

	private static String normalize(String name) {
		return name.replace("query.", "q.").replace("variable.", "v.").replace("temp.", "t.");
	}
}
