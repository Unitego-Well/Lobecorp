package org.unitego.lobecorp.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.unitego.lobecorp.init.LcEntityDataSerializers;
import org.unitego.lobecorp.init.LcEntityTypes;

public class EntityCorpse extends Mob {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AABB INITIAL_AABB = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static final EntityDataAccessor<CompoundTag> DATA_OWNER_ENTITY = SynchedEntityData.defineId(
            EntityCorpse.class, LcEntityDataSerializers.COMPOUND_TAG.get()
    );

    @Nullable
    public Entity ownerEntity;
    @Nullable
    private AABB fluidInteractionBox;

    public EntityCorpse(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        updateOwnerEntity(null);
    }

    public EntityCorpse(Level level, @Nullable Entity ownerEntity) {
        super(LcEntityTypes.ENTITY_CORPSE.get(), level);
        updateOwnerEntity(ownerEntity);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_OWNER_ENTITY, input.read("OwnerEntity", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (ownerEntity != null) {
            output.store("OwnerEntity", CompoundTag.CODEC, getOwnerEntityTag());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_OWNER_ENTITY, new CompoundTag());
    }

    @Override
    public @Nullable AABB getFluidInteractionBox() {
        if (fluidInteractionBox != null) {
            return fluidInteractionBox;
        }
        return super.getFluidInteractionBox();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == DATA_OWNER_ENTITY) {
            entityData.set(DATA_OWNER_ENTITY, getOwnerEntityTag());
//            updateOwnerEntity(createOwnerEntity(getOwnerEntityTag()));
        }
    }

    public void updateOwnerEntity(@Nullable Entity newOwnerEntity) {
        ownerEntity = newOwnerEntity;
        if (newOwnerEntity == null) {
            setBoundingBox(INITIAL_AABB);
            fluidInteractionBox = null;
            return;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(newOwnerEntity.problemPath(), LOGGER)) {
            TagValueOutput tagOutput = TagValueOutput.createWithContext(reporter, newOwnerEntity.registryAccess());
            newOwnerEntity.save(tagOutput);
            entityData.set(DATA_OWNER_ENTITY, tagOutput.buildResult());
            setBoundingBox(newOwnerEntity.getBoundingBox());
            fluidInteractionBox = newOwnerEntity.getFluidInteractionBox();
        }
        if (newOwnerEntity instanceof LivingEntity livingEntity) {
            setHealth((float) livingEntity.getAttributeValue(Attributes.MAX_HEALTH));
        }
    }

    public void setOwnerEntityTag(CompoundTag tag) {
        entityData.set(DATA_OWNER_ENTITY, tag);
        ownerEntity = null;
//        updateOwnerEntity(createOwnerEntity(tag));
    }

    public CompoundTag getOwnerEntityTag() {
        return entityData.get(DATA_OWNER_ENTITY);
    }

    @Nullable
    public Entity getOwnerEntity() {
        if (ownerEntity == null) {
            updateOwnerEntity(createOwnerEntity(getOwnerEntityTag()));
        }
        return ownerEntity;
    }

    @Nullable
    public Entity createOwnerEntity(CompoundTag tag) {
        if (tag.isEmpty()) {
            return null;
        }

        try (ProblemReporter.ScopedCollector reporterx = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            Level level = level();
            ValueInput input = TagValueInput.create(reporterx.forChild(() -> ""),
                    level.registryAccess(), tag);
            return EntityType.create(input, level, EntitySpawnReason.LOAD).orElse(null);
        }
    }

    @Override
    public Component getName() {
        return super.getName();
    }
}