package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonCompiledObject;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;

import java.io.PrintWriter;
import java.io.StringWriter;

/// 真实客户端模型的静态程序共享与实例隔离契约；不替代游戏验收。
public class PhotonStaticModelContractCheck {
	public static JsonObject verify() {
		var report = new JsonObject();
		report.addProperty("schema", "photon-static-model-contract-v1");
		var cases = new JsonArray();
		var result = new JsonObject();
		result.addProperty("name", "shared_program_independent_pose_and_workspace");
		result.addProperty("expected", "同来源共享 sampler/topology，工作区与姿态独立，关闭一例不影响另一例");
		var actual = new JsonObject();
		result.add("actual", actual);
		var cleanup = new JsonObject();
		result.add("cleanup", cleanup);
		PhotonModelOutput first = null;
		PhotonModelOutput second = null;
		PhotonModelOutput independentlyCompiled = null;
		boolean passed = false;

		try {
			var client = Minecraft.getInstance();
			require(client.isSameThread() && client.level != null, "必须在已有世界的客户端线程执行");
			var authored = new PhotonModelOutput();
			authored.config.source = PhotonModelOutputConfig.Source.RESOURCE;
			authored.config.resource = Identifier.parse("lobecorp:models/photon/the_queen_of_hatred/beam/laser_body.obj");
			var compiled = PhotonCompiledObject.compile(authored);
			var prepared = compiled.preparedModel();
			require(prepared != null && prepared.source() != null, "真实模型必须有静态准备来源");
			prepared.prepareStatic();
			var sampler = prepared.source().sampler();
			require(sampler == prepared.source().sampler(), "静态准备必须复用采样程序");
			var independentPlan = PhotonCompiledObject.compile(authored);
			var independentPrepared = independentPlan.preparedModel();
			require(independentPrepared != null && independentPrepared.source() != null
					&& independentPrepared.source() != prepared.source(), "独立编译必须创建不同准备来源");
			independentPrepared.prepareStatic();
			require(independentPrepared.source().sampler() == sampler,
					"同内容的独立编译计划必须复用已有采样程序");
			independentlyCompiled = (PhotonModelOutput) independentPlan.instantiate();
			independentlyCompiled.setLevel(client.level);
			independentlyCompiled.reset();
			var independentFrame = independentlyCompiled.sampleLocal(0);
			require(independentFrame != null && !independentFrame.groups().isEmpty()
					&& independentFrame.prepared() == sampler.prepared(),
					"独立编译实例必须实际消费共享拓扑并生成非空帧");
			first = (PhotonModelOutput) compiled.instantiate();
			second = (PhotonModelOutput) compiled.instantiate();
			first.setLevel(client.level);
			second.setLevel(client.level);
			first.reset();
			second.reset();
			require(field(first, "sampler") == sampler && field(second, "sampler") == sampler,
					"两个实际模型必须消费预热的同一程序");
			require(field(first, "poseWorkspace") != field(second, "poseWorkspace"),
					"两个实际模型的工作区必须独立");
			require(field(first, "poseBuffer") != field(second, "poseBuffer"), "姿态缓冲必须独立");
			var before = second.sampleLocal(0);
			require(before != null && !before.groups().isEmpty(), "真实模型必须产生非空公开帧");
			var frozenGroups = before.groups().toString();
			first.config.opacity = 0.25f;
			var changed = first.sampleLocal(0);
			require(changed != null && changed.opacity() != before.opacity(), "不同输入必须生成不同姿态");
			require(second.sampleLocal(0) == before, "另一实例的公开帧缓存不得被污染");
			require(before.groups().toString().equals(frozenGroups), "既有公开 Frame 必须保持不变");
			require(changed.prepared() == before.prepared(), "两个实例必须共享拓扑");
			first.remove(true);
			require(second.sampleLocal(0) == before, "关闭第一例不得破坏第二例");
			actual.addProperty("independentlyCompiledSharedSampler", true);
			actual.addProperty("independentlyCompiledSharedTopology", true);
			actual.addProperty("sharedSampler", true);
			actual.addProperty("sharedTopology", true);
			actual.addProperty("distinctWorkspace", true);
			actual.addProperty("firstOpacity", changed.opacity());
			actual.addProperty("secondOpacity", before.opacity());
			actual.addProperty("publicFrameStable", true);
			passed = true;
		} catch (Throwable failure) {
			result.addProperty("failure", failure.toString());
			var stack = new StringWriter();
			failure.printStackTrace(new PrintWriter(stack));
			result.addProperty("stack", stack.toString());
		} finally {
			boolean cleaned = true;

			for (var model : new PhotonModelOutput[]{first, second, independentlyCompiled}) {
				if (model != null) {
					try {
						model.remove(true);
					} catch (Throwable failure) {
						cleaned = false;
						cleanup.addProperty("failure", failure.toString());
					}
				}
			}

			cleanup.addProperty("completed", cleaned);
			passed &= cleaned;
		}

		result.addProperty("passed", passed);
		cases.add(result);
		report.add("cases", cases);
		report.addProperty("passed", passed);
		return report;
	}

	/// 仅观察真实实例所有权，不注入缓存或更改生产字段。
	private static Object field(PhotonModelOutput model, String name) throws ReflectiveOperationException {
		var field = PhotonModelOutput.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(model);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
