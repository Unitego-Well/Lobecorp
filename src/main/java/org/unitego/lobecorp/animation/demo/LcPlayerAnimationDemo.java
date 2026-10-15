package org.unitego.lobecorp.animation.demo;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationPolicy;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationSources;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;

import java.util.IdentityHashMap;
import java.util.Map;

public class LcPlayerAnimationDemo {
	public static final Identifier RESOURCE_ID = Lobecorp.id("example/player_pose");
	public static final String ANIMATION_NAME = "animation.example.player_pose";
	/// 示例切换混合来源的时间，单位为秒。
	public static final double MIX_TIME = 1.5;
	private static final Map<ServerPlayer, LcAnimationInstance> PLAYING = new IdentityHashMap<>();

	public static LcPlayerAnimationPolicy overridePolicy() {
		return LcPlayerAnimationPolicy.fullOverride().withFirstPersonArms(true)
				.withTransitions(LcAnimationPlaybackOptions.DEFAULT.transitions());
	}

	public static LcPlayerAnimationPolicy idlePolicy() {
		return overridePolicy().withSources(Map.of(LcPlayerAnimationSources.VANILLA_IDLE, 1.0));
	}

	public static LcAnimationInstance play(ServerPlayer player, Mode mode, LcAnimationPlaybackOptions options) {
		stop(player);
		var instance = LcServerAnimations.play(player, mode.definitionId());
		options.apply(instance);
		PLAYING.put(player, instance);
		return instance;
	}

	public static boolean setPaused(ServerPlayer player, boolean paused) {
		var instance = PLAYING.get(player);
		if (instance == null || instance.isStopped()) {
			return false;
		}

		instance.setPaused(paused);
		return true;
	}

	public static boolean setSpeed(ServerPlayer player, double speed) {
		var instance = PLAYING.get(player);
		if (instance == null || instance.isStopped()) {
			return false;
		}

		instance.setSpeed(speed);
		return true;
	}

	public static void stop(ServerPlayer player) {
		var instance = PLAYING.remove(player);

		if (instance != null) {
			instance.stop();
		}
	}

	public static void finish(LcAnimationInstance instance) {
		if (instance.sourceEntity() instanceof ServerPlayer player) {
			PLAYING.remove(player, instance);
		}
	}

	public enum Mode {
		OVERRIDE("override", Lobecorp.id("example/player_override")),
		IDLE("idle", Lobecorp.id("example/player_idle")),
		DYNAMIC("dynamic", Lobecorp.id("example/player_dynamic"));

		private final String command;
		private final Identifier definitionId;

		private Mode(String command, Identifier definitionId) {
			this.command = command;
			this.definitionId = definitionId;
		}

		public String command() {
			return command;
		}

		public Identifier definitionId() {
			return definitionId;
		}
	}
}
