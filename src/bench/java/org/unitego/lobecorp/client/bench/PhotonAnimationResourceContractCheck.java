package org.unitego.lobecorp.client.bench;

import com.geckolib.cache.GeckoLibResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;
import org.unitego.lobecorp.client.animation.LcClientAnimationResourceIndex;
import org.unitego.lobecorp.client.animation.LcClientAnimationResources;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/// D 本地动画资源真实客户端契约检查；必须在已完成资源 reload 的 Minecraft 客户端调用。
public final class PhotonAnimationResourceContractCheck {
	private static final String ANIMATION_JSON = """
			{
			  "format_version": "1.8.0",
			  "animations": {
			    "first": {
			      "loop": true,
			      "animation_length": 2.0,
			      "bones": {
			        "root": {
			          "position": {
			            "0.0": [0, 0, 0],
			            "2.0": [16, 0, 0]
			          }
			        }
			      }
			    },
			    "second": {
			      "loop": false,
			      "animation_length": 1.0,
			      "bones": {
			        "root": {
			          "rotation": {
			            "0.0": [0, 0, 0],
			            "1.0": [0, 90, 0]
			          }
			        }
			      }
			    },
			    "broken": {
			      "animation_length": 1.0,
			      "bones": {
			        "root": {
			          "position": {
			            "0.0": [0, 0, 0],
			            "1.0": ["1 + (", 0, 0]
			          }
			        }
			      }
			    }
			  }
			}
			""";
	private static final ILcAnimationQueries QUERIES = _ -> 0;

	private PhotonAnimationResourceContractCheck() {
	}

	public static JsonObject verify() {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("动画资源契约检查必须在客户端线程执行");
		}

		var report = new JsonObject();
		report.addProperty("schema", "photon-animation-resource-contract-v1");
		var failures = new JsonArray();
		var checks = new JsonObject();

		try {
			require(Minecraft.getInstance().level != null, "必须在独立空世界执行动画资源契约检查");
			verifyIndex(failures, checks);
			if (failures.isEmpty()) {
				verifyMigratedContracts(failures, checks);
			}
		} catch (Throwable failure) {
			failures.add("index: " + failure);
			report.add("stack", stack(failure));
		}

