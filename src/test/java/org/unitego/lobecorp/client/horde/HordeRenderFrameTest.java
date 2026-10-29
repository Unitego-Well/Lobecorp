package org.unitego.lobecorp.client.horde;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;

import static org.junit.jupiter.api.Assertions.*;

/// 不初始化游戏或GPU，检查嵌套渲染共享外层标识的契约。
public class HordeRenderFrameTest {
    @Test
    public void nestedRenderSharesOneIdentity() {
        assertFalse(PhotonFrameCapture.renderFrameOpen());
        long previous = PhotonFrameCapture.renderFrameId();
        PhotonFrameCapture.beginFrame();
        try {
            long current = PhotonFrameCapture.renderFrameId();
            assertEquals(previous + 1, current);
            assertTrue(PhotonFrameCapture.renderFrameOpen());
            PhotonFrameCapture.beginFrame();
            try {
                assertEquals(current, PhotonFrameCapture.renderFrameId());
            } finally {
                PhotonFrameCapture.endFrame(true);
            }
            assertTrue(PhotonFrameCapture.renderFrameOpen());
            assertEquals(current, PhotonFrameCapture.renderFrameId());
        } finally {
            PhotonFrameCapture.endFrame(true);
        }
        assertFalse(PhotonFrameCapture.renderFrameOpen());
    }

    @Test
    public void failedFrameStillReleasesIdentityScope() {
        assertFalse(PhotonFrameCapture.renderFrameOpen());
        PhotonFrameCapture.beginFrame();
        PhotonFrameCapture.endFrame(false);
        assertFalse(PhotonFrameCapture.renderFrameOpen());
        long closed = PhotonFrameCapture.renderFrameId();
        PhotonFrameCapture.beginFrame();
        try {
            assertEquals(closed + 1, PhotonFrameCapture.renderFrameId());
        } finally {
            PhotonFrameCapture.endFrame(true);
        }
    }
}
