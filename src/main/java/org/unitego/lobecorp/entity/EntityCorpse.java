package org.unitego.lobecorp.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.unitego.lobecorp.init.LcEntityDataSerializers;
import org.unitego.lobecorp.init.LcEntityTypes;

public class EntityCorpse<T extends Entity> extends LivingEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<CompoundTag> DATA_OWNER_ENTITY_TAG = SynchedEntityData.defineId(
            EntityCorpse.class, LcEntityDataSerializers.COMPOUND_TAG.get());

    // 缓存存储实体 不使用final是因为同步等因素会导致变换
    @Nullable
    private T ownerEntity;
    @Nullable
    private EntityDimensions cachedDimensions;

    public EntityCorpse(EntityType<? extends EntityCorpse<?>> type, Level level) {
        super(type, level);
        reset();
    }

    public static <T extends Entity> EntityCorpse<T> createCorpse(T entity) {
        EntityCorpse<T> entityCorpse = new EntityCorpse<>(LcEntityTypes.ENTITY_CORPSE.get(), entity.level());
        entityCorpse.updateOwnerEntity(entity);
        entityCorpse.setOwnerEntityTag(entityCorpse.getEntityCompoundTag(entity));
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
        if (this.isInWater()) {
            this.setUnderwaterMovement();
        } else if (this.isInLava()) {
            this.setUnderLavaMovement();
        } else {
            this.applyGravity();
        }
    }

    private void setUnderwaterMovement() {
        Vec3 movement = this.getDeltaMovement();
        double newY = movement.y + (movement.y < 0.06F ? 0.015 : 0.0);
        this.setDeltaMovement(movement.x * 0.99, Math.min(newY, 0.1), movement.z * 0.99);
    }

    private void setUnderLavaMovement() {
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x * 0.95, movement.y + 0.015, movement.z * 0.95);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)
            || source.is(DamageTypes.DROWN)
            || source.is(DamageTypes.IN_WALL)
            || source.is(DamageTypes.MAGIC)
            || source.is(DamageTypes.INDIRECT_MAGIC)
            || source.is(DamageTypes.WITHER)
            || source.is(DamageTypes.WITHER_SKULL)
            || source.is(DamageTypes.HOT_FLOOR)
            || source.is(DamageTypes.CACTUS)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
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
        ownerEntity.setOldPosAndRot();
        if (newOwnerEntity instanceof LivingEntity livingEntity) {
            livingEntity.yBodyRotO = livingEntity.yBodyRot;
            livingEntity.yHeadRotO = livingEntity.yHeadRot;
            float health = (float) (livingEntity.getAttributeValue(Attributes.MAX_HEALTH) / 4);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            setHealth(health);
        } else {
            float health = (float) (Attributes.MAX_HEALTH.value().getDefaultValue() / 4);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            setHealth(health);
        }
        updateCachedDimensions();
        refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (cachedDimensions != null) {
            return cachedDimensions;
        }
        return super.getDefaultDimensions(pose);
    }

    protected void updateCachedDimensions() {
        if (ownerEntity == null) {
            cachedDimensions = null;
            return;
        }
        EntityDimensions original = ownerEntity.getDimensions(Pose.STANDING);
        float newHeight = original.width() / 2.0F;
        float newWidth = original.height();
        cachedDimensions = EntityDimensions.fixed(newWidth, newHeight);
    }

    protected @NonNull CompoundTag getEntityCompoundTag(@Nullable T entity) {
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
        cachedDimensions = null;
        setOwnerEntityTag(new CompoundTag());
        float health = (float) (Attributes.MAX_HEALTH.value().getDefaultValue() / 4);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth(health);
        refreshDimensions();
    }

    @Override
    protected void tickDeath() {
        deathTime = 20;
        super.tickDeath();
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
    public Component getDisplayName() {
        if (ownerEntity != null) {
            return Component.translatable("entity.entity_corpse.display_name", ownerEntity.getDisplayName());
        }
        return super.getDisplayName();
    }
}