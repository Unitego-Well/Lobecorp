package org.unitego.lobecorp.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.particle.QueenMagicCircleParticleOptions;

/// 迟缓、星陨区域和光柱共用的固定落点、发光双面法阵。
@NullMarked
public class QueenMagicCircleParticle extends SingleQuadParticle {
	/// 法阵离地偏移，避免与方块表面重叠，单位为格。
	private static final double GROUND_OFFSET = 0.02;
	/// 光柱纹理片间距为半径的四分之一。
	private static final double PILLAR_SPACING_RATIO = 0.25;
	private final QueenMagicCircleParticleOptions options;

	public QueenMagicCircleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
	                                QueenMagicCircleParticleOptions options) {
		super(level, x, y + GROUND_OFFSET, z, sprites.first());
		this.options = options;
		this.lifetime = options.durationTicks();
		this.quadSize = (float) options.radius();
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
	public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
		if (options.radius() <= 0.0) return;
		alpha = Mth.clamp(1.0F - (age + partialTick) / lifetime, 0.0F, 1.0F);
		if (!options.pillar()) {
			extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(Mth.HALF_PI), partialTick);
			extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(-Mth.HALF_PI), partialTick);
			return;
		}
		Vec3 start = new Vec3(x, y, z).subtract(camera.position());
		for (double height = -options.radius(); height <= options.radius(); height += options.radius() * PILLAR_SPACING_RATIO) {
			extractRotatedQuad(renderState, new Quaternionf(camera.rotation()),
					(float) start.x, (float) (start.y + height), (float) start.z, partialTick);
		}
	}

	@Override
	public void tick() {
		if (++age >= lifetime) remove();
	}

	public static class Provider implements ParticleProvider<QueenMagicCircleParticleOptions> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(QueenMagicCircleParticleOptions options, ClientLevel level,
		                               double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random) {
			return new QueenMagicCircleParticle(level, x, y, z, sprites, options);
		}
	}
}
