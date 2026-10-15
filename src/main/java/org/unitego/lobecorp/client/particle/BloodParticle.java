package org.unitego.lobecorp.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/// 血粒子，带重力下落。
public class BloodParticle extends SingleQuadParticle {
	/// 血粒子的存活时间，单位为游戏 tick。
	private static final int LIFETIME = 30;
	/// 血粒子下落的重力倍率。
	private static final float GRAVITY = 1.0F;
	/// 血粒子的四边形半宽，单位为格。
	private static final float QUAD_SIZE = 0.05F;
	/// 血粒子的碰撞宽高，单位为格。
	private static final float COLLISION_SIZE = 0.025F;
	/// 血色的红通道，取值范围为 0～1。
	private static final float COLOR_RED = 0.8F;
	/// 血色的绿通道，取值范围为 0～1。
	private static final float COLOR_GREEN = 0.1F;
	/// 血色的蓝通道，取值范围为 0～1。
	private static final float COLOR_BLUE = 0.1F;
	private final SpriteSet sprites;

	public BloodParticle(ClientLevel level, double x, double y, double z, SpriteSet sprite) {
		super(level, x, y, z, sprite.first());
		this.sprites = sprite;
		this.setSpriteFromAge(sprites);
		this.lifetime = LIFETIME;
		this.gravity = GRAVITY;
		this.quadSize = QUAD_SIZE;
		this.setSize(COLLISION_SIZE, COLLISION_SIZE);
		this.setColor(COLOR_RED, COLOR_GREEN, COLOR_BLUE);
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
			var particle = new BloodParticle(level, x, y, z, this.sprites);
			particle.setParticleSpeed(xAux, yAux, zAux);
			return particle;
		}
	}
}
