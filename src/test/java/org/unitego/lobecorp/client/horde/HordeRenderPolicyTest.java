package org.unitego.lobecorp.client.horde;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HordeRenderPolicyTest {
    @Test
    public void nativePathUsesNativeOptions() {
        var normal = new HordeRenderPolicy.Options(true, true, false);
        var iris = new HordeRenderPolicy.Options(false, false, true);
        assertEquals(new HordeRenderPolicy.Settings(false, true, true, false),
                HordeRenderPolicy.forPath(false, normal, iris));
    }

    @Test
    public void shaderPathUsesItsOwnMotionAndVisibilityOptions() {
        var normal = new HordeRenderPolicy.Options(true, true, false);
        var iris = new HordeRenderPolicy.Options(false, false, true);
        assertEquals(new HordeRenderPolicy.Settings(true, false, false, true),
                HordeRenderPolicy.forPath(true, normal, iris));
    }

    @Test
    public void inactiveShaderOptionsDoNotChangeNativeCacheKey() {
        var normal = new HordeRenderPolicy.Options(true, true, false);
        assertEquals(HordeRenderPolicy.forPath(false, normal, new HordeRenderPolicy.Options(false, false, false)),
                HordeRenderPolicy.forPath(false, normal, new HordeRenderPolicy.Options(true, true, true)));
    }

    @Test
    public void inactiveNativeOptionsDoNotChangeShaderCacheKey() {
        var iris = new HordeRenderPolicy.Options(true, false, false);
        assertEquals(HordeRenderPolicy.forPath(true, new HordeRenderPolicy.Options(false, false, false), iris),
                HordeRenderPolicy.forPath(true, new HordeRenderPolicy.Options(true, true, true), iris));
    }

    @Test
    public void shaderSwitchRequiresCacheResetEvenWithIdenticalKernelFlags() {
        var same = new HordeRenderPolicy.Options(true, false, false);
        assertNotEquals(HordeRenderPolicy.forPath(false, same, same), HordeRenderPolicy.forPath(true, same, same));
    }

    @Test
    public void everyActiveKernelOptionParticipatesInCacheKey() {
        var base = new HordeRenderPolicy.Options(true, false, false);
        var key = HordeRenderPolicy.forPath(true, base, base);
        for (var changed : new HordeRenderPolicy.Options[]{
                new HordeRenderPolicy.Options(false, false, false),
                new HordeRenderPolicy.Options(true, true, false),
                new HordeRenderPolicy.Options(true, false, true)}) {
            assertNotEquals(key, HordeRenderPolicy.forPath(true, base, changed));
        }
    }
}
