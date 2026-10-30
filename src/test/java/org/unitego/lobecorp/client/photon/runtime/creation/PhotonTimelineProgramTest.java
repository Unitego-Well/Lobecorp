package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.core.HolderLookup;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonTimelineProgramTest {
	@Test
	public void instancesOwnMarkerAndTreeStateAndDoNotRetainAuthoredTimeline() {
		var before = PhotonTimelineProgram.decodeCounts();
		var provider = HolderLookup.Provider.create(Stream.empty());
		var authored = new Timeline();
		authored.markers().add(new Marker(12, "bookmark"));
		var program = PhotonTimelineProgram.compilePrepared(authored, provider);
		authored.markers().getFirst().tick(900);
		authored.markers().clear();

		var first = program.instantiate();
		var second = program.instantiate();
		assertEquals(12, first.markers().getFirst().tick());
		assertNotSame(first.markers(), second.markers());
		assertNotSame(first.markers().getFirst(), second.markers().getFirst());
		first.markers().getFirst().name("changed");
		first.tracks().add(new ActivatorTrack());
		assertEquals("bookmark", second.markers().getFirst().name());
		assertTrue(second.tracks().isEmpty());
		assertEquals(0, program.trackCount());
		assertTrue(program.fallbackReasons().isEmpty());
		assertEquals(before, PhotonTimelineProgram.decodeCounts());
	}

	@Test
	public void untypedTrackIsRejectedInsteadOfBecomingAnEmptyProgram() {
		var authored = new Timeline();
		authored.tracks().add(new ActivatorTrack());
		assertThrows(IllegalArgumentException.class,
				() -> PhotonTimelineProgram.compilePrepared(authored, HolderLookup.Provider.create(Stream.empty())));
	}
}

