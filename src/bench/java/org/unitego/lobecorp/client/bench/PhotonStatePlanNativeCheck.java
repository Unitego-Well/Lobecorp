package org.unitego.lobecorp.client.bench;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;


import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector2f;
import org.joml.Vector3fc;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.unitego.lobecorp.util.photon.creation.PhotonStatePlanUtil;
import com.lowdragmc.lowdraglib2.Platform;

import java.util.Arrays;
import java.util.Objects;

/// 仅在已初始化 FML／Photon 注册表的宿主调用；失败立即抛出，返回值只列出本次已完成断言的用例。
@SuppressWarnings({"FieldMayBeFinal", "unused"})
public class PhotonStatePlanNativeCheck {
	public static List<String> verify() throws Exception {
		var checks = new PhotonStatePlanNativeCheck();
		var completed = new ArrayList<String>();
		runCase(completed, "tagSnapshotsAndContainerFallbacksRemainIndependentInBothDirections", checks::tagSnapshotsAndContainerFallbacksRemainIndependentInBothDirections);
		runCase(completed, "curveLockUsesPersistedInputRatherThanLossyCurveCopy", checks::curveLockUsesPersistedInputRatherThanLossyCurveCopy);
		runCase(completed, "finalLeafUsesNativeFallbackAndSubgroupNeedsNoDefaultConstructor", checks::finalLeafUsesNativeFallbackAndSubgroupNeedsNoDefaultConstructor);
		runCase(completed, "customDeserializeIsVisibleAndFieldsEntryLeavesRootCustomizationToAdapter", checks::customDeserializeIsVisibleAndFieldsEntryLeavesRootCustomizationToAdapter);
		runCase(completed, "nestedCustomValueIoAndAdditionalDataUseNativeCallbacksAndPrivateTags", checks::nestedCustomValueIoAndAdditionalDataUseNativeCallbacksAndPrivateTags);
		runCase(completed, "jomlSnapshotsAndUnknownDirectAccessorDoNotRetainSourceValues", checks::jomlSnapshotsAndUnknownDirectAccessorDoNotRetainSourceValues);
		runCase(completed, "transferCopiesCurrentContainersAndTagsAndCountsNativeFieldFallbacks", checks::transferCopiesCurrentContainersAndTagsAndCountsNativeFieldFallbacks);
		runCase(completed, "transferPreservesCurveLockAndCurrentCurveDataWithoutCurveCopy", checks::transferPreservesCurveLockAndCurrentCurveDataWithoutCurveCopy);
		runCase(completed, "transferFieldsKeepsRootAdapterOwnershipAndReadsLiveCustomSubtreeAndAdditional", checks::transferFieldsKeepsRootAdapterOwnershipAndReadsLiveCustomSubtreeAndAdditional);
		runCase(completed, "fieldsTransferProjectsOnlyCompiledBaseFieldsIntoControlledSubclass", checks::fieldsTransferProjectsOnlyCompiledBaseFieldsIntoControlledSubclass);
		runCase(completed, "liveSubgroupTransferHonorsCustomSerializeWithDefaultDeserialize", checks::liveSubgroupTransferHonorsCustomSerializeWithDefaultDeserialize);
		return List.copyOf(completed);
	}

	private static void runCase(List<String> completed, String name, Runnable check) {
		try {
			check.run();
		} catch (RuntimeException | AssertionError failure) {
			throw new IllegalStateException("PhotonStatePlan host case: " + name, failure);
		}
		completed.add(name);
	}

	private static HolderLookup.Provider provider() {
		return Objects.requireNonNull(Platform.getFrozenRegistry(), "Host registry provider");
	}

