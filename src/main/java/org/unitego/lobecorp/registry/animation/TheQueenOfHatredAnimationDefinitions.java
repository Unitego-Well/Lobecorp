package org.unitego.lobecorp.registry.animation;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.definition.LcAnimationDefinition;
import org.unitego.lobecorp.animation.pose.LcSkeletalPose;
import org.unitego.lobecorp.animation.runtime.LcAnimationContext;
import org.unitego.lobecorp.attachment.LcTransform;

import java.util.Set;

public class TheQueenOfHatredAnimationDefinitions {
	public static final Identifier LASER = Lobecorp.id("the_queen_of_hatred/laser");
	public static final Identifier LASER_PHASE_TWO = Lobecorp.id("the_queen_of_hatred/laser_phase_two");
	public static final Identifier STAR_BEAM = Lobecorp.id("the_queen_of_hatred/star_beam");
	public static final Identifier STAR_BEAM_PHASE_TWO = Lobecorp.id("the_queen_of_hatred/star_beam_phase_two");
	private static final Identifier RESOURCE = Lobecorp.id("entity/the_queen_of_hatred");
	private static final Identifier LASER_EFFECT = Lobecorp.id("the_queen_of_hatred/laser_spell");
	private static final Identifier STAR_EFFECT = Lobecorp.id("the_queen_of_hatred/star_beam_spell");
	public static final String UPPER_BODY = "up_body";
	private static final String AIM_PITCH = "q.head_x_rotation";
	private static final Set<Identifier> BEAMS = Set.of(LASER, LASER_PHASE_TWO, STAR_BEAM, STAR_BEAM_PHASE_TWO);

	public static void init() {
		LcAnimationDefinitions.register(LASER, () -> create("aim", LASER_EFFECT));
		LcAnimationDefinitions.register(LASER_PHASE_TWO, () -> create("aim2", LASER_EFFECT));
		LcAnimationDefinitions.register(STAR_BEAM, () -> create("aim", STAR_EFFECT));
		LcAnimationDefinitions.register(STAR_BEAM_PHASE_TWO, () -> create("aim2", STAR_EFFECT));
	}

	public static boolean isBeam(Identifier id) {
		return BEAMS.contains(id);
	}

	public static boolean isStarBeam(Identifier id) {
		return id.equals(STAR_BEAM) || id.equals(STAR_BEAM_PHASE_TWO);
	}

	private static LcAnimationDefinition create(String animation, Identifier effect) {
		return LcAnimationDefinition.wrap(RESOURCE, RESOURCE, animation)
				.modifyPose(Set.of(UPPER_BODY), TheQueenOfHatredAnimationDefinitions::aim)
				.effect(effect).build();
	}

	private static LcSkeletalPose aim(LcAnimationContext context, LcSkeletalPose pose) {
		return aimPose(pose, (float) context.get(AIM_PITCH));
	}

	/// 服务端姿态和客户端逐帧显示共享纯俯仰计算，不在客户端执行动画行为回调。
	public static LcSkeletalPose aimPose(LcSkeletalPose pose, float pitchDegrees) {
		var body = pose.get(UPPER_BODY);
		// 先绕实体本地 X 俯仰，再叠加资源中的侧身姿态，持杖不会沿侧身后的斜轴偏转。
		var rotation = new Quaternionf().rotationX(pitchDegrees * Mth.DEG_TO_RAD)
				.mul(body.rotation());
		return pose.withBone(UPPER_BODY, new LcTransform(body.position(), rotation, body.scale()));
	}
}
