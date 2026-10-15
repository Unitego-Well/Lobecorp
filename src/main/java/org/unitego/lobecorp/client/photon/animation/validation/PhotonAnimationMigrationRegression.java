package org.unitego.lobecorp.client.photon.animation.validation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import com.lowdragmc.lowdraglib2.Platform;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/// 仅创建内存 NBT 的第六版迁移检查；旧动作仅作为输入夹具，不恢复旧动作运行层。
public class PhotonAnimationMigrationRegression {
	private final HolderLookup.Provider provider = HolderLookup.Provider.create(Stream.empty());
	private int assertions;

	public static int runLoaded() {
		var checks = new PhotonAnimationMigrationRegression();
		checks.formalRoundTrip();
		checks.singlePlayMigration();
		checks.mixedRuleMigration();
		checks.sharedParameterMigration();
		checks.failedRuleParameters();
		checks.boundParameterPreserved();
		checks.unknownEnums();
		checks.unknownTracks();
		checks.missingTriggerFields();
		checks.malformedLists();
		checks.missingDefaultAnimation();
		checks.unsupportedActions();
		checks.dynamicInputs();
		checks.invalidParameterDefinitions();
		checks.unownedParameter();
		checks.warningReport();
		checks.originalPreserved();
		return checks.assertions;
	}

	/// 在已加载环境中只读扫描真实工程和导出特效；不创建项目、运行态或预览，不写入任何资源。
	@SuppressWarnings("unused") // 通过 IDEA 已加载环境的调试表达式执行，只读读取真实资源。
	public static List<String> scanProjects(Path root) {
		var reports = new ArrayList<String>();

		try (var paths = Files.walk(root)) {
			for (var path : paths.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
					.filter(PhotonAnimationMigrationRegression::effectFile).sorted().toList()) {
				scanProject(path, reports);
			}
		} catch (IOException failure) {
			reports.add(root + " | 扫描失败：" + failure);
		}

		return List.copyOf(reports);
	}

	private static boolean effectFile(Path path) {
		var name = path.getFileName().toString();
		return name.endsWith(".fxproj") || name.endsWith(".fx");
	}

	private static void scanProject(Path path, List<String> reports) {
		try {
			// LDLib2 ProjectType 以未压缩 NBT 保存工程，Photon FXProject 以 GZIP NBT 导出 .fx。
			var tag = path.getFileName().toString().endsWith(".fxproj")
					? NbtIo.read(path) : NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			var definitions = new ArrayList<CompoundTag>();

			if (tag != null) {
				controllerDefinitions(tag, false, definitions);
			}

			if (definitions.isEmpty()) {
				reports.add(path + " | 控制器数量=0");
				return;
			}

			for (var index = 0; index < definitions.size(); index++) {
				var definition = definitions.get(index);
				var library = PhotonAnimationLibrary.read(Platform.getFrozenRegistry(), definition);
				reports.add(path + " | 控制器=" + index + " | version="
						+ definition.getIntOr("version", PhotonAnimationDataUtil.LEGACY_VERSION)
						+ " | 原始规则=" + definition.getListOrEmpty("rules").size()
						+ " | 原始共享参数=" + definition.getListOrEmpty("parameters").size()
						+ " | 原始形参=" + formalCount(definition) + " | 迁移规则=" + library.rules.size()
						+ " | 迁移形参=" + library.animations.stream().mapToInt(value -> value.parameters.size()).sum()
						+ " | blocked=" + library.migrationBlocked() + " | problems=" + library.migrationProblems()
						+ " | warnings=" + library.migrationWarnings());
			}
		} catch (IOException | RuntimeException failure) {
			reports.add(path + " | 读取或注册表解析失败：" + failure);
		}
	}

	private static int formalCount(CompoundTag definition) {
		var count = 0;

		for (var animation : definition.getListOrEmpty("animations")) {
			if (animation instanceof CompoundTag compound) {
				count += compound.getListOrEmpty("parameters").size();
			}
		}

		return count;
	}

