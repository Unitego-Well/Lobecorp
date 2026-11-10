package org.unitego.lobecorp.config.photon.model;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_ANCHOR_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_ANIMATION_OFFSET_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_CLIP_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_DURATION_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_ENABLED_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_LENGTH_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_LOOPING_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_OPACITY_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_OVERRIDE_RENDERER_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_PAUSE_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_PLAYBACK_SPEED_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_START_DELAY_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_USE_REFERENCE_DIMENSIONS_KEY;
import static org.unitego.lobecorp.registry.translation.PhotonTranslationKeys.PHOTON_MODEL_WIDTH_KEY;

/// 字段名沿用现有模型发射器协议；内嵌快照保持字节/字符串原表示，运行适配不改写来源。
public class PhotonModelOutputConfig implements IPersistedSerializable {
	public static final int DEFAULT_DURATION = 100;
	public static final int DEFAULT_COLOR = 0xFFFFFFFF;
	public static final String ASSET_KEY = "assetJson";

	@Persisted
	public Source source = Source.EMBEDDED;
	@Persisted
	@Nullable
	public Identifier resource;
	@Persisted
	public String file = "";
	@Persisted
	public boolean flipObjV;
	@Configurable(name = PHOTON_MODEL_ENABLED_KEY)
	public boolean enabled = true;
	@Configurable(name = PHOTON_MODEL_DURATION_KEY)
	public int duration = DEFAULT_DURATION;
	@Configurable(name = PHOTON_MODEL_START_DELAY_KEY)
	public int startDelay;
	@Configurable(name = PHOTON_MODEL_LOOPING_KEY)
	public boolean looping = true;
	@Configurable(name = PHOTON_MODEL_PAUSE_KEY)
	public boolean paused;
	@Persisted
	public String snapshotSource = "";
	@Configurable(name = PHOTON_MODEL_CLIP_KEY)
	public String clip = "";
	@Configurable(name = PHOTON_MODEL_PLAYBACK_SPEED_KEY)
	public float playbackSpeed = 1;
	@Configurable(name = PHOTON_MODEL_ANIMATION_OFFSET_KEY)
	public float animationOffset;
	@Configurable(name = PHOTON_MODEL_USE_REFERENCE_DIMENSIONS_KEY)
	public boolean useReferenceDimensions = true;
	@Configurable(name = PHOTON_MODEL_LENGTH_KEY)
	public float length = 1;
	@Configurable(name = PHOTON_MODEL_WIDTH_KEY)
	public float width = 1;
	@Configurable(name = PHOTON_MODEL_ANCHOR_KEY)
	public Anchor anchor = Anchor.START;
	@Configurable(name = PHOTON_MODEL_OPACITY_KEY)
	public float opacity = 1;
	@Persisted
	public int color = DEFAULT_COLOR;
	@Configurable(name = PHOTON_MODEL_OVERRIDE_RENDERER_KEY)
	public boolean overrideRenderer;
	@Persisted(subPersisted = true)
	public final RendererSetting modelRenderer;
	@Persisted(subPersisted = true)
	public final RendererSetting renderer;
	@Persisted
	public CompoundTag assetMaterialDependencies = new CompoundTag();
	@Persisted
	public List<Identifier> assetDependencies = new ArrayList<>();
	private Tag assetSnapshot = new ByteArrayTag(new byte[0]);


	public PhotonModelOutputConfig() {
		this(true);
	}

	/// 材质注册需要游戏环境；纯数据用途（资源与文件读取、序列化）可关闭默认渲染材质。
	public PhotonModelOutputConfig(boolean withRendererDefaults) {
		this.modelRenderer = withRendererDefaults ? rendererDefaults() : new RendererSetting();
		this.renderer = withRendererDefaults ? rendererDefaults() : new RendererSetting();
	}

	private static RendererSetting rendererDefaults() {
		var setting = new RendererSetting();
		setting.getMaterials().add(new MaterialSetting(new TextureMaterial()));
		return setting;
	}

	public Tag assetSnapshot() {
		return assetSnapshot.copy();
	}

	public void setAssetSnapshot(Tag value) {
		if (!(value instanceof ByteArrayTag) && !(value instanceof StringTag)) {
			throw new IllegalArgumentException("模型快照必须是字节或字符串。");
		}

		assetSnapshot = value.copy();
	}

	@Override
	public void serialize(@NotNull ValueOutput output) {
		IPersistedSerializable.super.serialize(output);
		output.store(ASSET_KEY, ExtraCodecs.NBT, assetSnapshot.copy());
	}

	@Override
	public void deserialize(@NotNull ValueInput input) {
		IPersistedSerializable.super.deserialize(input);
		setAssetSnapshot(input.read(ASSET_KEY, ExtraCodecs.NBT).orElseGet(() -> new ByteArrayTag(new byte[0])));
	}

	public enum Source {
		EMBEDDED, RESOURCE, FILE
	}

}
