package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackType;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelAnimationTrackEditor;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationTrack;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.ArrayList;
import java.util.List;

/// 新模型实例通过原 Photon 注册表接入，不改变原粒子类型。
public class LcPhotonModelEmitterTypes {
	/// 新模型实例保存与菜单的稳定类型标识。
	public static final String TYPE_NAME = "lobecorp_model_emitter";
	/// 原 Photon 对象类型注册表。
	private static final String REGISTRY = "photon:fx_object";
	/// 工程时间轴中的模型动画轨道标识，不随界面翻译修改。
	public static final String MODEL_ANIMATION_TRACK_NAME = "lobecorp_model_animation";
	@LDLRegisterClient(name = MODEL_ANIMATION_TRACK_NAME, registry = "photon:timeline_track")
	@SuppressWarnings("unused") // 原生注册器按注解发现此字段。
	public static final TrackType MODEL_ANIMATION = new TrackType(PhotonModelAnimationTrack.class,
			PhotonModelAnimationTrack::new, new PhotonModelAnimationTrackEditor());

	/// 单实例生命周期、模型参数和材质排序的原生动画绑定。
	private static final List<RuntimeBinding> BINDINGS = List.of(
			new RuntimeBinding("enabled", PhotonModelTextUtil.key("enabled"), ConfigValueType.BOOL,
					object -> ((PhotonModelEmitter) object).runtime().enabled),
			new RuntimeBinding("duration", PhotonModelTextUtil.key("duration"), ConfigValueType.INT,
					object -> ((PhotonModelEmitter) object).runtime().duration),
			new RuntimeBinding("startDelay", PhotonModelTextUtil.key("start_delay"), ConfigValueType.INT,
					object -> ((PhotonModelEmitter) object).runtime().startDelay),
			new RuntimeBinding("looping", PhotonModelTextUtil.key("looping"), ConfigValueType.BOOL,
					object -> ((PhotonModelEmitter) object).runtime().looping),
			new RuntimeBinding("paused", PhotonModelTextUtil.key("pause"), ConfigValueType.BOOL,
					object -> ((PhotonModelEmitter) object).runtime().paused),
			new RuntimeBinding("playbackSpeed", PhotonModelTextUtil.key("playback_speed"), ConfigValueType.FLOAT,
					object -> ((PhotonModelEmitter) object).runtime().playbackSpeed),
			new RuntimeBinding("animationOffset", PhotonModelTextUtil.key("animation_offset"), ConfigValueType.FLOAT,
					object -> ((PhotonModelEmitter) object).runtime().animationOffset),
			new RuntimeBinding("length", PhotonModelTextUtil.key("length"), ConfigValueType.FLOAT,
					object -> ((PhotonModelEmitter) object).runtime().length),
			new RuntimeBinding("width", PhotonModelTextUtil.key("width"), ConfigValueType.FLOAT,
					object -> ((PhotonModelEmitter) object).runtime().width),
			new RuntimeBinding("opacity", PhotonModelTextUtil.key("opacity"), ConfigValueType.FLOAT,
					object -> ((PhotonModelEmitter) object).runtime().opacity),
			new RuntimeBinding("color", PhotonModelTextUtil.key("color"), ConfigValueType.COLOR,
					object -> ((PhotonModelEmitter) object).runtime().color),
			new RuntimeBinding("renderer.orderInLayer", "photon.emitter.config.renderer.orderInLayer", ConfigValueType.INT,
					object -> ((PhotonModelEmitter) object).runtime().renderer().orderInLayer));

	/// 单实例模型特效的 Photon 对象类型，供原生注册器按注解发现。
	@LDLRegisterClient(name = TYPE_NAME, registry = REGISTRY)
	public static final FXObjectType MODEL_EMITTER = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonModelEmitter();
		}

		@Override
		public IGuiTexture icon() {
			return ParticleEmitter.ICON;
		}

		@Override
		public List<RuntimeBinding> runtimeBindings() {
			return BINDINGS;
		}

		@Override
		public List<AnimatedPropertyType> animatableProperties(FXObject target) {
			var properties = new ArrayList<>(super.animatableProperties(target));

			if (target instanceof PhotonModelEmitter model) {
				properties.addAll(model.runtime().dynamicProperties());
			}

			return List.copyOf(properties);
		}

		@Override
		@Nullable
		public RuntimeBinding resolveRuntimeBinding(FXObject target, String path) {
			var binding = super.resolveRuntimeBinding(target, path);

			if (binding != null) {
				return binding;
			}

			return target instanceof PhotonModelEmitter model ? model.runtime().resolveDynamicBinding(path) : null;
		}
	};
}
