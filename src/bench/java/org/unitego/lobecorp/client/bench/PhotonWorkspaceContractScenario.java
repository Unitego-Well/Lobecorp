package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import org.unitego.lobecorp.client.photon.acceptance.PhotonLogicWorkspaceCheck;
import org.unitego.lobecorp.Lobecorp;
import java.io.StringWriter;
import java.io.PrintWriter;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

/// 独立排查组合契约；不将本场景结果代替完整矩阵。
public class PhotonWorkspaceContractScenario implements BenchClientScenario {
	public static final String ID = "lobecorp.photon-09-workspace-contract";
	private final JsonArray assertions = new JsonArray();
	private EditorWindow window;
	private PhotonLogicWorkspaceCheck workspace;
	private boolean complete;

	@Override
	public void setup(BenchClientContext context) throws Exception {
		window = EditorWindow.open(PhotonEffectEditor.WINDOW_ID, PhotonEffectEditor::new);
		window.hostAsScreen();
		var editor = (PhotonEffectEditor) window.getCurrentEditor();
		workspace = new PhotonLogicWorkspaceCheck((name, passed) -> {
			var item = new JsonObject();
			item.addProperty("name", name);
			item.addProperty("passed", passed);
			assertions.add(item);
			if (!passed) {
				throw new AssertionError(name);
			}
		}, editor);
		try {
			workspace.begin();
		} catch (Exception | AssertionError failure) {
			write(context, failure);
			throw failure;
		}
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
		try {
			complete = workspace.step();
			if (complete) {
				write(context, null);
			}
			return complete ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
		} catch (Exception | AssertionError failure) {
			write(context, failure);
			throw failure;
		}
	}

	private void write(BenchClientContext context, Throwable failure) throws Exception {
		var report = new JsonObject();
		report.addProperty("scenario", ID);
		report.addProperty("pid", ProcessHandle.current().pid());
		report.addProperty("workspace_checks_passed", complete && failure == null);
		report.addProperty("full_requested_matrix_passed", false);
		report.add("assertions", assertions);
		if (failure != null) {
			report.addProperty("failure", failure.toString());
			var stack = new StringWriter();
			failure.printStackTrace(new PrintWriter(stack));
			report.addProperty("failure_stack", stack.toString());
			Lobecorp.LOGGER.error("组合契约检查失败", failure);
		}
		context.artifacts().write("workspace-contract.json", "application/json", report.toString());
	}

	@Override
	public void verify(BenchClientContext context) {
		if (!complete) {
			throw new IllegalStateException("组合契约尚未完成");
		}
	}

	@Override
	public void teardown(BenchClientContext context) {
		if (workspace != null) {
			workspace.close();
		}
		if (window != null) {
			window.closeWindow();
		}
	}
}
