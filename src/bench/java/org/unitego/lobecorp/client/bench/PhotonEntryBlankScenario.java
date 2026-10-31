package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

public class PhotonEntryBlankScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.photon-04-entry-blank-25s";

	@Override
	public void setup(BenchClientContext context) throws Exception {
		PhotonEntry25Capture.setup(context, false);
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
		return PhotonEntry25Capture.step(context);
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		PhotonEntry25Capture.verify(context, false);
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		PhotonEntry25Capture.teardown(context);
	}
}
