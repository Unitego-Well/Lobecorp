package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

public class PhotonHybridBlankScenario implements BenchClientScenario {
    public static final String ID = "lobecorp.photon-05-hybrid-blank25";

    @Override
    public void setup(BenchClientContext context) throws Exception {
        PhotonHybridMatrixCapture.setup(context, false);
    }

    @Override
    public BenchClientStepResult stabilize(BenchClientContext context) {
        return BenchClientStepResult.COMPLETE;
    }

    @Override
    public BenchClientStepResult warmup(BenchClientContext context) {
        return BenchClientStepResult.COMPLETE;
    }

    @Override
    public BenchClientStepResult measure(BenchClientContext context) throws Exception {
        return PhotonHybridMatrixCapture.step(context);
    }

    @Override
    public void verify(BenchClientContext context) throws Exception {
        PhotonHybridMatrixCapture.verify(context, false);
    }

    @Override
    public void teardown(BenchClientContext context) throws Exception {
        PhotonHybridMatrixCapture.teardown(context);
    }
}
