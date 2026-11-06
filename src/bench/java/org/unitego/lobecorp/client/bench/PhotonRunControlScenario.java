package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

public class PhotonRunControlScenario implements BenchClientScenario {
	private final PhotonRunMatrixCapture.Variant variant;

	public PhotonRunControlScenario(PhotonRunMatrixCapture.Variant variant) {
		this.variant = variant;
	}

	@Override
	public void setup(BenchClientContext context) {
		PhotonRunMatrixCapture.setup(context, variant);
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
		return PhotonRunMatrixCapture.step(context);
	}

	@Override
	public void verify(BenchClientContext context) {
		PhotonRunMatrixCapture.verify(context);
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		PhotonRunMatrixCapture.teardown(context);
	}
}
