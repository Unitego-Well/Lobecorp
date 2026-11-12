package org.unitego.lobecorp.world.horde;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.config.client.ClientConfig;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HordeAdaptiveConfigMigrationTest {
    @TempDir
    private Path directory;

    @Test
    public void oldAdaptiveFileKeepsRetainedAndUnrelatedValues() {
        var prefix = "events.midnight_cleanup.wave.display.";
        var obsolete = List.of("nativeGroupedMotion", "nativeSelectedScatter", "irisSelectedScatter",
                "nativeDepthScissor", "irisDepthScissor");
        try (var config = CommentedFileConfig.builder(directory.resolve("client.toml")).sync().build()) {
            ClientConfig.specification().correct(config);
            config.set(prefix + "adaptive.irisGroupedMotion", false);
            config.set(prefix + "layerCount", 7);
            config.set(prefix + "rowSpacing", 2.5d);
            for (var key : obsolete) {
                config.set(prefix + "adaptive." + key, true);
            }
            config.save();
            config.load();
            ClientConfig.specification().correct(config);
            assertEquals(Boolean.FALSE, config.get(prefix + "adaptive.irisGroupedMotion"));
            assertEquals(7, ((Number) config.get(prefix + "layerCount")).intValue());
            assertEquals(2.5d, ((Number) config.get(prefix + "rowSpacing")).doubleValue());
            for (var key : obsolete) {
                assertFalse(config.contains(prefix + "adaptive." + key));
            }
        }
    }
}
