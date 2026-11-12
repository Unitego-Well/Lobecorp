package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceDocument.Kind;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/// 只解码原始数据，不调用FX/项目注册或任何GPU、实体和编辑器接口。
public class PhotonResourceReader {
	public static PhotonResourceDocument read(Path path) throws IOException {
		return read(path, PhotonResourceLimits.DEFAULT);
	}

	public static PhotonResourceDocument read(Path path, PhotonResourceLimits limits) throws IOException {
		var source = path.toAbsolutePath().normalize();

		try (var input = Files.newInputStream(source)) {
			var bytes = readBytes(input, limits.sourceBytes());
			return decode(source.toString(), Kind.of(source.getFileName().toString()), bytes, new Budget(limits), 0);
		} catch (RuntimeException failure) {
			throw new IOException("资源无法无损读取：" + source, failure);
		}
	}

	public static PhotonResourceDocument read(String origin, Kind kind, InputStream input, PhotonResourceLimits limits) throws IOException {
		var bytes = readBytes(input, limits.sourceBytes());
		try {
			return decode(origin, kind, bytes, new Budget(limits), 0);
		} catch (RuntimeException invalid) {
			throw new IOException("资源无法无损读取：" + origin, invalid);
		}
	}

	private static PhotonResourceDocument decode(String origin, Kind kind, byte[] bytes, Budget budget, int depth) throws IOException {
		if (depth > budget.limits.depth()) {
			throw new IOException("资源包嵌套超出深度预算：" + origin);
		}

		var hash = sha256(bytes);

		return switch (kind) {
			case FX_PACK, MODEL_PACK -> new PhotonResourceDocument(origin, kind, hash, bytes, null, null,
					readEntries(origin, bytes, budget, depth));
			case FX, MODEL_FX, FX_PROJECT, MODEL_PROJECT, NBT -> decodeNbt(origin, kind, hash, bytes, budget);
			case JSON -> new PhotonResourceDocument(origin, kind, hash, bytes, null,
					PhotonResourceJsonUtil.read(text(bytes), budget.limits), List.of());
			case OBJ -> {
				text(bytes);
				yield new PhotonResourceDocument(origin, kind, hash, bytes, null, null, List.of());
			}
			case BINARY -> new PhotonResourceDocument(origin, kind, hash, bytes, null, null, List.of());
		};
	}

	private static PhotonResourceDocument decodeNbt(String origin, Kind kind, String hash, byte[] bytes, Budget budget) throws IOException {
		boolean compressed = bytes.length >= 2 && Byte.toUnsignedInt(bytes[0]) == 0x1f && Byte.toUnsignedInt(bytes[1]) == 0x8b;

		if ((kind == Kind.FX || kind == Kind.MODEL_FX) && !compressed) {
			throw new IOException("运行定义不是压缩NBT：" + origin);
		}

		if ((kind == Kind.FX_PROJECT || kind == Kind.MODEL_PROJECT) && compressed) {
			throw new IOException("工程容器不是未压缩NBT：" + origin);
		}

		var decoded = bytes;

		if (compressed) {
			try (var input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
				decoded = readBytes(input, Math.min(budget.limits.sourceBytes(), budget.remaining()));
				budget.consume(decoded.length);
			}
		}

		try (var input = new DataInputStream(new ByteArrayInputStream(decoded))) {
			var accounter = new NbtAccounter(budget.limits.nbtHeapBytes() - budget.nbtHeap, budget.limits.depth());
			var tag = NbtIo.read(input, accounter);
			budget.nbtHeap += accounter.getUsage();

			if (input.available() != 0) {
				throw new IOException("NBT根节点后存在额外内容：" + origin);
			}

			return new PhotonResourceDocument(origin, kind, hash, bytes, tag, null, List.of());
		}
	}

	private static List<PhotonResourceDocument> readEntries(String origin, byte[] bytes, Budget budget, int depth) throws IOException {
		int expected = zipEntries(bytes);
		var result = new ArrayList<PhotonResourceDocument>();
		var names = new HashSet<String>();
		int count = 0;

		try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				count++;
				budget.entry();
				var name = entry.getName();
				validateEntry(name);

				if (!names.add(name)) {
					throw new IOException("资源包包含重复条目：" + name);
				}

				var content = readBytes(zip, Math.min(budget.limits.sourceBytes(), budget.remaining()));
				budget.consume(content.length);

				if (entry.isDirectory()) {
					if (content.length != 0) {
						throw new IOException("资源包目录含有未声明的数据：" + name);
					}

					continue;
				}

				result.add(decode(origin + "!/" + name, Kind.of(name), content, budget, depth + 1));
			}
		}

		if (count != expected) {
			throw new IOException("ZIP目录与实际条目不一致：" + origin);
		}

		return List.copyOf(result);
	}

	private static int zipEntries(byte[] bytes) throws IOException {
		var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

		// 当前预算不接收ZIP64/分卷包；必须有完整结束目录，不能把截断包视为空包。
		for (int offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 22 - 65535); offset--) {
			if (buffer.getInt(offset) != 0x06054b50 || offset + 22 + Short.toUnsignedInt(buffer.getShort(offset + 20)) != bytes.length) {
				continue;
			}

			int count = Short.toUnsignedInt(buffer.getShort(offset + 10));
			long size = Integer.toUnsignedLong(buffer.getInt(offset + 12));
			long start = Integer.toUnsignedLong(buffer.getInt(offset + 16));

			if (buffer.getShort(offset + 4) != 0 || buffer.getShort(offset + 6) != 0 || count == 65535
					|| Short.toUnsignedInt(buffer.getShort(offset + 8)) != count || start + size != offset) {
				throw new IOException("ZIP目录无效或使用未支持的ZIP64/分卷格式。");
			}

			return count;
		}

		throw new IOException("资源包缺少完整ZIP结束目录。");
	}

	private static void validateEntry(String name) throws IOException {
		if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("//") || name.contains(":") || name.indexOf(0) >= 0) {
			throw new IOException("资源包条目路径无效：" + name);
		}

		for (var part : name.split("/")) {
			if (part.equals("..") || part.equals(".")) {
				throw new IOException("资源包条目路径包含越界片段：" + name);
			}
		}
	}

	private static byte[] readBytes(InputStream input, long maximum) throws IOException {
		var output = new ByteArrayOutputStream();
		var buffer = new byte[8192];
		long total = 0;

		for (int count; (count = input.read(buffer)) != -1; ) {
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedIOException("资源读取已取消。");
			}

			total += count;

			if (total > maximum) {
				throw new IOException("资源内容超出读取字节预算。");
			}

			output.write(buffer, 0, count);
		}

		return output.toByteArray();
	}

	private static String text(byte[] bytes) throws CharacterCodingException {
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static class Budget {
		private final PhotonResourceLimits limits;
		private long unpacked;
		private long nbtHeap;
		private int entries;

		private Budget(PhotonResourceLimits limits) {
			this.limits = limits;
		}

		private long remaining() {
			return limits.unpackedBytes() - unpacked;
		}

		private void consume(long bytes) throws IOException {
			unpacked += bytes;

			if (unpacked > limits.unpackedBytes()) {
				throw new IOException("资源包超出总展开预算。");
			}
		}

		private void entry() throws IOException {
			if (++entries > limits.entries()) {
				throw new IOException("资源包超出条目预算。");
			}
		}
	}
}
