package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/// 只读索引特效目录并统计其初始化开销；不创建FX运行态、不解析材质、不触碰GPU与编辑器状态。
///
/// 统计基于定义NBT的静态结构：对象数、发射器数、子外接特效、子外接特效递归深度与估算渲染类型数。
/// 这些数字用于在选择开关和拆分资源前先看清“哪些特效一次性初始化内容很多”，不是运行测量结果。
public class PhotonEffectInventoryUtil {
	/// 对象类型字段的键名，与Photon定义一致。
	private static final String TYPE_KEY = "type";
	/// 子外接特效指向的资源键，与子发射器配置一致。
	private static final String FX_LOCATION_KEY = "fxLocation";
	/// 定义中对象列表的路径前缀。
	private static final String OBJECTS_KEY = "fxData";
	/// 定义中对象列表的键名。
	private static final String OBJECT_LIST_KEY = "fxObjects";
	/// 单个对象数据的容器键名。
	private static final String OBJECT_DATA_KEY = "data";
	/// 递归解析子外接特效的最大深度，防止环引用无限展开。
	private static final int MAX_LINK_DEPTH = 4;
	/// 单个特效解析的子外接特效总数上限。
	private static final int MAX_LINKS = 64;

	private PhotonEffectInventoryUtil() {
	}

	/// 一个特效定义的静态开销画像；字段均为可复述的整数，便于展示与比较。
	public record Entry(Path file, String id, long sourceBytes, long decodedBytes, String sha256,
	                    int objects, int emitters, int spawners, int models, int controllers,
	                    int linkedEffects, int linkDepth, int estimatedRenderTypes,
	                    List<String> linkedIds, String failure) {
		/// 一次性初始化成本的经验权重，仅用于排序，不是毫秒。
		public long cost() {
			return (long) objects + estimatedRenderTypes + (long) linkedEffects * 4L;
		}

		public boolean readable() {
			return failure == null;
		}
	}

	/// 索引一个根目录下的全部特效定义；单文件失败只记录在该条目上，不中断整批。
	public static List<Entry> scan(Path root) throws IOException {
		if (root == null || !Files.isDirectory(root)) {
			return List.of();
		}

		List<Entry> result = new ArrayList<>();

		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fx"))
					.sorted()
					.forEach(path -> result.add(inspect(path, root)));
		}

