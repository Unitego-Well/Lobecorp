package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.registry.client.photon.LcPhotonPlaybackTypes;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedCodecUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonNamedPlaybackDefinitionUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimeCopyUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/// 制作定义独占保存；播放状态由所属FXRuntime持有，不进入工程或模板复制。
public class PhotonNamedController extends EmptyFXObject {
	private static final AtomicLong LIBRARY_DECODES = new AtomicLong();
	@Persisted
	private CompoundTag definition = new CompoundTag();
	private CompoundTag original = new CompoundTag();
	private @Nullable PhotonNamedRuntime playback;
	private @Nullable CompiledDefinition compiled;
	private long definitionRevision;

	public static long libraryDecodeCount() {
		return LIBRARY_DECODES.get();
	}

	public static PhotonNamedLibrary readLibrary(CompoundTag source) throws IOException {
		LIBRARY_DECODES.incrementAndGet();
		return PhotonNamedCodecUtil.read(source);
	}

	public static CompiledDefinition compileDefinition(CompoundTag definition, CompoundTag original) throws IOException {
		return new CompiledDefinition(definition, original);
	}

	public CompiledDefinition compileDefinition() throws IOException {
		if (compiled == null) {
			compiled = new CompiledDefinition(definition, original);
		}

		return compiled;
	}

	public @Nullable CompiledDefinition compiledDefinition() {
		return compiled;
	}

	public long definitionRevision() {
		return definitionRevision;
	}

	public boolean matchesDefinition(CompoundTag value) {
		return definition.equals(value);
	}

	private void definitionChanged() {
		compiled = null;
		definitionRevision++;
	}

	@Override
	public void beforeDeserialize() {
		// 解析器可能就地更新 NBT，必须在它写入前脱离共享快照。
		definition = definition.copy();
		original = original.copy();
		definitionChanged();
	}

	@Override
	public void afterDeserialize() {
		// 反序列化可绕过 setter；恢复为独占数据后使旧编译快照失效。
		definition = definition.copy();
		original = original.copy();
		definitionChanged();
	}

	/// 施放与预热共用的最终播放定义：默认动画不自动启动，子动画轨道由外层时间轴控制。
	public CompoundTag playbackDefinition() {
		return PhotonNamedPlaybackDefinitionUtil.prepare(definition);
	}

	public void preparePlaybackDefinition() {
		if (compiled == null) {
			definition(playbackDefinition());
			return;
		}
		try {
			var prepared = compiled.playbackVersion();
			if (prepared != compiled) {
				prepared.install(this);
			}
		} catch (IOException invalid) {
			throw new IllegalArgumentException("命名播放定义无法准备。", invalid);
		}
	}

	@Override
	public @Nullable IFXObject deepCopy() {
		return PhotonRuntimeCopyUtil.deepCopy(this);
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonPlaybackTypes.CONTROLLER;
	}

	public CompoundTag definition() {
		return definition.copy();
	}

	public void definition(CompoundTag value) {
		definition = value.copy();
		definitionChanged();
	}

	public CompoundTag source() {
		return original.copy();
	}

	public void rememberSource(CompoundTag source) {
		original = source.copy();
		definitionChanged();
	}

	public void attach(@Nullable PhotonNamedRuntime value) {
		playback = value;
	}

	@Override
	public boolean isAlive() {
		return !removed && playback != null && !playback.closed() || super.isAlive();
	}

	@Override
	public boolean isPlaying() {
		return !removed && playback != null && !playback.closed() || super.isPlaying();
	}

	@Override
	public PhotonNamedController shallowCopy() {
		return (PhotonNamedController) deepCopy();
	}

	@Override
	public void remove(boolean force) {
		if (playback != null) {
			playback.close();
		}

		super.remove(force);
	}

	/// 只持有私有制作快照和无目标计划，不持有场景、运行时或播放句柄。
	public static final class CompiledDefinition {
		private final CompoundTag definition;
		private final CompoundTag original;
		private final PhotonNamedLibrary library;
		private final Map<UUID, PhotonNamedPlan.Template> templates;
		private final int decodedTracks;
		private @Nullable CompiledDefinition playbackVersion;

		private CompiledDefinition(CompoundTag definition, CompoundTag original) throws IOException {
			this.definition = definition.copy();
			this.original = original.copy();
			library = readLibrary(this.definition);
			var prepared = new LinkedHashMap<UUID, PhotonNamedPlan.Template>();

			for (var animation : library.animations()) {
				prepared.put(animation.id(), PhotonNamedPlan.compile(Platform.getFrozenRegistry(), animation));
			}

			templates = Map.copyOf(prepared);
			decodedTracks = templates.values().stream().mapToInt(PhotonNamedPlan.Template::decodedTracks).sum();
		}

		private CompiledDefinition playbackVersion() throws IOException {
			if (playbackVersion == null) {
				var value = PhotonNamedPlaybackDefinitionUtil.prepare(definition);
				playbackVersion = definition.equals(value) ? this : new CompiledDefinition(value, original);
			}
			return playbackVersion;
		}

		/// 提前生成不可变播放定义；不安装控制器或执行播放回调。
		public void prepareStatic() throws IOException {
			var prepared = playbackVersion();
			prepared.playbackVersion();
		}

		public boolean matches(PhotonNamedController controller) {
			return definition.equals(controller.definition);
		}

		public boolean matchesDefinition(CompoundTag source) {
			return definition.equals(source);
		}

		public PhotonNamedLibrary library() {
			return library;
		}

		public PhotonNamedPlan.Template template(UUID animation) {
			var result = templates.get(animation);

			if (result == null) {
				throw new IllegalArgumentException("编译命名定义不存在：" + animation);
			}

			return result;
		}

		public int libraryDecodeCount() {
			return 1;
		}

		public int trackDecodeCount() {
			return decodedTracks;
		}

		public List<String> fallbackReasons() {
			return templates.values().stream().flatMap(template -> template.fallbackReasons().stream()).toList();
		}

		/// 仅安装命名制作数据；对象身份、变换和其他原生对象数据由外层工厂初始化。
		public void install(PhotonNamedController target) {
			target.definition = definition;
			target.original = original;
			target.compiled = this;
			target.definitionRevision++;
		}

		public PhotonNamedController newController() {
			var result = new PhotonNamedController();
			install(result);
			return result;
		}
	}
}
