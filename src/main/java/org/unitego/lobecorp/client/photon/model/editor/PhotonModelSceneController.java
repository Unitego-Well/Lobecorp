package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld.ClientSupport;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelEditUtil.Edge;

import java.util.HashSet;
import java.util.function.IntSupplier;

import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

import java.util.ArrayList;
import java.util.List;

/// 模型会话绑定原生场景、粒子管理器和真实特效运行时，不创建并行视口。
public class PhotonModelSceneController {
	/// 与特效编辑器一致的场景根位置，单位为方块。
	private static final Vector3f MODEL_ORIGIN = new Vector3f(0.5f, 2f, 0.5f);
	private final PhotonModelEditSession session;
	private final PhotonModelEmitter emitter;
	private final PhotonModelProjectEditor owner;
	private final PhotonModelPreview preview;
	private boolean initialized;
	private float fractionalTime;
	private boolean animationMode;
	private String animationClip = "";

	public PhotonModelSceneController(PhotonModelEditSession session, PhotonModelEmitter emitter, PhotonModelProjectEditor owner) {
		this.session = session;
		this.emitter = emitter;
		this.owner = owner;
		preview = new PhotonModelPreview(session, owner.sceneView.sceneEditor, emitter);
	}

	public PhotonModelPreview preview() {
		return preview;
	}

	public PhotonModelProjectEditor owner() {
		return owner;
	}

	public void initialize() {
		if (initialized) {
			return;
		}

		try {
			owner.sceneView.fxObjectAnimationView.setDisplay(false);
			owner.sceneView.fxObjectAnimationView.removeSelf();
			emitter.transform().parent(null, false);
			emitter.transform().position(new Vector3f());
			emitter.transform().rotation(new Quaternionf());
			emitter.transform().scale(new Vector3f(1));
			emitter.config.enabled = true;
			emitter.config.startDelay = 0;
			emitter.config.paused = false;
			emitter.config.looping = true;
			refresh();
			emitter.setPreviewSampler(partialTicks -> preview.sample(time(partialTicks)));
			owner.runtime = new FXRuntime(new FXData(new ArrayList<>(List.of(emitter))));
			owner.runtime.root.updatePos(new Vector3f(MODEL_ORIGIN));
			initialized = true;
			owner.sceneView.loadScene();
			var manager = owner.sceneView.particleManager;

			if (manager.getLevel() == null) {
				manager.setLevel(ClientSupport.asClientWorld(owner.sceneView.level));
			}

			preview.initialize();
			owner.sceneView.fxObjectInfoView.inspect(emitter);
			installModelInformation();
			owner.sceneView.play();
			onNativeSeek(0);
			pause();
		} catch (RuntimeException | Error failure) {
			close();
			throw failure;
		}
	}

	public void refresh() {
		emitter.setAsset(session.asset());
	}

	public boolean isAnimationMode() {
		return animationMode;
	}

	public @Nullable Clip activeClip() {
		return animationMode ? session.asset().clips.stream().filter(clip -> clip.id.equals(animationClip))
				.findFirst().orElse(null) : null;
	}

	public void setAnimationMode(boolean animationMode) {
		this.animationMode = animationMode;
		animationClip = session.selectedClip;
		session.clearPose();
		preview.previewMorph("");
		preview.setAnimationMode(animationMode);
	}

	public void setAnimationClip(String id) {
		animationClip = id;
	}

	/// 保留原生 S 浮窗及停靠行为，模型项目不展示粒子种子和重复播放控件。
	private void installModelInformation() {
		var info = owner.sceneView.fxObjectInfoView;
		for (var child : info.titleBar.getChildren()) {
			if (child instanceof Label title) {
				title.setText(PhotonModelTextUtil.key("editor.model_information"));
			}
		}

		var modelStatus = info.inspector.getChildren().getLast();
		modelStatus.removeSelf();
		info.contentContainer.clearAllChildren();
		addModelCount("parts", () -> session.asset().parts.size());
		addModelCount("vertex", () -> session.asset().vertices.size());
		addModelCount("edge", this::edgeCount);
		addModelCount("face", () -> session.asset().faces.size());
		addModelCount("editor.selected_count", this::selectedCount);
		info.contentContainer.addChild(modelStatus);
		info.contentContainer.addChild(info.createInformation(Component.literal("FPS"),
				() -> Component.literal(Integer.toString(Minecraft.getInstance().getFps()))));
	}