		report.addProperty("passed", failures.isEmpty());
		report.addProperty("status", failures.isEmpty() ? "PASSED" : "FAILED");
		report.add("failures", failures);
		report.add("checks", checks);
		return report;
	}

	private static void verifyMigratedContracts(JsonArray failures, JsonObject checks) {
		var newer = LcClientAnimationResources.currentIndex();
		runCheck("installingAnOlderGenerationDoesNotReplaceNewerGeneration", failures, checks, () -> {
			var older = new LcClientAnimationResourceIndex(newer.revision() - 1,
					newer.modelGeneration(), newer.animationGeneration(), Map.of(), Map.of(), Map.of(),
					Map.of(), Map.of());
			LcClientAnimationResources.install(older);
			require(LcClientAnimationResources.currentIndex() == newer, "旧代次替换了实际本地索引");
		});

		var fixtureId = Identifier.fromNamespaceAndPath("lobecorp", "entity/example");
		var content = LcAnimationContent.of(fixtureId, fixtureId, "{}", ANIMATION_JSON);
		try {
			runCheck("contentClipReportsStableFailureAndMissingNameWithoutReparse", failures, checks, () -> {
				var prepared = LcClientAnimationResources.prepareContent(content);
				require(LcClientAnimationResources.contentClip(content, "first") != null, "有效 clip 缺失");
				var broken = expectedFailure(content, "broken", IllegalStateException.class);
				require(broken.equals(expectedFailure(content, "broken", IllegalStateException.class)),
						"broken 重复消息不稳定");
				require(prepared == LcClientAnimationResources.prepareContent(content), "broken 后重新准备");
				var missing = expectedFailure(content, "missing", IllegalArgumentException.class);
				require(missing.equals(expectedFailure(content, "missing", IllegalArgumentException.class)),
						"missing 重复消息不稳定");
				require(prepared == LcClientAnimationResources.prepareContent(content), "missing 后重新准备");
			});
			runCheck("clearingConnectionPreparationInvalidatesContentButKeepsLocalIndex", failures, checks, () -> {
				var prepared = LcClientAnimationResources.prepareContent(content);
				LcClientAnimationResources.clearConnectionContent();
				require(prepared != LcClientAnimationResources.prepareContent(content), "连接内容未失效");
				require(newer == LcClientAnimationResources.currentIndex(), "连接清理改变了实际本地索引");
			});
		} finally {
			// 独立空世界中验证断连生命周期，只清理服务器内容，不触碰本地索引。
			LcClientAnimationResources.clearConnectionContent();
		}
	}

	private static String expectedFailure(LcAnimationContent content, String name,
	                                      Class<? extends RuntimeException> type) {
		try {
			LcClientAnimationResources.contentClip(content, name);
		} catch (RuntimeException failure) {
			if (!type.isInstance(failure)) {
				throw failure;
			}

			require(failure.getMessage() != null, "异常缺少消息：" + name);
			return failure.getMessage();
		}

		throw new AssertionError("未抛出预期异常：" + name);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void runCheck(String name, JsonArray failures, JsonObject checks, Runnable action) {
		var result = new JsonObject();
		checks.add(name, result);
		try {
			action.run();
			result.addProperty("passed", true);
		} catch (Throwable failure) {
			result.addProperty("passed", false);
			result.add("stack", stack(failure));
			failures.add(name + ": " + failure);
		}
	}

	private static JsonArray stack(Throwable failure) {
		var stack = new StringWriter();
		failure.printStackTrace(new PrintWriter(stack));
		var lines = new JsonArray();
		for (var line : stack.toString().split("\\R")) {
			lines.add(line);
		}

		return lines;
	}

	private static void verifyIndex(JsonArray failures, JsonObject checks) {
		var index = LcClientAnimationResources.currentIndex();
		if (index == null) {
			failures.add("currentIndex 为 null：资源 reload 未发布索引");
			return;
		}

		var currentModels = GeckoLibResources.getBakedModels();
		var currentAnimations = GeckoLibResources.getBakedAnimations();
		checks.addProperty("revision", index.revision());
		checks.addProperty("latestRequestedRevision", LcClientAnimationResources.latestRequestedRevision());
		checks.addProperty("modelGenerationMatches", index.modelGeneration() == currentModels);
		checks.addProperty("animationGenerationMatches", index.animationGeneration() == currentAnimations);
		if (index.revision() != LcClientAnimationResources.latestRequestedRevision()) {
			failures.add("index.revision 不是当前最新请求代次");
		}

		if (index.modelGeneration() != currentModels) {
			failures.add("index.modelGeneration 与当前 GeckoLib 模型缓存 identity 不一致");
		}

		if (index.animationGeneration() != currentAnimations) {
			failures.add("index.animationGeneration 与当前 GeckoLib 动画缓存 identity 不一致");
		}

		checks.addProperty("modelCount", index.localModels().size());
		checks.addProperty("animationCount", index.localAnimations().size());
		checks.addProperty("clipCount", index.localClips().size());
		if (index.localModels().isEmpty()) {
			failures.add("Lobecorp 有效模型目录为空");
		}

		if (index.localAnimations().isEmpty()) {
			failures.add("Lobecorp 有效动画目录为空");
		}

		if (index.localClips().isEmpty()) {
			failures.add("Lobecorp 有效 clip 目录为空");
		}

		if (!failures.isEmpty()) {
			return;
		}

		var groups = new LinkedHashMap<Identifier, Map<String, LcAnimationClip>>();
		for (var entry : index.localClips().entrySet()) {
			groups.computeIfAbsent(entry.getKey().animationId(), _ -> new LinkedHashMap<>())
					.put(entry.getKey().name(), entry.getValue());
		}

		var selected = groups.entrySet().stream()
				.filter(entry -> entry.getValue().size() >= 2)
				.findFirst()
				.orElse(null);
		if (selected == null) {
			failures.add("没有同一动画文件中至少两个可用 clip 的实际 fixture；键=" + groups.keySet());
			return;
		}

		var animationId = selected.getKey();
		var names = new ArrayList<>(selected.getValue().keySet());
		var firstName = names.get(0);
		var secondName = names.get(1);
		var firstKey = new LcClientAnimationResourceIndex.LocalClipKey(index.revision(), animationId, firstName);
		var secondKey = new LcClientAnimationResourceIndex.LocalClipKey(index.revision(), animationId, secondName);
		var firstPrepared = index.localClips().get(firstKey);
		var secondPrepared = index.localClips().get(secondKey);
		if (firstPrepared == null || secondPrepared == null) {
			failures.add("索引中选中的实际 clip 无法按 revision 键回读：" + firstKey + ", " + secondKey);
			return;
		}

		var modelId = index.localModels().keySet().stream().findFirst()
				.orElseThrow(() -> new AssertionError("缺少实际模型 fixture"));
		var content = new LcAnimationContent(modelId, animationId, "{}", "{}", "bench");
		var first = LcClientAnimationResources.visualClip(content, firstName);
		var second = LcClientAnimationResources.visualClip(content, secondName);
		var firstAgain = LcClientAnimationResources.visualClip(content, firstName);
		var secondAgain = LcClientAnimationResources.visualClip(content, secondName);

		checks.addProperty("animationId", animationId.toString());
		checks.addProperty("firstClip", firstName);
		checks.addProperty("secondClip", secondName);
		checks.addProperty("firstPresent", first != null);
		checks.addProperty("secondPresent", second != null);
		checks.addProperty("firstReused", first != null && first == firstAgain);
		checks.addProperty("secondReused", second != null && second == secondAgain);
		checks.addProperty("firstMatchesIndex", first == firstPrepared);
		checks.addProperty("secondMatchesIndex", second == secondPrepared);
		if (first == null) {
			failures.add("visualClip 未返回索引中的第一个实际 clip：" + firstKey);
		} else if (first != firstPrepared) {
			failures.add("visualClip 第一个实际 clip 未复用索引结果：" + firstKey);
		} else if (first != firstAgain) {
			failures.add("visualClip 第一个实际 clip 重复查询未复用结果：" + firstKey);
		}

		if (second == null) {
			failures.add("visualClip 未返回索引中的第二个实际 clip：" + secondKey);
		} else if (second != secondPrepared) {
			failures.add("visualClip 第二个实际 clip 未复用索引结果：" + secondKey);
		} else if (second != secondAgain) {
			failures.add("visualClip 第二个实际 clip 重复查询未复用结果：" + secondKey);
		}

		if (first != null && second != null) {
			checks.addProperty("distinctClipIdentities", first != second);
			if (first == second) {
				failures.add("同文件两个不同名称返回了同一 clip 实例");
			}

			checks.addProperty("firstDurationSeconds", first.durationSeconds());
			checks.addProperty("secondDurationSeconds", second.durationSeconds());
			checks.addProperty("firstLooping", first.looping());
			checks.addProperty("secondLooping", second.looping());
			var firstPose = first.sample(0, QUERIES);
			var secondPose = second.sample(0, QUERIES);
			checks.addProperty("firstPoseBones", firstPose.bones().size());
			checks.addProperty("secondPoseBones", secondPose.bones().size());
		}
	}
}
