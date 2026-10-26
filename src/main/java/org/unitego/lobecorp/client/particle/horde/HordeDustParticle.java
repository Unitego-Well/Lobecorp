package org.unitego.lobecorp.client.particle.horde;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// 仅浪潮使用飞扑短烟纹理，运动、寿命和淡出保留原版篝火烟雾规则。
public class HordeDustParticle extends CampfireSmokeParticle {
	private static @Nullable SpriteSet sprites;

	private HordeDustParticle(ClientLevel level, Vec3 point, SpriteSet spriteSet) {
		super(level, point.x, point.y, point.z, 0, 0.03, 0, false, spriteSet.get(level.getRandom()));
		this.setAlpha(0.9F);
	}

	public static void bindSprites(SpriteSet spriteSet) {
		// 保留随资源重载原位更新的集合，不缓存旧图集中的单张纹理。
		sprites = spriteSet;
	}

	public static @Nullable HordeDustParticle create(ClientLevel level, Vec3 point) {
		var spriteSet = sprites;
		return spriteSet == null ? null : new HordeDustParticle(level, point, spriteSet);
	}
}
