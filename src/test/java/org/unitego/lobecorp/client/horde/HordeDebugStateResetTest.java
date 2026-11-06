package org.unitego.lobecorp.client.horde;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 检查会话重置与当帧清理职责；通过实际私有字段验证，不初始化渲染器或游戏客户端。
public class HordeDebugStateResetTest {
	@Test
	public void resetClearsEverySessionObservation() throws ReflectiveOperationException {
		HordeDebugState.reset();
		HordeDebugState.setEvents(3);
		HordeDebugState.setBatches(2, 40);
		HordeDebugState.setGroups(4);
		HordeDebugState.setTide(12.5, 3.5, 2, 9);
		HordeDebugState.gapChanged();
		HordeDebugState.drawn(120);

		HordeDebugState.reset();

		assertEquals(0, field("events"));
		assertEquals(0, field("batches"));
		assertEquals(0L, field("instances"));
		assertEquals(0, field("groups"));
		assertEquals(0, field("splitCircles"));
		assertEquals(0.0, field("splitMaximumRadius"));
		assertEquals(0, field("splitWords"));
		assertEquals(0, field("splitCapacity"));
		assertEquals(0L, field("gapChanges"));
		assertEquals(0.0, field("head"));
		assertEquals(0.0, field("tail"));
		assertEquals(0L, field("firstRow"));
		assertEquals(0L, field("lastRow"));
		assertEquals(0L, field("drawnTotal"));
	}

	@Test
	public void beginFrameOnlyClearsCurrentFrameValues() throws ReflectiveOperationException {
		HordeDebugState.reset();
		HordeDebugState.setEvents(5);
		HordeDebugState.setBatches(2, 40);
		HordeDebugState.setGroups(4);
		HordeDebugState.setTide(12.5, 3.5, 2, 9);
		HordeDebugState.gapChanged();
		HordeDebugState.drawn(120);

		HordeDebugState.beginFrame();

		assertEquals(5, field("events"));
		assertEquals(0, field("batches"));
		assertEquals(0L, field("instances"));
		assertEquals(0, field("groups"));
		assertEquals(120L, field("drawnTotal"));
		assertEquals(1L, field("gapChanges"));
	}

	@Test
	public void beginDrawOnlyClearsSplitSummary() throws ReflectiveOperationException {
		HordeDebugState.reset();
		HordeDebugState.setEvents(5);
		HordeDebugState.setBatches(2, 40);
		HordeDebugState.setGroups(4);
		HordeDebugState.setTide(12.5, 3.5, 2, 9);
		HordeDebugState.gapChanged();
		HordeDebugState.drawn(120);

		HordeDebugState.beginDraw();

		assertEquals(5, field("events"));
		assertEquals(2, field("batches"));
		assertEquals(4, field("groups"));
		assertEquals(120L, field("drawnTotal"));
		assertEquals(12.5, field("head"));
		assertEquals(1L, field("gapChanges"));
		assertEquals(0, field("splitCircles"));
		assertEquals(0.0, field("splitMaximumRadius"));
		assertEquals(0, field("splitWords"));
		assertEquals(0, field("splitCapacity"));
	}

	@Test
	public void resetDoesNotAffectOtherFrameMethodsAfterwards() throws ReflectiveOperationException {
		HordeDebugState.reset();
		HordeDebugState.setEvents(2);
		HordeDebugState.setBatches(1, 8);
		HordeDebugState.setGroups(1);
		HordeDebugState.beginFrame();

		assertEquals(2, field("events"));
		assertEquals(0, field("batches"));
	}

	private static Object field(String name) throws ReflectiveOperationException {
		Field field = HordeDebugState.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}
}

