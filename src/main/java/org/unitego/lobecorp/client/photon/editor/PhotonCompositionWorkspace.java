package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectAssets;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition.*;
import org.unitego.lobecorp.util.photon.editor.PhotonCompositionExportUtil;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/// 只提供制作输入和宿主传输控制；组成件触发统一交给生产组合执行核心。
public class PhotonCompositionWorkspace extends View implements AutoCloseable {
	// 外部文件按半秒检查，连续修改稳定四分之一秒后才发布新作者快照。
	private static final long POLL_NANOS = 500_000_000L;
	private static final long DEBOUNCE_NANOS = 250_000_000L;
	// 定位使用有限的编辑器时间和分批模拟，避免一次输入阻塞整个编辑器。
	private static final long MAX_PREVIEW_TICKS = 6000;
	private static final int REPLAY_BATCH = 40;
	private final PhotonEffectEditor editor;
	private final TextElement status = new Label().setText("请选择激光作者工程");
	private final UIElement rows = new UIElement();
	private final Map<String, TextField> fields = new LinkedHashMap<>();
	private final HashSet<Integer> disabled = new HashSet<>();
	private final TextField seekField = new TextField().setText("0");
	private final TextField exportPath = new TextField().setText("");
	private final double[] durations = {40, 60, 20};
	private final float[] rootPosition = {0.5f, 2, 0.5f};
	private final float[] beamPosition = {0.5f, 2, 0.5f};
	private final float[] beamParameters = {16, 1};
	private final Map<String, float[]> poseOffsets = new LinkedHashMap<>();
	private final Map<String, float[]> poseRotations = new LinkedHashMap<>();
	private boolean restoring;
	private Definition definition = PhotonOwnedLaserComposition.defaults();
	private FXProject project;
	private PhotonOwnedLaser preview;
	private Phase phase;
	private String observed = "";
	private String published = "";
	private long pollAt;
	private long changedAt;
	private long revision;
	private long refreshGeneration;
	private String pendingFingerprint = "";
	private boolean probeInFlight;
	private long time;
	private long target = -1;
	private boolean playing;
	private boolean live = true;
	private boolean authoring = true;
	private boolean closed;
	private CompoundTag authorData = new CompoundTag();

	public PhotonCompositionWorkspace(PhotonEffectEditor editor) {
		super("自有激光组合");
		this.editor = editor;
		setFloatable(false);
		var scroll = new ScrollerView();
		scroll.layout(layout -> layout.flex(1).widthPercent(100));
		addChild(scroll);
		scroll.addScrollViewChild(status);
		var controls = new UIElement();
		scroll.addScrollViewChild(controls);
		button(controls, "开始", () -> begin(true));
		button(controls, "暂停／继续", () -> playing = !playing);
		button(controls, "后退一帧", () -> seek(Math.max(0, time - 1)));
		button(controls, "前进一步", () -> seek(time + 1));
		controls.addChild(seekField);
		button(controls, "定位（tick）", () -> seek(Long.parseLong(seekField.getText())));
		button(controls, "结束并播放消散", this::finish);
		button(controls, "取消并清理", this::cancel);
		button(controls, "实时刷新开关", () -> {
			live = !live;
			if (!live) {
				refreshGeneration++;
				pendingFingerprint = "";
				PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
				PhotonOwnedEffectResources.markPublishedPreviewDirty(PhotonOwnedLaser.ID);
			}
			status.setText(live ? "实时刷新开启" : "实时刷新暂停，修改后请手动刷新");
		});
		button(controls, "立即刷新并保留时间", this::refreshPreview);
		button(controls, "切回原生编辑", () -> {
			cancel();
			editor.reloadEffect();
		});
		for (int i = 0; i < durations.length; i++) {
			int index = i;
			number(controls, new String[]{"蓄力时长（tick）", "发射时长（tick）", "恢复时长（tick）"}[i],
					durations[i], value -> {
						durations[index] = Math.max(1, value);
						changed();
					});
		}
		vector(controls, "根位置", rootPosition);
		vector(controls, "光束位置", beamPosition);
		number(controls, "光束长度", beamParameters[0], value -> {
			beamParameters[0] = Math.max(0, value.floatValue());
			changed();
		});
		number(controls, "光束宽度", beamParameters[1], value -> {
			beamParameters[1] = Math.max(0, value.floatValue());
			changed();
		});
		button(controls, "保存组合到当前工程", this::persist);
		button(controls, "恢复工程组合", this::restore);
		button(controls, "添加阶段组成件", this::addCue);
		scroll.addScrollViewChild(rows);
		scroll.addScrollViewChild(new Label().setText("发布目录／组合包路径（不会覆盖已有文件）"));
		scroll.addScrollViewChild(exportPath);
		button(scroll, "导出冻结组件与组合清单", this::export);
		button(scroll, "重载资源并打开发布包", this::mountPublished);
		rebuildRows();
		addEventListener(UIEvents.TICK, _ -> tickWorkspace());
	}