		return result;
	}

	/// 读取单个特效定义并统计；定义不可读时返回带failure的条目，不抛异常。
	public static Entry inspect(Path file, Path root) {
		long sourceBytes;
		PhotonResourceDocument document;

		try {
			sourceBytes = Files.size(file);
			document = PhotonResourceReader.read(file);
		} catch (IOException | RuntimeException unreadable) {
			return new Entry(file, id(file, root), safeSize(file), 0, "", 0, 0, 0, 0, 0, 0, 0, 0,
					List.of(), unreadable.toString());
		}

		var nbt = document.nbt();

		if (nbt == null) {
			return new Entry(file, id(file, root), sourceBytes, sourceBytes, document.sha256(),
					0, 0, 0, 0, 0, 0, 0, 0, List.of(), "定义缺少NBT数据。");
		}

		var objects = objects(nbt);
		int emitters = 0;
		int spawners = 0;
		int models = 0;
		int controllers = 0;
		Set<String> links = new LinkedHashSet<>();

		for (var object : objects) {
			String type = type(object);

			// 分类按最具体的类型先匹配：模型动画不是模型发射器，子发射器也不是普通发射器。
			if (type.contains("spawner")) {
				spawners++;
			} else if (type.contains("animation")) {
				// 模型动画与控制器都不产生独立几何，单独计数后再按控制器归类。
				if (type.contains("controller")) {
					controllers++;
				}
			} else if (type.contains("controller")) {
				controllers++;
			} else if (type.contains("model")) {
				models++;
			} else if (type.contains("emitter") || type.contains("particle")) {
				emitters++;
			}

			collectLinks(object, links);
		}

		var linkedIds = List.copyOf(links);

		return new Entry(file, id(file, root), sourceBytes, decodedSize(nbt), document.sha256(),
				objects.size(), emitters, spawners, models, controllers,
				linkedIds.size(), depth(linkedIds, root), estimateRenderTypes(objects), linkedIds, null);
	}

	/// 估算解压后的定义体积：按NBT结构递归累计，不保留中间副本。
	/// 与 `.fx` 的 gzip 解压长度同量级，用于找出真正占体积的对象，不作为精确字节数。
	private static int decodedSize(CompoundTag node) {
		return size((Tag) node);
	}

	private static int size(Tag tag) {
		return switch (tag) {
			case CompoundTag compound -> {
				int total = 0;
				for (var key : compound.keySet()) {
					total += 2 + key.length() + size(compound.get(key));
				}
				yield total + 1;
			}
			case ListTag list -> {
				int total = 5;
				for (var value : list) {
					total += size(value);
				}
				yield total;
			}
			case ByteArrayTag bytes -> 4 + bytes.size();
			case IntArrayTag ints -> 4 + ints.size() * 4;
			case LongArrayTag longs -> 4 + longs.size() * 8;
			case StringTag text -> 2 + text.value().length();
			case ByteTag ignored -> 1;
			case ShortTag ignored -> 2;
			case IntTag ignored -> 4;
			case LongTag ignored -> 8;
			case FloatTag ignored -> 4;
			case DoubleTag ignored -> 8;
			case null, default -> 0;
		};
	}
	/// 估算一次性渲染类型数量：每个发射器至少一个材质，模型输出按材质组追加，子发射器自身不提交几何。
	private static int estimateRenderTypes(List<CompoundTag> objects) {
		int total = 0;

		for (var object : objects) {
			String type = type(object);

			if (type.contains("spawner")) {
				continue;
			}

			total++;

			if (type.contains("model")) {
				total++;
			}
		}

		return total;
	}

	/// 子外接特效的递归深度；只按文件名猜测，不解析目标定义，避免连锁读取整棵树。
	private static int depth(List<String> ids, Path root) {
		if (ids.isEmpty() || root == null) {
			return 0;
		}

		int max = 0;

		for (var id : ids) {
			max = Math.max(max, 1 + descend(id, root, 1, new LinkedHashSet<>(List.of(id))));
		}

		return max;
	}

	private static int descend(String id, Path root, int depth, Set<String> seen) {
		if (depth >= MAX_LINK_DEPTH) {
			return 0;
		}

		var path = resolve(id, root);

		if (path == null) {
			return 0;
		}

		PhotonResourceDocument document;

		try {
			document = PhotonResourceReader.read(path);
		} catch (IOException | RuntimeException unreadable) {
			return 0;
		}

		var nbt = document.nbt();

		if (nbt == null) {
			return 0;
		}

		Set<String> links = new LinkedHashSet<>();

		for (var object : objects(nbt)) {
			collectLinks(object, links);
		}

		int max = 0;

		for (var link : links) {
			if (!seen.add(link)) {
				continue;
			}

			max = Math.max(max, 1 + descend(link, root, depth + 1, seen));
		}

		return max;
	}

	/// 把 `ns:path` 形式的外接ID解析为根目录下的定义文件；找不到返回null。
	private static Path resolve(String id, Path root) {
		var relative = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		var candidate = root.resolve(relative + ".fx");
		return Files.isRegularFile(candidate) ? candidate : null;
	}

	/// 特效ID按根目录相对路径推导，去掉 `.fx` 后缀。
	public static String id(Path file, Path root) {
		var name = file.getFileName().toString();
		var base = name.toLowerCase(Locale.ROOT).endsWith(".fx") ? name.substring(0, name.length() - 3) : name;

		if (root == null) {
			return base;
		}

		try {
			var relative = root.relativize(file).toString().replace('\\', '/');
			var withoutSuffix = relative.toLowerCase(Locale.ROOT).endsWith(".fx")
					? relative.substring(0, relative.length() - 3) : relative;
			return withoutSuffix;
		} catch (IllegalArgumentException unrelated) {
			return base;
		}
	}

	/// 定义中的对象列表；结构不符合预期时返回空列表，不抛异常。
	static List<CompoundTag> objects(CompoundTag definition) {
		var list = definition.getCompoundOrEmpty(OBJECTS_KEY).getListOrEmpty(OBJECT_LIST_KEY);
		List<CompoundTag> result = new ArrayList<>(list.size());

		for (var value : list) {
			if (value instanceof CompoundTag object) {
				result.add(object);
			}
		}

		return result;
	}

	/// 对象的类型标识；定义里 `type` 在对象顶层，兼容旧结构时回退到 `data.type`。
	private static String type(CompoundTag object) {
		var direct = object.get(TYPE_KEY);

		if (direct != null) {
			var value = direct.asString().orElse("");
			if (!value.isEmpty()) {
				return value;
			}
		}

		var container = object.contains(OBJECT_DATA_KEY) ? object.getCompoundOrEmpty(OBJECT_DATA_KEY) : object;
		var nested = container.get(TYPE_KEY);
		return nested == null ? "" : nested.asString().orElse("");
	}

	/// 收集对象树里指向其它特效的ID，去重并保持出现顺序。
	private static void collectLinks(CompoundTag object, Set<String> out) {
		collectLinks((Tag) object, out, 0);
	}

	private static void collectLinks(Tag tag, Set<String> out, int depth) {
		if (tag == null || depth > MAX_LINK_DEPTH || out.size() >= MAX_LINKS) {
			return;
		}

		switch (tag) {
			case CompoundTag compound -> {
				compound.getString(FX_LOCATION_KEY)
						.filter(location -> !location.isEmpty())
						.ifPresent(out::add);

				for (var key : compound.keySet()) {
					collectLinks(compound.get(key), out, depth + 1);
				}
			}
			case ListTag list -> {
				for (var value : list) {
					collectLinks(value, out, depth + 1);
				}
			}
			default -> {
			}
		}
	}

	private static long safeSize(Path file) {
		try {
			return Files.size(file);
		} catch (IOException unavailable) {
			return 0;
		}
	}
}
