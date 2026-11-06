package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

/// 仅用于真实光束与十五组成件的视觉诊断，不参与性能门槛或正式 A/B 统计。
public class PhotonQuietVisualScenario implements BenchClientScenario {
    @Override
    public void setup(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.setup(context, PhotonQuietMatrixCapture.Variant.LASER3);
    }

    @Override
    public BenchClientStepResult measure(BenchClientContext context) throws Exception {
        return PhotonQuietMatrixCapture.step(context);
    }

    @Override
    public void verify(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.verifyVisual(context);
    }

    @Override
    public void teardown(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.teardown(context);
    }
}
