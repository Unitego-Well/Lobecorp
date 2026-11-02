package org.unitego.lobecorp.client.animation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 仅验证纯分块重组，不初始化客户端或代替内容 ready 集成验收。
public class LcClientAnimationContentsTest {
	@Test
	public void assemblesSequentialChunksOnlyAfterLastSlot() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 0, 3, bytes("甲")));
		assertNull(pending.receive("v", 1, 3, bytes("乙")));
		assertArrayEquals(bytes("甲乙丙"), pending.receive("v", 2, 3, bytes("丙")));
	}

	@Test
	public void assemblesReverseOrderAndEmptyChunks() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 2, 3, bytes("尾")));
		assertNull(pending.receive("v", 1, 3, new byte[0]));
		assertArrayEquals(bytes("头尾"), pending.receive("v", 0, 3, bytes("头")));
	}

	@Test
	public void replacementDoesNotCountAsAnotherReceivedSlot() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 0, 3, bytes("long prefix")));
		assertNull(pending.receive("v", 0, 3, bytes("a")));
		assertNull(pending.receive("v", 2, 3, bytes("c")));
		assertNull(pending.receive("v", 2, 3, bytes("long suffix")));
		assertArrayEquals(bytes("ablong suffix"), pending.receive("v", 1, 3, bytes("b")));
		assertArrayEquals(bytes("abz"), pending.receive("v", 2, 3, bytes("z")));
	}

	@Test
	public void missingSlotDoesNotProduceContent() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 1, 3, bytes("b")));
		assertNull(pending.receive("v", 2, 3, bytes("c")));
		assertNull(pending.receive("v", 1, 3, bytes("replacement")));
	}

	@Test
	public void rejectsCountMismatchWithoutChangingPendingChunks() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 0, 2, bytes("a")));
		var error = assertThrows(IllegalArgumentException.class,
				() -> pending.receive("v", 1, 3, bytes("wrong")));
		assertEquals("v", error.getMessage());
		assertArrayEquals(bytes("ab"), pending.receive("v", 1, 2, bytes("b")));
	}

	@Test
	public void rejectsInvalidCountsAndIndices() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertThrows(IllegalArgumentException.class, () -> pending.receive("v", 0, 0, bytes("x")));
		assertThrows(IllegalArgumentException.class, () -> pending.receive("v", 0, -1, bytes("x")));
		assertThrows(IllegalArgumentException.class, () -> pending.receive("v", -1, 2, bytes("x")));
		assertThrows(IllegalArgumentException.class, () -> pending.receive("v", 2, 2, bytes("x")));
		assertArrayEquals(bytes("ok"), pending.receive("v", 0, 1, bytes("ok")));
	}

	@Test
	public void clearDropsEveryVersionAndAllowsSameVersionWithNewCount() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("v", 0, 2, bytes("old")));
		assertNull(pending.receive("other", 0, 2, bytes("old")));
		pending.clear();
		assertNull(pending.receive("v", 1, 3, bytes("b")));
		assertNull(pending.receive("v", 2, 3, bytes("c")));
		assertArrayEquals(bytes("abc"), pending.receive("v", 0, 3, bytes("a")));
		assertNull(pending.receive("other", 1, 2, bytes("new")));
		assertArrayEquals(bytes("freshnew"), pending.receive("other", 0, 2, bytes("fresh")));
	}

	@Test
	public void removeReleasesOnlyCompletedVersion() {
		var pending = new LcClientAnimationContents.Reassembly();
		assertNull(pending.receive("other", 0, 2, bytes("a")));
		assertArrayEquals(bytes("done"), pending.receive("v", 0, 1, bytes("done")));
		pending.remove("v");
		assertNull(pending.receive("v", 1, 2, bytes("new")));
		assertArrayEquals(bytes("ab"), pending.receive("other", 1, 2, bytes("b")));
		assertArrayEquals(bytes("freshnew"), pending.receive("v", 0, 2, bytes("fresh")));
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
