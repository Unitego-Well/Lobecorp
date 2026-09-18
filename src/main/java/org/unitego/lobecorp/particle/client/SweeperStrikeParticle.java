package org.unitego.lobecorp.particle.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/// 清道夫打击粒子
public class SweeperStrikeParticle extends SingleQuadParticle {
	/// 刀光粒子的持续 tick 数
	private static final int LIFETIME = 8;
	/// 刀光粒子的渲染尺寸
	private static final float QUAD_SIZE = 0.5F;
	/// 刀光粒子的碰撞尺寸
	private static final float COLLISION_SIZE = 1.0F;
	private final SpriteSet sprites;

	public SweeperStrikeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprite, float roll) {
		super(level, x, y, z, sprite.first());
		this.sprites = sprite;
		this.setSpriteFromAge(sprites);
		this.lifetime = LIFETIME;
		this.quadSize = QUAD_SIZE;
		this.setSize(COLLISION_SIZE, COLLISION_SIZE);
		this.roll = roll;
		this.oRoll = roll;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float a) {
		return LightCoordsUtil.FULL_BRIGHT;
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
			return new SweeperStrikeParticle(level, x, y, z, this.sprites, random.nextFloat() * Mth.TWO_PI);
		}
	}
}
