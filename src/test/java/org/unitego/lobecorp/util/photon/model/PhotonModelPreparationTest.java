package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelPreparationTest {
	private static final String MODEL = """
			{"version":1,"vertices":[[0,0,0],[1,0,0],[0,1,0]],
			"faces":[{"vertices":[0,1,2],"uv":[[0,0],[1,0],[0,1]]}],
			"materials":{"main":{"materialData":"{type:'texture',data:{texture:'source:textures/a.png'}}"}}}
			""";

	@Test
	public void reusesEqualContentAndKeepsEarlierFramesIndependent() throws Exception {
		var cache = new PhotonModelSourceUtil.PreparationCache(4, 1024 * 1024);
		var source = StringTag.valueOf(MODEL);
		var dependencies = new CompoundTag();
		var first = cache.prepare(source, dependencies);
		assertSame(first, cache.prepare(StringTag.valueOf(MODEL), dependencies.copy()));
		var bytes = new ByteArrayTag(MODEL.getBytes(StandardCharsets.UTF_8));
		var bytePrepared = cache.prepare(bytes, dependencies);
		assertSame(bytePrepared, cache.prepare(bytes.copy(), dependencies));
		var input = new PhotonModelPoseSampler.Input(0, "", true, 1, 1, Anchor.NONE, 1, Map.of());
		var frame = first.sample(input, false);
		var reference = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(PhotonModelAssetUtil.read(source, dependencies)));
		assertEquals(reference.sample(input, false).groups(), frame.groups());
		first.sample(new PhotonModelPoseSampler.Input(0, "", true, 5, 2, Anchor.START, 0.25f, Map.of()), false);
		assertEquals(reference.sample(input, false).groups(), frame.groups());
		var exposed = first.prepared().definition().source();
		exposed.addProperty("version", -1);
		assertEquals(1, first.prepared().definition().source().get("version").getAsInt());
	}

	@Test
	public void invalidatesChangedContentAndOwnsMaterialDependencyKeys() throws Exception {
		var cache = new PhotonModelSourceUtil.PreparationCache(4, 1024 * 1024);
		var source = StringTag.valueOf(MODEL);
		var dependencies = new CompoundTag();
		var first = cache.prepare(source, dependencies);
		var material = PhotonModelAssetUtil.material("{type:'texture',data:{texture:'other:textures/b.png'}}");
		dependencies.put("main", material);
		var second = cache.prepare(source, dependencies);
		assertNotSame(first, second);
		assertTrue(second.prepared().definition().materials().get("main").materialData().contains("other:textures/b.png"));
		dependencies.remove("main");
		assertSame(first, cache.prepare(source, dependencies));
		var changed = cache.prepare(StringTag.valueOf(MODEL.replace("[1,0,0]", "[2,0,0]")), dependencies);
		assertNotSame(first, changed);
		assertEquals(2, changed.prepared().definition().vertices().get(1).x());
	}

	@Test
	public void evictsByEntryAndKeyBudgetsWithoutCachingFailures() throws Exception {
		var source = StringTag.valueOf(MODEL);
		var changed = StringTag.valueOf(MODEL.replace("[1,0,0]", "[2,0,0]"));
		var dependencies = new CompoundTag();
		var cache = new PhotonModelSourceUtil.PreparationCache(1, 1024 * 1024);
		var first = cache.prepare(source, dependencies);
		var second = cache.prepare(changed, dependencies);
		assertNotSame(first, second);
		assertNotSame(first, cache.prepare(source, dependencies));
		var tooSmall = new PhotonModelSourceUtil.PreparationCache(4, 1);
		var uncached = tooSmall.prepare(source, dependencies);
		assertNotSame(uncached, tooSmall.prepare(source, dependencies));
		assertThrows(IOException.class, () -> cache.prepare(StringTag.valueOf("{"), dependencies));
		assertNotNull(cache.prepare(source, dependencies));
	}
}