	private static void controllerDefinitions(Tag tag, boolean controller, List<CompoundTag> result) {
		if (tag instanceof CompoundTag compound) {
			var inController = controller || compound.getStringOr("type", "").equals(LcPhotonAnimationTypes.TYPE_NAME);

			if (inController && compound.get("definition") instanceof CompoundTag definition) {
				result.add(definition.copy());
				return;
			}

			for (var key : compound.keySet()) {
				var child = compound.get(key);

				if (child != null) {
					controllerDefinitions(child, inController, result);
				}
			}

			return;
		}

		if (tag instanceof ListTag list) {
			for (var child : list) {
				controllerDefinitions(child, controller, result);
			}
		}
	}

	private void formalRoundTrip() {
		var first = animation("first");
		var second = animation("second");
		var firstParameter = parameter("radius", 2);
		var secondParameter = parameter("radius", 3);
		first.parameters.add(firstParameter);
		second.parameters.add(secondParameter);
		first.durationParameter = firstParameter.id.toString();
		var library = new PhotonAnimationLibrary();
		library.animations.addAll(List.of(first, second));
		var tag = library.write(provider);
		check(tag.getIntOr("version", 0) == PhotonAnimationDataUtil.VERSION, "current version");
		check(!tag.contains("parameters"), "shared parameter table removed");
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(!loaded.migrationBlocked(), "current data is writable");
		var loadedFirst = Objects.requireNonNull(loaded.find(first.id.toString()));
		var loadedSecond = Objects.requireNonNull(loaded.find(second.id.toString()));
		check(loadedFirst.id.equals(first.id) && loadedSecond.id.equals(second.id), "animation identities preserved");
		check(Objects.requireNonNull(loadedFirst.parameter(firstParameter.id.toString())).id.equals(firstParameter.id), "formal UUID preserved");
		check(Objects.requireNonNull(loadedFirst.parameter("radius")).id.equals(firstParameter.id), "formal name lookup");
		check(Objects.requireNonNull(loadedSecond.parameter("radius")).id.equals(secondParameter.id), "same names remain animation local");
		check(loadedFirst.durationParameter.equals(firstParameter.id.toString()), "duration formal reference preserved");
		check(loaded.write(provider).equals(tag), "current library round trip");
	}

	private void singlePlayMigration() {
		var target = animation("target");
		var tag = legacy(target);
		var action = action("PLAY", target.id.toString());
		var rule = rule("TRIGGER", action);
		rule.putString("name", "launch");
		tag.put("rules", list(rule));
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(!loaded.migrationBlocked(), "single SELF PLAY migrates");
		check(loaded.rules.size() == 1, "one simplified trigger");
		check(loaded.rules.getFirst().target.equals(target.id.toString()), "trigger target preserved");
		check(!loaded.write(provider).getListOrEmpty("rules").getCompoundOrEmpty(0).contains("actions"),
				"new trigger does not retain operation chain");
	}

	private void mixedRuleMigration() {
		var target = animation("target");
		target.duration = 40;
		target.autoDuration = false;
		target.autoEnableModules = true;
		target.loop = true;
		target.priority = 4;
		target.completion = PhotonAnimationDefinition.Completion.RELEASE;
		var binding = new PhotonAnimationBinding();
		binding.property = "roll";
		target.bindings.add(binding);
		var markerData = new CompoundTag();
		markerData.putString("payload", "keep");
		target.markers.add(new PhotonAnimationDefinition.Marker(12, "signal", markerData));
		var second = animation("second");
		var firstRule = rule("START", action("PLAY", target.id.toString()));
		var secondRule = rule("TRIGGER", action("PLAY", second.id.toString()));
		secondRule.putString("name", "next");

		for (int version = PhotonAnimationDataUtil.LEGACY_VERSION; version < PhotonAnimationDataUtil.VERSION; version++) {
			var tag = legacy(target);
			tag.putInt("version", version);
			tag.getListOrEmpty("animations").add(second.write(provider));
			tag.putString("defaultAnimation", target.id.toString());
			tag.put("rules", list(firstRule.copy(), rule("COMPLETE", action("FINISH", "")),
					secondRule.copy(), rule("START")));
			var original = tag.copy();
			var loaded = PhotonAnimationLibrary.read(provider, tag);
			check(!loaded.migrationBlocked(), "mixed rules migrate version " + version);
			check(loaded.migrationWarnings().size() == 2, "only unsupported rules are reported version " + version);
			check(loaded.rules.size() == 2, "valid rules survive version " + version);
			check(loaded.rules.getFirst().target.equals(target.id.toString())
					&& loaded.rules.getLast().target.equals(second.id.toString()), "valid rule order is retained version " + version);
			check(loaded.rules.getLast().name.equals("next"), "valid trigger condition is retained version " + version);
			check(loaded.defaultAnimation.equals(target.id.toString()), "default animation is retained version " + version);
			check(loaded.animations.getFirst().write(provider).equals(target.write(provider)),
					"animation UUID duration binding and marker data are retained version " + version);
			check(loaded.animations.getLast().id.equals(second.id), "second animation identity is retained version " + version);
			check(tag.equals(original), "migration input stays unchanged version " + version);
		}
	}

