package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.menu.FileMenu;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import com.lowdragmc.photon.gui.editor.resource.MeshResource;
import com.lowdragmc.photon.gui.editor.resource.PhotonShaderFunctionGraphResource;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelEditSession;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelPlaybackConfig;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.util.photon.model.PhotonModelExportUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelRendererUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelDefaultUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/// 使用原生 NBT 项目格式保存的独立模型特效工程；会话是资产的唯一编辑来源。
/// 工程具有可变会话和生命周期身份，保持框架项目类的扩展语义。
public class PhotonModelProject implements IProject {
	/// 注册到原编辑器文件菜单的模型工程类型。
	public static final ProjectType TYPE = new ModelProjectType();
	private static final ProjectType DEFINITION_TYPE = new ModelSourceType("definition.type", PhotonModelFileUtil.DEFINITION_SUFFIX, false);
	private static final ProjectType OBJ_TYPE = new ModelSourceType("obj.type", PhotonModelEmitterConfig.OBJ_SUFFIX, true);
	/// 原生历史快照中的资产字段，使用字节数组避开 NBT 字符串长度上限。
	private static final String ASSET_KEY = "asset";
	/// 默认播放设置与资产一同进入工程保存和脏状态快照。
	private static final String PLAYBACK_KEY = "playback";
	/// 默认渲染配置以原生结构保存，使工程、定义和包使用同一份模型设置。
	private static final String RENDERER_KEY = "renderer";
	/// 模型工程复用原生材质、网格和文件浏览资源，不持有共享库的可变副本。
	private static final Resources MODEL_RESOURCES = Resources.of(MaterialResource.INSTANCE, MeshResource.INSTANCE,
			ShaderGraphResource.INSTANCE, PhotonShaderFunctionGraphResource.INSTANCE);

	private final PhotonModelEditSession session;
	private PhotonModelPlaybackConfig playback;
	private final RendererSetting renderer;
	private final PhotonModelEmitter previewEmitter;
	private final FXProject exportProject;
	private final @Nullable PhotonModelEmitter sourceEmitter;
	private final @Nullable FXEditor sourceEditor;
	/// 来源工程的对象身份，用于拒绝向已关闭或切换的特效工程应用模型。
	private final @Nullable IProject sourceProject;
	/// 工程显示名称用于标签和导出对象，不改变模型资产 JSON 协议。
	private String modelName = "";

	public PhotonModelProject() {
		this(PhotonModelDefaultUtil.whiteBlock(), new PhotonModelEmitter(), null, null);
	}

	public PhotonModelProject(PhotonModelAsset asset) {
		this(asset, new PhotonModelEmitter(), null, null);
	}

	public PhotonModelProject(PhotonModelEmitter emitter, @Nullable FXEditor sourceEditor) {
		this(emitter.getAsset(), (PhotonModelEmitter) emitter.copy(true), sourceEditor == null ? null : emitter, sourceEditor);
		modelName = emitter.getName();
	}

	private PhotonModelProject(PhotonModelAsset asset, PhotonModelEmitter previewEmitter,
	                           @Nullable PhotonModelEmitter sourceEmitter, @Nullable FXEditor sourceEditor) {
		session = new PhotonModelEditSession(asset);
		playback = PhotonModelPlaybackConfig.from(previewEmitter.config);
		session.selectedClip = playback.clip;
		this.previewEmitter = previewEmitter;
		renderer = PhotonModelRendererUtil.copy(previewEmitter.config.modelRenderer);
		previewEmitter.config.overrideRenderer = false;
		this.sourceEmitter = sourceEmitter;
		this.sourceEditor = sourceEditor;
		sourceProject = sourceEditor == null ? null : sourceEditor.getCurrentProject();
		exportProject = PhotonModelExportUtil.createProject(this);
	}

	public String modelName() {
		return modelName;
	}

	public String displayModelName() {
		return modelName.isBlank() ? Component.translatable(PhotonModelTextUtil.key("project.unnamed")).getString() : modelName;
	}

	public FXProject exportProject() {
		return exportProject;
	}

	/// 文件菜单可以导入模型定义和 OBJ，源文件不进入工程的覆盖保存路径。
	public static void installFileTypes(FileMenu menu) {
		menu.addProjectProvider(TYPE);
		menu.addProjectProvider(DEFINITION_TYPE);
		menu.addProjectProvider(OBJ_TYPE);
		menu.registerNewMenuCreator((_, branch) -> {
			branch.remove(DEFINITION_TYPE.getName());
			branch.remove(OBJ_TYPE.getName());
		});
	}

	/// 从工程或模型源文件名更新显示名称，保留名称中的其他点号。
	public void updateModelName(File file) {
		var name = file.getName();

		for (var suffix : List.of(PhotonModelFileUtil.PROJECT_SUFFIX, PhotonModelFileUtil.DEFINITION_SUFFIX,
				PhotonModelEmitterConfig.OBJ_SUFFIX)) {
			if (name.endsWith(suffix)) {
				modelName = name.substring(0, name.length() - suffix.length());
				return;
			}
		}

		modelName = name;
	}

	public PhotonModelEditSession session() {
		return session;
	}

	public PhotonModelPlaybackConfig playback() {
		return playback;
	}

	public void setPlayback(PhotonModelPlaybackConfig playback) {
		this.playback = playback.copy();
	}

