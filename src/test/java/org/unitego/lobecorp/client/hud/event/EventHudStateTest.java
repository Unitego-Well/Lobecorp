package org.unitego.lobecorp.client.hud.event;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.hud.EventHudDirection;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventHudStateTest {
	private static final Identifier TOP = Identifier.fromNamespaceAndPath("lobecorp", "test_event_top");
	private static final Identifier LEFT = Identifier.fromNamespaceAndPath("lobecorp", "test_event_left");
	private EventHudState<ComponentStub> state;

	@BeforeEach
	public void setUp() {
		state = new EventHudState<>(ComponentStub::direction);
		state.register(TOP, "Top", "顶部", () -> new ComponentStub(EventHudDirection.TOP, "top"));
		state.register(LEFT, "Left", "左侧", () -> new ComponentStub(EventHudDirection.LEFT, "left"));
	}

	@Test
	public void duplicateDefinitionIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> state.register(TOP, "Again", "重复", () -> new ComponentStub(EventHudDirection.TOP, "again")));
	}

	@Test
	public void sameUuidAndTypeIsIdempotentButUuidTypeConflictIsRejected() {
		var instanceId = UUID.randomUUID();

		var first = state.show(TOP, instanceId);
		var second = state.show(TOP, instanceId);

		assertEquals(first, second);
		assertEquals(1, state.activeInOrder(EventHudDirection.TOP).size());
		assertThrows(IllegalArgumentException.class, () -> state.show(LEFT, instanceId));
	}

	@Test
	public void hideRemovesOnlyTargetAndKeepsDirectionOrder() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var third = UUID.randomUUID();
		state.show(TOP, first);
		state.show(TOP, second);
		state.show(TOP, third);

		state.hide(second);

		var remaining = state.activeInOrder(EventHudDirection.TOP);
		assertEquals(2, remaining.size());
		assertEquals("top", remaining.getFirst().label());
		assertEquals("top", remaining.getLast().label());
		assertTrue(state.activeInOrder(EventHudDirection.LEFT).isEmpty());
	}

	@Test
	public void directionsKeepIndependentStacks() {
		state.show(TOP, UUID.randomUUID());
		state.show(TOP, UUID.randomUUID());
		state.show(LEFT, UUID.randomUUID());

		assertEquals(2, state.activeInOrder(EventHudDirection.TOP).size());
		assertEquals(1, state.activeInOrder(EventHudDirection.LEFT).size());
		assertTrue(state.activeInOrder(EventHudDirection.RIGHT).isEmpty());
	}

	@Test
	public void clearActiveKeepsDefinitionsAndAllowsShowAgain() {
		var instanceId = UUID.randomUUID();
		state.show(TOP, instanceId);
		state.clearActive();

		assertTrue(state.activeEmpty());
		assertTrue(state.activeInOrder(EventHudDirection.TOP).isEmpty());

		var shownAgain = state.show(TOP, instanceId);
		assertEquals("top", shownAgain.label());
		assertEquals(1, state.activeInOrder(EventHudDirection.TOP).size());
	}

	@Test
	public void factoryRunsOnlyForNewInstance() {
		var created = new AtomicInteger();
		var type = Identifier.fromNamespaceAndPath("lobecorp", "test_event_counted");
		state.register(type, "Counted", "计数", () -> {
			created.incrementAndGet();
			return new ComponentStub(EventHudDirection.RIGHT, "counted");
		});
		var instanceId = UUID.randomUUID();

		state.show(type, instanceId);
		state.show(type, instanceId);

		assertEquals(1, created.get());
	}

	private record ComponentStub(EventHudDirection direction, String label) {
	}
}
