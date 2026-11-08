package org.unitego.lobecorp.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/// 事件地面烟雾独立交接生命周期，不改变其它短烟雾的大小或透明度。
public class HordeGroundSmokeParticle extends ShortSmokeParticle {
	private static final int MIN_LIFETIME = 64, LIFETIME_VARIATION = 17;
	private static final float GROUND_SIZE = 2.4F, OPACITY = 0.22F;
	private final SpriteSet sprites;

	public HordeGroundSmokeParticle(ClientLevel level, double x, double y, double z,
	                                double xSpeed, double zSpeed, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, xSpeed, zSpeed, sprites, random);
		this.sprites = sprites;
		lifetime = MIN_LIFETIME + random.nextInt(LIFETIME_VARIATION);
		quadSize = GROUND_SIZE * (0.9F + random.nextFloat() * 0.2F);
		alpha = 0;
		setColor(0.42F, 0.43F, 0.46F);
	}

	@Override
	public void tick() {
		super.tick();
		float enter = Math.clamp(age / 8F, 0, 1);
		float leave = Math.clamp((lifetime - age) / 20F, 0, 1);
		alpha = OPACITY * enter * leave;
		setSpriteFromAge(sprites);
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
		                               double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
			return new HordeGroundSmokeParticle(level, x, y, z, xSpeed, zSpeed, sprites, random);
		}
	}
}
