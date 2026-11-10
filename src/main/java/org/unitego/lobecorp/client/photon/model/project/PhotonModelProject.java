package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;

import org.unitego.lobecorp.util.photon.playback.PhotonNamedTrackAuthoringUtil;
import org.unitego.lobecorp.registry.client.photon.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.client.photon.editor.PhotonProjectRawAccess;
import org.unitego.lobecorp.util.photon.compat.PhotonProjectNbtUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonTimelineDecodeUtil;
import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.nbt.NbtOps;
import static org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations.PROJECT_TYPE;

/// 正式模型工程保留原data原树；原生FX仅承担预览，不代替工程文件的数据结构。
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PhotonModelProject extends FXProject implements PhotonProjectRawAccess {
	private CompoundTag rawContainer = new CompoundTag();

	@Override
	public CompoundTag lobecorp$rawContainer() {
		return rawContainer.copy();
	}

	@Override
	public void lobecorp$rawContainer(CompoundTag value) {
		rawContainer = value.copy();
	}

	public static final ProjectType TYPE = ProjectType.of(PROJECT_TYPE, ".mfxproj", PhotonModelProject::new);
	private CompoundTag source = new CompoundTag();
	private CompoundTag metadata = new CompoundTag();
	private PhotonModelOutput model;
	private CompoundTag baseline = new CompoundTag();
	private CompoundTag rendererBaseline = new CompoundTag();
	private CompoundTag emitterBaseline = new CompoundTag();
	private CompoundTag timelineBaseline = new CompoundTag();
	private CompoundTag rawTimeline = new CompoundTag();
	private ISubscription modelExports;
	private PhotonModelWorkspace workspace;

	public PhotonModelProject() {
		var initial = new CompoundTag();
		initial.put("asset", new ByteArrayTag("{\"version\":1,\"vertices\":[],\"faces\":[]}".getBytes(StandardCharsets.UTF_8)));
		initial.put("playback", new CompoundTag());
		initial.put("renderer", new CompoundTag());
		restore(initial);
	}

	public CompoundTag document() {
		requireSingleModel();
		var result = source.copy();
		assemblePlayback(result);
		assembleAsset(result);
		assembleRenderer(result);
		var emitter = requireWrapper(model).getCompoundOrEmpty("data");
		assembleEmitter(result, emitter);
		assembleTimeline(result, emitter);
		return result;
	}

	private void requireSingleModel() {
		if (getFx().getFxData().objects().size() != 1 || getFx().getFxData().objects().getFirst() != model) {
			throw new IllegalStateException("模型工程只管理一个模型资产，组合对象请使用FX工程。");
		}
	}

	private void assemblePlayback(CompoundTag result) {
		var serialized = PersistedParser.serializeNBT(model.config, Platform.getFrozenRegistry());
		var playback = result.getCompoundOrEmpty("playback");

		for (var key : List.of("enabled", "duration", "startDelay", "looping", "paused", "clip", "playbackSpeed", "animationOffset")) {
			var value = serialized.get(key);

			if (!Objects.equals(value, baseline.get(key)) && value != null) {
				playback.put(key, value.copy());
			}
		}

		if (source.contains("playback") || !playback.isEmpty()) {
			result.put("playback", playback);
		}
	}

	private void assembleAsset(CompoundTag result) {
		if (!Objects.equals(model.config.assetSnapshot(), source.get("asset"))) {
			result.put("asset", model.config.assetSnapshot());
		}
	}

	private void assembleRenderer(CompoundTag result) {
		var renderer = PersistedParser.serializeNBT(model.config.modelRenderer, Platform.getFrozenRegistry());

		if (!renderer.equals(rendererBaseline)) {
			result.put("renderer", PhotonNamedTrackAuthoringUtil.merge(result.getCompoundOrEmpty("renderer"), renderer));
		}
	}

	private void assembleEmitter(CompoundTag result, CompoundTag emitter) {
		if (!source.contains("emitterData")) {
			result.put("emitterData", emitter.copy());
		} else if (!emitter.equals(emitterBaseline)) {
			result.put("emitterData", PhotonLosslessNbtUtil.changed(source.getCompoundOrEmpty("emitterData"), emitterBaseline, emitter));
		}
	}

	private void assembleTimeline(CompoundTag result, CompoundTag emitter) {
		var timeline = getFx().getFxData().timeline().serializeNBT(Platform.getFrozenRegistry());

		if (timeline.equals(timelineBaseline)) {
			return;
		}

		var definition = result.getCompoundOrEmpty("definitionSource");

		if (definition.isEmpty()) {
			definition = getFx().serializeNBT(Platform.getFrozenRegistry());
		}

		definition.getCompoundOrEmpty("fxData").put("timeline", PhotonLosslessNbtUtil.changed(rawTimeline, timelineBaseline, timeline));
		result.put("definitionSource", definition);

		if (!result.contains("emitterData")) {
			result.put("emitterData", emitter.copy());
		}
	}

	private static CompoundTag requireWrapper(PhotonModelOutput output) {
		var wrapper = output.serializeWrapper();

		if (wrapper == null) {
			throw new IllegalStateException("模型对象无法序列化。");
		}

		return wrapper;
	}

	public PhotonModelOutput model() {
		return model;
	}

	public PhotonModelProject copy() {
		var result = new PhotonModelProject();
		result.restore(document());
		result.model.transform()._setInternalID(model.id());
		var wrapper = result.model.serializeWrapper();
		if (wrapper == null) {
			throw new IllegalStateException("模型对象无法序列化。");
		}
		result.emitterBaseline = wrapper.getCompoundOrEmpty("data").copy();
		result.metadata = metadata.copy();
		result.rawContainer = rawContainer.copy();
		return result;
	}

	public FX exportFX() {
		var original = source.getCompoundOrEmpty("definitionSource");
		var current = getFx().serializeNBT(Platform.getFrozenRegistry());
		var timeline = current.getCompoundOrEmpty("fxData").getCompoundOrEmpty("timeline");
		current.getCompoundOrEmpty("fxData").put("timeline", PhotonLosslessNbtUtil.changed(rawTimeline, timelineBaseline, timeline));
		return new PhotonExportDefinition(PhotonNamedTrackAuthoringUtil.merge(original, current), Platform.getFrozenRegistry());
	}

	/// 先准备模型与原生配置的独立候选，失败不替换现有工程或预览数据。
	public void restore(CompoundTag candidate) {
		applyRestore(prepareRestore(candidate));
	}

	private RestoredProject prepareRestore(CompoundTag candidate) {
		var data = candidate.copy();

		if (data.contains("emitterData") && !(data.get("emitterData") instanceof CompoundTag)
				|| data.contains("definitionSource") && !(data.get("definitionSource") instanceof CompoundTag)) {
			throw new IllegalArgumentException("模型工程的扩展制作数据类型不受支持。");
		}

		var asset = Objects.requireNonNull(data.get("asset"), "模型工程缺少asset。");
		validateAsset(asset);

		if (data.contains("playback") && !(data.get("playback") instanceof CompoundTag)
				|| data.contains("renderer") && !(data.get("renderer") instanceof CompoundTag)) {
			throw new IllegalArgumentException("模型工程播放或渲染配置类型无效。");
		}

		var playback = data.getCompoundOrEmpty("playback");
		var renderer = data.getCompoundOrEmpty("renderer");
		var next = data.get("emitterData") instanceof CompoundTag originalEmitter
				? (PhotonModelOutput) LcPhotonModelOutputTypes.MODEL.codec().parse(NbtOps.INSTANCE, originalEmitter).getOrThrow()
				: new PhotonModelOutput();
		next.config.setAssetSnapshot(asset);
		PersistedParser.deserializeNBT(playback, next.config, Platform.getFrozenRegistry());
		next.config.setAssetSnapshot(asset);
		PersistedParser.deserializeNBT(renderer, next.config.modelRenderer, Platform.getFrozenRegistry());
		next.config.source = PhotonModelOutputConfig.Source.EMBEDDED;
		next.config.resource = null;
		next.config.file = "";
		validatePlayback(next);

		if (!data.contains("emitterData")) {
			next.transform()._setInternalParentID(FXRuntime.ROOT_UUID);
		}

		if (model != null && !data.contains("emitterData")) {
			next.transform()._setInternalID(model.id());
		}

		var timelineSource = data.getCompoundOrEmpty("definitionSource").getCompoundOrEmpty("fxData").getCompoundOrEmpty("timeline").copy();
		var timeline = PhotonTimelineDecodeUtil.read(Platform.getFrozenRegistry(), timelineSource);
		var timelineBaseline = timeline.serializeNBT(Platform.getFrozenRegistry());
		var wrapper = requireWrapper(next);
		var baseline = PersistedParser.serializeNBT(next.config, Platform.getFrozenRegistry());
		var rendererBaseline = PersistedParser.serializeNBT(next.config.modelRenderer, Platform.getFrozenRegistry());
		var emitterBaseline = wrapper.getCompoundOrEmpty("data").copy();
		return new RestoredProject(data, next, baseline, rendererBaseline, emitterBaseline,
				timelineBaseline, timelineSource, timeline);
	}

	private static void validateAsset(Tag asset) {
		try {
			var tree = switch (asset) {
				case ByteArrayTag bytes ->
						PhotonResourceJsonUtil.read(bytes.getAsByteArray(), PhotonResourceLimits.DEFAULT);
				case StringTag(var text) -> PhotonResourceJsonUtil.read(text, PhotonResourceLimits.DEFAULT);
				default -> throw new IOException("模型工程asset必须为字节或字符串。");
			};
			PhotonModelTopologyUtil.prepare(PhotonModelCodec.read(tree.getAsJsonObject()));
		} catch (IOException invalid) {
			throw new IllegalArgumentException("模型工程资产无效。", invalid);
		}
	}

	private static void validatePlayback(PhotonModelOutput output) {
		if (output.config.duration < 1 || output.config.startDelay < 0 || !Float.isFinite(output.config.playbackSpeed)
				|| output.config.playbackSpeed < 0 || !Float.isFinite(output.config.animationOffset)) {
			throw new IllegalArgumentException("模型工程播放设置无效。");
		}
	}

	private void applyRestore(RestoredProject restored) {
		var next = restored.model();
		source = restored.source();
		model = next;
		baseline = restored.baseline();
		rendererBaseline = restored.rendererBaseline();
		emitterBaseline = restored.emitterBaseline();
		timelineBaseline = restored.timelineBaseline();
		rawTimeline = restored.rawTimeline();
		getFx().getFxData().objects().clear();
		getFx().getFxData().objects().add(next);
		getFx().getFxData().timeline().tracks().clear();
		getFx().getFxData().timeline().tracks().addAll(restored.timeline().tracks());
		getFx().getFxData().timeline().markers().clear();
		getFx().getFxData().timeline().markers().addAll(restored.timeline().markers());
		if (workspace != null) {
			workspace.invalidate();
		}
	}

	private record RestoredProject(CompoundTag source, PhotonModelOutput model, CompoundTag baseline,
	                               CompoundTag rendererBaseline, CompoundTag emitterBaseline,
	                               CompoundTag timelineBaseline, CompoundTag rawTimeline, Timeline timeline) {
	}

	@Override
	public ProjectType getProjectType() {
		return TYPE;
	}

	@Override
	public void onLoad(Editor editor) {
		super.onLoad(editor);
		unsubscribeExports();

		if (editor instanceof FXEditor fx) {
			subscribeExports(fx);
			showWorkspace(fx);
		}
	}

	private void subscribeExports(FXEditor editor) {
		modelExports = PhotonModelAuthoringPanel.exports(editor);
	}

	private void unsubscribeExports() {
		var current = modelExports;
		modelExports = null;

		if (current != null) {
			current.unsubscribe();
		}
	}

	public void showWorkspace(FXEditor editor) {
		if (workspace != null) {
			workspace.close();
		}
		workspace = new PhotonModelWorkspace(editor, this);
	}

	@Override
	public void onClosed(Editor editor) {
		if (workspace != null) {
			workspace.close();
			workspace = null;
		}
		unsubscribeExports();
		super.onClosed(editor);
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public void serializeProject(ValueOutput output) {
		for (var entry : document().entrySet()) {
			output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
		}
	}

	@Override
	public void serialize(ValueOutput output) {
		super.serialize(output);
		for (var entry : rawContainer.entrySet()) {
			if (!entry.getKey().equals("meta") && !entry.getKey().equals("data")) {
				output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
			}
		}
	}

	@Override
	public void deserializeProject(ValueInput input) {
		var data = new CompoundTag();
		for (var key : source.keySet()) {
			input.read(key, ExtraCodecs.NBT).ifPresent(value -> data.put(key, value));
		}
		for (var key : List.of("asset", "playback", "renderer")) {
			input.read(key, ExtraCodecs.NBT).ifPresent(value -> data.put(key, value));
		}
		restore(data);
	}

	@Override
	public void deserialize(ValueInput input) {
		rawContainer = PhotonProjectNbtUtil.read(input);
		var value = input.read("data", ExtraCodecs.NBT).orElse(null);
		if (!(value instanceof CompoundTag data)) {
			throw new IllegalArgumentException("模型工程data缺失。");
		}
		var meta = input.read("meta", ExtraCodecs.NBT).orElse(null);
		metadata = meta instanceof CompoundTag compound ? compound.copy() : new CompoundTag();
		restore(data);
	}

	@Override
	public void serializeMetadata(ValueOutput output) {
		super.serializeMetadata(output);
		for (var entry : metadata.entrySet()) {
			output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
		}
	}
}
