package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;

/// 复用原生片段绘制、拖动、框选、复制和检视，只添加子动画调用数据。
public class PhotonChildAnimationTrackEditor extends ClipTrackEditor {
	/// 按轨道弱引用缓存候选库；只在定义对象替换时解析，避免每帧反复读取完整 NBT。
	private final Map<Track, LibraryCache> libraries = new WeakHashMap<>();

	private record LibraryCache(CompoundTag definition, @Nullable PhotonAnimationLibrary library) {
	}

	@Override
	public ColorPattern chipColor() {
		return ColorPattern.GREEN;
	}

	@Override
	public ColorPattern clipFillColor() {
		return ColorPattern.T_GREEN;
	}

	@Override
	public UIElement buildHeaderContent(TimelineContext context, Track track, TrackUIState state) {
		return buildTargetSlot(context, track, false);
	}

	@Override
	protected String clipLabel(TimelineContext context, Track track, Clip clip) {
		if (!(clip instanceof PhotonChildAnimationClip child)) {
			return null;
		}

		var library = childLibrary(context, track);
		var animation = library == null ? null : library.find(child.animation);
		return animation == null ? Component.translatable(PhotonAnimationTextUtil.key(animationSelectorKey())).getString() : animation.name;
	}

	@Override
	protected void onLaneDoubleClick(TimelineContext context, Track track, float x, float y) {
		addClip(context, track, x);
	}

	@Override
	protected void onLaneRightClick(TimelineContext context, Track track, float x, float y) {
		var menu = TreeBuilder.Menu.start();
		menu.leaf("photon.gui.editor.timeline.add_clip", () -> addClip(context, track, x));
		context.openMenu(x, y, menu);
	}

	private void addClip(TimelineContext context, Track track, float x) {
		var library = childLibrary(context, track);
		var animation = library == null ? null : library.find(library.defaultAnimation);

		if (animation == null && library != null && !library.animations.isEmpty()) {
			animation = library.animations.getFirst();
		}

		var duration = animation == null ? context.defaultClipDuration(track.targetId()) : animation.playbackDuration();
		var clip = createClip(Math.max(0, Math.round(context.xToTick(x))), Math.max(1, duration));

		if (animation != null) {
			clip.animation = animation.id.toString();
		}

		context.addClip(track, clip);
	}

	protected PhotonChildAnimationClip createClip(double start, double duration) {
		return new PhotonChildAnimationClip(start, duration, 1);
	}

	@Override
	protected void buildHeaderConfigurator(ConfiguratorGroup group, TimelineContext context, Track track) {
		var runtime = context.runtime();
		var spawners = runtime == null ? List.<PhotonEmitterSpawner>of() : runtime.objects.values().stream()
				.filter(PhotonEmitterSpawner.class::isInstance).map(PhotonEmitterSpawner.class::cast).toList();
		var candidates = new ArrayList<String>();
		candidates.add("");
		spawners.stream().map(spawner -> spawner.id().toString()).forEach(candidates::add);
		var selected = targetReference(track);

		if (!candidates.contains(selected)) {
			candidates.add(selected);
		}

		PhotonAnimationForms.select(group, "action_spawner",
				() -> targetReference(track),
				value -> context.bind(track, value.isEmpty() ? null : UUID.fromString(value)), candidates,
				value -> spawners.stream().filter(spawner -> spawner.id().toString().equals(value))
						.map(PhotonEmitterSpawner::getName).findFirst()
						.orElseGet(() -> Component.translatable(PhotonAnimationTextUtil.key("no_reference")).getString()));
	}

	@Override
	protected void buildClipConfigurator(ConfiguratorGroup group, TimelineContext context, Track track, Clip clip) {
		if (!(clip instanceof PhotonChildAnimationClip child)) {
			return;
		}

		var library = childLibrary(context, track);
		animationSelector(group, context, child, library, animationSelectorKey());
		PhotonAnimationUiUtil.enumeration(group, modeSelectorKey(), () -> child.mode,
				value -> edit(context, child, () -> child.mode = value, true), PhotonChildAnimationClip.Mode.values());

		if (child.mode == PhotonChildAnimationClip.Mode.CONTROLLED) {
			PhotonAnimationUiUtil.enumeration(group, "child_completion", () -> child.completion,
					value -> edit(context, child, () -> child.completion = value, false), PhotonChildAnimationClip.Completion.values());
		}

		if (child.mode == PhotonChildAnimationClip.Mode.INDEPENDENT) {
			PhotonAnimationUiUtil.number(group, "action_duration", () -> child.playbackDuration,
					value -> edit(context, child, () -> {
						var duration = value.doubleValue();

						if (!Double.isFinite(duration) || (duration != -1 && duration < 0)) {
							throw new IllegalArgumentException();
						}

						child.playbackDuration = duration;
					}, false), child.playbackDuration);
		}

		var workspace = PhotonAnimationTimelineUtil.workspace(context);
		var source = workspace == null ? null : workspace.editingDefinition();
		PhotonAnimationInputEditor.build(group, library == null ? null : library.find(child.animation),
				source, child.inputs, action -> edit(context, child, action, false), workspace);
	}

