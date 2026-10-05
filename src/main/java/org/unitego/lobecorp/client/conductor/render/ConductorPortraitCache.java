package org.unitego.lobecorp.client.conductor.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.conductor.ConductorHud;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.conductor.data.ConductorDirectory;

import java.util.*;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.*;

public class ConductorPortraitCache {
	private static final String TEXTURE_LABEL = "lobecorp_conductor_portrait";
	private static final float FACING_ROTATION = 180.0F;
	private static final Map<UUID, Portrait> cards = new HashMap<>();
	private static final Map<UUID, Portrait> focus = new HashMap<>();
	private static final Map<Portrait, List<Target>> destinations = new LinkedHashMap<>();
	private static Object world;
	private static boolean staticCards;
	private static long frame;

	public static void clear() {
		cards.clear();
		focus.clear();
		destinations.clear();
		world = null;
	}

	public static void beginFrame(Object level, ConductorDirectory members, boolean reduced) {
		if (world != level)
			clear();
		world = level;
		frame++;
		staticCards = reduced;
		destinations.clear();
		cards.keySet().removeIf(id -> members.unit(id) == null && !ConductorControls.isSelected(id));
		focus.keySet().removeIf(id -> !id.equals(ConductorHud.INSTANCE.focusedMember()));
	}

	public static void card(GuiGraphicsExtractor graphics, UUID id, LivingEntity entity,
	                        int x, int y, int width, int height) {
		Portrait portrait = cards.get(id);
		var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		if (portrait != null && portrait.renderer != renderer) {
			cards.remove(id);
			portrait = null;
		}
		if (portrait == null && staticCards) {
			String name = entity.getDisplayName().getString();
			String icon = name.isEmpty() ? UNKNOWN_MARK : name.substring(0, name.offsetByCodePoints(0, 1));
			graphics.text(Minecraft.getInstance().font, Component.literal(icon), x + BORDER, y + BORDER, MUTED_COLOR, false);
		} else {
			if (portrait == null) {
				portrait = new Portrait();
				cards.put(id, portrait);
			}
			if (portrait.state == null || !staticCards && portrait.frame != frame)
				extract(portrait, entity, 0);
			queue(graphics, portrait, x, y, width, height, 1);
		}
		if (staticCards)
			graphics.fill(x + width - 1, y + height - 1, x + width, y + height, GOLD);
	}

	public static void focus(GuiGraphicsExtractor graphics, LivingEntity entity, int x, int y, int size, float angle, float scale) {
		Portrait portrait = focus.computeIfAbsent(entity.getUUID(), ignored -> new Portrait());
		extract(portrait, entity, angle);
		queue(graphics, portrait, x, y, size, size, scale);
	}

	private static void extract(Portrait portrait, LivingEntity entity, float angle) {
		var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = FACING_ROTATION + angle;
			living.yRot = 0;
			if (living.pose != Pose.FALL_FLYING)
				living.xRot = 0;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1;
		}
		portrait.state = state;
		portrait.renderer = renderer;
		portrait.frame = frame;
		portrait.revision++;
	}

	private static void queue(GuiGraphicsExtractor graphics, Portrait portrait, int x, int y, int width, int height, float multiplier) {
		float scale = Math.max(1, (int) (Math.min(width, height) * PORTRAIT_SCALE
				/ Math.max(1, portrait.state.boundingBoxHeight))) * multiplier;
		portrait.scale = destinations.containsKey(portrait) ? Math.max(portrait.scale, scale) : scale;
		destinations.computeIfAbsent(portrait, ignored -> new ArrayList<>())
				.add(new Target(x, y, width, height, graphics.peekScissorStack()));
	}

	public static void submit(GuiGraphicsExtractor graphics) {
		for (var entry : destinations.entrySet()) {
			Portrait portrait = entry.getKey();
			entry.getValue().sort((first, second) -> Integer.compare(second.width(), first.width()));
			graphics.submitPictureInPictureRenderState(new State(portrait, portrait.state, portrait.revision,
					portrait.scale, List.copyOf(entry.getValue())));
		}
	}

	public static class Portrait {
		private EntityRenderState state;
		private Object renderer;
		private long frame;
		private long revision;
		private float scale;
	}

	public record Target(int x, int y, int width, int height, @Nullable ScreenRectangle clip) {
	}

	public record State(Portrait portrait, EntityRenderState entity, long revision, float scale,
	                    List<Target> targets) implements PictureInPictureRenderState {
		@Override
		public int x0() {
			return targets.getFirst().x();
		}

		@Override
		public int y0() {
			return targets.getFirst().y();
		}

		@Override
		public int x1() {
			return x0() + targets.getFirst().width();
		}

		@Override
		public int y1() {
			return y0() + targets.getFirst().height();
		}

		@Override
		public @Nullable ScreenRectangle scissorArea() {
			return targets.size() == 1 ? targets.getFirst().clip() : null;
		}

		@Override
		public @Nullable ScreenRectangle bounds() {
			ScreenRectangle result = null;
			for (Target target : targets) {
				ScreenRectangle next = PictureInPictureRenderState.getBounds(target.x(), target.y(),
						target.x() + target.width(), target.y() + target.height(), target.clip());
				if (next == null)
					continue;
				if (result == null)
					result = next;
				else {
					int left = Math.min(result.left(), next.left());
					int top = Math.min(result.top(), next.top());
					result = new ScreenRectangle(left, top, Math.max(result.right(), next.right()) - left,
							Math.max(result.bottom(), next.bottom()) - top);
				}
			}
			return result;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof State state && portrait == state.portrait;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(portrait);
		}
	}

	public static class Renderer extends PictureInPictureRenderer<State> {
		private Portrait renderedPortrait;
		private long renderedRevision = -1;
		private float renderedScale;

		public Renderer(MultiBufferSource.BufferSource buffers) {
			super(buffers);
		}

		@Override
		public Class<State> getRenderStateClass() {
			return State.class;
		}

		@Override
		protected boolean textureIsReadyToBlit(State state) {
			return renderedPortrait == state.portrait() && renderedRevision == state.revision() && renderedScale == state.scale();
		}

		@Override
		protected void renderToTexture(State state, PoseStack pose) {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
			pose.translate(0, state.entity().boundingBoxHeight / 2 + PORTRAIT_Y_OFFSET, 0);
			pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
			var features = minecraft.gameRenderer.getFeatureRenderDispatcher();
			CameraRenderState camera = new CameraRenderState();
			camera.orientation = new Quaternionf().rotateY((float) Math.PI);
			minecraft.getEntityRenderDispatcher().submit(state.entity(), camera, 0, 0, 0, pose, features.getSubmitNodeStorage());
			features.renderAllFeatures();
			renderedPortrait = state.portrait();
			renderedRevision = state.revision();
			renderedScale = state.scale();
		}

		@Override
		protected void blitTexture(State state, GuiRenderState gui) {
			for (Target target : state.targets()) {
				super.blitTexture(new State(state.portrait(), state.entity(), state.revision(), state.scale(), List.of(target)), gui);
			}
		}

		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2.0F;
		}

		@Override
		protected String getTextureLabel() {
			return TEXTURE_LABEL;
		}
	}
}
