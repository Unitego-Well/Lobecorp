package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonTrackCompatibilityTest {
    @Test
    public void nativeTracksPreserveUnknownFieldsAndRejectInvalidWindows() {
        for (var type : List.of("activator", "control", "animation", "speed", "signal", "audio", "post_process")) {
            var wrapper = track(type);
            wrapper.getCompoundOrEmpty("data").putString("future", "preserve");
            var before = wrapper.copy();
            assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper), type);
            assertEquals(before, wrapper);
            var clip = new CompoundTag();
            clip.putDouble("start", -1);
            var clips = new ListTag();
            clips.add(clip);
            wrapper.getCompoundOrEmpty("data").put("clips", clips);
            assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper), type);
        }
    }

    @Test
    public void childTrackValidatesIdentityModesAndFiniteTime() {
        var wrapper = child("lobecorp_child_animation");
        assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        var data = wrapper.getCompoundOrEmpty("data");
        var clips = data.getListOrEmpty("clips");
        clips.add(clips.getFirst().copy());
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        clips.removeLast();
        ((CompoundTag) clips.getFirst()).putString("mode", "future_mode");
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        ((CompoundTag) clips.getFirst()).putString("mode", "CONTROLLED");
        ((CompoundTag) clips.getFirst()).putDouble("speed", Double.POSITIVE_INFINITY);
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
    }

    @Test
    public void projectCallsRemainIsolatedFromPublishedDefinitions() {
        var call = child("lobecorp_animation_call");
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(call));
        var preview = PhotonProjectPreviewUtil.create();
        var tracks = new ListTag();
        tracks.add(call);
        preview.put("tracks", tracks);
        var project = new CompoundTag();
        var data = new CompoundTag();
        data.put(PhotonProjectPreviewUtil.PROJECT_FIELD, preview);
        project.put("data", data);
        assertFalse(PhotonEditorProjectGuardUtil.containsProjectTag(project));
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(preview));
    }

    @Test
    public void modelTrackRejectsUnknownCompletionAndNonBooleanLoop() {
        var wrapper = track("lobecorp_model_animation");
        var clips = new ListTag();
        var clip = new CompoundTag();
        clip.putDouble("duration", 20);
        clip.putString("completion", "HOLD");
        clips.add(clip);
        wrapper.getCompoundOrEmpty("data").put("clips", clips);
        assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        clip.putInt("loop", 1);
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
        clip.putBoolean("loop", true);
        clip.putString("completion", "future_completion");
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
    }

    @Test
    public void signalsRejectInvalidTimeAndSpawnersRejectWrongVersionType() {
        var wrapper = track("signal");
        var signals = new ListTag();
        var signal = new CompoundTag();
        signal.putDouble("time", Double.NaN);
        signals.add(signal);
        wrapper.getCompoundOrEmpty("data").put("signals", signals);
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));

        var spawner = track("lobecorp_emitter_spawner");
        spawner.getCompoundOrEmpty("data").put("config", new CompoundTag());
        assertFalse(PhotonEditorProjectGuardUtil.containsTag(spawner));
        spawner.getCompoundOrEmpty("data").putString("version", "0");
        assertTrue(PhotonEditorProjectGuardUtil.containsTag(spawner));
    }

    private static CompoundTag track(String type) {
        var wrapper = new CompoundTag();
        wrapper.putString("type", type);
        wrapper.put("data", new CompoundTag());
        return wrapper;
    }

    private static CompoundTag child(String type) {
        var wrapper = track(type);
        var clip = new CompoundTag();
        clip.putString("id", UUID.randomUUID().toString());
        clip.putString("animation", "child");
        clip.putDouble("duration", 20);
        clip.putFloat("speed", 1);
        var clips = new ListTag();
        clips.add(clip);
        wrapper.getCompoundOrEmpty("data").put("clips", clips);
        return wrapper;
    }
}
