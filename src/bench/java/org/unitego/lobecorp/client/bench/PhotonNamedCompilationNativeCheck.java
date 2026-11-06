package org.unitego.lobecorp.client.bench;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedController;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/// 已注册真实宿主中的命名编译契约检查；不发射或推进游戏。
@SuppressWarnings("unused")
public class PhotonNamedCompilationNativeCheck {
	public static List<String> verify() throws Exception {
		var cases = new ArrayList<String>();
		compiledSnapshotsDetachOnBothSettersAndKeepUnknownData();
		cases.add("compiledSnapshotsDetachOnBothSettersAndKeepUnknownData");

		deserializationInvalidatesCompilationAndReinstallRestoresSnapshot();
		cases.add("deserializationInvalidatesCompilationAndReinstallRestoresSnapshot");

		playbackVersionIsCompiledOnceAndDoesNotReplaceAuthoringSnapshot();
		cases.add("playbackVersionIsCompiledOnceAndDoesNotReplaceAuthoringSnapshot");

		unsupportedLibraryCannotInstallPartialCompilation();
		cases.add("unsupportedLibraryCannotInstallPartialCompilation");

		return List.copyOf(cases);
	}

	private static void compiledSnapshotsDetachOnBothSettersAndKeepUnknownData() throws IOException {
		long reads = PhotonNamedController.libraryDecodeCount();
		long tracks = PhotonNamedPlan.trackDecodeCount();
		var source = new CompoundTag();
		source.putInt("version", 6);
		source.putString("future", "authored");
		var original = new CompoundTag();
		original.putString("future", "original");
		var compiled = PhotonNamedController.compileDefinition(source, original);
		source.putString("future", "caller_changed");
		original.putString("future", "caller_changed");

		var first = compiled.newController();
		var second = compiled.newController();
		require("authored".equals(first.definition().getStringOr("future", "")), "Equals: \"authored\", first.definition().getStringOr(\"future\", \"\")");
		require("original".equals(first.source().getStringOr("future", "")), "Equals: \"original\", first.source().getStringOr(\"future\", \"\")");
		require(compiled == first.compileDefinition(), "Same: compiled, first.compileDefinition()");
		require(compiled.library() == second.compileDefinition().library(), "Same: compiled.library(), second.compileDefinition().library()");
		require(1 == compiled.libraryDecodeCount(), "Equals: 1, compiled.libraryDecodeCount()");
		require(0 == compiled.trackDecodeCount(), "Equals: 0, compiled.trackDecodeCount()");
		require(compiled.fallbackReasons().isEmpty(), "True: compiled.fallbackReasons().isEmpty()");
		require(reads + 1 == PhotonNamedController.libraryDecodeCount(), "Equals: reads + 1, PhotonNamedController.libraryDecodeCount()");
		require(tracks == PhotonNamedPlan.trackDecodeCount(), "Equals: tracks, PhotonNamedPlan.trackDecodeCount()");

		var external = first.definition();
		external.putString("future", "getter_changed");
		require("authored".equals(first.definition().getStringOr("future", "")), "Equals: \"authored\", first.definition().getStringOr(\"future\", \"\")");
		var externalSource = first.source();
		externalSource.putString("future", "getter_changed");
		require("original".equals(first.source().getStringOr("future", "")), "Equals: \"original\", first.source().getStringOr(\"future\", \"\")");

		long revision = first.definitionRevision();
		first.definition(external);
		require(first.definitionRevision() > revision, "True: first.definitionRevision() > revision");
		require(first.compiledDefinition() == null, "Null: first.compiledDefinition()");
		require("authored".equals(second.definition().getStringOr("future", "")), "Equals: \"authored\", second.definition().getStringOr(\"future\", \"\")");
		require(compiled == second.compiledDefinition(), "Same: compiled, second.compiledDefinition()");

		second.rememberSource(externalSource);
		require(second.compiledDefinition() == null, "Null: second.compiledDefinition()");
		require("original".equals(compiled.newController().source().getStringOr("future", "")), "Equals: \"original\", compiled.newController().source().getStringOr(\"future\", \"\")");
	}

	private static void deserializationInvalidatesCompilationAndReinstallRestoresSnapshot() throws IOException {
		var compiled = PhotonNamedController.compileDefinition(new CompoundTag(), new CompoundTag());
		var controller = compiled.newController();
		long revision = controller.definitionRevision();
		controller.afterDeserialize();
		require(controller.compiledDefinition() == null, "Null: controller.compiledDefinition()");
		require(controller.definitionRevision() > revision, "True: controller.definitionRevision() > revision");
		compiled.install(controller);
		require(compiled == controller.compiledDefinition(), "Same: compiled, controller.compiledDefinition()");
		require(controller.matchesDefinition(new CompoundTag()), "True: controller.matchesDefinition(new CompoundTag())");
	}

	private static void playbackVersionIsCompiledOnceAndDoesNotReplaceAuthoringSnapshot() throws IOException {
		var source = new CompoundTag();
		source.putInt("version", 6);
		var compiled = PhotonNamedController.compileDefinition(source, new CompoundTag());
		var first = compiled.newController();
		var second = compiled.newController();
		long reads = PhotonNamedController.libraryDecodeCount();
		first.preparePlaybackDefinition();
		long preparedRevision = first.definitionRevision();
		first.preparePlaybackDefinition();
		require(first.definitionRevision() == preparedRevision, "重复准备相同播放定义不得使 PROJECT 缓存失效");
		second.preparePlaybackDefinition();
		require(first.compiledDefinition() == second.compiledDefinition(), "Same: first.compiledDefinition(), second.compiledDefinition()");
		require(compiled != first.compiledDefinition(), "NotSame: compiled, first.compiledDefinition()");
		require(reads + 1 == PhotonNamedController.libraryDecodeCount(), "Equals: reads + 1, PhotonNamedController.libraryDecodeCount()");
		require(!compiled.newController().definition().contains("defaultAnimation"), "False: compiled.newController().definition().contains(\"defaultAnimation\")");
		require(first.definition().getStringOr("defaultAnimation", "missing").isEmpty(), "defaultAnimation 必须为空");
	}

	private static void unsupportedLibraryCannotInstallPartialCompilation() {
		var invalid = new CompoundTag();
		invalid.putInt("version", -1);

		try {
			PhotonNamedController.compileDefinition(invalid, new CompoundTag());
		} catch (IOException _) {
			return;
		}

		throw new AssertionError("不支持版本必须抛出 IOException");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
