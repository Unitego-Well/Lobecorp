package org.unitego.lobecorp.client.bench;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;

/// 为标准MCP端到端导出提供独立游戏宿主；外部证据核对后才结束。
public class PhotonBridgeContractScenario implements BenchClientScenario {
    public static final String ID = "lobecorp.photon-11-bridge-contract";
    public static volatile boolean verified;
    private EditorWindow window;

    @Override
    public void setup(BenchClientContext context) {
        verified = false;
        window = EditorWindow.open(PhotonEffectEditor.WINDOW_ID, PhotonEffectEditor::new);
        window.hostAsScreen();
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
    public BenchClientStepResult measure(BenchClientContext context) {
        return verified ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
    }

    @Override
    public void verify(BenchClientContext context) {
        if (!verified) {
            throw new IllegalStateException("MCP导出证据尚未核对");
        }
    }

    @Override
    public void teardown(BenchClientContext context) {
        if (window != null) {
            window.closeWindow();
        }
    }
}
