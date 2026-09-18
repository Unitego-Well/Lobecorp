package org.unitego.lobecorp.particle.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

/// 短时烟雾粒子，不受重力影响且不会产生垂直位移。
@NullMarked
public class ShortSmokeParticle extends SingleQuadParticle {
	/// 烟雾粒子的最短存活 tick 数
	private static final int MIN_LIFETIME = 20;
	/// 烟雾粒子的最长存活 tick 数
	private static final int MAX_LIFETIME = 40;
	/// 烟雾粒子每 tick 的速度保留比例
	private static final float AIR_FRICTION = 0.96F;
	/// 烟雾粒子的渲染缩放倍数
	private static final float QUAD_SCALE = 3.0F;
	/// 烟雾粒子的碰撞尺寸
	private static final float COLLISION_SIZE = 0.25F;
	/// 烟雾粒子的初始透明度
	private static final float INITIAL_ALPHA = 0.9F;

	public ShortSmokeParticle(ClientLevel level, double x, double y, double z,
	                          double xSpeed, double zSpeed, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, sprites.get(random));
		this.scale(QUAD_SCALE);
		this.setSize(COLLISION_SIZE, COLLISION_SIZE);
		this.lifetime = random.nextIntBetweenInclusive(MIN_LIFETIME, MAX_LIFETIME);
		this.friction = AIR_FRICTION;
		this.gravity = 0.0F;
		this.xd = xSpeed;
		this.yd = 0.0;
		this.zd = zSpeed;
		this.alpha = INITIAL_ALPHA;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level,
		                               double x, double y, double z,
		                               double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
			return new ShortSmokeParticle(level, x, y, z, xSpeed, zSpeed, sprites, random);
		}
	}
}
