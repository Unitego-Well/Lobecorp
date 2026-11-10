package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.launch.plugin.PhotonMixinPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 不加载客户端类；检查材质探针显式开关与生产元数据入口隔离。
public class PhotonMaterialMixinSelectionTest {
    private static final String PROPERTY = "lobecorp.photon.diag.material";
    private static final List<String> MIXINS = List.of("MaterialSettingMixin", "MaterialRenderTypesMixin",
            "ShaderGraphMaterialMixin", "ShaderGraphRuntimeMixin", "RenderTypeFactoryMixin",
            "PhotonPipelinesMixin", "GlDeviceMixin", "GlProgramMixin");

    @Test
    public void materialHooksRequireExplicitFlagAndRestorePriorProperty() {
        var prior = System.getProperty(PROPERTY);
        try {
            var plugin = new PhotonMixinPlugin();
            System.clearProperty(PROPERTY);
            for (var mixin : MIXINS) {
                assertFalse(plugin.shouldApplyMixin("unused", fullName(mixin)), mixin);
            }

            System.setProperty(PROPERTY, "true");
            for (var mixin : MIXINS) {
                assertTrue(plugin.shouldApplyMixin("unused", fullName(mixin)), mixin);
            }

            System.setProperty(PROPERTY, "false");
            for (var mixin : MIXINS) {
                assertFalse(plugin.shouldApplyMixin("unused", fullName(mixin)), mixin);
            }
        } finally {
            if (prior == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, prior);
            }
        }
    }

    @Test
    public void productionMetadataRemainsInstalledWithoutMaterialDiagnostics() {
        var plugin = new PhotonMixinPlugin();
        assertTrue(plugin.shouldApplyMixin("unused",
                "org.unitego.lobecorp.mixin.photon.performance.client.PhotonPersistedMetadataMixin"));
    }

    @Test
    public void allMaterialHooksAreRegisteredInClientManifest() throws IOException {
        try (var input = PhotonMaterialMixinSelectionTest.class.getResourceAsStream("/META-INF/lobecorp.mixins.json")) {
            if (input == null) {
                throw new IOException("缺少生产 Mixin 清单");
            }

            var text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (var mixin : MIXINS) {
                assertTrue(text.contains("\"photon.performance.client." + mixin + "\""), mixin);
            }
        }
    }

    private static String fullName(String mixin) {
        return "org.unitego.lobecorp.mixin.photon.performance.client." + mixin;
    }
}
