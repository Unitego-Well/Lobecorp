package org.unitego.lobecorp.registry.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.unitego.lobecorp.animation.definition.LcAnimationDefinition;
import org.unitego.lobecorp.animation.demo.LcAnimationDemo;
import org.unitego.lobecorp.animation.demo.LcAnimationDemoSession;
import org.unitego.lobecorp.animation.demo.LcPlayerAnimationDemo;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.world.item.animation.LcItemAnimationSession;
import org.unitego.lobecorp.animation.player.LcPlayerAnimationSources;

import java.util.Map;

public class LcAnimationExampleDefinitions {
	private static final String HEAD_ROTATION = "math.sin(q.anim_time * 90) * q.example_flourish";

	public static void init() {
		LcAnimationDefinitions.register(LcAnimationDemo.DEFINITION_ID, LcAnimationExampleDefinitions::create);
		LcAnimationDefinitions.register(LcItemAnimationSession.DEFINITION_ID, LcAnimationExampleDefinitions::createItem);

		for (var mode : LcPlayerAnimationDemo.Mode.values()) {
			LcAnimationDefinitions.register(mode.definitionId(), () -> createPlayer(mode));
		}
	}

	private static LcAnimationDefinition createPlayer(LcPlayerAnimationDemo.Mode mode) {
		var policy = mode == LcPlayerAnimationDemo.Mode.IDLE
				? LcPlayerAnimationDemo.idlePolicy() : LcPlayerAnimationDemo.overridePolicy();
		var definition = LcAnimationDefinition.wrap(LcPlayerAnimationDemo.RESOURCE_ID,
						LcPlayerAnimationDemo.RESOURCE_ID, LcPlayerAnimationDemo.ANIMATION_NAME)
				.playerAnimation(policy).onStop(LcPlayerAnimationDemo::finish);

		if (mode == LcPlayerAnimationDemo.Mode.DYNAMIC) {
			definition.at(LcPlayerAnimationDemo.MIX_TIME,
					instance -> {
						var current = instance.playerPolicy();
						if (current != null) {
							instance.setPlayerPolicy(current.withSources(Map.of(LcPlayerAnimationSources.VANILLA_IDLE, 1.0)));
						}
					});
		}

		return definition.build();
	}

	private static LcAnimationDefinition createItem() {
		return LcAnimationDefinition.wrap(LcItemAnimationSession.DEFINITION_ID, LcItemAnimationSession.DEFINITION_ID,
						LcItemAnimationSession.ANIMATION_NAME)
				.onTick(LcItemAnimationSession::tick).onStop(LcItemAnimationSession::finish).build();
	}

	private static LcAnimationDefinition create() {
		return LcAnimationDefinition.wrap(LcAnimationDemo.MODEL_ID, LcAnimationDemo.ANIMATION_ID,
						LcAnimationDemo.ANIMATION_NAME)
				.editAnimation(LcAnimationExampleDefinitions::addHeadRotation)
				.editAnimation(LcAnimationExampleDefinitions::addPlayerDraw)
				.query(LcAnimationDemo.FLOURISH_QUERY, LcAnimationExampleDefinitions::flourish)
				.onTick(LcAnimationDemoSession::tick)
				.at(0, LcAnimationDemoSession::attachBlade)
				.at(LcAnimationDemo.HAND_TIME, LcAnimationDemoSession::moveToHand)
				.at(LcAnimationDemo.WORLD_TIME, LcAnimationDemoSession::moveToWorld)
				.onStop(LcAnimationDemoSession::finish)
				.build();
	}

	private static double flourish(LcAnimationContext context) {
		return context.get(LcAnimationDemo.FLOURISH_PARAMETER);
	}

	private static void addPlayerDraw(JsonObject animation) {
		var position = animation.getAsJsonObject("animations").getAsJsonObject(LcAnimationDemo.ANIMATION_NAME)
				.getAsJsonObject("bones").getAsJsonObject(LcAnimationDemo.DRAW_BONE).getAsJsonObject("position");
		var end = new JsonArray();
		end.add(LcAnimationDemo.HAND_X_PARAMETER);
		end.add((LcAnimationDemo.HAND_HEIGHT - LcAnimationDemo.BACK_OFFSET.position().y) * LcAnimationDemo.MODEL_UNITS);
		end.add(LcAnimationDemo.BACK_OFFSET.position().z * LcAnimationDemo.MODEL_UNITS);
		position.add(Double.toString(LcAnimationDemo.HAND_TIME), end);
		position.add(Double.toString(LcAnimationDemo.DURATION_SECONDS), end.deepCopy());
	}

	private static void addHeadRotation(JsonObject animation) {
		var rotation = new JsonArray();
		rotation.add(0);
		rotation.add(HEAD_ROTATION);
		rotation.add(0);
		animation.getAsJsonObject("animations").getAsJsonObject(LcAnimationDemo.ANIMATION_NAME)
				.getAsJsonObject("bones").getAsJsonObject(LcAnimationDemo.HEAD_BONE).add("rotation", rotation);
	}
}