	protected String animationSelectorKey() {
		return "child_animation";
	}

	protected String modeSelectorKey() {
		return "child_mode";
	}

	private static void animationSelector(ConfiguratorGroup group, TimelineContext context,
	                                      PhotonChildAnimationClip clip, @Nullable PhotonAnimationLibrary library, String key) {
		var candidates = new ArrayList<String>();
		candidates.add("");

		if (library != null) {
			library.animations.stream().map(animation -> animation.id.toString()).forEach(candidates::add);
		}

		if (!candidates.contains(clip.animation)) {
			candidates.add(clip.animation);
		}

		PhotonAnimationForms.select(group, key, () -> clip.animation,
				value -> edit(context, clip, () -> {
					clip.animation = value;
					clip.inputs.clear();
				}, true), candidates, value -> {
					var animation = library == null ? null : library.find(value);
					return animation == null ? Component.translatable(PhotonAnimationTextUtil.key("no_reference")).getString() : animation.name;
				});
	}

	private static void edit(TimelineContext context, PhotonChildAnimationClip clip, Runnable action, boolean inspect) {
		var before = clip.copy();
		before.id = clip.id;
		context.pushEdit("photon.gui.editor.timeline.edit_clip", () -> {
			action.run();
			refresh(context, clip, inspect);
		}, () -> {
			clip.restoreFrom(before);
			refresh(context, clip, inspect);
		});
	}

	private static void refresh(TimelineContext context, PhotonChildAnimationClip clip, boolean inspect) {
		context.requestRebuild();
		context.refreshPreview();
		var track = context.selectedClipTrack();

		if (!inspect || track == null || context.selectedClip() != clip) {
			return;
		}

		var type = track.type();

		if (type != null && type.editor() instanceof ClipTrackEditor editor) {
			context.inspectProperty(track, editor.clipConfigurator(context, track, clip));
		}
	}

	protected @Nullable PhotonAnimationLibrary childLibrary(TimelineContext context, Track track) {
		var runtime = context.runtime();

		if (runtime == null || !(runtime.objects.get(track.targetId()) instanceof PhotonEmitterSpawner spawner)) {
			return null;
		}

		if (spawner.config.source == PhotonEmitterSpawnConfig.Source.PROJECT) {
			var scope = spawner.templateScope();

			if (scope == null) {
				return null;
			}

			var workspace = PhotonAnimationTimelineUtil.workspace(context);

			if (workspace != null && workspace.projectTemplateUsesLibrary(track)) {
				return workspace.library();
			}

			return library(track, scope.getAllSceneObjects().stream().filter(PhotonAnimationController.class::isInstance)
					.map(PhotonAnimationController.class::cast).toList());
		}

		var fx = spawner.config.fxLocation == null ? null : FXHelper.getFX(spawner.config.fxLocation);
		return fx == null ? null : library(track, fx.getFxData().objects().stream().filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList());
	}

	private @Nullable PhotonAnimationLibrary library(Track track, List<PhotonAnimationController> controllers) {
		if (controllers.size() != 1) {
			return null;
		}

		var source = controllers.getFirst();
		var cached = libraries.get(track);

		if (cached != null && cached.definition() == source.definition) {
			return cached.library();
		}

		PhotonAnimationLibrary library;

		try {
			library = source.library();

			if (library.migrationBlocked()) {
				library = null;
			}
		} catch (IllegalArgumentException invalidDefinition) {
			library = null;
		}

		libraries.put(track, new LibraryCache(source.definition, library));
		return library;
	}

	private static String targetReference(Track track) {
		var target = track.targetId();
		return target == null ? "" : target.toString();
	}

	@Override
	public void onRemoved(TimelineContext context, Track track, TrackUIState state) {
		libraries.remove(track);
	}

	@Override
	public void onTargetWillChange(TimelineContext context, Track track, @Nullable UUID oldTargetId) {
		libraries.remove(track);
	}
}
