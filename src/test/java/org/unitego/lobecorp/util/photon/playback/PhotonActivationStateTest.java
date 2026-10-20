package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions.StartBehavior;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions.EndBehavior;
import org.unitego.lobecorp.util.photon.playback.PhotonActivationStateUtil.Window;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonActivationStateTest {
	@Test
	public void stopEmissionKeepsSimulationAndRewindReturnsToWindow() {
		var options = new PhotonActivationOptions(true, StartBehavior.RESTART, true, EndBehavior.STOP_EMISSION, false);
		var windows = List.of(new Window(2, 5, 0));
		var ended = PhotonActivationStateUtil.evaluate(windows, options, 5);
		assertTrue(ended.active());
		assertTrue(ended.stopped());
		assertEquals(new PhotonActivationStateUtil.Boundary(2, 0), ended.restart());
		assertNull(ended.clear());
		var rewind = PhotonActivationStateUtil.evaluate(windows, options, 3);
		assertTrue(rewind.active());
		assertFalse(rewind.stopped());
	}

	@Test
	public void overlapAndTouchingIntervalsDoNotRestartAtInternalEdges() {
		var windows = List.of(new Window(0, 5, 0), new Window(3, 8, 1), new Window(8, 10, 2));
		var state = PhotonActivationStateUtil.evaluate(windows, PhotonActivationOptions.LEGACY, 9);
		assertTrue(state.active());
		assertEquals(new PhotonActivationStateUtil.Boundary(0, 0), state.restart());
		assertFalse(PhotonActivationStateUtil.evaluate(windows, PhotonActivationOptions.LEGACY, 10).active());
	}

	@Test
	public void pointControlsAcquireOwnershipOnlyAfterTheirBoundary() {
		var start = new PhotonActivationOptions(true, StartBehavior.RESTART, false, EndBehavior.CLEAR, false);
		var end = new PhotonActivationOptions(false, StartBehavior.CONTINUE, true, EndBehavior.STOP_EMISSION, true);
		var windows = List.of(new Window(2, 5, 0), new Window(8, 8, 1));
		assertFalse(PhotonActivationStateUtil.evaluate(windows, start, 1).owned());
		assertTrue(PhotonActivationStateUtil.evaluate(windows, start, 7).active());
		assertEquals(new PhotonActivationStateUtil.Boundary(8, 1), PhotonActivationStateUtil.evaluate(windows, start, 8).restart());
		assertFalse(PhotonActivationStateUtil.evaluate(windows, end, 4).owned());
		var stopped = PhotonActivationStateUtil.evaluate(windows, end, 5);
		assertTrue(stopped.active());
		assertTrue(stopped.stopped());
		assertFalse(PhotonActivationStateUtil.evaluate(windows, end, 4).owned());
	}

	@Test
	public void clearAndDisabledControlsHaveDistinctOwnership() {
		var clear = new PhotonActivationOptions(true, StartBehavior.CONTINUE, true, EndBehavior.CLEAR, true);
		var windows = List.of(new Window(2, 5, 0));
		var ended = PhotonActivationStateUtil.evaluate(windows, clear, 6);
		assertFalse(ended.active());
		assertTrue(ended.stopped());
		assertEquals(new PhotonActivationStateUtil.Boundary(5, 0), ended.clear());
		assertNull(ended.restart());
		var disabled = new PhotonActivationOptions(false, StartBehavior.RESTART, false, EndBehavior.CLEAR, true);
		assertFalse(PhotonActivationStateUtil.evaluate(windows, disabled, 6).owned());
		var original = PhotonActivationStateUtil.evaluate(windows, PhotonActivationOptions.NATIVE, 6);
		assertFalse(original.active());
		assertFalse(original.stopped());
	}

	@Test
	public void legacySingleFrameAndUnknownFieldsSurviveEditing() {
		var source = new CompoundTag();
		source.putBoolean("singleFrame", true);
		source.putString("future", "keep");
		var original = source.copy();
		var options = PhotonActivationOptions.read(source);
		assertTrue(options.startControl());
		assertFalse(options.endControl());
		assertEquals(StartBehavior.RESTART, options.startBehavior());
		assertEquals(original, source);
		var edited = new PhotonActivationOptions(false, StartBehavior.CONTINUE, true, EndBehavior.CLEAR, false).merge(source);
		assertEquals("keep", edited.getStringOr("future", ""));
		assertFalse(edited.contains("singleFrame"));
		assertFalse(PhotonActivationOptions.read(edited).startControl());
	}

	@Test
	public void invalidProtocolAndNonFiniteRangesFailWithoutChangingSource() {
		var source = new CompoundTag();
		source.putString("endBehavior", "unknown");
		var original = source.copy();
		assertThrows(IllegalArgumentException.class, () -> PhotonActivationOptions.read(source));
		assertEquals(original, source);
		source.putString("endBehavior", "ORIGINAL");
		source.putString("startControl", "true");
		assertThrows(IllegalArgumentException.class, () -> PhotonActivationOptions.read(source));
		assertThrows(IllegalArgumentException.class, () -> new Window(0, Double.NaN, 0));
		assertThrows(IllegalArgumentException.class, () -> PhotonActivationStateUtil.evaluate(List.of(), PhotonActivationOptions.NATIVE, -1));
	}

	@Test
	public void guardAllowsOnlyValidatedActivationInNativeTrackWrapper() {
		var wrapper = new CompoundTag();
		wrapper.putString("type", "activator");
		var data = new CompoundTag();
		var options = PhotonActivationOptions.LEGACY.merge(new CompoundTag());
		options.putString("future", "keep");
		data.put(PhotonActivationOptions.TAG, options);
		wrapper.put("data", data);
		var before = wrapper.copy();
		assertFalse(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		assertEquals(before, wrapper);
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(data));
		wrapper.putString("type", "animation");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		wrapper.putString("type", "activator");
		options.putString("endBehavior", "broken");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
		options.putString("endBehavior", "ORIGINAL");
		options.putString("lobecorp$future", "unsupported");
		assertTrue(PhotonEditorProjectGuardUtil.containsTag(wrapper));
	}

	@Test
	public void newestPointOverridesHeldIntervalUntilANewIntervalEntry() {
		var interval = PhotonActivationOptions.NATIVE;
		var end = new PhotonActivationOptions(false, StartBehavior.CONTINUE, true, EndBehavior.CLEAR, true);
		var ranges = List.of(new Window(0, 10, 0), new Window(20, 30, 1));
		var points = List.of(new Window(0, 5, 0));
		var stopped = PhotonActivationStateUtil.combine(List.of(
				new PhotonActivationStateUtil.Contribution(interval, PhotonActivationStateUtil.evaluate(ranges, interval, 6)),
				new PhotonActivationStateUtil.Contribution(end, PhotonActivationStateUtil.evaluate(points, end, 6))));
		assertFalse(stopped.active());
		assertEquals(1, stopped.command());
		var resumed = PhotonActivationStateUtil.combine(List.of(
				new PhotonActivationStateUtil.Contribution(interval, PhotonActivationStateUtil.evaluate(ranges, interval, 21)),
				new PhotonActivationStateUtil.Contribution(end, PhotonActivationStateUtil.evaluate(points, end, 21))));
		assertTrue(resumed.active());
		assertTrue(resumed.emitting());
		assertEquals(0, resumed.command());
	}

	@Test
	public void editingAndUnbindingKeepUnknownTrackAndClipFields() {
		var original = new CompoundTag();
		original.putString("target", "old");
		original.putString("future", "keep");
		var before = original.copy();
		var current = new CompoundTag();
		current.putString("name", "edited");
		var track = PhotonActivationCodecUtil.track(original, current);
		assertFalse(track.contains("target"));
		assertEquals("keep", track.getStringOr("future", ""));
		assertEquals(before, original);
		original.putString("clipTarget", "old");
		var clip = PhotonActivationCodecUtil.clip(original, new CompoundTag());
		assertFalse(clip.contains("clipTarget"));
		assertEquals("keep", clip.getStringOr("future", ""));
	}
}
