package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PhotonNamedPlaybackDefinitionUtilTest {
	@Test
	public void finalPlaybackDefinitionDisablesDefaultAndControlsChildClips() {
		var source = library("laser_start");
		var playback = PhotonNamedPlaybackDefinitionUtil.prepare(source);
		var tracks = playback.getListOrEmpty("animations").getCompound(0).orElseThrow().getListOrEmpty("tracks");
		var child = tracks.getCompound(0).orElseThrow();
		var group = tracks.getCompound(1).orElseThrow();

		assertEquals("", playback.getStringOr("defaultAnimation", "missing"));
		assertEquals("CONTROLLED", child.getCompoundOrEmpty("data").getListOrEmpty("clips")
				.getCompound(0).orElseThrow().getStringOr("mode", ""));
		assertEquals("CONTROLLED", group.getCompoundOrEmpty("data").getListOrEmpty("children")
				.getCompound(0).orElseThrow().getCompoundOrEmpty("data").getListOrEmpty("clips")
				.getCompound(0).orElseThrow().getStringOr("mode", ""));
		assertEquals("laser_start", source.getStringOr("defaultAnimation", ""));
		assertEquals("FREE", source.getListOrEmpty("animations").getCompound(0).orElseThrow()
				.getListOrEmpty("tracks").getCompound(0).orElseThrow().getCompoundOrEmpty("data")
				.getListOrEmpty("clips")
				.getCompound(0).orElseThrow().getStringOr("mode", ""));
	}

	private static CompoundTag library(String defaultAnimation) {
		var source = new CompoundTag();
		source.putString("defaultAnimation", defaultAnimation);
		var clip = new CompoundTag();
		clip.putString("mode", "FREE");
		var clips = new ListTag();
		clips.add(clip);
		var childData = new CompoundTag();
		childData.put("clips", clips);
		var child = new CompoundTag();
		child.putString("type", "lobecorp_child_animation");
		child.put("data", childData);
		var group = new CompoundTag();
		group.putString("type", "group");
		var groupData = new CompoundTag();
		var children = new ListTag();
		children.add(child.copy());
		groupData.put("children", children);
		group.put("data", groupData);
		var tracks = new ListTag();
		tracks.add(child);
		tracks.add(group);
		var animation = new CompoundTag();
		animation.put("tracks", tracks);
		var animations = new ListTag();
		animations.add(animation);
		source.put("animations", animations);
		return source;
	}
}
