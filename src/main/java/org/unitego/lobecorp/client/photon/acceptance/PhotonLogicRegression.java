package org.unitego.lobecorp.client.photon.acceptance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/// 显式的一次性开发逻辑检查；只拥有测试视图、定义与资源来源。
public class PhotonLogicRegression {
	public static final String ENABLE_PROPERTY = "lobecorp.photon.logicRegression";
	private static boolean started;
	private final UIElement driver = new UIElement();
	private final UIEventListener tickListener = _ -> tick();
	private final UIEventListener removedListener = _ -> hostRemoved();
	private final JsonObject report = new JsonObject();
	private final JsonArray assertions = new JsonArray();
	private final PhotonLogicPreparationCheck preparation = new PhotonLogicPreparationCheck(this::check);
	private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
	private final Path output;
	private final PhotonLogicLateWorkerCheck lateWorker = new PhotonLogicLateWorkerCheck(this::check);
	private final PhotonLogicLifecycleCheck lifecycle = new PhotonLogicLifecycleCheck(this::check);
	private boolean expectingNativeClose;
	private boolean nativeCloseRemovedHost;
	private boolean finished;
	private int checkStage;
	private PhotonLogicCloseCheck closeCheck;
	private PhotonLogicWorkspaceCheck workspaceCheck;
	private PhotonEffectProjectUtil.Handle bridgeProject;
	private FXEditor host;

	private PhotonLogicRegression() {
		output = Minecraft.getInstance().gameDirectory.toPath()
				.resolve("agent-results/photon-logic-regression-20261101/report.json");
		report.addProperty("scenario", "lobecorp.photon-08-logic-regression");
		report.addProperty("pid", ProcessHandle.current().pid());
		report.addProperty("performance_evaluated", false);
		report.addProperty("failure_injection", "isolated_preparation_callback_not_gpu_fault");
		report.add("assertions", assertions);
		var omitted = new JsonArray();
		omitted.add("原生保存对话框取消：需要独立脏工程和真实UI交互");
		omitted.add("真实worker已运行时的晚到提交：当前公共API没有确定性暂停worker入口");
		omitted.add("组合工作区私有时间/播放状态保持及真实冻结文件导出");
		report.add("not_run", omitted);
	}

	public static boolean available() {
		return !FMLEnvironment.isProduction() && Boolean.getBoolean(ENABLE_PROPERTY) && !started;
	}

	public static void start(FXEditor editor) {
		startBench(editor);
	}

	public static PhotonLogicRegression startBench(FXEditor editor) {
		var client = Minecraft.getInstance();
		if (!client.isSameThread() || !available() || client.level == null || editor.getModularUI() == null) {
			throw new IllegalStateException("逻辑检查仅允许在已启用的开发客户端和游戏内编辑器中运行一次");
		}
		if (!System.getProperty("modBench.scenarios", "").isBlank()
				&& !System.getProperty("modBench.scenarios", "").equals("lobecorp.photon-08-logic-regression")) {
			throw new IllegalStateException("逻辑检查不能使用Bench采集入口");
		}

		started = true;
		var run = new PhotonLogicRegression();
		run.host = editor;
		try {
			Files.createDirectories(run.output.getParent());
			if (Files.exists(run.output)) {
				throw new IllegalStateException("逻辑报告已存在，请使用独立运行目录");
			}
			run.driver.layout(layout -> layout.width(1).height(1));
			run.driver.addEventListener(UIEvents.TICK, run.tickListener);
			run.driver.addEventListener(UIEvents.REMOVED, run.removedListener);
			editor.addChild(run.driver);
			try (var owner = new PhotonLogicOwnerCheck(run::check)) {
				owner.run();
			}
			run.preparation.begin();
		} catch (Exception | AssertionError failure) {
			run.finish(failure);
		}
		return run;
	}

	public boolean advanceBench() {
		tick();
		return finished;
	}

	public JsonObject benchReport() {
		return report.deepCopy();
	}

	public void cancelBench() {
		finish(new IllegalStateException("Bench场景结束"));
	}

