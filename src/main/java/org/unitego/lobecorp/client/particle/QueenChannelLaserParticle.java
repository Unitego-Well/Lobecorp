package org.unitego.lobecorp.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.LaserSkill;
import org.unitego.lobecorp.particle.QueenChannelLaserParticleOptions;
import org.unitego.lobecorp.util.EntitySkillUtil;

/// 持续光束每次提取都读取女皇当前法杖和方向，取消、后摇、死亡或移除即停止显示。
@NullMarked
public class QueenChannelLaserParticle extends QueenLaserParticle {
	private final QueenChannelLaserParticleOptions options;

	public QueenChannelLaserParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
	                                 QueenChannelLaserParticleOptions options) {
		super(level, x, y, z, Vec3.ZERO, sprites);
		this.options = options;
		this.lifetime = options.durationTicks();
		this.quadSize = (float) (LaserSkill.WIDTH / 2.0);
	}

	@Nullable
	private TheQueenOfHatred owner() {
		return level.getEntity(options.entityId()) instanceof TheQueenOfHatred queen ? queen : null;
	}

	@Override
	public void tick() {
		age = (int) Math.max(age + 1L, level.getGameTime() - options.startGameTime());
		TheQueenOfHatred queen = owner();
		if (age >= lifetime || queen == null || !queen.isAlive() || EntitySkillUtil.getActiveSkills(queen).stream()
				.noneMatch(runtime -> runtime.id() == options.runtimeId() && runtime.state() == EntitySkillRuntime.SkillState.ACTIVE)) {
			remove();
			return;
		}
		Vec3 start = LaserSkill.origin(queen);
		setPos(start.x, start.y, start.z);
	}

	@Override
	public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
		TheQueenOfHatred queen = owner();
		if (queen == null) return;
		Vec3 start = LaserSkill.origin(queen);
		setPos(start.x, start.y, start.z);
		beam = LaserSkill.endpoint(queen, start.add(queen.getLookAngle())).subtract(start);
		super.extract(renderState, camera, partialTick);
	}

	public static class Provider implements ParticleProvider<QueenChannelLaserParticleOptions> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(QueenChannelLaserParticleOptions options, ClientLevel level,
		                               double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random) {
			return new QueenChannelLaserParticle(level, x, y, z, sprites, options);
		}
	}
}