	private void tagSnapshotsAndContainerFallbacksRemainIndependentInBothDirections() {
		var prototype = new Containers();
		prototype.values.add(11);
		prototype.mapping.put("a", 12);
		prototype.array[0] = 13;
		prototype.tag.putInt("number", 14);
		var tag = (CompoundTag) PersistedParser.serialize(NbtOps.INSTANCE, prototype, provider()).getOrThrow();
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Containers::new);
		prototype.values.set(0, 101);
		prototype.mapping.put("a", 102);
		prototype.array[0] = 103;
		prototype.tag.putInt("number", 104);
		tag.put("tag", new CompoundTag());
		var first = (Containers) plan.create();
		var second = (Containers) plan.create();
		assertEquals(List.of(11), first.values);
		assertEquals(Map.of("a", 12), first.mapping);
		assertArrayEquals(new int[]{13, 2}, first.array);
		assertEquals(14, first.tag.getIntOr("number", -1));
		first.values.set(0, 201);
		first.mapping.put("a", 202);
		first.array[0] = 203;
		first.tag.putInt("number", 204);
		assertEquals(List.of(11), second.values);
		assertEquals(Map.of("a", 12), second.mapping);
		assertArrayEquals(new int[]{13, 2}, second.array);
		assertEquals(14, second.tag.getIntOr("number", -1));
		assertEquals(104, prototype.tag.getIntOr("number", -1));
		assertEquals(List.of(101), prototype.values);
		assertEquals(Map.of("a", 102), prototype.mapping);
		assertArrayEquals(new int[]{103, 2}, prototype.array);
		assertTrue(plan.fallbackFields() >= 3);
	}

	private void curveLockUsesPersistedInputRatherThanLossyCurveCopy() {
		var prototype = new Curve();
		prototype.setLockControlPoint(false);
		var tag = new CompoundTag();
		tag.putBoolean("lockControlPoint", false);
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Curve::new);
		var first = (Curve) plan.create();
		var second = (Curve) plan.create();
		assertFalse(first.isLockControlPoint());
		assertFalse(second.isLockControlPoint());
		assertNotSame(first.getCurves(), second.getCurves());
		assertNotSame(prototype.getCurves(), first.getCurves());
		assertTrue(plan.fallbackFields() > 0);
	}

	private void finalLeafUsesNativeFallbackAndSubgroupNeedsNoDefaultConstructor() {
		var prototype = new FinalLeaf();
		var tag = new CompoundTag();
		var data = new ListTag();
		data.add(IntTag.valueOf(31));
		tag.put("values", data);
		var plan = PhotonStatePlanUtil.compile(prototype, tag, FinalLeaf::new);
		var target = (FinalLeaf) plan.create();
		assertEquals(List.of(31), target.values);
		assertEquals(List.of(1), prototype.values);
		assertEquals(1, plan.fallbackFields());
		assertTrue(plan.fallbackReasons().getFirst().contains("final leaf"));
		var childTag = new CompoundTag();
		childTag.putInt("amount", 73);
		var ownerTag = new CompoundTag();
		ownerTag.put("child", childTag);
		var ownerPlan = PhotonStatePlanUtil.compile(new NoDefaultOwner(), ownerTag, NoDefaultOwner::new);
		var owner = (NoDefaultOwner) ownerPlan.create();
		assertEquals(73, owner.child.amount);
	}

	private void customDeserializeIsVisibleAndFieldsEntryLeavesRootCustomizationToAdapter() {
		var tag = new CompoundTag();
		tag.putInt("value", 9);
		tag.putInt("asset", 50);
		var prototype = new CustomRoot();
		prototype.value = 9;
		var full = PhotonStatePlanUtil.compile(prototype, tag, CustomRoot::new);
		assertTrue(full.fallbackFields() > 0);
		assertTrue(full.fallbackReasons().getFirst().contains("native subtree"));
		var nativeTarget = (CustomRoot) full.create();
		assertEquals(50, nativeTarget.value);
		assertEquals(1, nativeTarget.customCalls);
		var fields = PhotonStatePlanUtil.compileFields(prototype, tag, CustomRoot::new, Set.of());
		var target = (CustomRoot) fields.create();
		assertEquals(9, target.value);
		assertEquals(0, target.customCalls);
		assertEquals(List.of("before", "after"), target.events);
		assertEquals(1, fields.assignmentCount());
		assertEquals(0, fields.fallbackFields());
	}

	private void nestedCustomValueIoAndAdditionalDataUseNativeCallbacksAndPrivateTags() {
		var tag = new CompoundTag();
		var child = new CompoundTag();
		child.putInt("amount", 26);
		tag.put("child", child);
		var additional = new CompoundTag();
		additional.putInt("extra", 36);
		tag.put("_additional", additional);
		var prototype = new CustomOwner();
		var plan = PhotonStatePlanUtil.compileFields(prototype, tag, CustomOwner::new, Set.of());
		var first = (CustomOwner) plan.create();
		var second = (CustomOwner) plan.create();
		assertEquals(26, first.child.amount);
		assertEquals(1, first.child.calls);
		assertEquals(36, first.extra);
		assertEquals(36, second.extra);
		assertNotSame(first.child, second.child);
		assertNotSame(prototype.child, first.child);
		assertEquals(0, prototype.child.calls);
		assertEquals(1, second.child.calls);
		assertEquals(0, prototype.extra);
		assertEquals(36, additional.getIntOr("extra", -1));
		assertEquals(List.of("before", "additional", "after"), first.events);
		assertTrue(plan.fallbackFields() >= 2);
	}

	private void jomlSnapshotsAndUnknownDirectAccessorDoNotRetainSourceValues() {
		if (AccessorRegistries.findByClassOrNull(Vector3f.class) == null) {
			AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(Vector3fc.class, true)
					.codec(ExtraCodecs.VECTOR3F).streamCodec(ByteBufCodecs.VECTOR3F).build(), 100);
		}
		if (AccessorRegistries.findByClassOrNull(Payload.class) == null) {
			AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(Payload.class)
					.codec(Codec.INT.xmap(Payload::new, Payload::amount))
					.streamCodec(ByteBufCodecs.INT.map(Payload::new, Payload::amount)).build(), 100);
		}

		var prototype = new Complex();
		prototype.vector.set(1, 2, 3);
		var tag = (CompoundTag) PersistedParser.serialize(NbtOps.INSTANCE, prototype, provider()).getOrThrow();
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Complex::new);
		prototype.vector.set(9, 9, 9);
		var first = (Complex) plan.create();
		var second = (Complex) plan.create();
		assertEquals(new Vector3f(1, 2, 3), first.vector);
		first.vector.set(4, 5, 6);
		assertEquals(new Vector3f(1, 2, 3), second.vector);
		assertNotSame(first.vector, second.vector);
		assertEquals(7, first.payload.amount());
		assertEquals(1, plan.assignmentCount());
		assertEquals(1, plan.fallbackFields());
		assertTrue(plan.fallbackReasons().getFirst().contains("payload"));
		prototype.payload = new Payload(27);
		var liveTarget = new Complex();
		plan.transfer(prototype, liveTarget);
		assertEquals(new Vector3f(9, 9, 9), liveTarget.vector);
		assertEquals(27, liveTarget.payload.amount());
		assertNotSame(prototype.payload, liveTarget.payload);
		liveTarget.vector.set(8, 8, 8);
		assertEquals(new Vector3f(9, 9, 9), prototype.vector);
		assertNotSame(prototype.vector, liveTarget.vector);
		assertTrue(plan.fallbackReasons().stream().anyMatch(reason -> reason.contains("payload") && reason.contains("transfer native field")));
	}

	private void transferCopiesCurrentContainersAndTagsAndCountsNativeFieldFallbacks() {
		var plan = PhotonStatePlanUtil.compile(new Containers(), new CompoundTag(), Containers::new);
		var source = new Containers();
		source.values.add(101);
		source.mapping.put("live", 102);
		source.array[0] = 103;
		source.tag.putInt("number", 104);
		var nativeCopy = new Containers();
		PersistedParser.deserialize(NbtOps.INSTANCE,
				PersistedParser.serialize(NbtOps.INSTANCE, source, provider()).getOrThrow(), nativeCopy, provider());
		var first = new Containers();
		var second = new Containers();
		plan.transfer(source, first);
		plan.transfer(source, second);
		assertEquals(nativeCopy.values, first.values);
		assertEquals(nativeCopy.mapping, first.mapping);
		assertArrayEquals(nativeCopy.array, first.array);
		assertEquals(nativeCopy.tag, first.tag);
		first.values.set(0, 201);
		first.mapping.put("live", 202);
		first.array[0] = 203;
		first.tag.putInt("number", 204);
		assertEquals(List.of(101), source.values);
		assertEquals(List.of(101), second.values);
		assertEquals(Map.of("live", 102), source.mapping);
		assertArrayEquals(new int[]{103, 2}, second.array);
		assertEquals(Map.of("live", 102), second.mapping);
		assertEquals(103, source.array[0]);
		assertEquals(104, second.tag.getIntOr("number", -1));
		assertEquals(104, source.tag.getIntOr("number", -1));
		assertTrue(plan.fallbackReasons().stream().anyMatch(reason -> reason.contains("transfer native field")));
	}

	private void transferPreservesCurveLockAndCurrentCurveDataWithoutCurveCopy() {
		var plan = PhotonStatePlanUtil.compile(new Curve(), new CompoundTag(), Curve::new);
		var source = new Curve();
		source.setLockControlPoint(false);
		source.setMin(7);
		((Vector2f) source.getCurves().getSegments().getFirst().p0).set(0, 0.3f);
		var nativeCopy = new Curve();
		PersistedParser.deserialize(NbtOps.INSTANCE,
				PersistedParser.serialize(NbtOps.INSTANCE, source, provider()).getOrThrow(), nativeCopy, provider());
		var target = new Curve();
		plan.transfer(source, target);
		assertEquals(PersistedParser.serialize(NbtOps.INSTANCE, nativeCopy, provider()).getOrThrow(),
				PersistedParser.serialize(NbtOps.INSTANCE, target, provider()).getOrThrow());
		assertFalse(target.isLockControlPoint());
		assertEquals(7, target.getMin());
		assertNotSame(source.getCurves(), target.getCurves());
		assertNotSame(source.getCurves().getSegments().getFirst(), target.getCurves().getSegments().getFirst());
		assertEquals(0.3f, target.getCurves().getSegments().getFirst().p0.y());
		var sibling = new Curve();
		plan.transfer(source, sibling);
		((Vector2f) target.getCurves().getSegments().getFirst().p0).set(0, 0.8f);
		assertEquals(0.3f, source.getCurves().getSegments().getFirst().p0.y());
		assertEquals(0.3f, sibling.getCurves().getSegments().getFirst().p0.y());
		assertNotSame(target.getCurves(), sibling.getCurves());
		assertTrue(plan.fallbackReasons().stream().anyMatch(reason -> reason.contains("curves") && reason.contains("transfer native field")));
	}

	private void transferFieldsKeepsRootAdapterOwnershipAndReadsLiveCustomSubtreeAndAdditional() {
		var source = new CustomOwner();
		source.child.amount = 51;
		source.extra = 61;
		var plan = PhotonStatePlanUtil.compileFields(new CustomOwner(), new CompoundTag(), CustomOwner::new, Set.of());
		var target = new CustomOwner();
		plan.transfer(source, target);
		assertEquals(51, target.child.amount);
		assertEquals(61, target.extra);
		assertEquals(61, source.extra);
		assertEquals(List.of("before", "additional", "after"), target.events);
		assertTrue(plan.fallbackReasons().stream().anyMatch(reason -> reason.contains("transfer native subgroup")));
		var custom = new CustomRoot();
		custom.value = 74;
		var fields = PhotonStatePlanUtil.compileFields(custom, new CompoundTag(), CustomRoot::new, Set.of());
		var copy = new CustomRoot();
		fields.transfer(custom, copy);
		assertEquals(74, copy.value);
		assertEquals(0, copy.customCalls);
		var full = PhotonStatePlanUtil.compile(custom, new CompoundTag(), CustomRoot::new);
		assertIllegalState(() -> full.transfer(custom, new CustomRoot()));
		assertTrue(full.fallbackReasons().stream().anyMatch(reason -> reason.contains("transfer unsupported")));
	}

	private void fieldsTransferProjectsOnlyCompiledBaseFieldsIntoControlledSubclass() {
		var frozen = new CompoundTag();
		frozen.putInt("amount", 8);
		var plan = PhotonStatePlanUtil.compileFields(new ProjectionBase(), frozen, ProjectionChild::new, Set.of());
		var source = new ProjectionBase();
		source.amount = 71;
		source.values.add(81);
		var target = new ProjectionChild();
		plan.transfer(source, target);
		assertEquals(71, target.amount);
		assertEquals(List.of(81), target.values);
		assertEquals(900, target.extension);
		assertEquals(0, target.extensionSetterCalls);
		assertEquals(1, plan.assignmentCount());
		target.values.set(0, 91);
		assertEquals(List.of(81), source.values);
		var reverse = new ProjectionBase();
		target.amount = 72;
		plan.transfer(target, reverse);
		assertEquals(72, reverse.amount);
		assertEquals(900, target.extension);
		var created = (ProjectionChild) plan.create();
		assertEquals(8, created.amount);
		assertEquals(900, created.extension);
	}

	private void liveSubgroupTransferHonorsCustomSerializeWithDefaultDeserialize() {
		var plan = PhotonStatePlanUtil.compileFields(new SerializeOwner(), new CompoundTag(), SerializeOwner::new, Set.of());
		var source = new SerializeOwner();
		source.child.amount = 21;
		var nativeTag = PersistedParser.serialize(NbtOps.INSTANCE, source, provider()).getOrThrow();
		var nativeCopy = new SerializeOwner();
		PersistedParser.deserialize(NbtOps.INSTANCE, nativeTag, nativeCopy, provider());
		var first = new SerializeOwner();
		var identity = first.child;
		plan.transfer(source, first);
		assertSame(identity, first.child);
		assertEquals(nativeCopy.child.amount, first.child.amount);
		assertEquals(31, first.child.amount);
		source.child.amount = 41;
		var second = new SerializeOwner();
		plan.transfer(source, second);
		assertEquals(51, second.child.amount);
		assertEquals(31, first.child.amount);
		assertEquals(41, source.child.amount);
		assertNotSame(first.child, second.child);
		assertTrue(plan.fallbackReasons().stream().anyMatch(reason -> reason.contains("transfer native subgroup")));
	}

	private static void assertEquals(Object expected, Object actual) {
		var equal = expected instanceof Number left && actual instanceof Number right
				? Double.compare(left.doubleValue(), right.doubleValue()) == 0 : Objects.equals(expected, actual);
		if (!equal) {
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}

	private static void assertTrue(boolean condition) {
		if (!condition) {
			throw new AssertionError("Expected true");
		}
	}

	private static void assertFalse(boolean condition) {
		assertTrue(!condition);
	}

	private static void assertSame(Object expected, Object actual) {
		assertTrue(expected == actual);
	}

	private static void assertNotSame(Object expected, Object actual) {
		assertTrue(expected != actual);
	}

	private static void assertArrayEquals(int[] expected, int[] actual) {
		if (!Arrays.equals(expected, actual)) {
			throw new AssertionError("Expected " + Arrays.toString(expected) + ", got " + Arrays.toString(actual));
		}
	}

	private static void assertIllegalState(Runnable action) {
		try {
			action.run();
		} catch (IllegalStateException _) {
			return;
		}
		throw new AssertionError("Expected IllegalStateException");
	}


	private static class SerializeOwner {
		@Persisted(subPersisted = true)
		private final SerializeOnly child = new SerializeOnly();
	}

	private static class SerializeOnly implements IPersistedSerializable {
		@Persisted
		private int amount;

		@Override
		public void serialize(ValueOutput output) {
			output.putInt("amount", amount + 10);
		}
	}


	private static class ProjectionBase {
		@Persisted
		protected int amount = 3;
		@Persisted
		protected final List<Integer> values = new ArrayList<>();
	}

	private static class ProjectionChild extends ProjectionBase {
		@Persisted
		private int extension = 900;
		private int extensionSetterCalls;

		@ConfigSetter(field = "amount")
		private void extensionSetter(int amount) {
			extensionSetterCalls++;
			this.amount = -amount;
			extension = -1;
		}
	}

	private record Payload(int amount) {
	}

	private static class Complex {
		@Persisted
		private Vector3f vector = new Vector3f();
		@Persisted
		private Payload payload = new Payload(7);
	}


	private static class Containers {
		@Persisted
		private List<Integer> values = new ArrayList<>();
		@Persisted
		private Map<String, Integer> mapping = new LinkedHashMap<>();
		@Persisted
		private int[] array = {1, 2};
		@Persisted
		private CompoundTag tag = new CompoundTag();
	}

	private static class FinalLeaf {
		@Persisted
		private final List<Integer> values = new ArrayList<>(List.of(1));
	}

	private static class NoDefault {
		@Persisted
		private int amount;

		private NoDefault(int amount) {
			this.amount = amount;
		}
	}

	private static class NoDefaultOwner {
		@Persisted(subPersisted = true)
		private final NoDefault child = new NoDefault(3);
	}

	private static class CustomRoot implements IPersistedSerializable {
		@Persisted
		private int value;
		private int customCalls;
		private List<String> events = new ArrayList<>();

		@Override
		public void beforeDeserialize() {
			events.add("before");
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
		}

		@Override
		public void deserialize(ValueInput input) {
			customCalls++;
			value = input.getIntOr("asset", 0);
		}
	}

	private static class CustomValue implements ValueIOSerializable {
		private int amount;
		private int calls;

		@Override
		public void serialize(ValueOutput output) {
			output.putInt("amount", amount);
		}

		@Override
		public void deserialize(ValueInput input) {
			calls++;
			amount = input.getIntOr("amount", 0);
		}
	}

	private static class CustomOwner implements IPersistedSerializable {
		@Persisted(subPersisted = true)
		private final CustomValue child = new CustomValue();
		private int extra;
		private List<String> events = new ArrayList<>();

		@Override
		public void beforeDeserialize() {
			events.add("before");
		}

		@Override
		public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
			var tag = new CompoundTag();
			tag.putInt("extra", extra);
			return tag;
		}

		@Override
		public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
			events.add("additional");
			if (tag instanceof CompoundTag compound) {
				extra = compound.getIntOr("extra", -1);
				compound.putInt("extra", -100);
			}
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
		}
	}
}

