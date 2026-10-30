package org.unitego.lobecorp.util.photon.creation;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonFrozenTagTest {
	@Test
	public void inputAndEveryExportHaveIndependentMutableTrees() {
		var input = new CompoundTag();
		var bytes = new ByteArrayTag(new byte[]{1, 2, 3});
		input.put("payload", bytes);
		var frozen = new PhotonFrozenTag(input);
		bytes.getAsByteArray()[0] = 9;
		input.putInt("other", 7);
		var first = frozen.compoundCopy();
		((ByteArrayTag) Objects.requireNonNull(first.get("payload"))).getAsByteArray()[1] = 8;
		var second = frozen.compoundCopy();
		assertArrayEquals(new byte[]{1, 2, 3}, ((ByteArrayTag) Objects.requireNonNull(second.get("payload"))).getAsByteArray());
		assertFalse(second.contains("other"));
		assertTrue(frozen.matches(second));
		assertFalse(frozen.matches(first));
	}

	@Test
	public void opaqueEqualityDoesNotExposeEitherOwnedTag() {
		var first = new PhotonFrozenTag(new ByteArrayTag(new byte[]{4, 5}));
		var second = new PhotonFrozenTag(first.copy());
		assertTrue(first.matches(second));
		((ByteArrayTag) second.copy()).getAsByteArray()[0] = 0;
		assertTrue(first.matches(second));
		assertThrows(IllegalStateException.class, first::compoundCopy);
	}
}