	private void sharedParameterMigration() {
		var target = animation("target");
		var binding = new PhotonAnimationBinding();
		binding.target = UUID.randomUUID();
		binding.property = "roll";
		binding.source = PhotonAnimationBinding.Source.PARAMETER;
		binding.parameter = "radius";
		target.bindings.add(binding);
		var tag = legacy(target);
		tag.put("parameters", list(parameter("radius", 2).write()));
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(!loaded.migrationBlocked(), "referenced shared parameter has an owner");
		var definition = loaded.animations.getFirst();
		var formal = Objects.requireNonNull(definition.parameter("radius"));
		check(definition.parameters.contains(formal), "legacy shared parameter copied into animation");
		check(definition.bindings.getFirst().parameter.equals(formal.id.toString()), "binding migrated to formal UUID");
		check(!loaded.write(provider).contains("parameters"), "migrated shared table removed");
	}

	private void failedRuleParameters() {
		var target = animation("target");
		var tag = legacy(target);
		tag.put("parameters", list(parameter("radius", 2).write(), parameter("extra", 3).write()));
		var invalid = action("PLAY", target.id.toString());
		invalid.put("inputs", list(input("radius", 3), input("extra", 1, 2)));
		tag.put("rules", list(rule("TRIGGER", invalid)));
		var failed = discarded(tag, "invalid input rolls back rule formals");
		check(failed.animations.getFirst().parameters.isEmpty(), "failed input leaves no appended formal");
		check(failed.migrationWarnings().size() == 3, "failed input does not claim either shared parameter");

		var valid = action("PLAY", target.id.toString());
		valid.put("inputs", list(input("radius", 4)));
		tag.put("rules", list(rule("TRIGGER", invalid.copy()), rule("TRIGGER", valid)));
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(!loaded.migrationBlocked(), "valid rule after failed rule is writable");
		check(loaded.rules.size() == 1, "valid rule after failed rule survives");
		var definition = loaded.animations.getFirst();
		var formal = Objects.requireNonNull(definition.parameter("radius"));
		check(definition.parameters.size() == 1 && definition.parameter("extra") == null,
				"later valid rule owns only its own formal");
		check(loaded.rules.getFirst().inputs.getFirst().target.equals(formal.id.toString()), "later valid input has the formal UUID");
		check(loaded.rules.getFirst().inputs.getFirst().value[0] == 4, "later valid input value is retained");
		check(loaded.migrationWarnings().size() == 2, "failure and unowned parameter both remain reported");
		var repeated = PhotonAnimationLibrary.read(provider, tag);
		check(Objects.requireNonNull(repeated.animations.getFirst().parameter("radius")).id.equals(formal.id),
				"rollback and reopen preserve deterministic formal identity");
	}

	private void boundParameterPreserved() {
		var target = animation("target");
		var binding = new PhotonAnimationBinding();
		binding.property = "roll";
		binding.source = PhotonAnimationBinding.Source.PARAMETER;
		binding.parameter = "radius";
		target.bindings.add(binding);
		var tag = legacy(target);
		tag.put("parameters", list(parameter("radius", 2).write(), parameter("extra", 3).write()));
		var before = PhotonAnimationLibrary.read(provider, tag);
		var beforeFormal = Objects.requireNonNull(before.animations.getFirst().parameter("radius"));
		var invalid = action("PLAY", target.id.toString());
		invalid.put("inputs", list(input("radius", 4), input("extra", Float.NaN)));
		tag.put("rules", list(rule("TRIGGER", invalid)));
		var loaded = discarded(tag, "failed input retains binding formal");
		var definition = loaded.animations.getFirst();
		var formal = Objects.requireNonNull(definition.parameter("radius"));
		check(definition.parameters.size() == 1 && definition.parameter("extra") == null, "binding formal survives failed rule cleanup");
		check(formal.id.equals(beforeFormal.id), "binding formal identity stays unchanged after rollback");
		check(formal.defaults[0] == 2, "failed override cannot change binding formal default");
		check(definition.bindings.getFirst().parameter.equals(formal.id.toString()), "binding keeps the existing formal reference");
		check(loaded.migrationWarnings().size() == 2, "binding-owned shared parameter is not reported as unused");
	}