	private void button(UIElement parent, String text, Runnable action) {
		var button = new Button().setText(text).setOnClick(_ -> guarded(action));
		if (parent instanceof ScrollerView scroll) {
			scroll.addScrollViewChild(button);
		} else {
			parent.addChild(button);
		}
	}

	private void guarded(Runnable action) {
		try {
			action.run();
		} catch (Exception failure) {
			playing = false;
			status.setText("操作失败：" + failure.getMessage());
		}
	}

	private void number(UIElement parent, String label, double value, Consumer<Double> change) {
		parent.addChild(new Label().setText(label));
		var field = new TextField().setText(Double.toString(value)).setTextResponder(text -> {
			if (restoring) {
				return;
			}
			try {
				double parsed = Double.parseDouble(text);
				if (Double.isFinite(parsed)) {
					guarded(() -> change.accept(parsed));
				}
			} catch (NumberFormatException ignored) {
				status.setText("数值尚未填写完整");
			}
		});
		fields.put(label, field);
		parent.addChild(field);
	}

	private void vector(UIElement parent, String label, float[] values) {
		for (int i = 0; i < values.length; i++) {
			int axis = i;
			number(parent, label + " " + "XYZ".charAt(i), values[i], value -> {
				values[axis] = value.floatValue();
				changed();
			});
		}
	}

