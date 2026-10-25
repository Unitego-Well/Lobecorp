package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonBenchWorkloadTest {
	@Test
	public void preservesExistingSixteenLayoutAndExpandsMixedLoadsWithinBudget() {
		var old = new PhotonBenchWorkloadUtil.Layout(16, 0, 16, 0);
		assertEquals(new Vector3(-3.75f, 0, -3.75f), old.offset(0));
		assertEquals(new Vector3(3.75f, 0, 3.75f), old.offset(15));
		var extreme = new PhotonBenchWorkloadUtil.Layout(32, 32, 16, 4);
		assertEquals(64, extreme.models());
		assertEquals(new Vector3(-8.75f, 0, -8.75f), extreme.offset(0));
		assertEquals(new Vector3(8.75f, 0, 8.75f), extreme.offset(63));
		assertEquals(Vector3.ZERO, new PhotonBenchWorkloadUtil.Layout(0, 1, 16, 0).offset(0));
	}

	@Test
	public void generatedGridKeepsOriginalTopologyUvsAndSkeletalAnimation() throws Exception {
		var definition = PhotonModelCodec.read(PhotonBenchWorkloadUtil.asset(16));
		assertEquals(289, definition.vertices().size());
		assertEquals(256, definition.faces().size());
		assertEquals(289, definition.weights().size());
		assertEquals("turn", definition.clips().getFirst().id());
		assertTrue(definition.clips().getFirst().boneTracks().containsKey("root"));
		var topology = PhotonModelTopologyUtil.prepare(definition);
		assertEquals(1536, topology.groups().getFirst().indices().size());
		assertEquals(4, definition.faces().getFirst().uv().size());
		assertEquals(9, PhotonModelCodec.read(PhotonBenchWorkloadUtil.asset(2)).vertices().size());
	}

	@Test
	public void rejectsUnrepresentableAndUnboundedFixtureRequests() {
		assertThrows(IllegalArgumentException.class, () -> new PhotonBenchWorkloadUtil.Layout(65, 0, 16, 0));
		assertThrows(IllegalArgumentException.class, () -> new PhotonBenchWorkloadUtil.Layout(Integer.MAX_VALUE, 1, 16, 0));
		assertThrows(IllegalArgumentException.class, () -> new PhotonBenchWorkloadUtil.Layout(-1, 1, 16, 0));
		assertThrows(IllegalArgumentException.class, () -> new PhotonBenchWorkloadUtil.Layout(16, 16, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new PhotonBenchWorkloadUtil.Layout(16, 16, 16, 2));
		assertThrows(IllegalArgumentException.class, () -> PhotonBenchWorkloadUtil.asset(65));
		assertThrows(IndexOutOfBoundsException.class, () -> new PhotonBenchWorkloadUtil.Layout(0, 0, 16, 0).offset(0));
	}
}
