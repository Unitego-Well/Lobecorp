package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;

/// 独立全局执行契约场景；仅验证四项真实 API 契约，不进入旧 05/08/09 或 Quiet 性能负载。
public class PhotonGlobalContractScenario implements BenchClientScenario {
    public static final String ID = "lobecorp.photon-11-global-contracts";
    public static final String ARTIFACT = "photon_global_contracts.json";

    private JsonObject result;

	@Override
	public void setup(BenchClientContext context) {
		result = null;
	}

	@Override
	public BenchClientStepResult stabilize(BenchClientContext context) {
		return context.environment().readiness().ready()
				? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
	}

	@Override
	public BenchClientStepResult measure(BenchClientContext context) throws Exception {
        result = new JsonObject();
        result.addProperty("schema", "photon-global-contracts-v1");
        result.addProperty("complete", false);
        var checks = new JsonArray();
        result.add("checks", checks);
        try {
            collect("named_execution", PhotonNamedExecutionContractCheck::verify, checks);
            archiveProgress(context);
            collect("animation_resource", PhotonAnimationResourceContractCheck::verify, checks);
            archiveProgress(context);
            collect("program_contract", PhotonProgramContractCheck::verify, checks);
            archiveProgress(context);
        } catch (Throwable failure) {
            var error = new JsonObject();
            error.addProperty("type", failure.getClass().getName());
            error.addProperty("message", String.valueOf(failure.getMessage()));
            var stack = new JsonArray();
            for (var frame : failure.getStackTrace()) {
                stack.add(frame.toString());
            }
            error.add("stack", stack);
            result.add("exception", error);
            result.addProperty("passed", false);
            context.artifacts().write(ARTIFACT, "application/json", result.toString());
            throw failure;
        }
        result.addProperty("passed", checks.asList().stream()
                .allMatch(value -> value.getAsJsonObject().get("passed").getAsBoolean()));
        result.addProperty("complete", true);
        context.artifacts().write(ARTIFACT, "application/json", result.toString());
        if (!result.get("passed").getAsBoolean()) {
            throw new IllegalStateException("photon_global_contract_failed");
        }
        return BenchClientStepResult.COMPLETE;
    }

    private void archiveProgress(BenchClientContext context) throws Exception {
        var partial = result.deepCopy();
        partial.addProperty("passed", result.getAsJsonArray("checks").asList().stream()
                .allMatch(value -> value.getAsJsonObject().get("passed").getAsBoolean()));
        context.artifacts().write(ARTIFACT, "application/json", partial.toString());
    }

    private static void collect(String name, Check check, JsonArray checks) {
        var item = new JsonObject();
        item.addProperty("name", name);
        try {
            var report = check.verify();
            item.addProperty("passed", report.has("passed") && report.get("passed").getAsBoolean());
            item.add("report", report);
        } catch (Throwable failure) {
            item.addProperty("passed", false);
            var error = new JsonObject();
            error.addProperty("type", failure.getClass().getName());
            error.addProperty("message", String.valueOf(failure.getMessage()));
            var stack = new JsonArray();
            for (var frame : failure.getStackTrace()) {
                stack.add(frame.toString());
            }
            error.add("stack", stack);
            item.add("exception", error);
        }
        checks.add(item);
    }

    @Override
    public void verify(BenchClientContext context) {
        if (result == null || !result.has("passed") || !result.get("passed").getAsBoolean()) {
            throw new IllegalStateException("photon_global_contract_missing");
        }
    }

    private interface Check {
        JsonObject verify();
    }
}
