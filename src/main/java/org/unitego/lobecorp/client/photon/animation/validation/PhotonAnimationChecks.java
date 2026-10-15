package org.unitego.lobecorp.client.photon.animation.validation;

import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.client.photon.runtime.validation.PhotonEmissionPrimeRegression;

import java.util.function.IntSupplier;
import java.util.stream.Stream;

/// 工具菜单与 IDEA 调试入口共用的独立检查目录；每项自行创建隔离夹具。
public enum PhotonAnimationChecks {
	CLOCK_PARAMETERS(PhotonEditorTextUtil.key("test.clock_parameters"), PhotonAnimationRegression::runIndependent),
	PROPERTY_SLOTS(PhotonAnimationTextUtil.key("validation"), PhotonAnimationRegression::runPropertySlots),
	INSTANCES(PhotonEditorTextUtil.key("test.instances"), PhotonAnimationRegression::runInstances),
	RUNTIME(PhotonEditorTextUtil.key("test.runtime"), PhotonAnimationRuntimeRegression::runLoaded),
	GUI(PhotonEditorTextUtil.key("test.gui"), PhotonAnimationGuiRegression::runLoaded),
	TIMELINE(PhotonEditorTextUtil.key("test.timeline"), PhotonAnimationTimelineRegression::runLoaded),
	INITIAL_EMISSION(PhotonEditorTextUtil.key("test.initial_emission"), PhotonEmissionPrimeRegression::runLoaded);

	private final String title;
	private final IntSupplier run;

	@SuppressWarnings("UnnecessaryModifier") // 项目规范要求明确最小可见性。
	private PhotonAnimationChecks(String title, IntSupplier run) {
		this.title = title;
		this.run = run;
	}

	public String title() {
		return title;
	}

	public int run() {
		return run.getAsInt();
	}

	/// 保留原加载入口的顺序和首次失败即抛出的行为。
	public static int runAll() {
		return Stream.of(values()).mapToInt(PhotonAnimationChecks::run).sum();
	}
}
