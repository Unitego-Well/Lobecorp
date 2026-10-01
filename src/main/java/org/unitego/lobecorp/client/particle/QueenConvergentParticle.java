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
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime.SkillState;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.ConvergentSkill;
import org.unitego.lobecorp.particle.QueenConvergentParticleOptions;
import org.unitego.lobecorp.util.EntitySkillUtil;

/// 聚爆星星与向内收拢的双面光环在提取时读取女皇的插值位置。
@NullMarked
public class QueenConvergentParticle extends SingleQuadParticle {
	/// shock_wave 纹理纵向排列的帧数。
	private static final int TEXTURE_FRAME_COUNT = 7;
	/// 成型星星相对女皇脚下的高度，单位为格。
	private static final double STAR_HEIGHT = 5.0;
	/// 光环离地高度，单位为格。
	private static final double GROUND_OFFSET = 0.05;
	private final QueenConvergentParticleOptions options;
	private int textureFrame;

	public QueenConvergentParticle(ClientLevel level, double x, double y, double z,
	                              SpriteSet sprites, QueenConvergentParticleOptions options) {
		super(level, x, y, z, sprites.get(options.star() ? 0 : 1, 1));
		this.options = options;
		this.lifetime = options.star() ? options.durationTicks() : options.pulseTicks();
		this.age = (int) Math.max(0L, level.getGameTime() - options.startGameTime());
	}

	@Nullable
	private TheQueenOfHatred owner() {
		return level.getEntity(options.entityId()) instanceof TheQueenOfHatred queen ? queen : null;
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
		float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
		return options.star() ? MagicStarProjectile.StarSize.LARGE.width() / 2.0F
				* Mth.clamp((age + partialTick) / (lifetime - options.pulseTicks()), 0.0F, 1.0F)
				: (float) ConvergentSkill.PULL_RADIUS * (1.0F - progress);
	}

	@Override
	protected float getV0() {
		return options.star() ? super.getV0() : Mth.lerp((float) textureFrame / TEXTURE_FRAME_COUNT, sprite.getV0(), sprite.getV1());
	}

	@Override
	protected float getV1() {
		return options.star() ? super.getV1() : Mth.lerp((float) (textureFrame + 1) / TEXTURE_FRAME_COUNT, sprite.getV0(), sprite.getV1());
	}

	@Override
	public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
		TheQueenOfHatred queen = owner();
		if (queen == null) return;
		float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
		double height = options.star() ? STAR_HEIGHT * (1.0 - Mth.clamp(
				(age + partialTick - (lifetime - options.pulseTicks())) / options.pulseTicks(), 0.0F, 1.0F)) : GROUND_OFFSET;
		Vec3 position = queen.getPosition(partialTick).add(0.0, height, 0.0);
		setPos(position.x, position.y, position.z);
		xo = x;
		yo = y;
		zo = z;
		if (options.star()) {
			alpha = Mth.clamp((age + partialTick) / (lifetime - options.pulseTicks()), 0.0F, 1.0F);
			super.extract(renderState, camera, partialTick);
		} else {
			textureFrame = Math.min((int) (progress * TEXTURE_FRAME_COUNT), TEXTURE_FRAME_COUNT - 1);
			extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(Mth.HALF_PI), partialTick);
			extractRotatedQuad(renderState, camera, new Quaternionf().rotationX(-Mth.HALF_PI), partialTick);
		}
	}

	@Override
	public void tick() {
		age = (int) Math.max(age + 1L, level.getGameTime() - options.startGameTime());
		TheQueenOfHatred queen = owner();
		if (age >= lifetime || queen == null || !queen.isAlive() || EntitySkillUtil.getActiveSkills(queen).stream()
				.noneMatch(runtime -> runtime.id() == options.runtimeId() && runtime.state() != SkillState.RECOVERY)) {
			remove();
			return;
		}
		setPos(queen.getX(), queen.getY() + (options.star() ? STAR_HEIGHT : GROUND_OFFSET), queen.getZ());
	}

	public static class Provider implements ParticleProvider<QueenConvergentParticleOptions> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(QueenConvergentParticleOptions options, ClientLevel level,
		                               double x, double y, double z,
		                               double xAux, double yAux, double zAux, RandomSource random) {
			return new QueenConvergentParticle(level, x, y, z, sprites, options);
		}
	}
}

