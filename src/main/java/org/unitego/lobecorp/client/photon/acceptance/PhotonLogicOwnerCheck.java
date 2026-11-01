package org.unitego.lobecorp.client.photon.acceptance;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.floating.FloatingViewRoot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

import java.util.function.BiConsumer;

/// 不接入ModularUI的独占真实UI树；验证通知，不打开窗口或请求焦点。
public class PhotonLogicOwnerCheck implements AutoCloseable {
    private static final String CONTROL_NAME = "photon.lobecorp.rotation_cycle";
    private final BiConsumer<String, Boolean> check;
    private EditorWindow window;
    private FXEditor nativeEditor;
    private PhotonEffectEditor extension;
    private final FloatingViewRoot floating = new FloatingViewRoot();
    private final UIElement detached = new UIElement();
    private final UIElement panel = new UIElement();
    private final Configurator control = new Configurator(CONTROL_NAME);

    public PhotonLogicOwnerCheck(BiConsumer<String, Boolean> check) {
        this.check = check;
    }

    public void run() {
        window = new EditorWindow(FXEditor::new);
        nativeEditor = (FXEditor) window.getCurrentEditor();
        extension = (PhotonEffectEditor) window.createNewEditor(PhotonEffectEditor::new);
        panel.addChild(control);
        detached.addChild(panel);
        expect("owner.unbound", control, true);
        nativeEditor.addChild(panel);
        expect("owner.native_parent", control, false);
        extension.addChild(panel);
        expect("owner.extension_parent_migration", control, true);
        nativeEditor.addChild(panel);
        expect("owner.native_reverse_migration", control, false);
        // 模拟初始化前保留的可见样式，构造器必须覆盖已有控件。
        control.setDisplay(true);
        var initializedTools = new PhotonEditorTools(nativeEditor);
        expect("owner.initialization_refreshes_existing_control", control, false);
        initializedTools.clearPreview();
        removedBoundary();
        detached.addChild(panel);
        expect("owner.rebound_unowned", control, true);
        windowSwitch();
        floatingSwitch();
        closeTabs();
    }

    private void removedBoundary() {
        boolean[] observed = {false};
        UIEventListener listener = _ -> observed[0] = control.getParent() == panel
                && panel.getParent() == nativeEditor && control.getLayoutStyle().getValueImmediate(LayoutProperties.DISPLAY) == TaffyDisplay.NONE;
        control.addEventListener(UIEvents.REMOVED, listener);
        try {
            panel.removeSelf();
            check.accept("owner.removed_preserves_old_display_during_callback", observed[0]);
        } finally {
            control.removeEventListener(UIEvents.REMOVED, listener);
        }
    }

    private void windowSwitch() {
        var shared = new Configurator(CONTROL_NAME);
        window.addChild(shared);
        window.showEditor(extension);
        expect("owner.window_shared_extension", shared, true);
        var local = new Configurator(CONTROL_NAME);
        extension.addChild(local);
        window.showEditor(nativeEditor);
        expect("owner.window_shared_native", shared, false);
        expect("owner.closest_editor_not_active_tab", local, true);
        window.showEditor(nativeEditor);
        expect("owner.same_window_owner", shared, false);
        window.showEditor(extension);
        expect("owner.window_switch_back", shared, true);
    }

    private void floatingSwitch() {
        floating.addChild(panel);
        floating.setHostedEditor(extension);
        expect("owner.floating_extension", control, true);
        floating.setHostedEditor(nativeEditor);
        expect("owner.floating_native", control, false);
        floating.setHostedEditor(nativeEditor);
        expect("owner.floating_same_owner", control, false);
        floating.setHostedEditor(null);
        expect("owner.floating_null", control, true);
        extension.addChild(panel);
        expect("owner.floating_dock_tree_transfer", control, true);
    }

    private void closeTabs() {
        window.showEditor(nativeEditor);
        nativeEditor.removeSelf();
        window.removeEditor(nativeEditor);
        check.accept("owner.remaining_tab_selected", window.getCurrentEditor() == extension);
        extension.removeSelf();
        window.removeEditor(extension);
        check.accept("owner.last_tab_null", window.getCurrentEditor() == null);
        var shared = window.getChildren().stream().filter(Configurator.class::isInstance).findFirst().orElseThrow();
        expect("owner.last_tab_shared_unowned", shared, true);
    }

    private void expect(String name, UIElement element, boolean visible) {
        check.accept(name, (element.getLayoutStyle().getValueImmediate(LayoutProperties.DISPLAY) != TaffyDisplay.NONE) == visible);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (var editor : new FXEditor[]{nativeEditor, extension}) {
            if (editor == null) {
                continue;
            }
            try {
                if (editor.runtime != null) {
                    editor.runtime.destroy(true);
                }
                editor.closeCurrentProject(false, null);
                editor.removeSelf();
                editor.clearAllChildren();
            } catch (RuntimeException cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else if (cleanup != failure) {
                    failure.addSuppressed(cleanup);
                }
            }
        }
        floating.clearAllChildren();
        detached.clearAllChildren();
        panel.clearAllChildren();
        if (window != null) {
            window.clearAllChildren();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
