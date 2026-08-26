package org.unitego.lobecorp.entity.ai.skill;

import net.minecraft.resources.Identifier;

public abstract class EntitySkill implements IEntitySkill {
    private final Identifier id;
    private final int windupTicks;
    private final int durationTicks;
    private final int recoveryTicks;
    private final int cooldownTicks;

    public EntitySkill(Properties properties) {
        this.id = properties.id;
        this.windupTicks = properties.windupTicks;
        this.durationTicks = properties.durationTicks;
        this.recoveryTicks = properties.recoveryTicks;
        this.cooldownTicks = properties.cooldownTicks;
    }

    @Override
    public final Identifier id() {
        return id;
    }

    @Override
    public final int windupTicks() {
        return windupTicks;
    }

    @Override
    public final int durationTicks() {
        return durationTicks;
    }

    @Override
    public final int recoveryTicks() {
        return recoveryTicks;
    }

    @Override
    public final int cooldownTicks() {
        return cooldownTicks;
    }

    /// 实体技能的基础属性配置。
    public static class Properties {
        private Identifier id;
        private int windupTicks;
        private int durationTicks;
        private int recoveryTicks;
        private int cooldownTicks;

        /// 设置技能唯一标识。
        public Properties id(Identifier id) {
            this.id = id;
            return this;
        }

        /// 设置技能前摇时长。
        public Properties windupTicks(int windupTicks) {
            this.windupTicks = windupTicks;
            return this;
        }

        /// 设置技能持续时长。
        public Properties durationTicks(int durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        /// 设置技能后摇时长。
        public Properties recoveryTicks(int recoveryTicks) {
            this.recoveryTicks = recoveryTicks;
            return this;
        }

        /// 设置技能冷却时长。
        public Properties cooldownTicks(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }
    }
}
