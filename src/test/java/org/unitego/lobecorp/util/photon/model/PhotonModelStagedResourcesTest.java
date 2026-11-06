package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.Input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// 只读核对G1已隔离的真实输入；不重建缺失源码目录，不代表原生制作或独立包运行通过。
public class PhotonModelStagedResourcesTest {
    @Test
    public void samplesEveryVerifiedModelOccurrenceWithoutChangingInputs() throws Exception {
        var project = projectRoot();
        var inputs = project.resolve("run/g1_resource_acceptance_20261021/ldlib2/assets/g1_inputs").toRealPath();
        var manifest = JsonParser.parseString(Files.readString(inputs.resolve("manifest.json"))).getAsJsonObject();
        var rows = manifest.getAsJsonArray("rows");
        assertEquals(501, rows.size());
        int checked = 0;
        int unavailable = 0;
        int models = 0;
        for (var value : rows) {
            var row = value.getAsJsonObject();
            if (!row.get("staging").getAsString().equals("hash_verified")) {
                assertEquals("editor_state", row.get("root").getAsString());
                unavailable++;
                continue;
            }
            var source = Path.of(row.get("inputPath").getAsString()).toRealPath();
            assertTrue(source.startsWith(inputs), source.toString());
            var before = Files.readAllBytes(source);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before));
            assertEquals(row.get("sha256").getAsString(), hash, source.toString());
            var document = PhotonResourceReader.read(source);
            assertEquals(hash, document.sha256(), source.toString());
            for (var binding : PhotonModelCodec.readEmbedded(document)) {
                var definition = binding.definition();
                var sampler = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(definition));
                for (float time : List.of(0f, 5f, 100f)) {
                    var frame = sampler.sample(new Input(time, "", true, definition.referenceLength(),
                            definition.referenceWidth(), Anchor.START, 1, Map.of()), true);
                    assertFalse(frame.groups().isEmpty(), binding.path());
                    assertTrue(frame.min().x() <= frame.max().x(), binding.path());
                    assertNotNull(frame.timings());
                }
                models++;
            }
            assertArrayEquals(before, Files.readAllBytes(source), source.toString());
            checked++;
        }
        assertEquals(496, checked);
        assertEquals(5, unavailable);
        assertTrue(models > 0);
        System.out.println("G1_STAGED_CPU_SAMPLING checked=" + checked + " sourceUnavailable=" + unavailable + " models=" + models);
    }

    private static Path projectRoot() throws Exception {
        for (var start : List.of(Path.of("").toAbsolutePath(), Path.of(PhotonModelStagedResourcesTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            for (var root = start; root != null; root = root.getParent()) {
                if (Files.isRegularFile(root.resolve("docs/systems/photon-resource-baseline-20261019.json"))) {
                    return root;
                }
            }
        }
        throw new IOException("无法找到G1资源基线所在仓库。");
    }
}
