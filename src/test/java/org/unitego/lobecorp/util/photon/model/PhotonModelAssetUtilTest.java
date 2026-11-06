package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PhotonModelAssetUtilTest {
    @Test
    public void canonicalGuardBorrowsOriginalConfigAndSubtreesWithoutAdaptationCopy() throws IOException {
        var source = canonical();
        var config = source.getCompoundOrEmpty("config");
        // canonical 分支原本不解析快照，借读不能额外加入资产校验。
        config.put("assetJson", new ByteArrayTag(new byte[]{1, 2, 3}));
        var before = source.copy();
        var borrowed = PhotonModelAssetUtil.borrowCanonicalConfig(source);
        assertSame(config, borrowed);

        for (var key : List.of("assetJson", "assetMaterialDependencies", "modelRenderer", "unknown")) {
            assertSame(config.get(key), borrowed.get(key));
        }

        assertEquals(before, source);
        assertEquals(before, PhotonModelAssetUtil.adaptObject(source));
    }

    @Test
    public void publicAdaptationOwnsAllMutableKnownAndUnknownSubtrees() throws IOException {
        var source = canonical();
        var first = PhotonModelAssetUtil.adaptObject(source);
        var second = PhotonModelAssetUtil.adaptObject(source);
        var before = source.copy();
        assertNotSame(source, first);

        for (var key : List.of("assetJson", "assetMaterialDependencies", "modelRenderer", "renderer", "unknown")) {
            assertNotSame(source.getCompoundOrEmpty("config").get(key), first.getCompoundOrEmpty("config").get(key));
            assertNotSame(first.getCompoundOrEmpty("config").get(key), second.getCompoundOrEmpty("config").get(key));
        }

        assertNotSame(source.get("unknown"), first.get("unknown"));
        Objects.requireNonNull(assertInstanceOf(ByteArrayTag.class, first.getCompoundOrEmpty("config").get("assetJson"))).getAsByteArray()[0] = 99;
        first.getCompoundOrEmpty("config").getCompoundOrEmpty("assetMaterialDependencies").putInt("changed", 1);
        first.getCompoundOrEmpty("config").getCompoundOrEmpty("modelRenderer").putInt("changed", 2);
        Objects.requireNonNull(assertInstanceOf(ListTag.class, first.getCompoundOrEmpty("config").get("unknown"))).clear();
        first.getCompoundOrEmpty("unknown").putInt("changed", 3);
        assertEquals(before, source);
        assertEquals(before, second);
        source.getCompoundOrEmpty("config").getCompoundOrEmpty("renderer").putInt("changed", 4);
        source.getCompoundOrEmpty("unknown").putInt("changed", 5);
        assertEquals(before, second);
        assertFalse(first.getCompoundOrEmpty("config").getCompoundOrEmpty("renderer").contains("changed"));
        assertEquals(3, first.getCompoundOrEmpty("unknown").getIntOr("changed", -1));
    }

    @Test
    public void sharedGuardsKeepVersionConfigResourceRendererErrorOrder() {
        var source = new CompoundTag();
        source.putString("version", "future");
        assertBothReject(source, "模型对象版本不支持。");
        source.putInt("version", 0);
        assertBothReject(source, "模型对象缺少配置。");
        var config = new CompoundTag();
        source.put("config", config);
        config.putString("resource", "BAD RESOURCE ID");
        config.putString("modelRenderer", "invalid");
        assertBothReject(source, "模型资源标识无效。");
        config.remove("resource");
        assertBothReject(source, "模型对象没有有效的渲染设置。");
        config.put("renderer", new CompoundTag());
        assertBothReject(source, "模型对象没有有效的渲染设置。");
    }

    @Test
    public void invalidVersionAndResourceTypesAreRejectedByBothEntrypoints() {
        for (var invalid : List.<Tag>of(IntTag.valueOf(-1), IntTag.valueOf(1), StringTag.valueOf("0"))) {
            var source = canonical();
            source.put("version", invalid);
            assertBothReject(source, "模型对象版本不支持。");
        }

        for (var invalid : List.<Tag>of(IntTag.valueOf(1), StringTag.valueOf("BAD RESOURCE ID"))) {
            var source = canonical();
            source.getCompoundOrEmpty("config").put("resource", invalid);
            assertBothReject(source, "模型资源标识无效。");
        }
    }

    @Test
    public void missingVersionAndNullableResourceRepresentationsRemainAccepted() throws IOException {
        var source = canonical();
        source.remove("version");

        for (var resource : List.of("", LDLibExtraCodecs.NULL_STRING, "lobecorp:valid")) {
            source.getCompoundOrEmpty("config").putString("resource", resource);
            assertSame(source.get("config"), PhotonModelAssetUtil.borrowCanonicalConfig(source));
            assertEquals(source, PhotonModelAssetUtil.adaptObject(source));
        }
    }

    @Test
    public void legacyRendererStillRequiresSnapshotAdaptationAndReturnsOwnedCopy() throws IOException {
        var source = canonical();
        var config = source.getCompoundOrEmpty("config");
        config.remove("modelRenderer");
        config.putString("assetJson", "{\"version\":1,\"vertices\":[],\"faces\":[]}");
        var before = source.copy();
        assertThrows(IOException.class, () -> PhotonModelAssetUtil.borrowCanonicalConfig(source));
        var adapted = PhotonModelAssetUtil.adaptObject(source);
        var adaptedConfig = adapted.getCompoundOrEmpty("config");
        assertEquals(config.get("renderer"), adaptedConfig.get("modelRenderer"));
        assertNotSame(config.get("renderer"), adaptedConfig.get("modelRenderer"));
        assertNotSame(adaptedConfig.get("renderer"), adaptedConfig.get("modelRenderer"));
        adaptedConfig.getCompoundOrEmpty("modelRenderer").putInt("changed", 1);
        assertFalse(adaptedConfig.getCompoundOrEmpty("renderer").contains("changed"));
        assertEquals(before, source);
        assertFalse(config.contains("modelRenderer"));
    }

    @Test
    public void legacyAdaptationRetainsSnapshotFailures() {
        var source = canonical();
        var config = source.getCompoundOrEmpty("config");
        config.remove("modelRenderer");
        config.remove("assetJson");
        assertEquals("不能为缺少当前模型快照的对象推导默认渲染设置。",
                assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source)).getMessage());
        config.putInt("assetJson", 1);
        assertEquals("模型快照类型不支持。",
                assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source)).getMessage());
        config.putString("assetJson", "[]");
        assertEquals("模型快照根必须为JSON对象。",
                assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source)).getMessage());
    }

    private static void assertBothReject(CompoundTag source, String message) {
        var before = source.copy();
        assertEquals(message, assertThrows(IOException.class, () -> PhotonModelAssetUtil.adaptObject(source)).getMessage());
        assertEquals(message, assertThrows(IOException.class, () -> PhotonModelAssetUtil.borrowCanonicalConfig(source)).getMessage());
        assertEquals(before, source);
    }

    private static CompoundTag canonical() {
        var source = new CompoundTag();
        source.putInt("version", 0);
        var config = new CompoundTag();
        source.put("config", config);
        config.put("modelRenderer", new CompoundTag());
        config.put("renderer", new CompoundTag());
        config.put("assetJson", new ByteArrayTag(new byte[]{1, 2, 3}));
        config.put("assetMaterialDependencies", new CompoundTag());
        var unknown = new ListTag();
        unknown.add(new ByteArrayTag(new byte[]{4, 5, 6}));
        config.put("unknown", unknown);
        var rootUnknown = new CompoundTag();
        rootUnknown.put("bytes", new ByteArrayTag(new byte[]{7, 8, 9}));
        source.put("unknown", rootUnknown);
        return source;
    }
}
