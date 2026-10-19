package org.unitego.lobecorp.client.photon.playback;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Parameter;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ParameterType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonNamedHandleTest {
	@Test
	public void isolatesParametersAndKeepsProgressWhenDurationParameterChanges() {
		var definition = definition();
		var first = new PhotonNamedHandle(definition, 40, 1, -1, true, false, Map.of());
		var second = new PhotonNamedHandle(definition, 40, 2, -1, true, false, Map.of());
		first.advance(5);
		first.parameters(Map.of("duration", List.of(20f)));
		assertEquals(0.5, first.progress(0));
		assertEquals(20, first.authoredTime(0));
		first.advance(5);
		assertEquals(0.75, first.progress(0));
		assertEquals(0, second.progress(0));
		assertEquals(List.of(10f), second.parameter("duration"));
		assertThrows(UnsupportedOperationException.class, () -> first.parameters().clear());
	}

	@Test
	public void followsLiveParentThenFreezesItsEndedValues() {
		var definition = definition();
		var parent = new PhotonNamedHandle(definition, 40, 1, -1, false, false, Map.of());
		var child = new PhotonNamedHandle(definition, 40, 2, -1, false, false, Map.of());
		child.follow("duration", parent, "duration");
		parent.parameters(Map.of("duration", List.of(20f)));
		child.advance(0);
		assertEquals(List.of(20f), child.parameter("duration"));
		parent.advance(20);
		parent.parameters(Map.of("duration", List.of(30f)));
		child.advance(0);
		assertEquals(List.of(20f), child.parameter("duration"));
		assertThrows(IllegalArgumentException.class, () -> child.follow("duration", child, "duration"));
	}

	@Test
	public void controlledClockUsesParentWindowAndExplicitProgress() {
		var handle = new PhotonNamedHandle(definition(), 40, 1, 8, true, true, Map.of());
		handle.drive(0.5);
		handle.parameters(Map.of("duration", List.of(99f)));
		handle.advance(50);
		assertEquals(8, handle.clock().duration());
		assertEquals(20, handle.authoredTime(10));
		handle.completeControlled();
		assertEquals(40, handle.authoredTime(0));
		handle.dispose();
		assertThrows(IllegalStateException.class, () -> handle.parameters(Map.of()));
	}

	@Test
	public void rejectsUnknownParameterWithoutChangingCurrentValues() {
		var handle = new PhotonNamedHandle(definition(), 40, 1, -1, true, false, Map.of());
		var original = handle.parameters();
		assertThrows(IllegalArgumentException.class, () -> handle.parameters(Map.of("missing", List.of(5f))));
		assertEquals(original, handle.parameters());
		assertThrows(IllegalArgumentException.class, () -> handle.duration(0));
	}

	@Test
	public void blendsSelectedChannelsAndKeepsDeferredParticleTime() {
		var contribution = new PhotonValueContribution(Blend.ADD, 2, List.of(2f), List.of(3f), List.of(1f), null);
		assertEquals(4, contribution.apply(4, 0, 0, () -> 0f));
		assertEquals(11, contribution.apply(4, 2, 0, () -> 0f));
		var deferred = new PhotonValueContribution(Blend.MULTIPLY, -1, List.of(0f), List.of(1f), List.of(0f),
				(_, particleTime, _) -> particleTime * 2);
		assertEquals(2, deferred.apply(4, 0, 0.25f, () -> 0f));
		assertEquals(6, deferred.apply(4, 0, 0.75f, () -> 0f));
	}

	private static Definition definition() {
		var parameter = new Parameter(UUID.randomUUID(), "duration", ParameterType.NUMBER, List.of(10f), 0, 100);
		return new Definition(new CompoundTag(), UUID.randomUUID(), "pulse", 20, false, false, true, 0,
				Completion.HOLD, parameter.id().toString(), List.of(parameter), List.of(), List.of(), List.of());
	}
}
