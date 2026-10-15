package org.unitego.lobecorp.config.photon.animation;

import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationMigrationUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PhotonAnimationLibrary {
	/// 启动动画的稳定 ID；空值表示由外部指令启动。
	public String defaultAnimation = "";
	public final List<PhotonAnimationDefinition> animations = new ArrayList<>();
	public final List<PhotonAnimationRule> rules = new ArrayList<>();
	private final List<String> migrationProblems = new ArrayList<>();
	private final List<String> migrationWarnings = new ArrayList<>();
	private @Nullable CompoundTag originalDefinition;

	public List<String> migrationProblems() {
		return List.copyOf(migrationProblems);
	}

	/// 已丢弃旧条目的只读报告，不参与保存阻断，也不写入新定义。
	public List<String> migrationWarnings() {
		return List.copyOf(migrationWarnings);
	}

	public void recordMigrationWarnings(List<String> warnings) {
		migrationWarnings.clear();
		migrationWarnings.addAll(warnings);
	}

	public boolean migrationBlocked() {
		return !migrationProblems.isEmpty();
	}

	public void blockMigration(CompoundTag original, List<String> problems) {
		originalDefinition = original.copy();
		migrationProblems.clear();
		migrationProblems.addAll(problems);
	}

	public @Nullable CompoundTag originalDefinition() {
		return originalDefinition == null ? null : originalDefinition.copy();
	}

	public void requireMigrated() {
		if (migrationBlocked()) {
			throw new IllegalStateException(String.join("\n", migrationProblems));
		}
	}

	public PhotonAnimationDefinition find(String idOrName) {
		return animations.stream().filter(value -> value.id.toString().equals(idOrName)
				|| value.name.equals(idOrName)).findFirst().orElse(null);
	}

	public CompoundTag write(HolderLookup.Provider provider) {
		requireMigrated();
		var tag = new CompoundTag();
		tag.putInt("version", PhotonAnimationDataUtil.VERSION);
		tag.putString("defaultAnimation", defaultAnimation);
		var animationTags = new ListTag();
		animations.forEach(animation -> animationTags.add(animation.write(provider)));
		tag.put("animations", animationTags);

		var ruleTags = new ListTag();
		rules.forEach(rule -> ruleTags.add(rule.write()));
		tag.put("rules", ruleTags);
		return tag;
	}

	public static PhotonAnimationLibrary read(HolderLookup.Provider provider, CompoundTag tag) {
		var version = tag.getIntOr("version", PhotonAnimationDataUtil.LEGACY_VERSION);

		if (version != PhotonAnimationDataUtil.VERSION) {
			return PhotonAnimationMigrationUtil.read(provider, tag, version);
		}

		var value = new PhotonAnimationLibrary();
		value.defaultAnimation = tag.getStringOr("defaultAnimation", "");

		for (var entry : tag.getListOrEmpty("animations")) {
			if (entry instanceof CompoundTag animation) {
				value.animations.add(PhotonAnimationDefinition.read(provider, animation, version));
			}
		}


		for (var entry : tag.getListOrEmpty("rules")) {
			if (entry instanceof CompoundTag rule) {
				value.rules.add(PhotonAnimationRule.read(rule));
			}
		}

		return value;
	}

	public void remapTargets(Map<String, String> ids) {
		for (var animation : animations) {
			for (var track : animation.tracks) {
				remapTrack(track, ids);
			}

			for (var binding : animation.bindings) {
				if (ids.containsKey(binding.target.toString())) {
					binding.target = UUID.fromString(ids.get(binding.target.toString()));
				}
			}
		}


	}

	private static void remapTrack(Track track, Map<String, String> ids) {
		track.targetId(remapTarget(track.targetId(), ids));

		for (var clip : track.clips()) {
			clip.targetId(remapTarget(clip.targetId(), ids));
		}

		if (!(track instanceof TrackGroup group)) {
			return;
		}

		for (var child : group.children()) {
			remapTrack(child, ids);
		}
	}

	private static UUID remapTarget(UUID target, Map<String, String> ids) {
		if (target == null || !ids.containsKey(target.toString())) {
			return target;
		}

		return UUID.fromString(ids.get(target.toString()));
	}
}