	private void rebuildRows() {
		rows.clearAllChildren();
		for (int i = 0; i < definition.cues().size(); i++) {
			int index = i;
			var cue = definition.cues().get(i);
			var row = new UIElement();
			rows.addChild(row);
			row.addChild(new Label().setText("组成件 " + (i + 1)));
			button(row, disabled.contains(i) ? "已禁用：点击启用" : "已启用：点击禁用", () -> {
				if (!disabled.remove(index)) {
					disabled.add(index);
				}
				changed();
				rebuildRows();
			});
			row.addChild(new TextField().setText(cue.component()).setTextResponder(value ->
					guarded(() -> update(index, value, null, null, null, null, null))));
			cycle(row, "阶段", cue.phase(), Phase.values(), value -> update(index, null, value, null, null, null, null));
			number(row, "阶段延迟（tick）", cue.delayTicks(), value -> update(index, null, null, value, null, null, null));
			row.addChild(new TextField().setText(cue.animation()).setTextResponder(value -> update(index, null, null, null, value, null, null)));
			cycle(row, "绑定", cue.binding(), Binding.values(), value -> update(index, null, null, null, null, value, null));
			cycle(row, "停止", cue.stopPolicy(), StopPolicy.values(), value -> update(index, null, null, null, null, null, value));
			button(row, cue.scaleToPhase() ? "时序：随阶段缩放" : "时序：固定 tick", () -> {
				var current = definition.cues().get(index);
				replace(index, new Cue(current.component(), current.phase(), current.delayTicks(), current.animation(), current.binding(), current.stopPolicy(), !current.scaleToPhase()));
				rebuildRows();
			});
			button(row, "删除此阶段组成件", () -> removeCue(index));
			var pose = definition.poses().get(cue.component());
			var offset = poseOffsets.computeIfAbsent(cue.component(), _ -> {
				var value = pose == null ? new Vector3f() : pose.offset();
				return new float[]{value.x, value.y, value.z};
			});
			var rotation = poseRotations.computeIfAbsent(cue.component(), _ -> {
				var value = pose == null ? new Quaternionf() : pose.rotation();
				return new float[]{value.x, value.y, value.z, value.w};
			});
			for (int axis = 0; axis < 3; axis++) {
				int selectedAxis = axis;
				number(row, "组件偏移 " + "XYZ".charAt(axis), offset[axis], value -> {
					offset[selectedAxis] = value.floatValue();
					pose(cue.component(), offset, rotation, definition.poses().get(cue.component()) == null ? Map.of() : definition.poses().get(cue.component()).inputs());
				});
			}
			for (int axis = 0; axis < 4; axis++) {
				int selectedAxis = axis;
				number(row, "组件旋转四元数 " + "XYZW".charAt(axis), rotation[axis], value -> {
					rotation[selectedAxis] = value.floatValue();
					pose(cue.component(), offset, rotation, definition.poses().get(cue.component()) == null ? Map.of() : definition.poses().get(cue.component()).inputs());
				});
			}
			var inputs = pose == null ? Map.<String, List<Float>>of() : pose.inputs();
			number(row, "组件长度参数", inputs.getOrDefault("length", List.of(16f)).getFirst(), value -> input(cue.component(), "length", value.floatValue(), offset, rotation));
			number(row, "组件宽度参数", inputs.getOrDefault("width", List.of(1f)).getFirst(), value -> input(cue.component(), "width", value.floatValue(), offset, rotation));
		}
	}

	private <T extends Enum<T>> void cycle(UIElement parent, String label, T current, T[] values, Consumer<T> update) {
		button(parent, label + "：" + chinese(current), () -> {
			update.accept(values[(current.ordinal() + 1) % values.length]);
			rebuildRows();
		});
	}

	private String chinese(Enum<?> value) {
		return switch (value.name()) {
			case "CHARGE" -> "蓄力";
			case "OUTPUT" -> "发射";
			case "RECOVERY" -> "恢复";
			case "ROOT" -> "根节点";
			case "BEAM" -> "光束";
			case "NATURAL" -> "自然结束";
			case "IMMEDIATE" -> "立即结束";
			default -> value.name();
		};
	}

	private void update(int index, String component, Phase phase, Double delay, String animation, Binding binding, StopPolicy stop) {
		var old = definition.cues().get(index);
		replace(index, new Cue(component == null ? old.component() : component, phase == null ? old.phase() : phase,
				delay == null ? old.delayTicks() : delay, animation == null ? old.animation() : animation,
				binding == null ? old.binding() : binding, stop == null ? old.stopPolicy() : stop, old.scaleToPhase()));
	}

	private void replace(int index, Cue cue) {
		var cues = new ArrayList<>(definition.cues());
		cues.set(index, cue);
		definition = new Definition(definition.version(), cues, definition.poses());
		changed();
	}

	private void pose(String name, float[] offset, float[] rotation, Map<String, List<Float>> inputs) {
		var quaternion = new Quaternionf(rotation[0], rotation[1], rotation[2], rotation[3]);
		if (quaternion.lengthSquared() < 0.000001f) {
			throw new IllegalArgumentException("旋转四元数不能为零");
		}
		var poses = new LinkedHashMap<>(definition.poses());
		poses.put(name, new Pose(new Vector3f(offset[0], offset[1], offset[2]), quaternion.normalize(), inputs));
		definition = new Definition(definition.version(), definition.cues(), poses);
		changed();
	}

