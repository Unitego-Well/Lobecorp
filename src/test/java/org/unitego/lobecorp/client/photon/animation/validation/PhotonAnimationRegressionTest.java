package org.unitego.lobecorp.client.photon.animation.validation;

import org.junit.jupiter.api.Test;

public class PhotonAnimationRegressionTest {
	@Test
	public void clockAndParameters() {
		PhotonAnimationRegression.runIndependent();
	}

	@Test
	public void guiData() {
		PhotonAnimationGuiRegression.runIndependent();
	}
}
