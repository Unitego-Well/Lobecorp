package org.unitego.lobecorp.config.photon.playback;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEmitterSpawnerTranslationKeys;

import javax.annotation.Nullable;

/// 沿用当前文件字段与枚举编码；运行副本不会把调度结果写进配置。
public class PhotonSpawnConfig implements IPersistedSerializable {
	public static final String ENUM_PREFIX = PhotonEmitterSpawnerTranslationKeys.PREFIX + "enum.";
	public static final int DEFAULT_DURATION = 100;
	public static final int DEFAULT_INTERVAL = 20;
	public static final int DEFAULT_MAX_INSTANCES = 64;
	public static final int DEFAULT_MAX_PER_TICK = 16;
	public static final int MAX_DEPTH = 8;
	public static final int MAX_TEMPLATE_OBJECTS = 4096;
	public static final int MAX_PLAYBACK_SPEED = 16;
	@Persisted
	public final TransformRef template = new TransformRef();
	@Persisted(subPersisted = true)
	public final PhotonSpawnShape shape = new PhotonSpawnShape();
	@Persisted(subPersisted = true)
	public final PhotonSpawnParameters parameters = new PhotonSpawnParameters();
	@Persisted
	public boolean enabled = true;
	@Persisted
	public Source source = Source.PROJECT;
	@Persisted
	public @Nullable Identifier fxLocation;
	@Persisted
	public boolean includeChildren = true;
	@Persisted
	public int duration = DEFAULT_DURATION;
	@Persisted
	public boolean looping = true;
	@Persisted
	public int startDelay;
	@Persisted
	public Mode mode = Mode.INTERVAL;
	@Persisted
	public int interval = DEFAULT_INTERVAL;
	@Persisted
	public NumberFunction count = NumberFunction.constant(1);
	@Persisted
	public NumberFunction rate = NumberFunction.constant(1f / DEFAULT_INTERVAL);
	@Persisted
	public NumberFunction probability = NumberFunction.constant(1);
	@Persisted
	public int maxInstances = DEFAULT_MAX_INSTANCES;
	@Persisted
	public int maxTotal;
	@Persisted
	public int maxPerTick = DEFAULT_MAX_PER_TICK;
	@Persisted
	public int maxDepth = MAX_DEPTH;
	@Persisted
	public int instanceLifetime;
	@Persisted
	public EndBehavior endBehavior = EndBehavior.WAIT;
	@Persisted
	public boolean clearOnRestart = true;
	@Persisted
	public boolean follow;
	@Persisted
	public boolean inheritRotation = true;
	@Persisted
	public boolean inheritScale;
	@Persisted
	public boolean inheritColor;
	@Persisted
	public boolean inheritVelocity;
	@Persisted
	public NumberFunction launchSpeed = NumberFunction.constant(0);

	public enum Source implements StringRepresentable {
		PROJECT, FX;

		@Override
		public @NotNull String getSerializedName() {
			return ENUM_PREFIX + name();
		}
	}

	public enum Mode implements StringRepresentable {
		ONCE, INTERVAL, RATE, ONE_SHOT;

		@Override
		public @NotNull String getSerializedName() {
			return ENUM_PREFIX + name();
		}
	}

	public enum EndBehavior implements StringRepresentable {
		WAIT, STOP_EMISSION, CLEAR;

		@Override
		public @NotNull String getSerializedName() {
			return ENUM_PREFIX + name();
		}
	}
}
