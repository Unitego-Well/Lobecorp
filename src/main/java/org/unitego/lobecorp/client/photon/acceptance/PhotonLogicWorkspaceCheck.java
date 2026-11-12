package org.unitego.lobecorp.client.photon.acceptance;

import org.unitego.lobecorp.util.photon.editor.PhotonFxPackExportUtil;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.unitego.lobecorp.client.photon.editor.PhotonCompositionWorkspace;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceReader;
import org.unitego.lobecorp.util.photon.editor.PhotonCompositionExportUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectFileUtil;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/// 独占作者工程与临时包；反射只读状态及调用真实动作，不写入被测状态。
/// step须由客户端线程逐tick调用；true表示断言和卸载清理均已完成。
public class PhotonLogicWorkspaceCheck implements AutoCloseable {
	private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(45);
	private static final String OBJ_A = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n";
	private static final String OBJ_B = "v 0 0 0\nv 2 0 0\nv 0 1 0\nf 1 2 3\n";
	private final BiConsumer<String, Boolean> check;
	private final FXEditor caller;
	private final String name = "photon_logic_" + UUID.randomUUID().toString().replace("-", "");
	private final List<Path> ownedFiles = new ArrayList<>();
	private PhotonEffectEditor editor;
	private PhotonCompositionWorkspace workspace;
	private FXProject project;
	private CompoundTag callerBefore;
	private Object callerProject;
	private Path directory;
	private Path dependency;
	private Path pack;
	private Path mounted;
	private FileTime dependencyTime;
	private List<String> selected;
	private CompletableFuture<Void> reload;
	private Object previousPreview;
	private String previousFingerprint;
	private long expectedTime;
	private long liveObservationUntil;
	private long liveRevision;
	private long deadline;
	private int phase = -1;
	private boolean closed;
	private boolean selectionChanged;
	private boolean cleanupStarted;
	private boolean filesCleaned;

	public PhotonLogicWorkspaceCheck(BiConsumer<String, Boolean> check, FXEditor editor) {
		this.check = check;
		this.caller = editor;
	}

