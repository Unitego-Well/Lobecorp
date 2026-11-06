package org.unitego.lobecorp.world.horde;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Path;

import org.unitego.lobecorp.config.client.ClientConfig;
import org.unitego.lobecorp.config.client.DamageParticleConfig;
import org.unitego.lobecorp.config.client.HealthBarStyle;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.config.client.HudStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.config.server.HordeEventConfig;
import org.unitego.lobecorp.config.server.ServerConfig;
import org.unitego.lobecorp.config.server.ShieldConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigStructureTest {
	@TempDir
	private Path directory;

	@Test
	public void freshFilesRoundTripOnlyTheNewClientAndServerTrees() {
		var clientPath = directory.resolve(ClientConfig.FILE_NAME);
		try (var config = CommentedFileConfig.builder(clientPath).sync().build()) {
			ClientConfig.specification().correct(config);
			config.save();
		}
		try (var config = CommentedFileConfig.builder(clientPath).sync().build()) {
			config.load();
			assertEquals(Boolean.TRUE, config.get("corpseBatch"));
			assertEquals(1.8d, ((Number) config.get("events.midnight_cleanup.wave.display.rowSpacing")).doubleValue());
			assertFalse(config.contains("horde"));
			assertFalse(config.contains("events.midnight_cleanup.wave.display.corpseBatch"));
		}
		var serverPath = directory.resolve(ServerConfig.FILE_NAME);
		try (var config = CommentedFileConfig.builder(serverPath).sync().build()) {
			ServerConfig.specification().correct(config);
			config.save();
		}
		try (var config = CommentedFileConfig.builder(serverPath).sync().build()) {
			config.load();
			assertEquals(128d, ((Number) config.get("events.midnight_cleanup.wave.width")).doubleValue());
			assertNotNull(config.get("combat.shields.enable_multi_shield"));
			assertFalse(config.contains("events.midnight_cleanup.wave.rowSpacing"));
			assertFalse(config.contains("events.midnight_cleanup.wave.columnSpacing"));
		}
	}

	@Test
	public void clientPathsSeparateCommonCorpseRenderingAndLocalWaveSpacing() {
		ClientConfig.specification();
		assertEquals(List.of("corpseBatch"), ClientConfig.CORPSE_BATCH.getPath());
		assertEquals(List.of("events", "midnight_cleanup", "wave", "display", "rowSpacing"),
				HordeVisualConfig.ROW_SPACING.getPath());
		assertEquals(List.of("events", "midnight_cleanup", "wave", "display", "columnSpacing"),
				HordeVisualConfig.COLUMN_SPACING.getPath());
		assertEquals(List.of("hud", "filters", "enable_low_rationality_filter"),
				PlayerHudConfig.ENABLE_LOW_RATIONALITY_FILTER.getPath());
		assertEquals(List.of("damageParticles", "merge", "mergeRadius"), DamageParticleConfig.MERGE_RADIUS.getPath());
	}

	@Test
	public void hudStyleKeysDefaultsAndEnumClassesAreStable() {
		assertNotNull(ClientConfig.specification());
		assertEquals(List.of("hud", "statusBars", "healthStyle"), PlayerHudConfig.HEALTH_STYLE.getPath());
		assertEquals(List.of("hud", "statusBars", "rationalityStyle"), PlayerHudConfig.RATIONALITY_STYLE.getPath());
		assertEquals(List.of("hud", "events", "midnight_cleanup", "timerStyle"), PlayerHudConfig.TIMER_STYLE.getPath());
		assertEquals(HealthBarStyle.VANILLA, PlayerHudConfig.HEALTH_STYLE.getDefault());
		assertEquals(HudStyle.DEFAULT, PlayerHudConfig.RATIONALITY_STYLE.getDefault());
		assertEquals(HudStyle.DEFAULT, PlayerHudConfig.TIMER_STYLE.getDefault());
		assertEquals(HealthBarStyle.class, PlayerHudConfig.HEALTH_STYLE.getSpec().getClazz());
		assertEquals(HudStyle.class, PlayerHudConfig.RATIONALITY_STYLE.getSpec().getClazz());
		assertEquals(HudStyle.class, PlayerHudConfig.TIMER_STYLE.getSpec().getClazz());
	}

	@Test
	public void hudStyleValuesRoundTripThroughTomlAndOldBooleanIsAbsent() {
		var clientPath = directory.resolve(ClientConfig.FILE_NAME);
		try (var config = CommentedFileConfig.builder(clientPath).sync().build()) {
			ClientConfig.specification().correct(config);
			config.set("hud.statusBars.healthStyle", "DEFAULT");
			config.set("hud.statusBars.rationalityStyle", "NEW");
			config.set("hud.events.midnight_cleanup.timerStyle", "NEW");
			config.save();
		}
		try (var config = CommentedFileConfig.builder(clientPath).sync().build()) {
			config.load();
			assertEquals(HealthBarStyle.DEFAULT, config.getEnum("hud.statusBars.healthStyle", HealthBarStyle.class));
			assertEquals(HudStyle.NEW, config.getEnum("hud.statusBars.rationalityStyle", HudStyle.class));
			assertEquals(HudStyle.NEW, config.getEnum("hud.events.midnight_cleanup.timerStyle", HudStyle.class));
			assertFalse(config.contains("hud.statusBars.enable_new_health_bar"));
			assertTrue(config.contains("hud.filters.enable_low_rationality_filter"));
			assertTrue(config.contains("hud.filters.enable_lobotomy_corporation_damage_filter"));
		}
	}

	@Test
	public void localSpacingKeepsItsDefaultAndRejectsInvalidValues() {
		ClientConfig.specification();
		assertEquals(1.8d, HordeVisualConfig.ROW_SPACING.getDefault());
		assertEquals(1.8d, HordeVisualConfig.COLUMN_SPACING.getDefault());
		assertFalse(HordeVisualConfig.ROW_SPACING.getSpec().test(0d));
		assertFalse(HordeVisualConfig.COLUMN_SPACING.getSpec().test(Double.NaN));
	}

	@Test
	public void serverSectionsShareOneSpecAndKeepWorldRulesSeparate() {
		var spec = ServerConfig.specification();
		assertSame(spec, ServerConfig.specification());
		assertEquals("lobecorp-server.toml", ServerConfig.FILE_NAME);
		assertEquals(List.of("combat", "shields", "enable_multi_shield"), ShieldConfig.ENABLE_MULTI_SHIELD.getPath());
		assertEquals(List.of("events", "midnight_cleanup", "wave", "width"), HordeEventConfig.WIDTH.getPath());
		assertEquals(List.of("events", "budget", "spawnWorkPerTick"), HordeEventConfig.WORK_PER_TICK.getPath());
		assertEquals(List.of("events", "network", "bytesPerPlayerTick"), HordeEventConfig.SEND_BYTES.getPath());
	}
}