	private void unknownEnums() {
		for (var field : List.of("type", "scope", "instances")) {
			var action = action("PLAY", "target");
			action.putString(field, "UNKNOWN");
			discarded(legacyRule(rule("TRIGGER", action)), "unknown action enum " + field);
		}

		discarded(legacyRule(rule("UNKNOWN", action("PLAY", "target"))), "unknown event enum");
	}

	private void unknownTracks() {
		var unknown = new CompoundTag();
		unknown.putString("type", "unknown_animation_track");
		unknown.put("data", new CompoundTag());
		var topLevel = legacy(animation("target"));
		topLevel.getListOrEmpty("animations").getCompoundOrEmpty(0).put("tracks", list(unknown));
		blocked(topLevel, "unknown top level track is not silently discarded");

		var group = new CompoundTag();
		group.putString("type", "group");
		var groupData = new CompoundTag();
		groupData.put("children", list(unknown.copy()));
		group.put("data", groupData);
		var nested = legacy(animation("target"));
		nested.getListOrEmpty("animations").getCompoundOrEmpty(0).put("tracks", list(group));
		blocked(nested, "unknown nested group track preserves complete original");

		var malformed = legacy(animation("target"));
		malformed.getListOrEmpty("animations").getCompoundOrEmpty(0).putString("tracks", "invalid_list");
		blocked(malformed, "malformed track collection is rejected");
		var malformedEntry = legacy(animation("target"));
		var entries = new ListTag();
		entries.add(StringTag.valueOf("invalid_track"));
		malformedEntry.getListOrEmpty("animations").getCompoundOrEmpty(0).put("tracks", entries);
		blocked(malformedEntry, "malformed track entry is rejected");
		var malformedData = legacy(animation("target"));
		var invalid = new CompoundTag();
		invalid.putString("type", "group");
		invalid.putString("data", "invalid_compound");
		malformedData.getListOrEmpty("animations").getCompoundOrEmpty(0).put("tracks", list(invalid));
		blocked(malformedData, "malformed registered track payload is rejected");
	}

	private void missingTriggerFields() {
		var withoutEvent = rule("TRIGGER", action("PLAY", "target"));
		withoutEvent.remove("event");
		discarded(legacyRule(withoutEvent), "missing event cannot silently become START");
		var withoutType = action("PLAY", "target");
		withoutType.remove("type");
		discarded(legacyRule(rule("TRIGGER", withoutType)), "missing action type cannot silently become PLAY");
	}

	private void malformedLists() {
		for (var field : List.of("animations", "parameters", "rules")) {
			var wrongList = legacy(animation("target"));
			wrongList.putString(field, "invalid_list");
			blocked(wrongList, "malformed root list " + field);
			var wrongEntry = legacy(animation("target"));
			var entries = new ListTag();
			entries.add(StringTag.valueOf("invalid_compound"));
			wrongEntry.put(field, entries);

			if (field.equals("rules")) {
				discarded(wrongEntry, "malformed rule entry");
				continue;
			}

			blocked(wrongEntry, "malformed root entry " + field);
		}

		var invalidInputs = action("PLAY", "target");
		invalidInputs.putString("inputs", "invalid_list");
		discarded(legacyRule(rule("TRIGGER", invalidInputs)), "malformed action input list");
		var invalidEntry = action("PLAY", "target");
		var inputs = new ListTag();
		inputs.add(StringTag.valueOf("invalid_compound"));
		invalidEntry.put("inputs", inputs);
		discarded(legacyRule(rule("TRIGGER", invalidEntry)), "malformed action input entry");

		for (var field : List.of("bindings", "markers")) {
			var malformed = legacy(animation("target"));
			malformed.getListOrEmpty("animations").getCompoundOrEmpty(0).putString(field, "invalid_list");
			blocked(malformed, "malformed animation list " + field);
		}
	}