	private void input(String name, String key, float value, float[] offset, float[] rotation) {
		var old = definition.poses().get(name);
		var inputs = new LinkedHashMap<String, List<Float>>(old == null ? Map.of() : old.inputs());
		inputs.put(key, List.of(value));
		pose(name, offset, rotation, inputs);
	}

	private void addCue() {
		var cues = new ArrayList<>(definition.cues());
		cues.add(new Cue("circle", Phase.CHARGE, 0, "charge_start", Binding.ROOT, StopPolicy.NATURAL));
		definition = new Definition(definition.version(), cues, definition.poses());
		changed();
		rebuildRows();
	}

	private void removeCue(int index) {
		var cues = new ArrayList<>(definition.cues());
		cues.remove(index);
		definition = new Definition(definition.version(), cues, definition.poses());
		var shifted = disabled.stream().filter(value -> value != index).map(value -> value > index ? value - 1 : value).toList();
		disabled.clear();
		disabled.addAll(shifted);
		changed();
		rebuildRows();
	}

	private Definition effective() {
		var cues = new ArrayList<Cue>();
		for (int i = 0; i < definition.cues().size(); i++) {
			if (!disabled.contains(i)) {
				cues.add(definition.cues().get(i));
			}
		}
		return new Definition(definition.version(), cues, definition.poses());
	}

	private void changed() {
		changedAt = System.nanoTime();
		published = "";
		pendingFingerprint = "";
		refreshGeneration++;
		if (project != null && authoring) {
			persist();
			// 内存编辑只入队准备，不删除当前快照；新快照成功后才替换。
			PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
		}
		if (!authoring && live && preview != null) {
			preview.composition(effective());
			applyTime();
		}
	}

	private void persist() {
		if (project == null) {
			return;
		}
		var data = authorData.copy();
		data.put("definition", PhotonOwnedLaserComposition.serialize(definition));
		var disabledTags = new ListTag();
		disabled.stream().sorted().forEach(value -> disabledTags.add(StringTag.valueOf(value.toString())));
		data.put("disabled", disabledTags);
		var environment = new CompoundTag();
		for (int i = 0; i < 3; i++) {
			environment.putDouble("duration_" + i, durations[i]);
			environment.putFloat("root_" + i, rootPosition[i]);
			environment.putFloat("beam_" + i, beamPosition[i]);
		}
		environment.putFloat("length", beamParameters[0]);
		environment.putFloat("width", beamParameters[1]);
		data.put("preview_environment", environment);
		((PhotonCompositionProjectAccess) project).lobecorp$composition(data);
		authorData = data;
	}

	private void restore() {
		if (project == null) {
			return;
		}
		var saved = ((PhotonCompositionProjectAccess) project).lobecorp$composition();
		authorData = saved == null ? new CompoundTag() : saved.copy();
		definition = saved == null ? PhotonOwnedLaserComposition.defaults()
				: PhotonOwnedLaserComposition.deserialize(saved.getCompoundOrEmpty("definition"));
		disabled.clear();
		for (int i = 0; i < 3; i++) {
			durations[i] = new double[]{40, 60, 20}[i];
			rootPosition[i] = new float[]{0.5f, 2, 0.5f}[i];
			beamPosition[i] = rootPosition[i];
		}
		beamParameters[0] = 16;
		beamParameters[1] = 1;
		if (saved != null) {
			for (var value : saved.getListOrEmpty("disabled")) {
				int index = Integer.parseInt(value.asString().orElseThrow());
				if (index >= 0 && index < definition.cues().size()) {
					disabled.add(index);
				}
			}
			var environment = saved.getCompoundOrEmpty("preview_environment");
			for (int i = 0; i < 3; i++) {
				durations[i] = Math.max(1, environment.getDoubleOr("duration_" + i, durations[i]));
				rootPosition[i] = environment.getFloatOr("root_" + i, rootPosition[i]);
				beamPosition[i] = environment.getFloatOr("beam_" + i, beamPosition[i]);
			}
			beamParameters[0] = environment.getFloatOr("length", 16);
			beamParameters[1] = environment.getFloatOr("width", 1);
		}
		restoring = true;
		try {
			var labels = new String[]{"蓄力时长（tick）", "发射时长（tick）", "恢复时长（tick）"};
			for (int i = 0; i < 3; i++) {
				fields.get(labels[i]).setText(Double.toString(durations[i]));
				fields.get("根位置 " + "XYZ".charAt(i)).setText(Float.toString(rootPosition[i]));
				fields.get("光束位置 " + "XYZ".charAt(i)).setText(Float.toString(beamPosition[i]));
			}
			fields.get("光束长度").setText(Float.toString(beamParameters[0]));
			fields.get("光束宽度").setText(Float.toString(beamParameters[1]));
		} finally {
			restoring = false;
		}
		poseOffsets.clear();
		poseRotations.clear();
		rebuildRows();
		changedAt = System.nanoTime();
		published = "";
		if (preview != null) {
			refreshPreview();
		}
	}

