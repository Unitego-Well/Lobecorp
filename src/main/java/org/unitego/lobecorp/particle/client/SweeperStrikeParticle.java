package org.unitego.lobecorp.particle.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

// TODO 要引入旋转
/// 清道夫打击粒子
public class SweeperStrikeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    public SweeperStrikeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprite) {
        super(level, x, y, z, sprite.first());
        this.sprites = sprite;
        this.setSpriteFromAge(sprites);
        this.lifetime = 8;
        this.quadSize = 0.5F;
        this.setSize(1.0F, 1.0F);
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public int getLightCoords(float a) {
        return 15728880;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z,
                                       double xAux, double yAux, double zAux, RandomSource random) {
            // TODO 要引入旋转 将 xAux yAux zAux 改成对应的旋转角度
            return new SweeperStrikeParticle(level, x, y, z, this.sprites);
        }
    }
}
