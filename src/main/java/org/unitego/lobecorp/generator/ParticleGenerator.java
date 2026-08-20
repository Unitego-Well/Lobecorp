package org.unitego.lobecorp.generator;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.data.ParticleDescriptionProvider;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.LcParticleTypes;

public class ParticleGenerator extends ParticleDescriptionProvider {
    public ParticleGenerator(PackOutput output) {
        super(output);
    }

    @Override
    protected void addDescriptions() {
        spriteSet(LcParticleTypes.SIMPLE_SHORT_SLASH.get(), Lobecorp.id("simple/short_slash"), 6, false);
        spriteSet(LcParticleTypes.SIMPLE_LONG_SLASH.get(), Lobecorp.id("simple/long_slash"), 6, false);
        spriteSet(LcParticleTypes.SIMPLE_DOUBLE_SLASH.get(), Lobecorp.id("simple/double_slash"), 6, false);
        // TODO 纹理资源未就绪，路径/帧数待定
        spriteSet(LcParticleTypes.BLOOD.get(), Lobecorp.id("blood"), 1, false);
    }

    protected void spriteSet(DeferredHolder<ParticleType<?>, ParticleType<?>> type, int numOfTextures, boolean reverse) {
        spriteSet(type.get(), type.getId(), numOfTextures, reverse);
    }
}