	private void begin(boolean play) {
		if (!(editor.getCurrentProject() instanceof FXProject current)) {
			throw new IllegalStateException("请先打开激光作者工程");
		}
		project = current;
		refreshGeneration++;
		pendingFingerprint = "";
		PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
		PhotonOwnedEffectResources.markPublishedPreviewDirty(PhotonOwnedLaser.ID);
		PhotonOwnedEffectAssets.authoringFingerprint(project.getFx());
		var next = PhotonOwnedLaser.preview(project.getFx(), ++revision, editor.sceneView.level);
		next.composition(effective());
		if (preview != null) {
			preview.close();
		}
		if (!authoring) {
			PhotonOwnedEffectResources.invalidatePublishedPreview(PhotonOwnedLaser.ID);
		}
		authoring = true;
		editor.sceneView.particleManager.pause();
		editor.sceneView.reset();
		if (editor.runtime != null) {
			editor.runtime.destroy(true);
		}
		preview = next;
		phase = null;
		time = 0;
		target = -1;
		playing = play;
		applyTime();
		status.setText("作者组合已就绪");
	}

	private void refreshPreview() {
		if (preview == null) {
			return;
		}
		if (authoring && project != null) {
			queueAuthoringRefresh(observed, false);
			return;
		}

		long at = target >= 0 ? target : time;
		boolean resume = playing;
		restartPreview();
		target = at;
		playing = resume;
	}

	private void restartPreview() {
		if (authoring) {
			begin(false);
		} else {
			importPublished(true);
		}
	}

	private void seek(long at) {
		if (at < 0 || at > MAX_PREVIEW_TICKS) {
			throw new IllegalArgumentException("定位范围为0至6000 tick");
		}
		if (preview == null) {
			begin(false);
		}
		playing = false;
		if (at < time) {
			restartPreview();
		}
		target = at;
		if (time == at) {
			applyTime();
			seekField.setText(Long.toString(time));
			target = -1;
		}
	}

	private void finish() {
		if (preview == null) {
			return;
		}
		seek((long) Math.ceil(durations[0] + durations[1]));
		playing = true;
	}

	private void applyTime() {
		if (preview == null) {
			return;
		}
		double local;
		Phase next;
		if (time < durations[0]) {
			next = Phase.CHARGE;
			local = time;
		} else if (time < durations[0] + durations[1]) {
			next = Phase.OUTPUT;
			local = time - durations[0];
		} else {
			next = Phase.RECOVERY;
			local = time - durations[0] - durations[1];
		}
		preview.anchor(new Vector3f(rootPosition[0], rootPosition[1], rootPosition[2]), new Quaternionf());
		preview.beam(new Vector3f(beamPosition[0], beamPosition[1], beamPosition[2]), new Quaternionf(),
				Map.of("length", List.of(beamParameters[0]), "width", List.of(beamParameters[1]), "enabled", List.of(1f)));
		if (phase != next) {
			phase = next;
			preview.phase(next, durations[next.ordinal()]);
		}
		preview.advance(local);
	}

