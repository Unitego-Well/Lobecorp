package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.BakedAnimationCache;
import com.geckolib.cache.BakedModelCache;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.animation.pose.ILcAnimationQueries;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 验证本地、内容与实例隔离使用不同键，并覆盖真实批量解析与内容发布语义。
public class LcClientAnimationResourceIndexTest {
	private static final Identifier MODEL = Identifier.fromNamespaceAndPath("lobecorp", "entity/example");
	private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath("lobecorp", "entity/example");
	private static final String ANIMATION_JSON = """
			{
			  "format_version": "1.8.0",
			  "animations": {
			    "first": {
			      "loop": true,
			      "animation_length": 2.0,
			      "bones": {
			        "root": {
			          "position": {
			            "0.0": [0, 0, 0],
			            "2.0": [16, 0, 0]
			          }
			        }
			      }
			    },
			    "second": {
			      "loop": false,
			      "animation_length": 1.0,
			      "bones": {
			        "root": {
			          "rotation": {
			            "0.0": [0, 0, 0],
			            "1.0": [0, 90, 0]
			          }
			        }
			      }
			    },
			    "broken": {
			      "animation_length": 1.0,
			      "bones": {
			        "root": {
			          "position": {
			            "0.0": [0, 0, 0],
			            "1.0": ["1 + (", 0, 0]
			          }
			        }
			      }
			    }
			  }
			}
			""";
	private static final ILcAnimationQueries QUERIES = _ -> 0;


	@Test
	public void localClipKeyChangesWithResourceRevision() {
		var first = new LcClientAnimationResourceIndex.LocalClipKey(1, ANIMATION, "idle");
		var second = new LcClientAnimationResourceIndex.LocalClipKey(2, ANIMATION, "idle");
		assertNotEquals(first, second);
	}

	@Test
	public void localSkeletonKeyChangesWithResourceRevision() {
		var first = new LcClientAnimationResourceIndex.LocalSkeletonKey(1, MODEL);
		var second = new LcClientAnimationResourceIndex.LocalSkeletonKey(2, MODEL);
		assertNotEquals(first, second);
	}

	@Test
	public void contentClipKeyUsesFullVersionInsteadOfResourceIdentity() {
		var first = new LcClientAnimationResourceIndex.ContentClipKey("version-a", "idle");
		var second = new LcClientAnimationResourceIndex.ContentClipKey("version-b", "idle");
		assertNotEquals(first, second);
	}

	@Test
	public void contentSkeletonKeyUsesFullVersionInsteadOfModelIdentity() {
		var first = new LcClientAnimationResourceIndex.ContentSkeletonKey("version-a");
		var second = new LcClientAnimationResourceIndex.ContentSkeletonKey("version-b");
		assertNotEquals(first, second);
	}

	@Test
	public void contentVersionChangesWhenModelOrAnimationContentChanges() {
		var first = LcAnimationContent.of(MODEL, ANIMATION, "{\"a\":1}", "{\"b\":1}");
		var modelChanged = LcAnimationContent.of(MODEL, ANIMATION, "{\"a\":2}", "{\"b\":1}");
		var animationChanged = LcAnimationContent.of(MODEL, ANIMATION, "{\"a\":1}", "{\"b\":2}");
		assertNotEquals(first.version(), modelChanged.version());
		assertNotEquals(first.version(), animationChanged.version());
	}

	@Test
	public void preparedIndexExposesOnlyImmutableMaps() {
		var models = new BakedModelCache(Map.of());
		var animations = new BakedAnimationCache(Map.of());
		var index = new LcClientAnimationResourceIndex(1, models, animations, Map.of(), Map.of(), Map.of(), Map.of(),
				Map.of());
		assertThrows(UnsupportedOperationException.class, () -> index.localModels().remove(MODEL));
		assertTrue(index.failures().isEmpty());
		assertEquals(1, index.revision());
	}


	@Test
	public void lateGenerationCannotPublishAfterARequestIsCleared() {
		LcClientAnimationResources.clear();
		var staleRevision = LcClientAnimationResources.beginGeneration();
		LcClientAnimationResources.beginGeneration();
		var stale = new LcClientAnimationResourceIndex(staleRevision,
				new BakedModelCache(Map.of()), new BakedAnimationCache(Map.of()), Map.of(), Map.of(), Map.of(),
				Map.of(), Map.of());
		LcClientAnimationResources.install(stale);
		assertNull(LcClientAnimationResources.currentIndex());
	}

