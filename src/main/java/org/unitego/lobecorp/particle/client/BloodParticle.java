package org.unitego.lobecorp.particle.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/// 血粒子，带重力下落。
public class BloodParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    public BloodParticle(ClientLevel level, double x, double y, double z, SpriteSet sprite) {
        super(level, x, y, z, sprite.first());
        this.sprites = sprite;
        this.setSpriteFromAge(sprites);
        // TODO 未指定的参数（颜色/寿命/尺寸）后续调整
        this.lifetime = 30;
        this.gravity = 1.0F;
        this.quadSize = 0.2F;
        this.setSize(0.1F, 0.1F);
        this.setColor(0.8F, 0.1F, 0.1F);
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        this.setSpriteFromAge(this.sprites);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z,
                                       double xAux, double yAux, double zAux, RandomSource random) {
            BloodParticle particle = new BloodParticle(level, x, y, z, this.sprites);
            particle.setParticleSpeed(xAux, yAux, zAux);
            return particle;
        }
    }
}
