package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

public class PhotonMigrationLaserScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.photon-06-migration-laser3";

	@Override
	public void setup(BenchClientContext context) throws Exception {
		PhotonMigrationMatrixCapture.setup(context, true);
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
		return PhotonMigrationMatrixCapture.step(context);
	}

	@Override
	public void verify(BenchClientContext context) throws Exception {
		PhotonMigrationMatrixCapture.verify(context, true);
	}

	@Override
	public void teardown(BenchClientContext context) throws Exception {
		PhotonMigrationMatrixCapture.teardown(context);
	}
}
