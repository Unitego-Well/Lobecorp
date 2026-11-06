package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

/// 安静模式通用实体／特效对照场景；按 ID 选择空白、原版牛、女皇或三次真实激光。
public class PhotonQuietControlScenario implements BenchClientScenario {
    private final PhotonQuietMatrixCapture.Variant variant;

    public PhotonQuietControlScenario(PhotonQuietMatrixCapture.Variant variant) {
        this.variant = variant;
    }

    @Override
    public void setup(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.setup(context, variant);
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
        return PhotonQuietMatrixCapture.step(context);
    }

    @Override
    public void verify(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.verify(context);
    }

    @Override
    public void teardown(BenchClientContext context) throws Exception {
        PhotonQuietMatrixCapture.teardown(context);
    }
}
