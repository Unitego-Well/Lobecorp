package org.unitego.lobecorp.client.hud;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.hud.host.HudFrame;
import org.unitego.lobecorp.client.hud.host.HudSession;
import org.unitego.lobecorp.client.hud.host.IHudHost;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HudLayerManagerTest {
	private final HudLayerManager manager = HudLayerManager.INSTANCE;
	private Identifier id;

	@BeforeEach
	public void resetManager() {
		manager.clear();
		id = Identifier.fromNamespaceAndPath("lobecorp", "test_hud_" + UUID.randomUUID().toString().replace("-", ""));
	}

	@Test
	public void duplicateRegistrationIsRejectedAndOnlyFirstFactoryIsUsed() {
		var first = new TestHost();
		manager.register(id, () -> true, () -> first);

		assertThrows(IllegalArgumentException.class, () -> manager.register(id, () -> true, TestHost::new));
		assertEquals(1, manager.contains(id) ? 1 : 0);
	}

	@Test
	public void renderCreatesHostOnceThenTemporarilyHiddenDoesNotReleaseIt() {
		var level = new Object();
		var player = new Object();
		var host = new TestHost();
		manager.register(id, () -> true, () -> host);

		manager.renderForSession(id, frame(), session(level, player));
		assertEquals(1, host.initialized);
		assertEquals(1, host.rendered);

		manager.setTemporarilyHidden(id, true);
		manager.renderForSession(id, frame(), session(level, player));
		assertEquals(1, host.initialized);
		assertEquals(1, host.rendered);
		assertEquals(0, host.released);

		manager.setTemporarilyHidden(id, false);
		manager.renderForSession(id, frame(), session(level, player));
		assertEquals(2, host.initialized);
		assertEquals(2, host.rendered);
		assertEquals(0, host.released);
	}

	@Test
	public void sameLevelReplacementPlayerReleasesOnceAndCreatesNewHost() {
		var level = new Object();
		var firstPlayer = new Object();
		var secondPlayer = new Object();
		var first = new TestHost();
		var second = new TestHost();
		var next = new ArrayList<>(List.of(first, second));
		manager.register(id, () -> true, next::removeFirst);

		manager.renderForSession(id, frame(), session(level, firstPlayer));
		manager.setTemporarilyHidden(id, true);
		manager.renderForSession(id, frame(), session(level, secondPlayer));
		assertEquals(1, first.released);
		assertEquals(0, second.released);

		manager.renderForSession(id, frame(), session(level, secondPlayer));
		assertEquals(2, second.initialized);
		assertEquals(2, second.rendered);
	}

	@Test
	public void invisibleLayerDoesNotCreateHostAndClearResetsTemporaryState() {
		var level = new Object();
		var player = new Object();
		var created = new ArrayList<TestHost>();
		var visible = new boolean[]{false};
		manager.register(id, () -> visible[0], () -> {
			var host = new TestHost();
			created.add(host);
			return host;
		});

		manager.renderForSession(id, frame(), session(level, player));
		assertTrue(created.isEmpty());

		visible[0] = true;
		manager.renderForSession(id, frame(), session(level, player));
		manager.setTemporarilyHidden(id, true);
		manager.clear();
		assertEquals(1, created.getFirst().released);

		manager.renderForSession(id, frame(), session(level, player));
		assertEquals(2, created.size());
		assertEquals(1, created.getLast().rendered);
	}

	@Test
	public void failedInitializationReleasesCreatedHostAndDoesNotRetainIt() {
		var level = new Object();
		var player = new Object();
		var first = new TestHost();
		first.failInitialization = true;
		var second = new TestHost();
		var next = new ArrayList<>(List.of(first, second));
		manager.register(id, () -> true, next::removeFirst);

		assertThrows(IllegalStateException.class,
				() -> manager.renderForSession(id, frame(), session(level, player)));
		assertTrue(first.released > 0);

		manager.renderForSession(id, frame(), session(level, player));
		assertEquals(1, second.rendered);
	}

	@Test
	public void clearReleasesCurrentHostAndRepeatedClearDoesNotRepeatCallback() {
		var level = new Object();
		var player = new Object();
		var host = new TestHost();
		manager.register(id, () -> true, () -> host);
		manager.renderForSession(id, frame(), session(level, player));

		manager.clear();
		manager.clear();
		assertEquals(1, host.released);
	}

	@Test
	public void releaseFailureStillResetsSessionAndAttemptsAllHosts() {
		var level = new Object();
		var firstPlayer = new Object();
		var secondPlayer = new Object();
		var secondId = id.withSuffix("_second");
		var firstHosts = new ArrayList<TestHost>();
		var secondHosts = new ArrayList<TestHost>();
		manager.register(id, () -> true, () -> {
			var host = new TestHost();
			host.throwOnRelease = true;
			firstHosts.add(host);
			return host;
		});
		manager.register(secondId, () -> true, () -> {
			var host = new TestHost();
			secondHosts.add(host);
			return host;
		});

		manager.renderForSession(id, frame(), session(level, firstPlayer));
		manager.renderForSession(secondId, frame(), session(level, firstPlayer));
		manager.setTemporarilyHidden(id, true);
		manager.setTemporarilyHidden(secondId, true);

		assertThrows(IllegalStateException.class,
				() -> manager.renderForSession(id, frame(), session(level, secondPlayer)));
		assertEquals(1, firstHosts.getFirst().released);
		assertEquals(1, secondHosts.getFirst().released);

		// 新会话已记录且暂隐已复位；即使上一帧释放抛错，后续宿主仍应恢复显示。
		manager.renderForSession(secondId, frame(), session(level, secondPlayer));
		assertEquals(2, secondHosts.size());
		assertEquals(1, secondHosts.getLast().rendered);
	}

	private static HudFrame frame() {
		return new HudFrame(null, null, null, null, 0, 0);
	}

	private static HudSession session(Object level, Object player) {
		return new HudSession(level, player);
	}

	private static final class TestHost implements IHudHost {
		private int initialized;
		private int rendered;
		private int released;
		private boolean failInitialization;
		private boolean throwOnRelease;

		@Override
		public void ensureInitialized(int screenWidth, int screenHeight) {
			if (failInitialization) {
				throw new IllegalStateException("初始化失败");
			}

			initialized++;
		}

		@Override
		public void render(HudFrame frame) {
			rendered++;
		}

		@Override
		public void release() {
			released++;
			if (throwOnRelease) {
				throw new IllegalStateException("释放失败");
			}
		}
	}
}
