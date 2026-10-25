package org.unitego.lobecorp.client.entity.corpse;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.constant.DataTickets;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.unitego.lobecorp.client.entity.ordeal.indigo.renderer.SweeperRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.entity.ordeal.indigo.model.SweeperModel;
import org.unitego.lobecorp.client.horde.HordeMeshCache;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

/// 明确支持的尸体模型适配器；未知渲染器保留原管线，不猜测其材质和骨骼契约。
public class FrozenCorpseMeshProvider {
	private static final HordeVisualDefinition SWEEPER = new HordeVisualDefinition(
			SweeperModel.MODEL_CORPSE, SweeperModel.ANIMATION, "run", SweeperModel.TEXTURES_CORPSE);
	private static Object generation;
	private static HordeMeshCache.Model sweeperMesh;
	private static boolean failed;

	public static Object generation() {
		Object current = GeckoLibResources.getBakedModels();
		if (generation != current) {
			generation = current;
			sweeperMesh = null;
			failed = false;
		}
		return current;
	}

	public static HordeMeshCache.@Nullable Model resolve(Entity owner, EntityRenderState state, EntityRenderer<?, ?> renderer) {
		if (renderer.getClass() != SweeperRenderer.class || !(owner instanceof Sweeper) || !(state instanceof LivingEntityRenderState living)
				|| living.isAutoSpinAttack || living.isUpsideDown || living.scale <= 0 || !Float.isFinite(living.scale)
				|| state.passengerOffset != null || state.isInvisible
				|| state.getGeckolibData(DataTickets.ENTITY_POSE) == Pose.SLEEPING
				|| state.getOrDefaultGeckolibData(DataTickets.IS_SHAKING, false)) {
			return null;
		}
		generation();
		if (failed) {
			return null;
		}
		if (sweeperMesh == null) {
			try {
				sweeperMesh = HordeMeshCache.freeze(SWEEPER);
			} catch (RuntimeException failure) {
				failed = true;
				Lobecorp.LOGGER.warn("清道夫尸体冻结模型不可用，保留原渲染路径", failure);
			}
		}
		return sweeperMesh;
	}

	public static void clear() {
		generation = null;
		sweeperMesh = null;
		failed = false;
	}
}
