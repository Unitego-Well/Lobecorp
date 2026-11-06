package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.ANIMATION_FRAME_MS;

public class ConductorHudAnimationStateTest {
	@Test
	public void repeatedHealthReadsDoNotMoveInterpolationStart() {
		var state = new ConductorHudAnimationState();
		var member = UUID.randomUUID();
		state.sampleHealth(member, 0.8F, false, 1_000L);
		assertEquals(0.8F, state.healthShown(member, 0F), 0.0001F);

		state.sampleHealth(member, 0.4F, false, 1_020L);
		assertEquals(0.6F, state.healthShown(member, 0F), 0.0001F);
		assertEquals(0.6F, state.healthShown(member, 0F), 0.0001F);
		assertEquals(0.4F, state.healthRatio(member, 0F), 0.0001F);

		state.sampleHealth(member, 0.4F, false, 1_030L);
		assertEquals(0.6F, state.healthShown(member, 0F), 0.0001F);

		state.sampleHealth(member, 0.4F, false, 1_080L);
		assertEquals(0.4F, state.healthShown(member, 0F), 0.0001F);
	}

	@Test
	public void reduceMotionShowsNewRatioImmediately() {
		var state = new ConductorHudAnimationState();
		var member = UUID.randomUUID();
		state.sampleHealth(member, 0.8F, false, 1_000L);
		state.sampleHealth(member, 0.4F, true, 1_020L);
		assertEquals(0.4F, state.healthShown(member, 0F), 0.0001F);
	}

	@Test
	public void cooldownFlashStartsOnceAndReadsAreStable() {
		var state = new ConductorHudAnimationState();
		state.sampleCooldown("skill", 5, 1_000L);
		assertEquals(0L, state.cooldownFlash("skill"));

		state.sampleCooldown("skill", 0, 1_100L);
		assertEquals(1_100L, state.cooldownFlash("skill"));

		state.sampleCooldown("skill", 0, 1_200L);
		assertEquals(1_100L, state.cooldownFlash("skill"));
		assertEquals(0, state.cooldownRemaining("skill", -1));
	}

	@Test
	public void removalsExpireOnlyAfterFrameInterval() {
		var state = new ConductorHudAnimationState();
		var member = UUID.randomUUID();
		state.addRemovedCard(member, 4, 8, Component.literal("a"), false, 1_000L);
		assertTrue(state.removedCards().containsKey(member));

		state.tickRemovals(1_000L + ANIMATION_FRAME_MS - 1);
		assertTrue(state.removedCards().containsKey(member));

		state.tickRemovals(1_000L + ANIMATION_FRAME_MS);
		assertFalse(state.removedCards().containsKey(member));
	}

	@Test
	public void pruneKeepsOnlyLiveMembers() {
		var state = new ConductorHudAnimationState();
		var kept = UUID.randomUUID();
		var removed = UUID.randomUUID();
		state.sampleHealth(kept, 0.5F, false, 1_000L);
		state.sampleHealth(removed, 0.5F, false, 1_000L);
		state.pruneHealth(Set.of(kept)::contains);

		assertEquals(0.5F, state.healthShown(kept, -1F), 0.0001F);
		assertEquals(-1F, state.healthShown(removed, -1F), 0.0001F);
	}
}