	private void tick() {
		if (finished) {
			return;
		}
		try {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("逻辑等待超时，仅表示未完成，不能推断性能或业务根因");
			}
			if (checkStage == 0 && preparation.step()) {
				if (Boolean.getBoolean("lobecorp.photon.fullLogic")) {
					lateWorker.begin();
					checkStage = 1;
				} else {
					finish(null);
				}
			} else if (checkStage == 1 && lateWorker.step()) {
				report.getAsJsonArray("not_run").remove(1);
				closeCheck = new PhotonLogicCloseCheck(this::check, host);
				closeCheck.begin();
				checkStage = 2;
			} else if (checkStage == 2 && advanceCloseCheck()) {
				bridgeProject = PhotonEffectProjectUtil.create("fx", "logic_bridge_discard");
				check("close.bridge_uses_owned_host", bridgeProject.editor() == host);
				closeCheck.checkBridgeDiscardRefusal(bridgeProject);
				report.add("close_runtime_observations", new GsonBuilder().create().toJsonTree(closeCheck.runtimeObservations()));
				report.getAsJsonArray("not_run").remove(0);
				closeCheck.close();
				closeCheck = null;
				workspaceCheck = new PhotonLogicWorkspaceCheck(this::check, host);
				workspaceCheck.begin();
				checkStage = 3;
			} else if (checkStage == 3 && workspaceCheck.step()) {
				report.getAsJsonArray("not_run").remove(0);
				lifecycle.begin();
				checkStage = 4;
			} else if (checkStage == 4 && lifecycle.step()) {
				lifecycle.finalShutdown();
				checkStage = 5;
			} else if (checkStage == 5 && lifecycle.step()) {
				finish(null);
			}
		} catch (RuntimeException | AssertionError failure) {
			finish(failure);
		}
	}

	private void hostRemoved() {
		if (expectingNativeClose) {
			nativeCloseRemovedHost = true;
		} else {
			finish(new IllegalStateException("测试宿主已卸载"));
		}
	}

	private boolean advanceCloseCheck() {
		expectingNativeClose = true;
		boolean complete;
		try {
			complete = closeCheck.step();
		} finally {
			expectingNativeClose = false;
		}
		if (complete && nativeCloseRemovedHost) {
			host.getWindow().hostAsScreen();
			check("close.harness_rehosted_after_native_window_close", host.getModularUI() != null);
		}
		return complete;
	}

	private void check(String name, boolean passed) {
		var item = new JsonObject();
		item.addProperty("name", name);
		item.addProperty("passed", passed);
		assertions.add(item);
		if (!passed) {
			throw new AssertionError(name);
		}
	}

	private void finish(Throwable failure) {
		if (finished) {
			return;
		}
		finished = true;
		driver.removeEventListener(UIEvents.TICK, tickListener);
		driver.removeEventListener(UIEvents.REMOVED, removedListener);
		try {
			if (checkStage >= 4) {
				lifecycle.close();
			}
			preparation.close();
			if (checkStage > 0) {
				lateWorker.close();
			}
			if (closeCheck != null) {
				closeCheck.close();
			}
			if (workspaceCheck != null) {
				workspaceCheck.close();
			}
			if (bridgeProject != null) {
				new PhotonEffectProjectUtil.PreparedClose(bridgeProject,
						PhotonEffectProjectUtil.revision(bridgeProject.project()), true).commit();
			}
		} catch (RuntimeException cleanup) {
			if (failure == null) {
				failure = cleanup;
			} else if (cleanup != failure) {
				failure.addSuppressed(cleanup);
			}
		}
		try {
			driver.removeSelf();
		} catch (RuntimeException cleanup) {
			if (failure == null) {
				failure = cleanup;
			} else if (cleanup != failure) {
				failure.addSuppressed(cleanup);
			}
		}
		report.addProperty("completed_checks_passed", failure == null);
		report.addProperty("full_requested_matrix_passed", failure == null
				&& Boolean.getBoolean("lobecorp.photon.fullLogic") && checkStage == 5
				&& report.getAsJsonArray("not_run").isEmpty());
		if (failure != null) {
			report.addProperty("failure", failure.toString());
			var errors = new JsonArray();
			for (var suppressed : failure.getSuppressed()) {
				errors.add(suppressed.toString());
			}
			report.add("cleanup_failures", errors);
		}
		try {
			Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report),
					StandardOpenOption.CREATE_NEW);
			Lobecorp.LOGGER.info("Photon逻辑检查结果：{}", output.toAbsolutePath());
		} catch (Exception writeFailure) {
			Lobecorp.LOGGER.error("Photon逻辑检查报告写入失败：{}", output, writeFailure);
		}
		if (failure != null) {
			Lobecorp.LOGGER.error("Photon逻辑检查未完成", failure);
		}
	}
}
