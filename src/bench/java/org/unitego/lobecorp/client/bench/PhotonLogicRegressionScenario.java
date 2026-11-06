package org.unitego.lobecorp.client.bench;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.acceptance.PhotonLogicRegression;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

/// 仅执行已有逻辑断言，不代表完整矩阵或性能验收。
public class PhotonLogicRegressionScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.photon-08-logic-regression";
	private EditorWindow window;
	private PhotonLogicRegression runner;

	@Override
	public void setup(BenchClientContext context) {
		window = EditorWindow.open(PhotonEffectEditor.WINDOW_ID, () -> {
			var editor = new PhotonEffectEditor();
			return editor;
		});
		window.hostAsScreen();
		runner = PhotonLogicRegression.startBench((PhotonEffectEditor) window.getCurrentEditor());
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
		if (!runner.advanceBench()) {
			return BenchClientStepResult.CONTINUE;
		}
		context.artifacts().write("photon_logic_regression.json", "application/json", runner.benchReport().toString());
		return BenchClientStepResult.COMPLETE;
	}

	@Override
	public void verify(BenchClientContext context) {
		if (runner == null || !runner.benchReport().get("completed_checks_passed").getAsBoolean()) {
			throw new IllegalStateException("逻辑断言未全部完成通过");
		}
	}

	@Override
	public void teardown(BenchClientContext context) {
		if (runner != null) {
			runner.cancelBench();
		}
		if (window != null) {
			window.closeWindow();
		}
	}
}
