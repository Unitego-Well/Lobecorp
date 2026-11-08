package org.unitego.lobecorp.client.particle;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.unitego.lobecorp.Lobecorp;
import org.jspecify.annotations.NullMarked;

/// 事件地面烟雾独立交接生命周期，不改变其它短烟雾的大小或透明度。
@NullMarked
public class HordeGroundSmokeParticle extends ShortSmokeParticle {
	private static final int MIN_LIFETIME = 64, LIFETIME_VARIATION = 17;
	private static final float GROUND_SIZE = 0.8F, OPACITY = 1F;
	/// 出生时固定的个体尺寸倍率，不参与每刻呼吸缩放。
	private static final float MIN_SIZE_MULTIPLIER = 0.8F, SIZE_MULTIPLIER_RANGE = 0.4F;
	private static final RenderPipeline GROUND_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
			.withLocation(Lobecorp.id("pipeline/horde_ground_smoke"))
			.withFragmentShader(Lobecorp.id("core/horde_ground_smoke"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
	private static final Layer GROUND_LAYER = new Layer(true, Layer.TRANSLUCENT.textureAtlasLocation(), GROUND_PIPELINE);
	private final SpriteSet sprites;

	public HordeGroundSmokeParticle(ClientLevel level, double x, double y, double z,
	                                double xSpeed, double zSpeed, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, xSpeed, zSpeed, sprites, random);
		this.sprites = sprites;
		lifetime = MIN_LIFETIME + random.nextInt(LIFETIME_VARIATION);
		quadSize = GROUND_SIZE * (MIN_SIZE_MULTIPLIER + random.nextFloat() * SIZE_MULTIPLIER_RANGE);
		alpha = 0;
		setColor(0.42F, 0.43F, 0.46F);
	}

	public static boolean usesPipeline(RenderPipeline pipeline) {
		return pipeline == GROUND_PIPELINE;
	}

	@Override
	protected Layer getLayer() {
		return GROUND_LAYER;
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
