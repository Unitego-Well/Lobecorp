package org.unitego.lobecorp.client.hud.event;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventHudStateTest {
	private static final Identifier DIRECT = Identifier.fromNamespaceAndPath("lobecorp", "test_event_direct");
	private static final Identifier LEFT_EVENT = Identifier.fromNamespaceAndPath("lobecorp", "test_event_left");
	private static final Identifier RIGHT_EVENT = Identifier.fromNamespaceAndPath("lobecorp", "test_event_right");
	private static final Identifier LEFT = Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_left");
	private static final Identifier RIGHT = Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_right");
	private static final Identifier LEFT_EVENTS = Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_left_events");
	private EventHudState<ComponentStub> state;

	@BeforeEach
	public void setUp() {
		state = new EventHudState<>();
		state.registerWrapper(LEFT);
		state.registerWrapper(RIGHT);
		state.registerWrapper(LEFT_EVENTS, LEFT);
		state.register(DIRECT, "Direct", "直接", null, () -> new ComponentStub("direct"));
		state.register(LEFT_EVENT, "Left", "左侧", LEFT, () -> new ComponentStub("left"));
		state.register(RIGHT_EVENT, "Right", "右侧", RIGHT, () -> new ComponentStub("right"));
	}

	@Test
	public void duplicateDefinitionIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> state.register(DIRECT, "Again", "重复", null, () -> new ComponentStub("again")));
	}

	@Test
	public void wrapperRegistrationRejectsUnknownAndDuplicate() {
		assertThrows(IllegalArgumentException.class,
				() -> state.register(Identifier.fromNamespaceAndPath("lobecorp", "test_event_orphan"),
						"Orphan", "孤儿", Identifier.fromNamespaceAndPath("lobecorp", "missing_wrapper"),
						() -> new ComponentStub("orphan")));
		assertThrows(IllegalArgumentException.class, () -> state.registerWrapper(LEFT));
		assertEquals(List.of(LEFT, RIGHT, LEFT_EVENTS), state.wrapperIds());
	}

	@Test
	public void nestedWrapperKeepsParentAndRegistrationOrder() {
		assertNull(state.wrapperParent(LEFT));
		assertEquals(LEFT, state.wrapperParent(LEFT_EVENTS));
		assertEquals(List.of(LEFT, RIGHT), state.childWrapperIds(null));
		assertEquals(List.of(LEFT_EVENTS), state.childWrapperIds(LEFT));
		assertTrue(state.childWrapperIds(LEFT_EVENTS).isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> state.wrapperParent(Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_missing")));
	}

	@Test
	public void unknownParentAndSelfParentAreRejected() {
		var unknownParent = Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_unknown_parent");
		var orphan = Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_orphan");

		assertThrows(IllegalArgumentException.class, () -> state.registerWrapper(orphan, unknownParent));
		assertThrows(IllegalArgumentException.class, () -> state.registerWrapper(orphan, orphan));
		assertTrue(state.wrapperIds().stream().noneMatch(orphan::equals));
	}

	@Test
	public void eventInNestedWrapperKeepsOwnShowOrder() {
		var nestedEvent = Identifier.fromNamespaceAndPath("lobecorp", "test_event_nested");
		state.register(nestedEvent, "Nested", "嵌套", LEFT_EVENTS, () -> new ComponentStub("nested"));
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();

		state.show(nestedEvent, first);
		state.show(nestedEvent, second);

		assertEquals(List.of("nested", "nested"), state.activeInOrder(LEFT_EVENTS).stream().map(ComponentStub::label).toList());
		assertTrue(state.activeInOrder(LEFT).isEmpty());
		assertTrue(state.activeInOrder(null).isEmpty());

		state.hide(first);

		assertEquals(1, state.activeInOrder(LEFT_EVENTS).size());
	}

	@Test
	public void lateWrapperRegistrationIsRejectedAfterHostCreation() {
		state.markHostCreated();

		assertTrue(state.hostCreated());
		assertThrows(IllegalStateException.class,
				() -> state.registerWrapper(Identifier.fromNamespaceAndPath("lobecorp", "test_wrapper_late")));
	}

	@Test
	public void sameUuidAndTypeIsIdempotentButUuidTypeConflictIsRejected() {
		var instanceId = UUID.randomUUID();

		var first = state.show(DIRECT, instanceId);
		var second = state.show(DIRECT, instanceId);

		assertEquals(first, second);
		assertEquals(1, state.activeInOrder(null).size());
		assertThrows(IllegalArgumentException.class, () -> state.show(LEFT_EVENT, instanceId));
	}

	@Test
	public void hideRemovesOnlyTargetAndKeepsShowOrder() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var third = UUID.randomUUID();
		state.show(DIRECT, first);
		state.show(DIRECT, second);
		state.show(DIRECT, third);

		state.hide(second);

		var remaining = state.activeInOrder(null);
		assertEquals(2, remaining.size());
		assertEquals("direct", remaining.getFirst().label());
		assertEquals("direct", remaining.getLast().label());
		assertTrue(state.activeInOrder(LEFT).isEmpty());
	}

	@Test
	public void twoWrappersKeepIndependentShowOrderAndFillAfterHide() {
		var leftFirst = UUID.randomUUID();
		var leftSecond = UUID.randomUUID();
		var rightOnly = UUID.randomUUID();
		state.show(LEFT_EVENT, leftFirst);
		state.show(DIRECT, UUID.randomUUID());
		state.show(LEFT_EVENT, leftSecond);
		state.show(RIGHT_EVENT, rightOnly);

		assertEquals(1, state.activeInOrder(null).size());
		assertEquals(List.of("left", "left"), state.activeInOrder(LEFT).stream().map(ComponentStub::label).toList());
		assertEquals(List.of("right"), state.activeInOrder(RIGHT).stream().map(ComponentStub::label).toList());

		state.hide(leftFirst);

		assertEquals(List.of("left"), state.activeInOrder(LEFT).stream().map(ComponentStub::label).toList());
		assertEquals(List.of("right"), state.activeInOrder(RIGHT).stream().map(ComponentStub::label).toList());
	}

	@Test
	public void clearActiveKeepsDefinitionsAndAllowsShowAgain() {
		var instanceId = UUID.randomUUID();
		state.show(DIRECT, instanceId);
		state.clearActive();

		assertTrue(state.activeEmpty());
		assertTrue(state.activeInOrder(null).isEmpty());

		var shownAgain = state.show(DIRECT, instanceId);
		assertEquals("direct", shownAgain.label());
		assertEquals(1, state.activeInOrder(null).size());
	}

	@Test
	public void factoryRunsOnlyForNewInstance() {
		var created = new AtomicInteger();
		var type = Identifier.fromNamespaceAndPath("lobecorp", "test_event_counted");
		state.register(type, "Counted", "计数", null, () -> {
			created.incrementAndGet();
			return new ComponentStub("counted");
		});
		var instanceId = UUID.randomUUID();

		state.show(type, instanceId);
		state.show(type, instanceId);

		assertEquals(1, created.get());
	}

	@Test
	public void eventLayoutStartsAtItsOwnOrigin() {
		state.show(DIRECT, UUID.randomUUID());
		var layout = EventHud.INSTANCE.layout(state);
		assertEquals(1, layout.size());
		assertEquals(0, layout.getFirst().top(), 0.001F);
	}

	@Test
	public void layoutUsesChildWrappersBeforeContainerEvents() {
		var root = Identifier.fromNamespaceAndPath("lobecorp", "test_event_root");
		var child = Identifier.fromNamespaceAndPath("lobecorp", "test_event_child");
		var nested = Identifier.fromNamespaceAndPath("lobecorp", "test_event_nested_layout");
		state.register(root, "Root", "根", null, () -> new ComponentStub("root", 10));
		state.register(child, "Child", "子", LEFT, () -> new ComponentStub("child", 12));
		state.register(nested, "Nested", "嵌套", LEFT_EVENTS, () -> new ComponentStub("nested", 14));
		state.show(root, UUID.randomUUID());
		state.show(child, UUID.randomUUID());
		state.show(nested, UUID.randomUUID());

		var layout = EventHud.INSTANCE.layout(state);
		assertEquals(List.of("nested", "child", "root"),
				layout.stream().map(placed -> ((ComponentStub) placed.component()).label()).toList());
		assertEquals(List.of(0F, 18F, 34F), layout.stream().map(EventHud.Placed::top).toList());
	}

	private static final class ComponentStub extends EventHudComponent {
		private final String label;
		private final int height;

		private ComponentStub(String label) {
			this(label, 0);
		}

		private ComponentStub(String label, int height) {
			this.label = label;
			this.height = height;
		}

		public String label() {
			return label;
		}

		@Override
		public int height() {
			return height;
		}
	}
}
