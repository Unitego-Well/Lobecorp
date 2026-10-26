package org.unitego.lobecorp.world.horde;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class HordeEvent {
	private final UUID id;
	private final Identifier profile;
	private final ResourceKey<Level> dimension;
	private final HordeRules rules;
	private final HordeRoute route;
	private double speed;
	private long tick;
	private long stopAt;
	private long revision;

	public HordeEvent(UUID id, Identifier profile, ResourceKey<Level> dimension, HordeRules rules, HordeRoute route) {
		this.id = id;
		this.profile = profile;
		this.dimension = dimension;
		this.rules = rules;
		this.route = route;
		var definition = HordeEvents.profile(profile);
		this.speed = definition == null ? 0 : definition.movementSpeed();
		if (!Double.isFinite(speed) || speed <= 0) {
			throw new IllegalArgumentException("事件奔跑速度必须大于零");
		}
		this.stopAt = rules.duration();
	}

	public UUID id() {
		return id;
	}

	public Identifier profile() {
		return profile;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public HordeRules rules() {
		return rules;
	}

	public HordeRoute route() {
		return route;
	}

	public long tick() {
		return tick;
	}

	public long revision() {
		return revision;
	}

	public long stopAt() {
		return stopAt;
	}

	public boolean running() {
		return tick < stopAt;
	}

	public boolean finished() {
		return tick >= stopAt + travelTicks();
	}

	public long travelTicks() {
		return (long) Math.ceil(route.length() / speed);
	}

	public double movementSpeed() {
		return speed;
	}

	public void advance() {
		tick++;
	}

	public void changed() {
		revision++;
	}

	public void stop() {
		stopAt = Math.min(stopAt, tick);
		changed();
	}

	public double head() {
		return Math.min(route.length(), tick * speed);
	}

	public double tail() {
		return Math.clamp((tick - stopAt) * speed, 0, route.length());
	}

	public boolean covers(Vec3 point) {
		// 必须落在当前波带 [tail, head] 内，而不是“离波头一个半径内”：否则宽度500时波头前方250格的目标
		// 也会被判定覆盖，真实清道夫/粒子会在浪潮到达前提前出现在远处目标身边。
		double station = route.nearest(point, 0, route.length());
		if (station < tail() - 0.001 || station > head() + 0.001) {
			return false;
		}
		return route.point(station, 0).distanceToSqr(point)
				<= Math.pow(rules.width() / 2 + rules.margin(), 2);
	}

	public double station(Vec3 point) {
		return Math.clamp(route.nearest(point, 0, route.length()), tail(), head());
	}

	public CompoundTag header() {
		var tag = new CompoundTag();
		tag.putInt("format", 1);
		tag.putString("id", id.toString());
		tag.putString("profile", profile.toString());
		tag.putString("dimension", dimension.identifier().toString());
		tag.putLong("tick", tick);
		tag.putLong("stop", stopAt);
		tag.putLong("revision", revision);
		tag.putDouble("length", route.length());
		tag.putDouble("speed", movementSpeed());
		tag.putInt("node_count", route.size());
		tag.put("rules", rules.save());
		return tag;
	}

	public CompoundTag save() {
		var tag = header();
		tag.put("route", route.save());
		return tag;
	}

	public static HordeEvent load(CompoundTag tag) {
		if (tag.getIntOr("format", 0) != 1) {
			throw new IllegalArgumentException("不支持的群体事件数据版本");
		}
		var event = new HordeEvent(UUID.fromString(tag.getStringOr("id", "")),
				Identifier.parse(tag.getStringOr("profile", "")),
				ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", ""))),
				HordeRules.load(tag.getCompoundOrEmpty("rules")),
				HordeRoute.load(tag.getCompoundOrEmpty("route")));
		event.tick = Math.max(0, tag.getLongOr("tick", 0));
		event.speed = tag.getDoubleOr("speed", event.speed);
		if (!Double.isFinite(event.speed) || event.speed <= 0) {
			throw new IllegalArgumentException("存档中的事件奔跑速度无效");
		}
		event.stopAt = Math.max(0, tag.getLongOr("stop", event.rules.duration()));
		event.revision = tag.getLongOr("revision", 0);
		return event;
	}
}