	private void simulateTick() {
		applyTime();
		editor.sceneView.particleManager.tickInternal();
		time++;
		applyTime();
		if (time >= durations[0] + durations[1] + durations[2]) {
			playing = false;
		}
	}

	private void tickWorkspace() {
		if (closed) {
			return;
		}
		guarded(() -> {
			var current = editor.getCurrentProject() instanceof FXProject value ? value : null;
			if (current != project) {
				cancel();
				project = current;
				observed = "";
				published = "";
				restore();
			}
			if (preview != null) {
				editor.sceneView.particleManager.pause();
				if (target >= 0) {
					for (int i = 0; i < REPLAY_BATCH && time < target; i++) {
						simulateTick();
					}
					if (time >= target) {
						target = -1;
					}
				} else if (playing) {
					simulateTick();
				}
				seekField.setText(Long.toString(time));
			}
			pollAuthoring();
		});
	}

	/// 只负责外部文件/外部修改的有界检测；内存编辑已由 changed() 立即标脏。
	private void pollAuthoring() {
		long now = System.nanoTime();
		if (!live || !authoring || project == null || now < pollAt) {
			return;
		}
		pollAt = now + POLL_NANOS;
		if (probeInFlight) {
			return;
		}

		probeInFlight = true;
		var expectedProject = project;
		long expectedGeneration = refreshGeneration;
		PhotonOwnedEffectAssets.probeAuthoring(project.getFx()).whenComplete((fingerprint, failure) ->
				Minecraft.getInstance().schedule(() -> {
					probeInFlight = false;
					if (closed || !live || !authoring || project != expectedProject
							|| refreshGeneration != expectedGeneration) {
						return;
					}
					if (failure != null) {
						status.setText("作者依赖读取失败，保留当前预览：" + failure.getMessage());
						return;
					}
					guarded(() -> acceptFingerprint(fingerprint));
				}));
	}

	private void acceptFingerprint(String fingerprint) {
		long now = System.nanoTime();
		if (!fingerprint.equals(observed)) {
			observed = fingerprint;
			changedAt = now;
			refreshGeneration++;
			pendingFingerprint = "";
			PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
			return;
		}
		if (!fingerprint.equals(published) && !fingerprint.equals(pendingFingerprint)
				&& now - changedAt >= DEBOUNCE_NANOS && preview != null) {
			queueAuthoringRefresh(fingerprint, true);
		}
	}

