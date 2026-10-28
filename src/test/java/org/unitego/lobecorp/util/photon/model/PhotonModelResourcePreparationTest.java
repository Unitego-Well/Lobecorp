package org.unitego.lobecorp.util.photon.model;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// 使用内存资源与显式代次验证资产共享，不依赖游戏注册或配置类的未提交构造入口。
public class PhotonModelResourcePreparationTest {
	private static final PackResources PACK = (PackResources) Proxy.newProxyInstance(
			PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class}, (_, method, _) -> {
				if (method.getName().equals("packId")) {
					return "resource-preparation-test";
				}

				throw new UnsupportedOperationException(method.getName());
			});
	private static final Identifier MODEL = id("model.obj");
	private static final Identifier COMPANION = id("model.mfx.nbt");
	private static final String OBJ = """
			v 0 0 0
			v 1 0 0
			v 0 1 0
			vt 0.1 0.2
			vt 0.3 0.4
			vt 0.5 0.6
			usemtl main
			f 1/1 2/2 3/3
			""";

	@Test
	public void repeatedPreparationReadsEachSourceOnce() throws Exception {
		var cache = cache(4);
		var resources = resources();
		resources.put(COMPANION, nbt(data(2)));
		var first = cache.prepare(resources, MODEL, false, 0);
		var second = cache.prepare(resources, MODEL, false, 0);

		assertSame(first, second);
		assertSame(first.sampler(), second.sampler());
		assertEquals(1, resources.requests(MODEL));
		assertEquals(1, resources.requests(COMPANION));
		assertEquals(2, resources.opens);
		assertEquals(2, first.sampler().prepared().definition().referenceLength());
	}

	@Test
	public void isolatesProviderIdentityPathAndFlipOption() throws Exception {
		var cache = cache(8);
		var firstProvider = resources();
		var secondProvider = resources();
		assertEquals(firstProvider, secondProvider);
		var first = cache.prepare(firstProvider, MODEL, false, 0);
		var second = cache.prepare(secondProvider, MODEL, false, 0);
		var flipped = cache.prepare(firstProvider, MODEL, true, 0);
		var other = id("other.obj");
		firstProvider.put(other, OBJ.getBytes(StandardCharsets.UTF_8));
		var otherPath = cache.prepare(firstProvider, other, false, 0);

		assertNotSame(first.sampler(), second.sampler());
		assertNotSame(first.sampler(), otherPath.sampler());
		assertNotSame(first.sampler(), flipped.sampler());
		assertEquals(first.sha256(), second.sha256());
		assertNotEquals(first.sha256(), flipped.sha256());
		assertEquals(0.2f, first.sampler().prepared().definition().faces().getFirst().uv().getFirst().y(), 0.00001f);
		assertEquals(0.8f, flipped.sampler().prepared().definition().faces().getFirst().uv().getFirst().y(), 0.00001f);
		assertEquals(2, firstProvider.requests(MODEL));
		assertEquals(1, secondProvider.requests(MODEL));
	}

	@Test
	public void reloadObservesCompanionAdditionChangeAndRemoval() throws Exception {
		var cache = cache(4);
		var resources = resources();
		var absent = cache.prepare(resources, MODEL, false, 0);
		resources.put(COMPANION, nbt(new CompoundTag()));
		assertSame(absent, cache.prepare(resources, MODEL, false, 0));
		var empty = cache.prepare(resources, MODEL, false, 1);
		assertNotEquals(absent.sha256(), empty.sha256());
		resources.put(COMPANION, nbt(data(3)));
		var added = cache.prepare(resources, MODEL, false, 2);
		resources.put(COMPANION, nbt(data(5)));
		var changed = cache.prepare(resources, MODEL, false, 3);

		assertNotEquals(empty.sha256(), added.sha256());
		assertNotEquals(added.sha256(), changed.sha256());
		assertEquals(3, added.sampler().prepared().definition().referenceLength());
		assertEquals(5, changed.sampler().prepared().definition().referenceLength());
		resources.contents.remove(COMPANION);
		var removed = cache.prepare(resources, MODEL, false, 4);
		assertEquals(absent.sha256(), removed.sha256());
		assertNotSame(absent.sampler(), removed.sampler());
		assertEquals(5, resources.requests(MODEL));
		assertEquals(5, resources.requests(COMPANION));
	}

	@Test
	public void reloadObservesMainContentAndClearReleasesCachedEntry() throws Exception {
		var cache = cache(4);
		var resources = resources();
		var first = cache.prepare(resources, MODEL, false, 7);
		resources.put(MODEL, OBJ.replace("v 1 0 0", "v 2 0 0").getBytes(StandardCharsets.UTF_8));
		assertSame(first, cache.prepare(resources, MODEL, false, 7));
		var changed = cache.prepare(resources, MODEL, false, 8);

		assertNotEquals(first.sha256(), changed.sha256());
		assertEquals(2, changed.sampler().prepared().definition().vertices().get(1).x());
		cache.clear();
		assertNotSame(changed.sampler(), cache.prepare(resources, MODEL, false, 8).sampler());
		assertEquals(3, resources.requests(MODEL));
	}

	@Test
	public void damagedAndMissingSourcesAreRetriedWithinSameGeneration() throws Exception {
		var cache = cache(4);
		var resources = resources();
		resources.put(COMPANION, new byte[]{10, 0});
		assertThrows(IOException.class, () -> cache.prepare(resources, MODEL, false, 0));
		resources.put(COMPANION, nbt(data(4)));
		assertEquals(4, cache.prepare(resources, MODEL, false, 0).sampler().prepared().definition().referenceLength());
		assertEquals(2, resources.requests(MODEL));
		assertEquals(2, resources.requests(COMPANION));

		var invalid = new CompoundTag();
		invalid.putString("raw_clips", "{");
		resources.put(COMPANION, nbt(invalid));
		assertThrows(IOException.class, () -> cache.prepare(resources, MODEL, false, 1));
		resources.put(COMPANION, nbt(data(6)));
		assertEquals(6, cache.prepare(resources, MODEL, false, 1).sampler().prepared().definition().referenceLength());

		resources.put(MODEL, "f 1 2 3".getBytes(StandardCharsets.UTF_8));
		assertThrows(IOException.class, () -> cache.prepare(resources, MODEL, false, 2));
		resources.put(MODEL, OBJ.getBytes(StandardCharsets.UTF_8));
		assertNotNull(cache.prepare(resources, MODEL, false, 2));
		var missing = id("missing.obj");
		assertThrows(IOException.class, () -> cache.prepare(resources, missing, false, 2));
		resources.put(missing, OBJ.getBytes(StandardCharsets.UTF_8));
		assertNotNull(cache.prepare(resources, missing, false, 2));
	}

	@Test
	public void evictsLeastRecentlyUsedEntriesAndHonorsByteBudget() throws Exception {
		var cache = cache(2);
		var resources = resources();
		var other = id("other.obj");
		var third = id("third.obj");
		resources.put(other, OBJ.getBytes(StandardCharsets.UTF_8));
		resources.put(third, OBJ.getBytes(StandardCharsets.UTF_8));
		var first = cache.prepare(resources, MODEL, false, 0);
		var second = cache.prepare(resources, other, false, 0);
		assertSame(first, cache.prepare(resources, MODEL, false, 0));
		cache.prepare(resources, third, false, 0);
		assertSame(first, cache.prepare(resources, MODEL, false, 0));
		assertNotSame(second.sampler(), cache.prepare(resources, other, false, 0).sampler());
		assertEquals(2, resources.requests(other));

		long bytes = new ByteArrayTag(PhotonModelSplitUtil.merge(OBJ, null, false).toString()
				.getBytes(StandardCharsets.UTF_8)).sizeInBytes();
		var bounded = new PhotonModelSourceUtil.ResourcePreparationCache(4, bytes);
		var budgetFirst = bounded.prepare(resources, MODEL, false, 0);
		bounded.prepare(resources, other, false, 0);
		assertNotSame(budgetFirst.sampler(), bounded.prepare(resources, MODEL, false, 0).sampler());
		var tooSmall = new PhotonModelSourceUtil.ResourcePreparationCache(4, 1);
		assertNotSame(tooSmall.prepare(resources, MODEL, false, 0).sampler(),
				tooSmall.prepare(resources, MODEL, false, 0).sampler());
		assertThrows(IllegalArgumentException.class, () -> new PhotonModelSourceUtil.ResourcePreparationCache(0, 1));
		assertThrows(IllegalArgumentException.class, () -> new PhotonModelSourceUtil.ResourcePreparationCache(1, 0));
	}

	@Test
	public void alternatingConsumersKeepFramesAndDefinitionIndependent() throws Exception {
		var cache = cache(4);
		var resources = resources();
		var data = data(1);
		data.putString("raw_morphs", """
				[{"id":"shift","weight":0,"deltas":{"1":[0,0,1]}}]
				""");
		resources.put(COMPANION, nbt(data));
		var first = cache.prepare(resources, MODEL, false, 0).sampler();
		var second = cache.prepare(resources, MODEL, false, 0).sampler();
		assertSame(first, second);
		var reference = new PhotonModelPoseSampler(PhotonModelTopologyUtil.prepare(
				PhotonModelCodec.read(PhotonModelSplitUtil.merge(OBJ, data, false))));
		var inputA = input(0, 1, 1, 1);
		var inputB = input(1, 4, 2, 0.25f);
		var frameA = first.sample(inputA, false);
		var frameB = second.sample(inputB, false);

		assertNotEquals(frameA.groups(), frameB.groups());
		assertEquals(reference.sample(inputA, false), frameA);
		first.sample(input(0.5f, 3, 5, 0.5f), false);
		assertEquals(reference.sample(inputB, false), frameB);
		assertEquals(reference.sample(inputA, false), frameA);
		assertEquals(frameA, second.sample(inputA, false));
		assertThrows(UnsupportedOperationException.class, () -> frameA.groups().clear());
		assertThrows(UnsupportedOperationException.class, () -> frameA.groups().getFirst().vertices().clear());
		var exposed = first.prepared().definition().source();
		exposed.addProperty("version", -1);
		assertEquals(1, second.prepared().definition().source().get("version").getAsInt());
	}

	private static PhotonModelPoseSampler.Input input(float morph, float length, float width, float opacity) {
		return new PhotonModelPoseSampler.Input(0, "", true, length, width, Anchor.NONE, opacity, Map.of("shift", morph));
	}

	private static PhotonModelSourceUtil.ResourcePreparationCache cache(int entries) {
		return new PhotonModelSourceUtil.ResourcePreparationCache(entries, 1024 * 1024);
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("test", "models/" + name);
	}

	private static MemoryResources resources() {
		var resources = new MemoryResources();
		resources.put(MODEL, OBJ.getBytes(StandardCharsets.UTF_8));
		return resources;
	}

	private static CompoundTag data(float length) {
		var data = new CompoundTag();
		data.putFloat("referenceLength", length);
		return data;
	}

	private static byte[] nbt(CompoundTag data) throws IOException {
		var output = new ByteArrayOutputStream();

		try (var stream = new DataOutputStream(output)) {
			NbtIo.write(data, stream);
		}

		return output.toByteArray();
	}

	/// 故意让不同 provider 值相等，验证缓存仍按对象身份隔离。
	private static class MemoryResources implements ResourceProvider {
		private final Map<Identifier, byte[]> contents = new HashMap<>();
		private final Map<Identifier, Integer> requests = new HashMap<>();
		private int opens;

		private void put(Identifier id, byte[] bytes) {
			contents.put(id, bytes);
		}

		private int requests(Identifier id) {
			return requests.getOrDefault(id, 0);
		}

		@Override
		public @NotNull Optional<Resource> getResource(@NotNull Identifier id) {
			requests.merge(id, 1, Integer::sum);
			var bytes = contents.get(id);

			if (bytes == null) {
				return Optional.empty();
			}

			return Optional.of(new Resource(PACK, () -> {
				opens++;
				return new ByteArrayInputStream(bytes);
			}));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof MemoryResources;
		}

		@Override
		public int hashCode() {
			return 1;
		}
	}
}
