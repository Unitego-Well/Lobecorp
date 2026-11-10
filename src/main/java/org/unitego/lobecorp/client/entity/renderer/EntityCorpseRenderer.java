package org.unitego.lobecorp.client.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.registry.client.animation.LcDataTickets;
import com.geckolib.constant.DataTickets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.unitego.lobecorp.client.entity.corpse.CorpseBatchRenderer;
import org.unitego.lobecorp.client.entity.corpse.FrozenCorpseMeshProvider;
import org.unitego.lobecorp.client.horde.HordeMeshCache;
import net.minecraft.util.Mth;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;

import java.util.Map;
import java.util.WeakHashMap;

public class EntityCorpseRenderer<T extends EntityCorpse<?>> extends EntityRenderer<T, EntityCorpseRenderer.RenderState> {
	/// 控制原实体模型是否旋转为尸体姿态的渲染数据键。
	public static final ContextKey<Boolean> IS_REVERSE = new ContextKey<>(Lobecorp.id("is_reverse"));
	private static final Map<EntityCorpse<?>, Cached> FROZEN = new WeakHashMap<>();
	private static Object cacheGeneration;

	public EntityCorpseRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	public static void invalidateResourceEpoch(Object generation) {
		if (cacheGeneration != generation) {
			FROZEN.clear();
			cacheGeneration = generation;
		}
	}

	public static void forget(EntityCorpse<?> corpse) {
		FROZEN.remove(corpse);
	}

	public static void clearFrozen() {
		FROZEN.clear();
		cacheGeneration = null;
	}

	@SuppressWarnings("unchecked")
	private Cached capture(T corpse, Entity owner, float partialTick, Object generation) {
		var renderer = (EntityRenderer<Entity, EntityRenderState>) entityRenderDispatcher.getRenderer(owner);
		var ownerState = renderer.createRenderState(owner, partialTick);
		ownerState.addGeckolibData(LcDataTickets.IS_CORPSE, true);
		ownerState.addGeckolibData(DataTickets.PACKED_OVERLAY, OverlayTexture.NO_OVERLAY);
		if (ownerState instanceof LivingEntityRenderState living) {
			living.hasRedOverlay = false;
			living.deathTime = 0;
		}
		float bodyYaw = ownerState.getOrDefaultGeckolibData(DataTickets.ENTITY_BODY_YAW,
				ownerState instanceof LivingEntityRenderState living ? living.bodyRot : owner.getYRot());
		var mesh = !corpse.hasCorpseAnimation() && !corpse.isComplexModel()
				? FrozenCorpseMeshProvider.resolve(owner, ownerState, renderer) : null;
		CorpseBatchRenderer.cacheBuilt();
		return new Cached(owner, renderer, ownerState, generation, corpse.visualRevision(),
				bodyYaw, corpse.getYRot(), mesh);
	}

	@Override
	public void extractRenderState(T entity, RenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.ownerEntityRenderer = null;
		state.ownerEntityRenderState = null;
		state.frozenMesh = null;
		state.cached = null;
		state.range = entity.renderDistance();
		Entity owner = entity.getOwnerEntity();
		if (owner == null) {
			forget(entity);
			return;
		}
		Object generation = FrozenCorpseMeshProvider.generation();
		invalidateResourceEpoch(generation);
		var cached = FROZEN.get(entity);
		if (entity.hasCorpseAnimation() || cached == null || cached.owner() != owner
				|| cached.generation() != generation || cached.revision() != entity.visualRevision()) {
			cached = capture(entity, owner, entity.hasCorpseAnimation() ? partialTicks : 0, generation);
			if (!entity.hasCorpseAnimation()) {
				FROZEN.put(entity, cached);
			}
		} else {
			CorpseBatchRenderer.cacheHit();
		}
		state.cached = cached;
		state.ownerEntityRenderer = cached.renderer();
		state.ownerEntityRenderState = cached.state();
		state.frozenMesh = entity.getClass() == EntityCorpse.class ? cached.mesh() : null;
		state.resourceEpoch = generation;
		state.bodyYaw = cached.bodyYaw() + entity.getYRot(partialTicks) - cached.corpseYaw();
		state.scale = cached.state() instanceof LivingEntityRenderState living ? living.scale : 1;
		state.color = cached.state().getOrDefaultGeckolibData(DataTickets.RENDER_COLOR, -1);
	}

	@Override
	public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		var renderer = state.ownerEntityRenderer;
		var ownerState = state.ownerEntityRenderState;
		if (renderer == null || ownerState == null) {
			return;
		}
		ownerState.outlineColor = 0;
		if (state.cached != null) {
			ConductorRendering.updateEntitySelection(state.cached.owner(), ownerState);
		}
		if (!ConductorRendering.isSelected(ownerState)) {
			ownerState.outlineColor = state.outlineColor;
		}
		boolean reverse = !Boolean.FALSE.equals(state.getRenderData(IS_REVERSE));
		if (!reverse && state.frozenMesh != null && state.resourceEpoch == FrozenCorpseMeshProvider.generation()
				&& !state.isInvisible && !state.appearsGlowing() && !ownerState.appearsGlowing()
				&& state.passengerOffset == null && Double.isFinite(state.range) && state.range > 0
				&& CorpseBatchRenderer.available()) {
			var relative = camera.pos;
			if (CorpseBatchRenderer.submit(new CorpseBatchRenderer.Instance(state.frozenMesh,
					(float) (state.x - relative.x), (float) (state.y - relative.y), (float) (state.z - relative.z),
					state.bodyYaw * Mth.DEG_TO_RAD, state.lightCoords, state.color, (float) state.range, state.scale))) {
				return;
			}
		}
		CorpseBatchRenderer.fallback();
		ownerState.lightCoords = state.lightCoords;
		ownerState.distanceToCameraSq = state.distanceToCameraSq;
		ownerState.x = state.x;
		ownerState.y = state.y;
		ownerState.z = state.z;
		if (ownerState instanceof LivingEntityRenderState living) {
			living.bodyRot = reverse ? 0 : state.bodyYaw;
			living.hasRedOverlay = false;
			living.deathTime = 0;
		}
		ownerState.addGeckolibData(DataTickets.ENTITY_BODY_YAW, reverse ? 0f : state.bodyYaw);
		poseStack.pushPose();
		try {
			if (reverse) {
				poseStack.mulPose(Axis.YP.rotationDegrees(state.bodyYaw));
				poseStack.mulPose(Axis.XP.rotationDegrees(-90));
				poseStack.translate(0, -ownerState.boundingBoxHeight / 2, 0);
			}
			renderer.submit(ownerState, poseStack, collector, camera);
		} finally {
			poseStack.popPose();
		}
	}

	private record Cached(Entity owner, EntityRenderer<Entity, EntityRenderState> renderer, EntityRenderState state,
	                      Object generation, long revision, float bodyYaw, float corpseYaw,
	                      @Nullable HordeMeshCache.Model mesh) {
	}

	public static class RenderState extends EntityRenderState {
		@Nullable
		public EntityRenderer<Entity, EntityRenderState> ownerEntityRenderer;
		@Nullable
		public EntityRenderState ownerEntityRenderState;
		@Nullable
		public HordeMeshCache.Model frozenMesh;
		public Object resourceEpoch;
		public double range;
		public float bodyYaw;
		public float scale;
		public int color;
		@Nullable
		private Cached cached;
	}
}
