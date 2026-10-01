package org.unitego.lobecorp.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RepelSkill.RepelWaveEffect;

/// 女皇退散冲击波的水平双面发光纹理，独立播放并在扩张结束后淡出。
@NullMarked
public class QueenRepelWaveParticle extends SingleQuadParticle {
	/// shock_wave.png 为 32×224 像素，纵向排列 7 个正方形帧。
	private static final int TEXTURE_FRAME_COUNT = 7;
	/// 纹理距施放位置的高度偏移，单位为格，避免与地面重叠。
	private static final double GROUND_OFFSET = 0.05;
	private int textureFrame;

	public QueenRepelWaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
		super(level, x, y + GROUND_OFFSET, z, sprites.first());
		this.lifetime = RepelWaveEffect.durationTicks();
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	public float getQuadSize(float partialTick) {
		return (float) RepelWaveEffect.radiusAtTick(this.age + partialTick + 1);
	}

	@Override
	protected float getV0() {
		return Mth.lerp((float) textureFrame / TEXTURE_FRAME_COUNT, sprite.getV0(), sprite.getV1());
	}

	@Override
	protected float getV1() {
		return Mth.lerp((float) (textureFrame + 1) / TEXTURE_FRAME_COUNT, sprite.getV0(), sprite.getV1());
	}

	@Override
	public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
		float tick = Math.min(this.age + partialTick + 1, this.lifetime);
		textureFrame = Math.min((int) ((tick - 1) * TEXTURE_FRAME_COUNT / this.lifetime), TEXTURE_FRAME_COUNT - 1);
		float fadeStart = RepelWaveEffect.maximumRadiusTick();
		this.alpha = 1.0F - Mth.clamp((tick - fadeStart) / (this.lifetime - fadeStart), 0.0F, 1.0F);
		this.extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(Mth.HALF_PI), partialTick);
		this.extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(-Mth.HALF_PI), partialTick);
	}

	@Override
	public void tick() {
		if (++this.age >= this.lifetime) {
			this.remove();
		}
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level,
		                               double x, double y, double z,
		                               double xAux, double yAux, double zAux, RandomSource random) {
			return new QueenRepelWaveParticle(level, x, y, z, sprites);
		}
	}
}
