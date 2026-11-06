package org.unitego.lobecorp.util.photon.editor;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonProjectFingerprintTest {
    @Test
    public void fingerprintIgnoresKeyOrderButPreservesTypesAndEveryAssetByte() {
        var first = new CompoundTag();
        first.putString("future", "preserve");
        first.putByteArray("asset", new byte[]{0, 1, -1, 42});
        var second = new CompoundTag();
        second.putByteArray("asset", new byte[]{0, 1, -1, 42});
        second.putString("future", "preserve");
        assertEquals(PhotonEffectProjectUtil.fingerprint(first), PhotonEffectProjectUtil.fingerprint(second));
        second.putByteArray("asset", new byte[]{0, 1, -1, 43});
        assertNotEquals(PhotonEffectProjectUtil.fingerprint(first), PhotonEffectProjectUtil.fingerprint(second));
        first.putInt("value", 1);
        second = first.copy();
        second.putLong("value", 1);
        assertNotEquals(PhotonEffectProjectUtil.fingerprint(first), PhotonEffectProjectUtil.fingerprint(second));
    }
}
