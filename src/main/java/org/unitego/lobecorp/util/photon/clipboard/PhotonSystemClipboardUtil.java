package org.unitego.lobecorp.util.photon.clipboard;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

public class PhotonSystemClipboardUtil {
	/**
	 * 系统剪贴板文本的类型标识及版本，避免把普通文本解释为编辑器数据。
	 */
	private static final String PREFIX = "LOBECORP_PHOTON:1\n";
	/**
	 * 单次剪贴板文本上限，单位字符，避免误粘贴过大的外部内容。
	 */
	private static final int MAX_TEXT_LENGTH = 4 * 1024 * 1024;
	/**
	 * 一次操作允许的最大条目数。
	 */
	public static final int MAX_ITEMS = 4096;
	/**
	 * 格式内的内容类型；与普通文本剪贴板互不混用。
	 */
	public static final String KEYS = "keys", TRACKS = "tracks", CLIPS = "clips", RESOURCE = "resource", MODULE = "module";
	/**
	 * 只缓存未变化的协议文本解析结果，不缓存粒子或编辑器对象。
	 */
	private static String cachedText = "";
	private static CompoundTag cachedEnvelope;

	public static void write(String kind, CompoundTag data) {
		var envelope = new CompoundTag();
		envelope.putString("kind", kind);
		envelope.put("data", data.copy());
		String text = PREFIX + envelope;
		if (text.length() > MAX_TEXT_LENGTH)
			throw new IllegalArgumentException();
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}

	public static boolean hasContent() {
		return Minecraft.getInstance().keyboardHandler.getClipboard().startsWith(PREFIX);
	}

	public static String kind() {
		var envelope = readEnvelope();
		return envelope == null ? "" : envelope.getStringOr("kind", "");
	}

	public static CompoundTag read(String kind) {
		var envelope = readEnvelope();
		return envelope != null && kind.equals(envelope.getStringOr("kind", ""))
				? envelope.getCompound("data").map(CompoundTag::copy).orElse(null) : null;
	}

	private static CompoundTag readEnvelope() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (!text.startsWith(PREFIX) || text.length() > MAX_TEXT_LENGTH) {
			cachedText = "";
			cachedEnvelope = null;
			return null;
		}
		if (text.equals(cachedText))
			return cachedEnvelope;
		cachedText = text;
		cachedEnvelope = null;
		try {
			cachedEnvelope = TagParser.parseCompoundFully(text.substring(PREFIX.length()));
		} catch (CommandSyntaxException | RuntimeException ignored) {
		}
		return cachedEnvelope;
	}
}