	@Test
	public void batchParseKeepsSuccessfulClipsAndIsolatesBrokenClip() {
		var batch = LcAnimationClip.parseAll(ANIMATION_JSON);
		var first = batch.clips().get("first");
		var second = batch.clips().get("second");
		assertNotNull(first);
		assertNotNull(second);
		assertTrue(batch.failures().containsKey("broken"));
		assertTrue(first.looping());
		assertFalse(second.looping());
		assertEquals(2, first.durationSeconds());
		assertEquals(1, second.durationSeconds());
	}

	@Test
	public void batchParseMatchesSingleClipResultsAndEasing() {
		var batch = LcAnimationClip.parseAll(ANIMATION_JSON);
		var single = LcAnimationClip.parse(ANIMATION_JSON, "first");
		var batched = batch.clips().get("first");
		assertNotNull(batched);
		assertEquals(single.durationSeconds(), batched.durationSeconds());
		assertEquals(single.looping(), batched.looping());
		var expected = single.sample(1, QUERIES);
		var actual = batched.sample(1, QUERIES);
		assertEquals(expected.bones().keySet(), actual.bones().keySet());
		var expectedRoot = expected.get("root");
		var actualRoot = actual.get("root");
		assertEquals(expectedRoot.position(), actualRoot.position());
		assertEquals(expectedRoot.scale(), actualRoot.scale());
		assertEquals(expected.rotationAngles("root"), actual.rotationAngles("root"));
	}

	@Test
	public void contentPreparationIsolatesStandardBakeAndBatchCompile() {
		var content = LcAnimationContent.of(MODEL, ANIMATION, "{}", ANIMATION_JSON);
		var prepared = LcClientAnimationResourceIndex.prepareContent(content, _ -> {
			throw new IllegalStateException("bake-failed");
		});
		assertNull(prepared.animations());
		assertTrue(prepared.failures().get("animation:" + ANIMATION).contains("bake-failed"));
		assertNotNull(prepared.clips().get(new LcClientAnimationResourceIndex.ContentClipKey(content.version(), "first")));
		assertNotNull(prepared.clips().get(new LcClientAnimationResourceIndex.ContentClipKey(content.version(), "second")));
		assertTrue(prepared.failures().containsKey("clip:" + ANIMATION + "/broken"));
	}

	@Test
	public void contentPreparationIsolatesBatchCompileWhenStandardBakeSucceeds() {
		var content = LcAnimationContent.of(MODEL, ANIMATION, "{}", ANIMATION_JSON);
		var prepared = LcClientAnimationResourceIndex.prepareContent(content);
		assertNotNull(prepared.animations());
		assertTrue(prepared.failures().containsKey("clip:" + ANIMATION + "/broken"));
		assertNotNull(prepared.clips().get(new LcClientAnimationResourceIndex.ContentClipKey(content.version(), "first")));
	}

	@Test
	public void sameAnimationIdWithDifferentVersionDoesNotReusePreparation() {
		LcClientAnimationResources.clear();
		var first = LcAnimationContent.of(MODEL, ANIMATION, "{}", ANIMATION_JSON);
		var second = LcAnimationContent.of(MODEL, ANIMATION, "{}",
				ANIMATION_JSON.replace("\"second\"", "\"second_changed\""));
		var firstPrepared = LcClientAnimationResources.prepareContent(first);
		var secondPrepared = LcClientAnimationResources.prepareContent(second);
		assertNotEquals(first.version(), second.version());
		assertNotSame(firstPrepared, secondPrepared);
		assertNull(secondPrepared.clips().get(new LcClientAnimationResourceIndex.ContentClipKey(second.version(), "second")));
	}


	@Test
	public void sameServerVersionReusesPreparation() {
		LcClientAnimationResources.clear();
		var content = LcAnimationContent.of(MODEL, ANIMATION, "{}", ANIMATION_JSON);
		var first = LcClientAnimationResources.prepareContent(content);
		var second = LcClientAnimationResources.prepareContent(content);
		assertSame(first, second);
	}

}