	public void applyPlayback(PhotonModelEmitterConfig config) {
		playback.validate(session.asset());
		playback.applyTo(config);
	}

	public PhotonModelEmitter previewEmitter() {
		return previewEmitter;
	}

	public RendererSetting renderer() {
		return renderer;
	}

	public void setRenderer(RendererSetting renderer) {
		PhotonModelRendererUtil.copyTo(renderer, this.renderer);
		applyRenderer(previewEmitter.config);
	}

	public void applyRenderer(PhotonModelEmitterConfig config) {
		PhotonModelRendererUtil.copyTo(renderer, config.modelRenderer);
	}

	public @Nullable PhotonModelEmitter sourceEmitter() {
		return sourceEmitter;
	}

	public @Nullable FXEditor sourceEditor() {
		return sourceEditor;
	}

	/// 来源发射器仍在原特效场景内，且标签和工程未切换时才能提交。
	public boolean canApplyToSource() {
		if (sourceEmitter == null || sourceEditor == null || sourceProject == null
				|| sourceEditor.getCurrentProject() != sourceProject || sourceEditor.runtime == null
				|| sourceEditor.runtime.getSceneObject(sourceEmitter.id()) != sourceEmitter) {
			return false;
		}

		var window = sourceEditor.getWindow();
		return window != null && window.getEditors().containsKey(sourceEditor);
	}

	@Override
	public Resources getResources() {
		return MODEL_RESOURCES;
	}

	@Override
	public ProjectType getProjectType() {
		return TYPE;
	}

	@Override
	public String getVersion() {
		return PhotonModelAsset.FORMAT_VERSION + ".0";
	}

	@Override
	public void serializeProject(ValueOutput output) {
		playback.validate();
		var bytes = PhotonModelIOUtil.writeJson(session.asset()).getBytes(StandardCharsets.UTF_8);
		output.store(ASSET_KEY, ExtraCodecs.NBT, new ByteArrayTag(bytes));
		playback.serialize(output.child(PLAYBACK_KEY));
		output.store(RENDERER_KEY, ExtraCodecs.NBT, PersistedParser.serializeNBT(renderer, Platform.getFrozenRegistry()));
	}

	@Override
	public void deserializeProject(ValueInput input) {
		if (!(input.read(ASSET_KEY, ExtraCodecs.NBT).orElse(null) instanceof ByteArrayTag tag)) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("project.missing_asset"));
		}

		var asset = PhotonModelIOUtil.readJson(new String(tag.getAsByteArray(), StandardCharsets.UTF_8));

		if (!(input.read(RENDERER_KEY, ExtraCodecs.NBT).orElse(null) instanceof CompoundTag rendererTag)) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("invalid_renderer"));
		}

		var restoredPlayback = new PhotonModelPlaybackConfig();
		restoredPlayback.deserialize(input.child(PLAYBACK_KEY)
				.orElseThrow(() -> new IllegalArgumentException(PhotonModelTextUtil.key("invalid_playback"))));
		restoredPlayback.validate();
		playback = restoredPlayback;
		var restoredRenderer = new RendererSetting();
		PersistedParser.deserializeNBT(rendererTag, restoredRenderer, Platform.getFrozenRegistry());
		setRenderer(restoredRenderer);
		session.restore(asset);
		session.selectedClip = playback.clip;
	}

	@Override
	public void onLoad(Editor editor) {
		if (!(editor instanceof PhotonModelProjectEditor modelEditor)) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("project.editor_required"));
		}

		modelEditor.installWorkspace(this);
		exportProject.onLoad(editor);
	}

	@Override
	public void onClosed(Editor editor) {
		session.playing = false;
		exportProject.onClosed(editor);
	}

	/// 模型源只读导入到新的模型特效工程，保存始终使用正式工程类型。
	private static class ModelSourceType extends ProjectType {
		private final boolean obj;

		private ModelSourceType(String name, String suffix, boolean obj) {
			super(PhotonModelTextUtil.key(name), suffix, PhotonModelProject::new);
			this.obj = obj;
		}

		@Override
		public IProject loadProjectFromFile(File file) throws IOException {
			var project = obj ? new PhotonModelProject(PhotonModelIOUtil.readObj(
					Files.readString(file.toPath(), StandardCharsets.UTF_8), true))
					: new PhotonModelProject(PhotonModelExportUtil.readDefinition(file), null);
			project.updateModelName(file);
			return project;
		}

		@Override
		public void openProject(Editor editor, File file) throws IOException {
			editor.loadProject(loadProjectFromFile(file), null);
		}
	}

	/// 工程文件沿用原生 NBT、元数据、保存与脏状态比较，资产快照放在项目数据内。
	private static class ModelProjectType extends ProjectType {
		private ModelProjectType() {
			super(PhotonModelTextUtil.key("project.type"), PhotonModelFileUtil.PROJECT_SUFFIX, PhotonModelProject::new);
		}

		@Override
		public IProject loadProjectFromFile(File file) throws Exception {
			var project = (PhotonModelProject) super.loadProjectFromFile(file);
			project.updateModelName(file);
			return project;
		}

		@Override
		public void saveProjectToFile(IProject project, File file) throws Exception {
			if (!(project instanceof PhotonModelProject model)) {
				throw new IllegalArgumentException(PhotonModelTextUtil.key("project.editor_required"));
			}

			super.saveProjectToFile(project, file);
			model.updateModelName(file);
		}
	}
}
