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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.StarBeamSkill;

/// 一次粒子消息显示整条短暂发光星束，不参与伤害或目标追踪。
@NullMarked
public class QueenLaserParticle extends SingleQuadParticle {
	/// 光束闪光持续两刻，伤害仅在服务端发射瞬间结算。
	private static final int FLASH_TICKS = 2;
	/// laser_tail.png 纵向排列四个纹理帧，闪光使用末帧。
	private static final int TEXTURE_FRAMES = 4;
	/// 每个纹理片间距相对四边形半宽的比例，使不同宽度的光束都保持连续。
	private static final double SEGMENT_SPACING_RATIO = 0.5;
	protected Vec3 beam;

	public QueenLaserParticle(ClientLevel level, double x, double y, double z, Vec3 beam, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.beam = beam;
		this.lifetime = FLASH_TICKS;
		this.quadSize = (float) (StarBeamSkill.WIDTH / 2.0);
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
	protected float getV0() {
		return Mth.lerp((float) (TEXTURE_FRAMES - 1) / TEXTURE_FRAMES, sprite.getV0(), sprite.getV1());
	}

	@Override
	public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
		Vec3 start = new Vec3(this.x, this.y, this.z).subtract(camera.position());
		double length = beam.length();
		if (length == 0.0)
			return;
		Vec3 direction = beam.scale(1.0 / length);
		Quaternionf rotation = new Quaternionf(camera.rotation());
		for (double distance = 0.0; distance <= length; distance += quadSize * SEGMENT_SPACING_RATIO) {
			Vec3 point = start.add(direction.scale(distance));
			this.extractRotatedQuad(renderState, rotation, (float) point.x, (float) point.y, (float) point.z, partialTick);
		}
	}

	@Override
	public void tick() {
		if (++this.age >= this.lifetime)
			this.remove();
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
		                               double xAux, double yAux, double zAux, RandomSource random) {
			return new QueenLaserParticle(level, x, y, z, new Vec3(xAux, yAux, zAux), sprites);
		}
	}
}
