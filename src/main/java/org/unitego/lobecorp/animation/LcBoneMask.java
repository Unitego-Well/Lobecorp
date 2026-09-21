package org.unitego.lobecorp.animation;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class LcBoneMask {
	private final List<Rule> rules;
	private final Map<BakedGeoModel, Map<String, LcChannelWeights>> resolvedMasks = new WeakHashMap<>();

	private LcBoneMask(List<Rule> rules) {
		this.rules = List.copyOf(rules);
	}

	public Map<String, LcChannelWeights> resolve(BakedGeoModel model) {
		return resolvedMasks.computeIfAbsent(model, this::resolveUncached);
	}

	private Map<String, LcChannelWeights> resolveUncached(BakedGeoModel model) {
		Map<String, LcChannelWeights> resolved = new LinkedHashMap<>();
		for (Rule rule : rules) {
			GeoBone bone = model.getBone(rule.boneName()).orElse(null);
			if (bone == null) {
				continue;
			}
			apply(resolved, bone, rule.weights(), rule.includeChildren());
		}
		return Map.copyOf(resolved);
	}

	private static void apply(Map<String, LcChannelWeights> resolved, GeoBone bone,
			LcChannelWeights weights, boolean includeChildren) {
		resolved.put(bone.name(), weights);
		if (!includeChildren) {
			return;
		}
		for (GeoBone child : bone.children()) {
			apply(resolved, child, weights, true);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private record Rule(String boneName, LcChannelWeights weights, boolean includeChildren) {
	}

	public static final class Builder {
		private final List<Rule> roots = new ArrayList<>();
		private final List<Rule> exclusions = new ArrayList<>();
		private final List<Rule> inclusions = new ArrayList<>();

		public Builder includeRoot(String boneName) {
			return includeRoot(boneName, LcChannelWeights.FULL);
		}

		public Builder includeRoot(String boneName, LcChannelWeights weights) {
			roots.add(new Rule(boneName, weights, true));
			return this;
		}

		public Builder exclude(String boneName, boolean includeChildren) {
			exclusions.add(new Rule(boneName, LcChannelWeights.NONE, includeChildren));
			return this;
		}

		public Builder include(String boneName, LcChannelWeights weights, boolean includeChildren) {
			inclusions.add(new Rule(boneName, weights, includeChildren));
			return this;
		}

		public LcBoneMask build() {
			List<Rule> ordered = new ArrayList<>(roots.size() + exclusions.size() + inclusions.size());
			ordered.addAll(roots);
			ordered.addAll(exclusions);
			ordered.addAll(inclusions);
			return new LcBoneMask(ordered);
		}
	}
}
