package org.unitego.lobecorp.util;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.conductor.ConductorUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConductorIdentifierParsingTest {
	@Test
	public void rejectsMalformedSkillIdsWithoutThrowing() {
		assertNull(ConductorUtil.parseIdentifier("lobecorp:Bad ID"));
		assertNull(ConductorUtil.parseIdentifier("BadNamespace:skill"));
		assertNull(ConductorUtil.parseIdentifier("lobecorp:skill with spaces"));
	}

	@Test
	public void preservesValidNamespacedSkillIds() {
		assertEquals(Identifier.parse("lobecorp:attack"), ConductorUtil.parseIdentifier("lobecorp:attack"));
	}

	@Test
	public void preservesDefaultNamespaceResolution() {
		assertEquals(Identifier.parse("minecraft:attack"), ConductorUtil.parseIdentifier("attack"));
	}
}