	/// 固定长度的合法PNG用于同大小同mtime替换；尾部补零不改变图像内容。
	private static byte[] textureBytes(boolean alternate) throws IOException {
		var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, alternate ? 0xff00ff00 : 0xffff0000);
		var output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return Arrays.copyOf(output.toByteArray(), 256);
	}

	private static Object read(Object owner, String name) throws ReflectiveOperationException {
		var field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static Object invoke(Object owner, String name, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
		Class<?> type = owner.getClass();
		while (type != null) {
			try {
				var method = type.getDeclaredMethod(name, types);
				method.setAccessible(true);
				return method.invoke(owner, arguments);
			} catch (NoSuchMethodException missing) {
				type = type.getSuperclass();
			}
		}
		throw new NoSuchMethodException(name);
	}

	private static RuntimeException failure(Exception failure) {
		if (failure instanceof InvocationTargetException wrapped && wrapped.getCause() instanceof RuntimeException cause) {
			return cause;
		}
		return new IllegalStateException("工作区逻辑检查失败", failure);
	}

	private static void requireThread() {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("工作区检查必须在客户端线程执行");
		}
	}

	public void begin() {
		requireThread();
		if (phase != -1 || closed) {
			throw new IllegalStateException("工作区检查不可重复启动");
		}
		try {
			callerProject = caller.getCurrentProject();
			if (callerProject instanceof FXProject current) {
				callerBefore = PhotonEffectProjectUtil.container(current);
			}
			var game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
			var root = PhotonEffectFileUtil.roots().stream().filter(path -> path.startsWith(game)).findFirst()
					.orElseThrow(() -> new IllegalStateException("没有gameDir内获准的临时资源根"));
			directory = root.resolve(name);
			Files.createDirectories(root);
			Files.createDirectory(directory);
			mountDependencyPack();
			advance(8);
		} catch (Exception failure) {
			throw failure(failure);
		}
	}

	private void mountDependencyPack() throws IOException {
		var game = Minecraft.getInstance().gameDirectory.toPath();
		var packs = game.resolve("resourcepacks");
		Files.createDirectories(packs);
		var packRoot = ownedDirectory(packs.resolve(name + "_dependency"));
		var assets = ownedDirectory(packRoot.resolve("assets"));
		var namespace = ownedDirectory(assets.resolve("lobecorp"));
		var textures = ownedDirectory(namespace.resolve("textures"));
		dependency = own(textures.resolve(name + ".png"));
		Files.write(dependency, textureBytes(false));
		dependencyTime = Files.getLastModifiedTime(dependency);
		int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
		Files.writeString(own(packRoot.resolve("pack.mcmeta")),
				"{\"pack\":{\"description\":\"Photon logic fixture\",\"min_format\":" + format
						+ ",\"max_format\":" + format + "}}", StandardCharsets.UTF_8);
		var repository = Minecraft.getInstance().getResourcePackRepository();
		selected = repository.getSelectedPacks().stream().map(Pack::getId).toList();
		repository.reload();
		var next = new ArrayList<>(selected);
		next.add("file/" + packRoot.getFileName());
		repository.setSelected(next);
		selectionChanged = true;
		reload = Minecraft.getInstance().reloadResourcePacks();
	}

	private Path ownedDirectory(Path path) throws IOException {
		Files.createDirectory(path);
		own(path);
		return path;
	}

	private void openNativeAuthor() throws Exception {
		project = authorProject();
		var emitter = new ParticleEmitter();
		emitter.setName("acceptance_dependency");
		emitter.config.emission.setEmissionRate(NumberFunction.constant(0));
		emitter.config.renderer.getMaterials().clear();
		emitter.config.renderer.getMaterials().add(new MaterialSetting(
				new TextureMaterial(Identifier.fromNamespaceAndPath("lobecorp", "textures/" + name + ".png"))));
		project.getFx().getFxData().objects().add(emitter);
		editor = new PhotonEffectEditor();
		editor.loadProject(project, null);
		expect("workspace.author.project_loaded", editor.getCurrentProject() == project);
		workspace = (PhotonCompositionWorkspace) read(editor, "compositionWorkspace");
		action("tickWorkspace");
		action("begin", new Class<?>[]{boolean.class}, false);
		action("seek", new Class<?>[]{long.class}, 17L);
		action("tickWorkspace");
		expect("workspace.author.seek", number("time") == 17 && number("target") == -1);
	}

	public boolean step() {
		requireThread();
		if (phase == -1) {
			throw new IllegalStateException("请先begin");
		}
		if (phase == 99) {
			return true;
		}
		try {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("工作区检查阶段超时：" + phase);
			}
			switch (phase) {
				case 8 -> {
					if (!reload.isDone()) {
						return false;
					}
					reload.join();
					openNativeAuthor();
					advance(0);
				}
				case 0 -> {
					action("tickWorkspace");
					if (!settled()) {
						return false;
					}
					editDuration();
					expect("workspace.author.edit_keeps_paused_time",
							number("time") == 17 && number("target") == -1 && !flag("playing"));
					previousPreview = read(workspace, "preview");
					advance(1);
				}
				case 1 -> {
					action("tickWorkspace");
					if (!replaced()) {
						return false;
					}
					expect("workspace.author.auto_refresh_keeps_time", number("time") == 17 && !flag("playing"));
					// 同大小、同mtime原位覆盖，不调用dirty/refresh或修改probe时钟。
					previousFingerprint = text("observed");
					previousPreview = read(workspace, "preview");
					Files.write(dependency, textureBytes(true));
					Files.setLastModifiedTime(dependency, dependencyTime);
					expect("workspace.dependency.same_size_same_mtime",
							Files.size(dependency) == textureBytes(false).length
									&& Files.getLastModifiedTime(dependency).equals(dependencyTime));
					advance(2);
				}
				case 2 -> {
					action("tickWorkspace");
					if (!replaced() || previousFingerprint.equals(text("observed"))) {
						return false;
					}
					expect("workspace.dependency.same_stamp_detected",
							previousPreview != read(workspace, "preview") && !previousFingerprint.equals(text("observed")));
					previousPreview = read(workspace, "preview");
					previousFingerprint = text("published");
					Files.delete(dependency);
					advance(3);
				}
				case 3 -> {
					action("tickWorkspace");
					// 原生纹理允许缺失回退；删除仍须被自动探测并完成新修订。
					if (!replaced() || previousFingerprint.equals(text("published"))) {
						return false;
					}
					expect("workspace.dependency.deleted_detected_with_native_fallback",
							!Files.exists(dependency) && number("time") == 17 && !flag("playing"));
					previousPreview = read(workspace, "preview");
					previousFingerprint = text("published");
					Files.write(dependency, textureBytes(false));
					Files.setLastModifiedTime(dependency, dependencyTime);
					advance(4);
				}
				case 4 -> {
					action("tickWorkspace");
					if (!replaced() || previousFingerprint.equals(text("published"))) {
						return false;
					}
					expect("workspace.dependency.restored_auto_refresh", number("time") == 17 && !flag("playing"));
					pressWorkspace("实时刷新开关");
					expect("workspace.live.disabled_by_ui", !flag("live"));
					previousPreview = read(workspace, "preview");
					liveRevision = number("revision");
					editDuration();
					liveObservationUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
					advance(9);
				}
				case 9 -> {
					action("tickWorkspace");
					if (read(workspace, "preview") != previousPreview || number("revision") != liveRevision) {
						expect("workspace.live.disabled_no_auto_refresh", false);
					}
					if (System.nanoTime() < liveObservationUntil || flag("probeInFlight")) {
						return false;
					}
					expect("workspace.live.disabled_no_auto_refresh", !flag("live")
							&& number("time") == 17 && number("target") == -1 && !flag("playing"));
					pressWorkspace("立即刷新并保留时间");
					advance(10);
				}
				case 10 -> {
					if (read(workspace, "preview") == previousPreview || !text("pendingFingerprint").isEmpty()) {
						return false;
					}
					expect("workspace.live.manual_refresh_completed", !flag("live")
							&& number("revision") > liveRevision && number("time") == 0
							&& number("target") == 17 && !flag("playing"));
					advance(11);
				}
				case 11 -> {
					action("tickWorkspace");
					if (number("target") != -1) {
						return false;
					}
					expect("workspace.live.manual_replay_completed", number("time") == 17 && !flag("playing"));
					pressWorkspace("实时刷新开关");
					expect("workspace.live.restored_by_ui", flag("live"));
					previousPreview = read(workspace, "preview");
					editDuration();
					advance(12);
				}
				case 12 -> {
					action("tickWorkspace");
					if (!replaced()) {
						return false;
					}
					expect("workspace.live.restored_auto_refresh_completed", flag("live")
							&& number("time") == 17 && !flag("playing"));
					authorPlaying();
					advance(5);
				}
				case 5 -> {
					// 仅驱动poll，隔离正常播放推进，观察异步替换自身是否保留播放和定位。
					action("pollAuthoring");
					if (previousPreview == read(workspace, "preview") || !settled()) {
						return false;
					}
					expect("workspace.author.refresh_keeps_playing_target",
							flag("playing") && number("target") == expectedTime && number("time") == 0);
					exportAndMount();
					advance(6);
				}
				case 6 -> {
					if (!reload.isDone()) {
						return false;
					}
					reload.join();
					expect("workspace.published.pack_selected",
							Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains("file/" + mounted.getFileName()));
					action("importPublished");
					expect("workspace.published.real_read", !flag("authoring") && read(workspace, "preview") != null);
					publishedChecks();
					exportOrdinary();
					verifyCaller();
					startCleanup();
					advance(7);
				}
				case 7 -> {
					if (!reload.isDone()) {
						return false;
					}
					reload.join();
					expect("workspace.cleanup.selection_restored",
							Minecraft.getInstance().getResourcePackRepository().getSelectedPacks().stream().map(Pack::getId).toList().equals(selected));
					cleanFiles();
					phase = 99;
					closed = true;
					return true;
				}
				default -> throw new IllegalStateException("未知检查阶段：" + phase);
			}
			return false;
		} catch (Exception failure) {
			throw failure(failure);
		}
	}

	private FXProject authorProject() throws IOException {
		var candidates = List.of("laser_spell", "queen_arcana_beam", "laser", "star_beam_spell");
		var failures = new IOException("没有可分割的真实激光作者工程");
		for (var candidate : candidates) {
			var path = own(directory.resolve(candidate + ".fxproj"));
			try (var input = getClass().getResourceAsStream("/assets/lobecorp/fx/the_queen_of_hatred/" + candidate + ".fxproj")) {
				if (input == null) {
					continue;
				}
				Files.copy(input, path);
				var container = Objects.requireNonNull(PhotonResourceReader.read(path).nbt(), "作者工程缺少NBT");
				var result = new FXProject();
				PhotonEffectProjectUtil.decode(result, container.getCompoundOrEmpty("data"));
				PhotonOwnedLaser.split(result.getFx());
				return result;
			} catch (Exception invalid) {
				failures.addSuppressed(invalid);
			}
		}
		throw failures;
	}

	@SuppressWarnings("unchecked")
	private void editDuration() throws ReflectiveOperationException {
		var fields = (Map<String, TextField>) read(workspace, "fields");
		var field = fields.get("蓄力时长（tick）");
		double next = Double.parseDouble(field.getText()) + 1;
		field.setText(Double.toString(next));
		expect("workspace.ui.responder_persisted",
				((double[]) read(workspace, "durations"))[0] == next);
	}

	private void authorPlaying() throws ReflectiveOperationException {
		action("begin", new Class<?>[]{boolean.class}, true);
		action("tickWorkspace");
		action("finish");
		expectedTime = number("target");
		previousPreview = read(workspace, "preview");
		editDuration();
	}

	private void pressWorkspace(String label) {
		var button = workspace.selfAndAllChildren().filter(Button.class::isInstance).map(Button.class::cast)
				.filter(value -> value.text.getText().getString().equals(label)).findFirst().orElseThrow();
		var event = UIEvent.create(UIEvents.MOUSE_DOWN);
		event.target = button;
		event.button = 0;
		UIEventDispatcher.dispatchEvent(event, false, false, false);
		var release = UIEvent.create(UIEvents.MOUSE_UP);
		release.target = button;
		release.button = 0;
		UIEventDispatcher.dispatchEvent(release, false, false, false);
	}

	private void exportAndMount() throws Exception {
		var before = PhotonEffectProjectUtil.container(project);
		var definition = (PhotonOwnedLaserComposition.Definition) read(workspace, "definition");
		pack = own(directory.resolve(name + ".fxpack"));
		PhotonCompositionExportUtil.export(project.getFx(), definition, pack.toString());
		expect("workspace.freeze.source_unchanged", before.equals(PhotonEffectProjectUtil.container(project)));
		var original = Files.readAllBytes(pack);
		boolean refused = false;
		try {
			PhotonCompositionExportUtil.export(project.getFx(), definition, pack.toString());
		} catch (IOException expected) {
			refused = true;
		}
		expect("workspace.freeze.failure_preserves_old_file", refused && Arrays.equals(original, Files.readAllBytes(pack)));
		expect("workspace.freeze.archive_has_manifest", PhotonResourceReader.read(pack).entries().stream()
				.anyMatch(entry -> entry.origin().endsWith("/assets/lobecorp/compositions/owned_laser.nbt")));
		var game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
		var packs = game.resolve("resourcepacks");
		Files.createDirectories(packs);
		mounted = own(packs.resolve(name + ".zip"));
		Files.copy(pack, mounted);
		var repository = Minecraft.getInstance().getResourcePackRepository();
		if (selected == null) {
			selected = repository.getSelectedPacks().stream().map(Pack::getId).toList();
		}
		repository.reload();
		var next = new ArrayList<>(repository.getSelectedPacks().stream().map(Pack::getId).toList());
		next.add("file/" + mounted.getFileName());
		repository.setSelected(next);
		selectionChanged = true;
		((TextField) read(workspace, "exportPath")).setText(pack.toString());
		reload = Minecraft.getInstance().reloadResourcePacks();
	}

	private void publishedChecks() throws Exception {
		var expected = (PhotonOwnedLaserComposition.Definition) read(workspace, "definition");
		var loaded = PhotonCompositionExportUtil.read(pack.toString(), 912345L, editor.sceneView.level);
		try (var runtime = loaded.runtime()) {
			expect("workspace.freeze.read_definition_equal",
					PhotonOwnedLaserComposition.serialize(expected).equals(PhotonOwnedLaserComposition.serialize(loaded.definition())));
			expect("workspace.freeze.read_components_complete",
					runtime.exportComponents().keySet().equals(Set.copyOf(PhotonOwnedLaser.COMPONENTS)));
		}
		action("importPublished");
		action("seek", new Class<?>[]{long.class}, 23L);
		action("tickWorkspace");
		editDuration();
		expect("workspace.published.ui_keeps_paused_time",
				number("time") == 23 && number("target") == -1 && !flag("playing"));
		action("refreshPreview");
		expect("workspace.published.refresh_keeps_target", number("target") == 23 && !flag("playing"));
		action("tickWorkspace");
		action("finish");
		long at = number("target");
		action("refreshPreview");
		expect("workspace.published.refresh_keeps_playing", flag("playing") && number("target") == at);
		var before = PhotonEffectProjectUtil.container(project);
		var destination = own(directory.resolve(name + "_published.fxpack"));
		PhotonCompositionExportUtil.export(((PhotonOwnedLaser) read(workspace, "preview")).exportComponents(),
				(PhotonOwnedLaserComposition.Definition) read(workspace, "definition"), destination.toString());
		expect("workspace.published.export_source_unchanged", before.equals(PhotonEffectProjectUtil.container(project)));
	}

	private void exportOrdinary() throws Exception {
		ordinary(project, "native");
	}

	private void ordinary(FXProject source, String suffix) throws Exception {
		var before = PhotonEffectProjectUtil.container(source);
		var copy = PhotonEffectProjectUtil.copy(source);
		FX fx = ((FXProject) copy).getFx();
		var tag = fx.serializeNBT(Platform.getFrozenRegistry());
		tag.putInt("version", FXProject.VERSION);
		var definition = own(directory.resolve(name + "_" + suffix + ".fx"));
		NbtIo.writeCompressed(tag, definition);
		expect("workspace." + suffix + ".definition_roundtrip", Objects.equals(tag, PhotonResourceReader.read(definition).nbt()));
		var archive = PhotonFxPackExportUtil.capture("lobecorp", name + "_" + suffix, fx, Platform.getFrozenRegistry());
		var target = own(directory.resolve(name + "_" + suffix + ".fxpack"));
		archive.write(target);
		archive.verify(PhotonResourceReader.read(target), null);
		expect("workspace." + suffix + ".pack_roundtrip", Files.size(target) > 0);
		expect("workspace." + suffix + ".source_unchanged", before.equals(PhotonEffectProjectUtil.container(source)));
	}

	private void verifyCaller() {
		expect("workspace.user.project_identity_unchanged", caller.getCurrentProject() == callerProject);
		if (callerProject instanceof FXProject current) {
			expect("workspace.user.project_content_unchanged", callerBefore.equals(PhotonEffectProjectUtil.container(current)));
		}
	}

	private boolean settled() throws ReflectiveOperationException {
		return !text("published").isEmpty() && text("published").equals(text("observed"))
				&& text("pendingFingerprint").isEmpty() && !flag("probeInFlight");
	}

	private boolean replaced() throws ReflectiveOperationException {
		return previousPreview != read(workspace, "preview") && settled() && number("target") == -1;
	}

	private void advance(int next) {
		phase = next;
		deadline = System.nanoTime() + WAIT_NANOS;
	}

	private Path own(Path path) {
		ownedFiles.add(path);
		return path;
	}

	private void expect(String label, boolean value) {
		check.accept(label, value);
		if (!value) {
			throw new AssertionError(label);
		}
	}

	private String text(String field) throws ReflectiveOperationException {
		return (String) read(workspace, field);
	}

	private long number(String field) throws ReflectiveOperationException {
		return ((Number) read(workspace, field)).longValue();
	}

	private boolean flag(String field) throws ReflectiveOperationException {
		return (boolean) read(workspace, field);
	}

	private void action(String method) throws ReflectiveOperationException {
		action(method, new Class<?>[0]);
	}

	private void action(String method, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
		invoke(workspace, method, types, arguments);
	}

	private void startCleanup() throws ReflectiveOperationException {
		if (cleanupStarted) {
			return;
		}
		cleanupStarted = true;
		if (workspace != null) {
			workspace.close();
		}
		if (editor != null) {
			invoke(editor, "closeCurrentProject", new Class<?>[0]);
			editor.removeSelf();
		}
		if (selectionChanged) {
			Minecraft.getInstance().getResourcePackRepository().setSelected(selected);
			reload = Minecraft.getInstance().reloadResourcePacks();
		} else {
			reload = CompletableFuture.completedFuture(null);
		}
	}

	private void cleanFiles() throws IOException {
		if (filesCleaned) {
			return;
		}
		for (var path : ownedFiles.reversed()) {
			Files.deleteIfExists(path);
		}
		if (directory != null) {
			Files.deleteIfExists(directory);
		}
		filesCleaned = true;
	}

	/// 提前结束时等待已有reload完成后恢复选择；不阻塞客户端线程。
	@Override
	public void close() {
		requireThread();
		if (closed) {
			return;
		}
		closed = true;
		var pending = reload == null ? CompletableFuture.<Void>completedFuture(null) : reload;
		pending.whenComplete((_, _) -> Minecraft.getInstance().execute(() -> {
			try {
				startCleanup();
				reload.whenComplete((_, restoreFailure) -> Minecraft.getInstance().execute(() -> {
					if (restoreFailure != null) {
						check.accept("workspace.cleanup.reload_failed:" + restoreFailure, false);
						return;
					}
					try {
						cleanFiles();
					} catch (IOException cleanupFailure) {
						check.accept("workspace.cleanup.files_failed:" + cleanupFailure, false);
					}
				}));
			} catch (ReflectiveOperationException cleanupFailure) {
				check.accept("workspace.cleanup.editor_failed:" + cleanupFailure, false);
			}
		}));
	}
}
