package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonActivationTargetUtilTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	@Test
	public void preservesFirstTargetOrderAndLeafOccurrenceOrder() {
		var activatorA = activator(A);
		var controlB = control(clip(B, 0, 10), clip(B, 20, 30));
		var controlC = control(clip(C, 0, 5));
		var activatorB = activator(B);
		List<Track> leaves = List.of(activatorA, controlB, controlC, activatorB);

		var expected = oracle(leaves);
		var actual = PhotonActivationTargetUtil.group(leaves);

		assertEquals(keys(expected), keys(actual));
		assertSameList(expected.get(A), actual.get(A));
		assertSameList(expected.get(B), actual.get(B));
		assertSameList(expected.get(C), actual.get(C));
		assertSameList(List.of(activatorA), actual.get(A));
		assertSameList(List.of(controlB, activatorB), actual.get(B));
		assertSameList(List.of(controlC), actual.get(C));
	}

	@Test
	public void keepsDuplicateControlOccurrencesButDeduplicatesWithinOneOccurrence() {
		var control = control(clip(A, 0, 10), clip(A, 20, 30), clip(B, 0, 10));
		List<Track> leaves = List.of(control, control);

		var actual = PhotonActivationTargetUtil.group(leaves);

		assertSameList(List.of(control, control), actual.get(A));
		assertSameList(List.of(control, control), actual.get(B));
		assertSameList(oracle(leaves).get(A), actual.get(A));
		assertSameList(oracle(leaves).get(B), actual.get(B));
	}

	@Test
	public void ignoresNullTargetsAndMutedTracks() {
		var nullActivator = activator(null);
		var mutedActivator = activator(A);
		mutedActivator.mute(true);
		var nullControl = control(clip(null, 0, 10));
		var mixedControl = control(clip(null, 0, 10), clip(B, 0, 10));
		var mutedControl = control(clip(C, 0, 10));
		mutedControl.mute(true);
		List<Track> leaves = List.of(nullActivator, mutedActivator, nullControl, mixedControl, mutedControl);

		var actual = PhotonActivationTargetUtil.group(leaves);

		assertEquals(List.of(B), keys(actual));
		assertSameList(List.of(mixedControl), actual.get(B));
	}

	@Test
	public void rereadsMuteTargetAndClipsBetweenCalls() {
		var activator = activator(A);
		var control = control(clip(A, 0, 10));
		List<Track> leaves = List.of(activator, control);

		assertEquals(List.of(A), keys(PhotonActivationTargetUtil.group(leaves)));

		activator.mute(true);
		control.clips().getFirst().targetId(B);
		assertEquals(List.of(B), keys(PhotonActivationTargetUtil.group(leaves)));

		activator.mute(false);
		activator.targetId(C);
		control.clips().clear();
		assertEquals(List.of(C), keys(PhotonActivationTargetUtil.group(leaves)));

		control.clips().add(clip(A, 0, 10));
		List<Track> reordered = List.of(control, activator);
		assertEquals(List.of(A, C), keys(PhotonActivationTargetUtil.group(reordered)));
		assertSameList(List.of(control), PhotonActivationTargetUtil.group(reordered).get(A));
		assertSameList(List.of(activator), PhotonActivationTargetUtil.group(reordered).get(C));
	}

	@Test
	public void keepsOverlapFirstMatchAndHalfOpenBoundaries() {
		var first = clip(A, 0, 10);
		var second = clip(A, 5, 15);
		var control = control(first, second);

		assertSame(first, control.clipForObjectAt(A, 0));
		assertSame(first, control.clipForObjectAt(A, 5));
		assertSame(second, control.clipForObjectAt(A, 10));
		assertNull(control.clipForObjectAt(A, 15));
		assertSameList(List.of(control), PhotonActivationTargetUtil.group(List.of(control)).get(A));
	}

	@Test
	public void keepsFirstActiveControlTrackAndIgnoresControlHeader() {
		var inactive = control(clip(A, 20, 30));
		inactive.targetId(C);
		var active = control(clip(A, 0, 10));
		active.targetId(C);
		List<Track> leaves = List.of(inactive, active);

		var expected = oracle(leaves);
		var actual = PhotonActivationTargetUtil.group(leaves);

		assertEquals(keys(expected), keys(actual));
		assertSameList(List.of(inactive, active), actual.get(A));
		assertNull(actual.get(C));
		assertSame(active.clipForObjectAt(A, 5), firstActive(actual.get(A), 5));
		assertNull(firstActive(actual.get(A), 15));
	}

	@Test
	public void keepsRepeatedActivatorOccurrences() {
		var repeated = activator(A);
		List<Track> leaves = List.of(repeated, repeated);

		var expected = oracle(leaves);
		var actual = PhotonActivationTargetUtil.group(leaves);

		assertEquals(keys(expected), keys(actual));
		assertSameList(List.of(repeated, repeated), actual.get(A));
		assertSameList(expected.get(A), actual.get(A));
	}

	@Test
	public void groupedTargetsReadOnlyTheirOwnTracks() {
		var first = new CountingActivator(A);
		var second = new CountingActivator(B);
		var unrelated = new CountingActivator(C);
		List<Track> leaves = List.of(first, unrelated, second);

		var expected = oracle(leaves);
		first.muteReads = 0;
		second.muteReads = 0;
		unrelated.muteReads = 0;
		var actual = PhotonActivationTargetUtil.group(leaves);

		assertEquals(keys(expected), keys(actual));
		assertSameList(expected.get(A), actual.get(A));
		assertSameList(expected.get(B), actual.get(B));

		int oracleVisits = 0;
		for (var ignored : keys(expected)) {
			oracleVisits += leaves.size();
		}

		int groupedVisits = 0;
		for (var tracks : actual.values()) {
			groupedVisits += tracks.size();
		}

		assertEquals(1, first.muteReads);
		assertEquals(1, unrelated.muteReads);
		assertEquals(1, second.muteReads);
		assertTrue(groupedVisits < oracleVisits);
		assertEquals(3, groupedVisits);
		assertEquals(9, oracleVisits);
	}

	private static Map<UUID, List<Track>> oracle(List<Track> leaves) {
		var ids = new LinkedHashMap<UUID, Boolean>();

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof ActivatorTrack && track.targetId() != null) {
				ids.put(track.targetId(), true);
			} else if (track instanceof ControlTrack controls) {
				for (var clip : controls.clips()) {
					if (clip.targetId() != null) {
						ids.put(clip.targetId(), true);
					}
				}
			}
		}

		var result = new LinkedHashMap<UUID, List<Track>>();

		for (var id : ids.keySet()) {
			var tracks = new ArrayList<Track>();

			for (var track : leaves) {
				if (track.mute()) {
					continue;
				}

				if (track instanceof ActivatorTrack && id.equals(track.targetId())) {
					tracks.add(track);
				} else if (track instanceof ControlTrack controls
						&& controls.clips().stream().anyMatch(clip -> id.equals(clip.targetId()))) {
					tracks.add(track);
				}
			}

			result.put(id, tracks);
		}

		return result;
	}

	private static Clip firstActive(List<Track> tracks, double time) {
		for (var track : tracks) {
			if (track instanceof ControlTrack controls) {
				var clip = controls.clipForObjectAt(A, time);

				if (clip != null) {
					return clip;
				}
			}
		}

		return null;
	}

	private static ActivatorTrack activator(UUID target) {
		return (ActivatorTrack) new ActivatorTrack().targetId(target);
	}

	private static ControlTrack control(Clip... clips) {
		var control = new ControlTrack();

		for (var clip : clips) {
			control.clips().add(clip);
		}

		return control;
	}

	private static Clip clip(UUID target, double start, double end) {
		return new Clip(start, end - start, 1).targetId(target);
	}

	private static List<UUID> keys(Map<UUID, List<Track>> map) {
		return new ArrayList<>(map.keySet());
	}

	private static void assertSameList(List<Track> expected, List<Track> actual) {
		assertNotNull(actual);
		assertEquals(expected.size(), actual.size());

		for (int index = 0; index < expected.size(); index++) {
			assertSame(expected.get(index), actual.get(index), "index " + index);
		}
	}

	private static class CountingActivator extends ActivatorTrack {
		private int muteReads;

		private CountingActivator(UUID target) {
			targetId(target);
		}

		@Override
		public boolean mute() {
			muteReads++;
			return super.mute();
		}
	}
}