	private void missingDefaultAnimation() {
		var tag = legacy(animation("target"));
		tag.putString("defaultAnimation", UUID.randomUUID().toString());
		blocked(tag, "invalid nonempty default animation reference");
	}

	private void unsupportedActions() {
		for (var type : List.of("CONTINUE", "STOP", "PAUSE", "RESUME", "SET_PARAMETER",
				"STOP_EMISSION", "FINISH", "SET_DURATION", "TRIGGER")) {
			discarded(legacyRule(rule("TRIGGER", action(type, "target"))), "unsupported action " + type);
		}

		discarded(legacyRule(rule("TRIGGER", action("PLAY", "target"), action("PLAY", "target"))),
				"multiple SELF PLAY operations are not collapsed");
		discarded(legacyRule(rule("TRIGGER", action("STOP", "target"), action("PLAY", "target"))),
				"ordered operation chain is not flattened");
		discarded(legacyRule(rule("START")), "empty START is discarded");
		var child = action("PLAY", "target");
		child.putString("scope", "CHILD_INSTANCES");
		discarded(legacyRule(rule("MARKER", child)), "legacy child invocation is discarded");
	}

	private void dynamicInputs() {
		var duration = action("PLAY", "target");
		duration.putString("durationParameter", "length");
		discarded(legacyRule(rule("TRIGGER", duration)), "dynamic duration is discarded");
		var dynamic = action("PLAY", "target");
		var source = input("radius", 2);
		source.putString("source", "shared");
		dynamic.put("inputs", list(source));
		discarded(legacyRule(rule("TRIGGER", dynamic)), "dynamic shared input is discarded");

		for (var value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -2}) {
			var invalid = action("PLAY", "target");
			invalid.putDouble("duration", value);
			discarded(legacyRule(rule("TRIGGER", invalid)), "invalid duration " + value);
		}
	}

	private void invalidParameterDefinitions() {
		var unknown = parameter("radius", 2).write();
		unknown.putString("type", "UNKNOWN");
		var tag = legacy(animation("target"));
		tag.put("parameters", list(unknown));
		blocked(tag, "invalid shared parameter type is still blocked");
		tag.put("parameters", list(parameter("radius", Float.NaN).write()));
		blocked(tag, "invalid shared parameter default is still blocked");
		var invalidAnimation = legacy(animation("target"));
		invalidAnimation.getListOrEmpty("animations").getCompoundOrEmpty(0).putDouble("duration", Double.NaN);
		blocked(invalidAnimation, "invalid animation timing is still blocked");
		var wrongContainer = legacy(animation("target"));
		wrongContainer.getListOrEmpty("animations").getCompoundOrEmpty(0).putString("parameters", "invalid_list");
		blocked(wrongContainer, "malformed animation formal container is still blocked");
		var wrongEntry = legacy(animation("target"));
		var parameters = new ListTag();
		parameters.add(StringTag.valueOf("invalid_compound"));
		wrongEntry.getListOrEmpty("animations").getCompoundOrEmpty(0).put("parameters", parameters);
		blocked(wrongEntry, "malformed animation formal entry is still blocked");
	}

	private void unownedParameter() {
		var tag = legacy(animation("target"));
		tag.put("parameters", list(parameter("unused", 7).write()));
		var loaded = discarded(tag, "unowned shared parameter is reported");
		check(loaded.animations.getFirst().parameters.isEmpty(), "unowned shared parameter creates no formal");
		check(!loaded.write(provider).contains("parameters"), "unowned shared table is not saved");
	}

	private void warningReport() {
		var loaded = discarded(legacyRule(rule("COMPLETE", action("FINISH", ""))), "discard report is separate from blockers");
		var readOnly = false;

		try {
			loaded.migrationWarnings().add("changed");
		} catch (UnsupportedOperationException expected) {
			readOnly = true;
		}

		check(readOnly, "discard report is read only");
		var snapshot = loaded.migrationWarnings();
		var replacement = new ArrayList<>(List.of("replacement"));
		loaded.recordMigrationWarnings(replacement);
		replacement.clear();
		check(loaded.migrationWarnings().equals(List.of("replacement")), "report does not retain caller list");
		check(!snapshot.equals(loaded.migrationWarnings()), "previous report snapshot stays independent");
		var written = loaded.write(provider);
		check(!written.contains("migrationWarnings") && !written.contains("migrationProblems"), "reports are not serialized");
		var restored = PhotonAnimationLibrary.read(provider, written);
		check(restored.migrationWarnings().isEmpty() && restored.migrationProblems().isEmpty(), "current round trip does not replay old reports");
		check(restored.write(provider).equals(written), "discarded library has a stable current round trip");
	}

	private void originalPreserved() {
		var tag = legacy(animation("target"));
		tag.putInt("version", PhotonAnimationDataUtil.VERSION + 1);
		tag.putString("custom", "original");
		var original = tag.copy();
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(loaded.migrationBlocked() && !loaded.migrationProblems().isEmpty(), "blocked migration has a report");
		var written = Objects.requireNonNull(loaded.originalDefinition());
		check(written.equals(original), "blocked migration preserves complete original NBT");
		written.putString("custom", "changed");
		tag.putString("custom", "changed input");
		check(Objects.equals(loaded.originalDefinition(), original), "original NBT copies are isolated");
		writeRejected(loaded, "blocked library cannot write new format");
		var readOnly = false;

		try {
			loaded.migrationProblems().add("changed");
		} catch (UnsupportedOperationException expected) {
			readOnly = true;
		}

		check(readOnly, "migration report is read only");
	}

	private PhotonAnimationLibrary discarded(CompoundTag tag, String name) {
		var original = tag.copy();
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(!loaded.migrationBlocked(), name + " is writable");
		check(loaded.migrationProblems().isEmpty(), name + " has no blocking report");
		check(!loaded.migrationWarnings().isEmpty(), name + " has a discard report");
		check(loaded.rules.isEmpty(), name + " leaves no executable rule");
		check(loaded.originalDefinition() == null, name + " does not retain a blocked definition");
		check(loaded.write(provider).getIntOr("version", 0) == PhotonAnimationDataUtil.VERSION, name + " writes current format");
		check(tag.equals(original), name + " does not mutate input NBT");
		return loaded;
	}

	private void blocked(CompoundTag tag, String name) {
		var original = tag.copy();
		var loaded = PhotonAnimationLibrary.read(provider, tag);
		check(loaded.migrationBlocked(), name);
		check(!loaded.migrationProblems().isEmpty(), name + " report");
		check(Objects.equals(loaded.originalDefinition(), original), name + " preserves original");
		writeRejected(loaded, name + " rejects new format");
	}

	private void writeRejected(PhotonAnimationLibrary library, String name) {
		var rejected = false;

		try {
			library.write(provider);
		} catch (IllegalStateException expected) {
			rejected = true;
		}

		check(rejected, name);
	}

	private CompoundTag legacyRule(CompoundTag rule) {
		var tag = legacy(animation("target"));
		tag.put("rules", list(rule));
		return tag;
	}

	private CompoundTag legacy(PhotonAnimationDefinition definition) {
		var tag = new CompoundTag();
		tag.putInt("version", 5);
		var animation = definition.write(provider);
		animation.remove("parameters");
		animation.remove("durationParameter");
		tag.put("animations", list(animation));
		return tag;
	}

	private static PhotonAnimationDefinition animation(String name) {
		var value = new PhotonAnimationDefinition();
		value.name = name;
		return value;
	}

	private static PhotonAnimationParameter parameter(String name, float value) {
		var parameter = new PhotonAnimationParameter();
		parameter.name = name;
		parameter.defaults = new float[]{value};
		return parameter;
	}

	private static CompoundTag input(String target, float... value) {
		var tag = new CompoundTag();
		tag.putString("target", target);
		tag.put("value", PhotonAnimationDataUtil.floats(value));
		return tag;
	}

	private static CompoundTag action(String type, String target) {
		var tag = new CompoundTag();
		tag.putString("type", type);
		tag.putString("scope", "SELF");
		tag.putString("instances", "ALL");
		tag.putString("target", target);
		tag.putDouble("duration", -1);
		return tag;
	}

	private static CompoundTag rule(String event, CompoundTag... actions) {
		var tag = new CompoundTag();
		tag.putString("event", event);
		tag.put("actions", list(actions));
		return tag;
	}

	private static ListTag list(CompoundTag... tags) {
		var list = new ListTag();
		list.addAll(List.of(tags));

		return list;
	}

	private void check(boolean value, String name) {
		assertions++;

		if (!value) {
			throw new AssertionError(name);
		}
	}
}
