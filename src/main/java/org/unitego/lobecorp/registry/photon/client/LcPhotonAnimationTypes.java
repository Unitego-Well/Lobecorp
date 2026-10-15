package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonAnimationCallTrack;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationCallTrackEditor;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonChildAnimationTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackType;

public class LcPhotonAnimationTypes {
	/// 保存文件中的扩展对象类型；不可随显示名称重命名。
	public static final String TYPE_NAME = "lobecorp_animation_controller";
	/// 复用 Photon 对象注册表。
	private static final String REGISTRY = "photon:fx_object";
	/// 复用原生 TL 注册表的子动画轨道，保存键不得随翻译修改。
	public static final String CHILD_TRACK_NAME = "lobecorp_child_animation";
	/// 子动画轨道的原生类型登记，保持工程文件中的类型标识。
	@LDLRegisterClient(name = CHILD_TRACK_NAME, registry = "photon:timeline_track")
	public static final TrackType CHILD_ANIMATION = new TrackType(PhotonChildAnimationTrack.class,
			PhotonChildAnimationTrack::new, new PhotonChildAnimationTrackEditor());

	/// 项目测试时间轴的本特效调用轨道，始终注册以恢复项目中的原生轨道数据。
	public static final String CALL_TRACK_NAME = "lobecorp_animation_call";
	/// 原生添加轨道菜单的显示键，同时用于限定项目测试入口。
	public static final String CALL_TRACK_TRANSLATION_KEY = "photon.timeline_track." + CALL_TRACK_NAME;
	/// 动画调用轨道的原生类型登记，保持工程文件中的类型标识。
	@LDLRegisterClient(name = CALL_TRACK_NAME, registry = "photon:timeline_track")
	@SuppressWarnings("unused") // 原生注册器按注解发现此字段。
	public static final TrackType ANIMATION_CALL = new TrackType(PhotonAnimationCallTrack.class,
			PhotonAnimationCallTrack::new, new PhotonAnimationCallTrackEditor());

	/// 命名动画控制器的 Photon 对象类型，供原生注册器按注解发现。
	@LDLRegisterClient(name = TYPE_NAME, registry = REGISTRY)
	public static final FXObjectType CONTROLLER = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonAnimationController();
		}

		@Override
		public IGuiTexture icon() {
			return EmptyFXObject.TYPE.icon();
		}
	};
}
