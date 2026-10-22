package org.unitego.lobecorp.world.horde;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.unitego.lobecorp.Lobecorp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/// 只持久化事件、线路和正常参战关系，不保存逐个虚拟单位。
public class HordeSavedData extends SavedData {
    public static final Codec<HordeSavedData> CODEC = CompoundTag.CODEC.xmap(HordeSavedData::load, HordeSavedData::save);
    private static final SavedDataType<HordeSavedData> TYPE =
		    new SavedDataType<>(Lobecorp.id("horde_events_gpu_v1"), HordeSavedData::new, CODEC);
    private final Map<UUID, HordeEvent> events = new LinkedHashMap<>();
    private final Map<String, HordeTargetAssignment> targets = new LinkedHashMap<>();
    private long clock;
    private final Map<UUID, UUID> memberEvents = new LinkedHashMap<>();

    public Map<UUID, UUID> memberEvents() {
        return memberEvents;
    }

    public static HordeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<UUID, HordeEvent> events() {
        return events;
    }

    public Map<String, HordeTargetAssignment> targets() {
        return targets;
    }

    public long clock() {
        return clock;
    }

    public void advance() {
        clock++;
        setDirty();
    }

    private CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("clock", clock);
        var eventTags = new ListTag();
        events.values().forEach(event -> eventTags.add(event.save()));
        tag.put("events", eventTags);
        var targetTags = new ListTag();
        targets.values().forEach(target -> targetTags.add(target.save()));
        tag.put("targets", targetTags);
        var memberships = new ListTag();
        memberEvents.forEach((member, event) -> {
            var membership = new CompoundTag();
            membership.putString("member", member.toString());
            membership.putString("event", event.toString());
            memberships.add(membership);
        });
        tag.put("memberships", memberships);
        return tag;
    }

    private static HordeSavedData load(CompoundTag tag) {
        var data = new HordeSavedData();
        data.clock = Math.max(0, tag.getLongOr("clock", 0));
        for (var element : tag.getListOrEmpty("events")) {
            if (element instanceof CompoundTag eventTag) {
                try {
                    var event = HordeEvent.load(eventTag);
                    data.events.put(event.id(), event);
                } catch (IllegalArgumentException exception) {
                    Lobecorp.LOGGER.warn("无法恢复群体事件记录", exception);
                }
            }
        }
        for (var element : tag.getListOrEmpty("memberships")) {
            if (element instanceof CompoundTag member) {
                try {
                    data.memberEvents.put(UUID.fromString(member.getStringOr("member", "")),
                            UUID.fromString(member.getStringOr("event", "")));
                } catch (IllegalArgumentException exception) {
                    Lobecorp.LOGGER.warn("无法恢复群体成员归属", exception);
                }
            }
        }
        for (var element : tag.getListOrEmpty("targets")) {
            if (element instanceof CompoundTag targetTag) {
                try {
                    var target = HordeTargetAssignment.load(targetTag);
                    data.targets.put(target.key, target);
                } catch (IllegalArgumentException exception) {
                    Lobecorp.LOGGER.warn("无法恢复群体参战记录", exception);
                }
            }
        }
        return data;
        
    }
}
