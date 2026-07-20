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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.unitego.lobecorp.init.LcEntityDataSerializers;
import org.unitego.lobecorp.init.LcEntityTypes;

public class EntityCorpse<T extends LivingEntity> extends LivingEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<CompoundTag> DATA_OWNER_ENTITY_TAG = SynchedEntityData.defineId(
            EntityCorpse.class, LcEntityDataSerializers.COMPOUND_TAG.get());

    // 缓存存储实体 不使用final是因为同步等因素会导致变换
    @Nullable
    private T ownerEntity;
    @Nullable
    private AABB fluidInteractionBox;

    public EntityCorpse(EntityType<? extends EntityCorpse<?>> type, Level level) {
        super(type, level);
        reset();
    }

    public static <T extends LivingEntity> EntityCorpse<T> createCorpse(T entity) {
        EntityCorpse<T> entityCorpse = new EntityCorpse<>(LcEntityTypes.ENTITY_CORPSE.get(), entity.level());
        entityCorpse.updateOwnerEntity(entity);
        entityCorpse.absSnapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
        return entityCorpse;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        CompoundTag compoundTag = input.read("OwnerEntity", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        setOwnerEntityTag(compoundTag);
        updateOwnerEntity(createOwnerEntity(compoundTag));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("OwnerEntity", CompoundTag.CODEC, getOwnerEntityTag());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_OWNER_ENTITY_TAG, new CompoundTag());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 20 == 0) {
            if (ownerEntity == null) {
                remove(RemovalReason.DISCARDED);
            }
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
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
        if (accessor == DATA_OWNER_ENTITY_TAG) {
            updateOwnerEntity(createOwnerEntity(getOwnerEntityTag()));
        }
    }

    protected void updateOwnerEntity(@Nullable T newOwnerEntity) {
        if (newOwnerEntity == null) {
            reset();
            return;
        }
        this.ownerEntity = newOwnerEntity;
        CompoundTag tag = getEntityCompoundTag(ownerEntity);
        setOwnerEntityTag(tag);
        fluidInteractionBox = ownerEntity.getFluidInteractionBox();
        float health = (float) (newOwnerEntity.getAttributeValue(Attributes.MAX_HEALTH) / 4);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth(health);
        refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (ownerEntity != null) {
            return ownerEntity.getDimensions(pose);
        }
        return super.getDefaultDimensions(pose);
    }

    protected @NonNull CompoundTag getEntityCompoundTag(@Nullable LivingEntity entity) {
        if (entity == null) {
            return new CompoundTag();
        }

        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput tagOutput = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(tagOutput);
            tagOutput.putString("id", entity.getEncodeId());
            return tagOutput.buildResult();
        }
    }

    protected void reset() {
        ownerEntity = null;
        setOwnerEntityTag(new CompoundTag());
        fluidInteractionBox = null;
        float health = (float) (Attributes.MAX_HEALTH.value().getDefaultValue() / 4);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth(health);
        refreshDimensions();
    }

    protected void setOwnerEntityTag(CompoundTag tag) {
        entityData.set(DATA_OWNER_ENTITY_TAG, tag);
    }

    protected CompoundTag getOwnerEntityTag() {
        return entityData.get(DATA_OWNER_ENTITY_TAG);
    }

    @Nullable
    public T getOwnerEntity() {
        return ownerEntity;
    }

    @Nullable
    public T createOwnerEntity(CompoundTag tag) {
        if (tag.isEmpty()) {
            return null;
        }

        try (ProblemReporter.ScopedCollector reporterx = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            Level level = level();
            ValueInput input = TagValueInput.create(reporterx.forChild(() -> ""),
                    level.registryAccess(), tag);
            return (T) EntityType.create(input, level, EntitySpawnReason.LOAD).orElse(null);
        }
    }

    @Override
    public Component getName() {
        // TODO 应该带上原生物的名称
        return super.getName();
    }
}