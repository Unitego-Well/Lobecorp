package org.unitego.lobecorp.util.resource;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@NullMarked
public class PhotonAcceptancePackResourcesTest {
	private static final Identifier SHARED = Identifier.fromNamespaceAndPath("probe", "textures/particle.png");
	private static final Identifier SIDECAR = SHARED.withPath(SHARED.getPath() + ".mcmeta");
	private static final Identifier ENGINE = Identifier.fromNamespaceAndPath("probe", "textures/entity.png");

	@Test
	public void missingSelectedPngCannotFallBackToHigherPriorityBasePack() throws Exception {
		var base = new MemoryPack("base", Map.of(SHARED, new byte[]{9}, SIDECAR, new byte[]{8}, ENGINE, new byte[]{1}));
		var selected = new MemoryPack("fxpack/selected.fxpack", Map.of());
		var filtered = PhotonAcceptancePackResourcesUtil.hideResources(base, Set.of(SHARED, SIDECAR));
		try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(selected, filtered))) {
			assertTrue(manager.getResource(SHARED).isEmpty());
			assertTrue(manager.getResource(SIDECAR).isEmpty());
			assertFalse(manager.listResources("textures", _ -> true).containsKey(SHARED));
			assertEquals("base", manager.getResourceOrThrow(ENGINE).sourcePackId());
			assertEquals(base.location(), filtered.location());
			assertEquals(base.isHidden(), filtered.isHidden());
			try (var input = Objects.requireNonNull(filtered.getRootResource("pack.mcmeta")).get()) {
				assertArrayEquals(new byte[]{3}, input.readAllBytes());
			}
			try (var input = Objects.requireNonNull(filtered.getResource(PackType.SERVER_DATA, SHARED)).get()) {
				assertArrayEquals(new byte[]{9}, input.readAllBytes());
			}
		}

		assertTrue(base.closed);
		assertTrue(selected.closed);
	}

	@Test
	public void selectedBytesWinWithoutChangingNativePackOrder() throws Exception {
		var base = new MemoryPack("base", Map.of(SHARED, new byte[]{9}));
		var selected = new MemoryPack("fxpack/selected.fxpack", Map.of(SHARED, new byte[]{4}));
		var filtered = PhotonAcceptancePackResourcesUtil.hideResources(base, Set.of(SHARED, SIDECAR));
		try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(selected, filtered))) {
			var resource = manager.getResourceOrThrow(SHARED);
			assertEquals(selected.packId(), resource.sourcePackId());
			try (var input = resource.open()) {
				assertArrayEquals(new byte[]{4}, input.readAllBytes());
			}
			assertEquals(1, manager.getResourceStack(SHARED).size());
		}
	}

	private static class MemoryPack implements PackResources {
		private final PackLocationInfo location;
		private final Map<Identifier, byte[]> resources;
		private boolean closed;

		private MemoryPack(String id, Map<Identifier, byte[]> resources) {
			location = new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
			this.resources = resources;
		}

		@Override
		public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
			return () -> new ByteArrayInputStream(new byte[]{3});
		}

		@Override
		public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
			var bytes = resources.get(location);
			return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
		}

		@Override
		public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
			resources.forEach((id, bytes) -> {
				if (id.getNamespace().equals(namespace) && id.getPath().startsWith(directory)) {
					output.accept(id, () -> new ByteArrayInputStream(bytes));
				}
			});
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return Set.of("probe");
		}

		@Override
		public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
			return null;
		}

		@Override
		public PackLocationInfo location() {
			return location;
		}

		@Override
		public boolean isHidden() {
			return true;
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}