	private void addModelCount(String key, IntSupplier count) {
		var info = owner.sceneView.fxObjectInfoView;
		info.contentContainer.addChild(info.createInformation(Component.translatable(PhotonModelTextUtil.key(key)),
				() -> Component.literal(Integer.toString(count.getAsInt()))));
	}

	private int edgeCount() {
		var edges = new HashSet<Edge>();
		for (var face : session.asset().faces) {
			for (int corner = 0; corner < face.vertices.length; corner++) {
				edges.add(Edge.of(face.vertices[corner], face.vertices[(corner + 1) % face.vertices.length]));
			}
		}
		return edges.size();
	}

	private int selectedCount() {
		return switch (session.selectionMode) {
			case PART -> session.part() == null ? 0 : 1;
			case VERTEX -> session.selectedVertices.size();
			case EDGE -> session.selectedEdges.size();
			case FACE -> session.selectedFaces.size();
		};
	}

	public void restart() {
		if (!initialized) {
			return;
		}

		owner.sceneView.reset();
		owner.sceneView.play();
		session.playing = true;
	}

	public void play() {
		owner.sceneView.particleManager.play();
		session.playing = true;
	}

	public void pause() {
		if (session.playing || owner.sceneView.particleManager.isPlaying()) {
			retainLoopPhase(owner.sceneView.particleManager.getTime(0) + fractionalTime);
		}

		owner.sceneView.particleManager.pause();
		session.playing = false;
	}

	/// 暂停循环时将原生时钟停在当前片段相位，避免停止循环采样后跳到末帧。
	private float retainLoopPhase(float time) {
		var clip = activeClip();

		if (clip == null || !clip.loop || clip.duration <= 0) {
			return time;
		}

		float phase = Math.max(0, time) % clip.duration;
		long wholeTicks = (long) Math.floor(phase);
		owner.sceneView.particleManager.setTime(wholeTicks);
		fractionalTime = phase - wholeTicks;
		session.time = phase;
		return phase;
	}

	public void stop() {
		seek(0);
	}

	public void seek(float time) {
		float target = Float.isFinite(time) ? Math.max(0, time) : 0;

		if (!initialized) {
			session.time = target;
			return;
		}

		owner.sceneView.particleManager.pause();
		session.playing = false;
		long wholeTicks = (long) Math.floor(target);
		owner.sceneView.simulateTo(wholeTicks);
		fractionalTime = target - wholeTicks;
		session.time = target;
	}

	public void seek(int time) {
		seek((float) time);
	}

	/// 原生 S 面板和重启的整数时间操作不继承模型的小数部分。
	public void onNativeSeek(long time) {
		fractionalTime = 0;
		session.time = Math.max(0, time);
		var runtime = owner.runtime;

		if (initialized && time == 0 && owner.sceneView.particleManager.getParticleAmount() == 0
				&& runtime != null && runtime.isEmitted()) {
			// 首次更新只将待添加粒子迁入渲染组，预览时间仍保持零刻。
			owner.sceneView.particleManager.tickInternal();
			owner.sceneView.particleManager.setTime(0);
		}
	}

	public void onNativeReset() {
		fractionalTime = 0;
		session.time = 0;
	}

	public float time() {
		return time(0);
	}

	private float time(float partialTicks) {
		boolean wasPlaying = session.playing;
		session.playing = owner.sceneView.particleManager.isPlaying();
		float time = owner.sceneView.particleManager.getTime(partialTicks) + fractionalTime;

		if (wasPlaying && !session.playing) {
			time = retainLoopPhase(time);
		}

		var clip = activeClip();

		if (session.playing && clip != null && !clip.loop && time >= clip.duration) {
			time = clip.duration;
			long wholeTicks = (long) Math.floor(time);
			owner.sceneView.particleManager.setTime(wholeTicks);
			fractionalTime = time - wholeTicks;
			pause();
		}

		session.time = time;
		return time;
	}

	public void close() {
		initialized = false;
		pause();
		preview.close();
		emitter.setPreviewSampler(null);

		if (owner.runtime != null) {
			owner.runtime.destroy(true);
			owner.runtime = null;
		}

		owner.sceneView.reset();
	}
}
