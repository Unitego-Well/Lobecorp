package org.unitego.lobecorp.client.hud.event;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.particle.screen.ScreenTextContent;
import org.unitego.lobecorp.client.particle.text.TextGlyphMesh;

import java.util.ArrayList;
import java.util.List;

/// 翻译后的真实字形局部材质；UV2携带本字退场进度与局部坐标，不更改原字体纹理。
@NullMarked
public class CinematicText {

	private static final float TAIL_PADDING = 0.28F;
	private static final int STRIDE = 5, PROGRESS_STEPS = 4095;
	private static final RenderPipeline TEXT = pipeline(false), INTENSITY = pipeline(true);
	private final Component text;
	private final int color;
	private List<Piece> pieces = List.of();
	private @Nullable Font font;
	private @Nullable Language language;
	private long epoch = -1;
	private boolean unicode, japanese;
	private float width, height;

	public CinematicText(String key, int color) {
		text = Component.translatable(key);
		this.color = color;
	}

	private static RenderPipeline pipeline(boolean intensity) {
		var builder = RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)
				.withLocation(Lobecorp.id(intensity ? "pipeline/cinematic_text_intensity" : "pipeline/cinematic_text"))
				.withVertexShader(Lobecorp.id("core/cinematic_text"))
				.withFragmentShader(Lobecorp.id("core/cinematic_text"))
				.withSampler("Sampler0");
		if (intensity) {
			builder.withShaderDefine("INTENSITY");
		}
		return builder.build();
	}

	public void extract(GuiGraphicsExtractor graphics, float x, float y, float scale, float available,
	                    float opacity, float dissolve) {
		prepare();
		if (opacity <= 0 || dissolve >= 1 || pieces.isEmpty()) {
			return;
		}

		float fitted = Math.min(scale, available / Math.max(1, width));
		var pose = new Matrix3x2f(graphics.pose()).translate(x, y).scale(fitted);
		var scissor = graphics.peekScissorStack();
		int halfWidth = (int) Math.ceil(width / 2 + 2);
		int halfHeight = (int) Math.ceil(height / 2 + height * TAIL_PADDING + 2);
		var fullBounds = new ScreenRectangle(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2).transformMaxBounds(pose);
		var bounds = scissor == null ? fullBounds : fullBounds.intersection(scissor);
		if (bounds == null) {
			return;
		}

		for (var piece : pieces) {
			float delay = pieces.size() <= 1 ? 0 : piece.order() / (float) (pieces.size() - 1) * 0.24F;
			float progress = dissolve <= 0 ? 0 : Math.clamp((dissolve - delay) / 0.76F, 0, 1);
			graphics.submitGuiElementRenderState(new GlyphState(pose, piece, opacity, progress, scissor, bounds));
		}
	}

	private void prepare() {
		var minecraft = Minecraft.getInstance();
		boolean currentUnicode = minecraft.options.forceUnicodeFont().get();
		boolean currentJapanese = minecraft.options.japaneseGlyphVariants().get();
		long currentEpoch = ScreenTextContent.layoutEpoch();
		if (font == minecraft.font && language == Language.getInstance() && epoch == currentEpoch
				&& unicode == currentUnicode && japanese == currentJapanese) {
			return;
		}

		font = minecraft.font;
		language = Language.getInstance();
		epoch = currentEpoch;
		unicode = currentUnicode;
		japanese = currentJapanese;
		var captured = new ArrayList<Piece>();
		var lines = font.split(text, Integer.MAX_VALUE);
		height = lines.size() * font.lineHeight;
		width = 0;
		float top = -height / 2;
		for (var line : lines) {
			float lineWidth = font.width(line);
			width = Math.max(width, lineWidth);
			font.prepareText(line, -lineWidth / 2, top, 0xFF000000 | color, false, false, 0)
					.visit(new Font.GlyphVisitor() {
						@Override
						public void acceptGlyph(TextRenderable.Styled glyph) {
							capture(glyph, captured);
						}

						@Override
						public void acceptEffect(TextRenderable effect) {
							capture(effect, captured);
						}
					});
			top += font.lineHeight;
		}
		pieces = List.copyOf(captured);
	}

	private static void capture(TextRenderable source, List<Piece> target) {
		var capture = new TextGlyphMesh.Capture();
		source.render(new Matrix4f(), capture, 0xF000F0, true);
		var mesh = capture.finish(() -> source.renderType(Font.DisplayMode.NORMAL, false), 0, 0);
		if (mesh.colors().length == 0) {
			return;
		}

		float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
		float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < mesh.vertices().length; i += STRIDE) {
			left = Math.min(left, mesh.vertices()[i]);
			right = Math.max(right, mesh.vertices()[i]);
			top = Math.min(top, mesh.vertices()[i + 1]);
			bottom = Math.max(bottom, mesh.vertices()[i + 1]);
		}
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var texture = TextureSetup.singleTexture(source.textureView(), sampler);
		boolean intensity = source.guiPipeline() == RenderPipelines.GUI_TEXT_INTENSITY;
		target.add(new Piece(mesh, texture, intensity, target.size(), left, top,
				Math.max(0.01F, right - left), Math.max(0.01F, bottom - top)));
	}

	private record Piece(TextGlyphMesh mesh, TextureSetup texture, boolean intensity, int order,
	                     float left, float top, float width, float height) {
	}

	private record GlyphState(Matrix3x2fc pose, Piece piece, float opacity, float dissolve,
	                          @Nullable ScreenRectangle scissorArea,
	                          ScreenRectangle bounds) implements GuiElementRenderState {

		@Override
		public RenderPipeline pipeline() {
			return piece.intensity() ? INTENSITY : TEXT;
		}

		@Override
		public TextureSetup textureSetup() {
			return piece.texture();
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			var vertices = piece.mesh().vertices();
			var colors = piece.mesh().colors();
			int progress = Math.round(dissolve * PROGRESS_STEPS) | (piece.order() & 7) << 12;
			for (int index = 0; index < colors.length; index++) {
				int offset = index * STRIDE;
				float x = vertices[offset], y = vertices[offset + 1];
				float localX = (x - piece.left()) / piece.width();
				float localY = (y - piece.top()) / piece.height();
				float v = vertices[offset + 4];
				if (localY > 0.99F) {
					// 只延长字形下沿的局部四边形，流淌不会采样到相邻图集字形。
					int quad = index / 4 * 4;
					float minV = Float.POSITIVE_INFINITY;
					for (int corner = quad; corner < Math.min(quad + 4, colors.length); corner++) {
						minV = Math.min(minV, vertices[corner * STRIDE + 4]);
					}
					v += (v - minV) * TAIL_PADDING;
					y += piece.height() * TAIL_PADDING;
					localY += TAIL_PADDING;
				}

				int local = Math.round(Math.clamp(localX, 0, 1) * 255)
						| Math.round(Math.clamp(localY, 0, 1.5F) * 160) << 8;
				int color = colors[index];
				int alpha = Math.round((color >>> 24) * opacity);
				buffer.addVertex(pose.m00() * x + pose.m10() * y + pose.m20(),
								pose.m01() * x + pose.m11() * y + pose.m21(), 0)
						.setColor(color & 0xFFFFFF | alpha << 24)
						.setUv(vertices[offset + 3], v).setUv2(progress, local);
			}
		}
	}
}