	private void queueAuthoringRefresh(String fingerprint, boolean automatic) {
		var expectedProject = project;
		long expectedGeneration = ++refreshGeneration;
		pendingFingerprint = fingerprint;
		var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
				PhotonOwnedEffectResources.SourceKind.AUTHORING, ++revision);
		PhotonOwnedEffectResources.queueSnapshot(key, PhotonOwnedLaser.split(project.getFx()))
				.whenComplete((source, failure) -> Minecraft.getInstance().schedule(() -> {
					if (closed || automatic && !live || !authoring || project != expectedProject
							|| refreshGeneration != expectedGeneration) {
						return;
					}

					pendingFingerprint = "";
					if (failure != null) {
						status.setText("作者准备已取消：" + failure.getMessage());
						return;
					}
					guarded(() -> {
						PhotonOwnedEffectAssets.authoringFingerprint(project.getFx());
						var next = PhotonOwnedLaser.preparedPreview(source, editor.sceneView.level);
						next.composition(effective());
						long at = target >= 0 ? target : time;
						if (preview != null) {
							preview.close();
						}
						preview = next;
						editor.sceneView.reset();
						phase = null;
						time = 0;
						target = at;
						published = fingerprint;
						applyTime();
						status.setText("作者修订已准备并替换");
					});
				}));
	}

	private void export() {
		if (authoring && project == null || !authoring && preview == null) {
			throw new IllegalStateException("没有当前工程或发布组合");
		}
		persist();
		long previousTime = target >= 0 ? target : time;
		boolean resume = playing;
		boolean restorePreview = preview != null && authoring;
		if (authoring) {
			releasePreview();
		}
		try {
			if (authoring) {
				PhotonCompositionExportUtil.export(project.getFx(), effective(), exportPath.getText());
			} else {
				var publishedPreview = preview;
				if (publishedPreview == null) {
					throw new IllegalStateException("没有当前发布组合");
				}
				PhotonCompositionExportUtil.export(publishedPreview.exportComponents(), effective(), exportPath.getText());
			}
		} catch (IOException failure) {
			throw new IllegalStateException("发布失败", failure);
		} finally {
			if (restorePreview) {
				begin(false);
				target = previousTime;
				playing = resume;
			}
		}
		status.setText("冻结组件和组合清单已导出；预览环境未发布");
	}

	private void mountPublished() {
		cancel();
		status.setText("正在挂载发布包并重载资源");
		try {
			var expectedProject = editor.getCurrentProject();
			var expectedPath = exportPath.getText();
			PhotonCompositionExportUtil.mountAndReload(expectedPath).whenComplete((_, failure) ->
					Minecraft.getInstance().execute(() -> {
						if (closed || editor.getCurrentProject() != expectedProject || !exportPath.getText().equals(expectedPath)) {
							return;
						}
						if (failure != null) {
							status.setText("资源重载失败：" + failure.getMessage());
							return;
						}
						guarded(this::importPublished);
					}));
		} catch (Exception failure) {
			status.setText("发布包无法挂载：" + failure.getMessage());
		}
	}

	private void importPublished() {
		importPublished(false);
	}

	private void importPublished(boolean preserveDefinition) {
		refreshGeneration++;
		pendingFingerprint = "";
		PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
		PhotonOwnedEffectResources.markPublishedPreviewDirty(PhotonOwnedLaser.ID);
		PhotonCompositionExportUtil.Loaded loaded;
		try {
			loaded = PhotonCompositionExportUtil.read(exportPath.getText(), ++revision, editor.sceneView.level);
		} catch (IOException failure) {
			throw new IllegalStateException("发布回读失败", failure);
		}
		if (preview != null) {
			preview.close();
		}
		if (authoring) {
			PhotonOwnedEffectResources.invalidateAuthoring(PhotonOwnedLaser.ID);
		}
		editor.sceneView.particleManager.pause();
		editor.sceneView.reset();
		if (editor.runtime != null) {
			editor.runtime.destroy(true);
		}
		preview = loaded.runtime();
		if (!preserveDefinition) {
			definition = loaded.definition();
			disabled.clear();
		}
		preview.composition(effective());
		phase = null;
		time = 0;
		target = -1;
		poseOffsets.clear();
		poseRotations.clear();
		authoring = false;
		playing = false;
		rebuildRows();
		applyTime();
		status.setText("已读取发布组合与当前有效包资源；遵循资源包覆盖，不跟随作者内存修改");
	}

	private void releasePreview() {
		refreshGeneration++;
		pendingFingerprint = "";
		PhotonOwnedEffectResources.markAuthoringDirty(PhotonOwnedLaser.ID);
		PhotonOwnedEffectResources.markPublishedPreviewDirty(PhotonOwnedLaser.ID);
		if (preview != null) {
			preview.close();
			preview = null;
			if (authoring) {
				PhotonOwnedEffectResources.invalidateAuthoring(PhotonOwnedLaser.ID);
			} else {
				PhotonOwnedEffectResources.invalidatePublishedPreview(PhotonOwnedLaser.ID);
			}
		}
	}

	private void cancel() {
		if (preview != null) {
			editor.sceneView.particleManager.pause();
		}
		playing = false;
		target = -1;
		releasePreview();
	}

	public void projectClosed() {
		cancel();
		project = null;
	}

	@Override
	public void close() {
		closed = true;
		cancel();
	}
}
