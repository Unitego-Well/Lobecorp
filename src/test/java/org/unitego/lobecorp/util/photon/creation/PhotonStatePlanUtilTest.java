package org.unitego.lobecorp.util.photon.creation;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.PrimitiveAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
// 容器、Curve、自定义 ValueIO／Additional 及原生 fallback 对照入口为 bench 的 PhotonStatePlanNativeCheck.verify()。
public class PhotonStatePlanUtilTest {
	private static final HolderLookup.Provider PROVIDER = HolderLookup.Provider.create(Stream.empty());

	@BeforeAll
	public static void accessors() {
		if (AccessorRegistries.findByClassOrNull(int.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.INT, ByteBufCodecs.INT,
					int.class, Integer.class), -1);
		}
		if (AccessorRegistries.findByClassOrNull(boolean.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.BOOL, ByteBufCodecs.BOOL,
					boolean.class, Boolean.class), -1);
		}
		if (AccessorRegistries.findByClassOrNull(float.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.FLOAT, ByteBufCodecs.FLOAT,
					float.class, Float.class), -1);
		}
		if (AccessorRegistries.findByClassOrNull(String.class) == null) {
			AccessorRegistries.registerAccessor(PrimitiveAccessor.of(Codec.STRING, ByteBufCodecs.STRING_UTF8,
					String.class), -1);
		}
		if (AccessorRegistries.findByClassOrNull(CompoundTag.class) == null) {
			AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(CompoundTag.class)
					.codec(CompoundTag.CODEC).streamCodec(ByteBufCodecs.COMPOUND_TAG).build(), 100);
		}
	}

	@Test
	public void aliasesDerivedSettersAndCallbacksMatchNativeWithoutMutatingPrototype() {
		var tag = scalarTag();
		var prototype = new Derived();
		PersistedParser.deserialize(NbtOps.INSTANCE, tag, prototype, PROVIDER);
		var events = List.copyOf(prototype.events);
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Derived::new);
		var first = (Derived) plan.create();
		var second = (Derived) plan.create();
		assertEquals(prototype.value, first.value);
		assertEquals(8, first.other);
		assertEquals(events, first.events);
		assertEquals(events, prototype.events);
		assertEquals(events, second.events);
		assertNotSame(first.events, second.events);
		assertEquals(0, plan.fallbackFields());
		assertEquals(2, plan.assignmentCount());
		assertThrows(UnsupportedOperationException.class, () -> plan.fallbackReasons().add("changed"));
	}

	@Test
	public void finalSubgroupsFlatteningAndDeclaredOrderKeepConstructorObjects() {
		var tag = new CompoundTag();
		var nested = new CompoundTag();
		nested.putInt("amount", 17);
		tag.put("group", nested);
		tag.putInt("amount", 29);
		var prototype = new Groups();
		prototype.group.amount = 17;
		prototype.flat.amount = 29;
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Groups::new);
		var target = new Groups();
		var group = target.group;
		var flat = target.flat;
		plan.apply(target);
		assertSame(group, target.group);
		assertSame(flat, target.flat);
		assertEquals(17, target.group.amount);
		assertEquals(29, target.flat.amount);
		assertEquals(List.of("before", "after"), target.group.events);
		assertNotSame(prototype.group, target.group);
		assertEquals(2, plan.assignmentCount());
	}

	@Test
	public void missingTagsAndExcludedFieldsKeepFreshDefaultsAndReportNativeNoOp() {
		var prototype = new Missing();
		prototype.present = 44;
		prototype.missing = 88;
		var tag = new CompoundTag();
		tag.putInt("renamed", 44);
		var plan = PhotonStatePlanUtil.compile(prototype, tag, Missing::new, Set.of("present"));
		var target = (Missing) plan.create();
		assertEquals(3, target.present);
		assertEquals(5, target.missing);
		assertEquals(1, plan.fallbackFields());
		assertTrue(plan.fallbackReasons().getFirst().contains("missing tag"));
		assertEquals(0, plan.assignmentCount());
		assertThrows(IllegalArgumentException.class, () -> plan.apply(new Object()));
	}

	@Test
	public void unannotatedObjectIsNativeFallbackRatherThanEmptyFastPlan() {
		var plan = PhotonStatePlanUtil.compile(new Object(), new CompoundTag(), Object::new);
		assertEquals(0, plan.assignmentCount());
		assertEquals(1, plan.fallbackFields());
		assertTrue(plan.fallbackReasons().getFirst().contains("empty"));
	}

	@Test
	public void transferReadsCurrentAuthorValuesWithoutConstructingOrApplyingFrozenConfiguration() {
		var prototype = new Missing();
		var frozen = new CompoundTag();
		frozen.putInt("renamed", 8);
		var constructions = new AtomicInteger();
		var plan = PhotonStatePlanUtil.compile(prototype, frozen, () -> {
			constructions.incrementAndGet();
			return new Missing();
		});
		var source = new Missing();
		source.present = 71;
		source.missing = 72;
		var first = new Missing();
		plan.transfer(source, first);
		assertEquals(71, first.present);
		assertEquals(72, first.missing);
		source.present = 81;
		source.missing = 82;
		var second = new Missing();
		plan.transfer(source, second);
		assertEquals(81, second.present);
		assertEquals(82, second.missing);
		assertEquals(71, first.present);
		assertEquals(0, constructions.get());
		assertEquals(8, ((Missing) plan.create()).present);
		assertEquals(1, constructions.get());
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(source, source));
	}

	@Test
	public void transferUsesDerivedSetterCallbacksAndKeepsFinalSubgroupIdentity() {
		var prototype = new Derived();
		var plan = PhotonStatePlanUtil.compile(prototype, scalarTag(), Derived::new);
		var source = new Derived();
		source.value = 31;
		source.other = 41;
		var target = new Derived();
		plan.transfer(source, target);
		assertEquals(62, target.value);
		assertEquals(41, target.other);
		assertEquals(List.of("before", "derived", "after"), target.events);
		assertEquals(31, source.value);
		assertTrue(source.events.isEmpty());
		var groups = new Groups();
		groups.group.amount = 91;
		groups.flat.amount = 92;
		var groupPlan = PhotonStatePlanUtil.compile(new Groups(), new CompoundTag(), Groups::new);
		var copy = new Groups();
		var groupIdentity = copy.group;
		groupPlan.transfer(groups, copy);
		assertSame(groupIdentity, copy.group);
		assertEquals(91, copy.group.amount);
		assertEquals(92, copy.flat.amount);
		assertEquals(List.of("before", "after"), copy.group.events);
		assertTrue(groups.group.events.isEmpty());
	}

	@Test
	public void transferHonorsExcludedFieldsAndReportsImpossibleSubgroupOwnership() {
		var source = new Missing();
		source.present = 77;
		source.missing = 88;
		var plan = PhotonStatePlanUtil.compileFields(new Missing(), new CompoundTag(), Missing::new, Set.of("present"));
		var target = new Missing();
		plan.transfer(source, target);
		assertEquals(3, target.present);
		assertEquals(88, target.missing);
		var ownerPlan = PhotonStatePlanUtil.compileFields(new NullableOwner(), new CompoundTag(), NullableOwner::new, Set.of());
		var owner = new NullableOwner();
		owner.group = new Group();
		assertThrows(IllegalStateException.class, () -> ownerPlan.transfer(owner, new NullableOwner()));
		assertTrue(ownerPlan.fallbackReasons().stream().anyMatch(reason -> reason.contains("missing or shared subgroup")));
	}

	@Test
	public void fieldsTransferProjectsOnlyCompiledBaseFieldsIntoControlledSubclass() {
		var frozen = new CompoundTag();
		frozen.putInt("amount", 8);
		var plan = PhotonStatePlanUtil.compileFields(new ProjectionBase(), frozen, ProjectionChild::new, Set.of());
		var source = new ProjectionBase();
		source.amount = 71;
		var target = new ProjectionChild();
		plan.transfer(source, target);
		assertEquals(71, target.amount);
		assertEquals(900, target.extension);
		assertEquals(0, target.extensionSetterCalls);
		assertEquals(1, plan.assignmentCount());
		var reverse = new ProjectionBase();
		target.amount = 72;
		plan.transfer(target, reverse);
		assertEquals(72, reverse.amount);
		assertEquals(900, target.extension);
		var created = (ProjectionChild) plan.create();
		assertEquals(8, created.amount);
		assertEquals(900, created.extension);
	}

	@Test
	public void fieldsTransferRejectsWrongRootTypesBeforeWritingTarget() {
		var plan = PhotonStatePlanUtil.compileFields(new ProjectionBase(), new CompoundTag(), ProjectionChild::new, Set.of());
		var source = new ProjectionBase();
		var target = new ProjectionChild();
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(new Object(), target));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(source, new Object()));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(null, target));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(source, null));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(source, source));
		assertEquals(3, target.amount);
		assertEquals(900, target.extension);
	}

	@Test
	public void ordinaryTransferRetainsStrictCompiledRootTypeCheck() {
		var plan = PhotonStatePlanUtil.compile(new ProjectionBase(), new CompoundTag(), ProjectionBase::new);
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(new ProjectionBase(), new ProjectionChild()));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(new ProjectionChild(), new ProjectionBase()));
		assertThrows(IllegalArgumentException.class, () -> plan.transfer(new ProjectionChild(), new ProjectionChild()));
		var source = new ProjectionBase();
		source.amount = 73;
		var target = new ProjectionBase();
		plan.transfer(source, target);
		assertEquals(73, target.amount);
	}

	@Test
	public void nullableDirectFieldsHonorNativeNullMarkers() {
		if (AccessorRegistries.findByClassOrNull(Identifier.class) == null) {
			AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(Identifier.class)
					.codec(Identifier.CODEC).streamCodec(ByteBufCodecs.fromCodec(Identifier.CODEC)).build(), 100);
		}

		var source = new NullableScalars();
		var tag = (CompoundTag) PersistedParser.serialize(NbtOps.INSTANCE, source, PROVIDER).getOrThrow();
		var nativeCopy = new NullableScalars();
		PersistedParser.deserialize(NbtOps.INSTANCE, tag, nativeCopy, PROVIDER);
		var plan = PhotonStatePlanUtil.compile(source, tag, NullableScalars::new);
		var frozen = tag.copy();
		source.number = 41;
		source.resource = Identifier.parse("lobecorp:changed");
		source.text = "changed";
		tag.putInt("number", 99);
		var copy = (NullableScalars) plan.create();
		var sibling = (NullableScalars) plan.create();
		assertEquals(nativeCopy.number, copy.number);
		assertEquals(nativeCopy.resource, copy.resource);
		assertEquals(nativeCopy.text, copy.text);
		assertEquals(frozen, PersistedParser.serialize(NbtOps.INSTANCE, copy, PROVIDER).getOrThrow());
		assertNull(copy.number);
		assertNull(copy.resource);
		assertNull(copy.text);
		copy.number = 7;
		copy.resource = Identifier.parse("lobecorp:sibling_check");
		copy.text = "copy";
		assertNull(sibling.number);
		assertNull(sibling.resource);
		assertNull(sibling.text);
		assertEquals(41, source.number.intValue());
		assertEquals("changed", source.text);
		assertEquals(Identifier.parse("lobecorp:changed"), source.resource);
	}

	private static class NullableScalars {
		@Persisted
		private Integer number;
		@Persisted
		private Identifier resource;
		@Persisted
		private String text;
	}


	private static CompoundTag scalarTag() {
		var tag = new CompoundTag();
		tag.putInt("alias", 7);
		tag.putInt("other", 8);
		return tag;
	}

	private static class ProjectionBase {
		@Persisted
		protected int amount = 3;
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


	private static class Base implements IPersistedSerializable {
		@Persisted(key = "alias")
		protected int value;
		protected List<String> events = new ArrayList<>();

		@ConfigSetter(field = "value")
		private void baseSetter(int value) {
			this.value = value + 100;
			events.add("base");
		}

		@Override
		public void beforeDeserialize() {
			events.add("before");
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
		}
	}

	private static class Derived extends Base {
		@Configurable
		private int other;

		@ConfigSetter(field = "value")
		private void derivedSetter(int value) {
			this.value = value * 2;
			events.add("derived");
		}
	}

	private static class Group implements IPersistedSerializable {
		@Persisted
		private int amount;
		private List<String> events = new ArrayList<>();

		@Override
		public void beforeDeserialize() {
			events.add("before");
		}

		@Override
		public void afterDeserialize() {
			events.add("after");
		}
	}

	private static class NullableOwner {
		@Persisted(subPersisted = true)
		private Group group;
	}

	private static class Groups {
		@Persisted(subPersisted = true)
		private final Group group = new Group();
		@Configurable(subConfigurable = true, subFlattenPersisted = true)
		private final Group flat = new Group();
	}

	private static class Missing {
		@Persisted(key = "renamed")
		private int present = 3;
		@Persisted
		private int missing = 5;
	}


}
