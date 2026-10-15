package org.unitego.lobecorp.client.particle.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.particle.text.TextGlyphMesh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 普通文本跨帧复用字形和轮廓顶点，提交时只冻结二维矩阵与透明度。
@NullMarked
public class ScreenTextContent implements IScreenParticleContent {

	private static final int DEFAULT_TEXT_COLOR = -1;
	private static final int VERTEX_STRIDE = 5;
	/// GUI 字体仍使用原版完整亮度的光照坐标。
	private static final int FULL_BRIGHT = 0xF000F0;
	private static long reloadEpoch;
	private final Component text;
	private final int outlineColor;
	private List<Piece> pieces = List.of();
	private List<Batch> batches = List.of();
	private @Nullable Font cachedFont;
	private @Nullable Language language;
	private boolean unicode;
	private boolean japanese;
	private boolean obfuscated;
	private long epoch = -1;
	private long preparedTick = Long.MIN_VALUE;
	private float width;
	private float height;

	public ScreenTextContent(Component text, int outlineColor) {
		var level = Minecraft.getInstance().level;
		var ops = level == null ? NbtOps.INSTANCE : level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ComponentSerialization.CODEC.encodeStart(ops, Objects.requireNonNull(text)).getOrThrow();
		this.text = ComponentSerialization.CODEC.parse(ops, encoded).getOrThrow();
		this.outlineColor = outlineColor;
	}

	public static void invalidateLayouts() {
		reloadEpoch++;
	}

	@Override
	public float width() {
		ensurePrepared();
		return width;
	}

	@Override
	public float height() {
		ensurePrepared();
		return height;
	}

	@Override
	public void extract(GuiGraphicsExtractor graphics, float opacity) {
		ensurePrepared();
		if (!(opacity > 0) || batches.isEmpty()) {
			return;
		}

		refreshMaterials();
		var pose = new Matrix3x2f(graphics.pose());
		var scissor = graphics.peekScissorStack();
		int halfWidth = (int) Math.ceil(width / 2);
		int halfHeight = (int) Math.ceil(height / 2);
		var bounds = new ScreenRectangle(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = bounds.intersection(scissor);
		}

		if (bounds == null) {
			return;
		}

		for (var batch : batches) {
			graphics.submitGuiElementRenderState(new MeshState(pose, batch, Math.min(opacity, 1), scissor, bounds));
		}
	}

	private void ensurePrepared() {
		var minecraft = Minecraft.getInstance();
		var currentLanguage = Language.getInstance();
		boolean currentUnicode = minecraft.options.forceUnicodeFont().get();
		boolean currentJapanese = minecraft.options.japaneseGlyphVariants().get();
		long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
		if (epoch == reloadEpoch && cachedFont == minecraft.font && language == currentLanguage
				&& unicode == currentUnicode && japanese == currentJapanese && (!obfuscated || preparedTick == tick)) {
			return;
		}

		cachedFont = minecraft.font;
		language = currentLanguage;
		unicode = currentUnicode;
		japanese = currentJapanese;
		epoch = reloadEpoch;
		preparedTick = tick;
		prepare(cachedFont);
	}

	private void prepare(Font font) {
		var preparedPieces = new ArrayList<Piece>();
		var lines = font.split(text, Integer.MAX_VALUE);
		float lineTop = -lines.size() * font.lineHeight / 2F;
		float logicalWidth = 0;
		obfuscated = false;
		for (var line : lines) {
			float lineWidth = font.width(line);
			logicalWidth = Math.max(logicalWidth, lineWidth);
			var prepared = font.prepareText(line, -lineWidth / 2, lineTop, DEFAULT_TEXT_COLOR, false, false, 0);
			prepared.visit(new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(TextRenderable.Styled glyph) {
					obfuscated |= glyph.style().isObfuscated();
					var mesh = capture(glyph);
					if (outlineColor != 0) {
						preparedPieces.add(new Piece(glyph, mesh.createOutline(
								() -> glyph.renderType(Font.DisplayMode.NORMAL, false), outlineColor), true));
					}

					preparedPieces.add(new Piece(glyph, mesh, false));
				}

				@Override
				public void acceptEffect(TextRenderable effect) {
					preparedPieces.add(new Piece(effect, capture(effect), false));
				}
			});
			lineTop += font.lineHeight;
		}

		width = logicalWidth;
		height = lines.size() * font.lineHeight;
		for (var piece : preparedPieces) {
			var vertices = piece.mesh().vertices();
			for (int offset = 0; offset < vertices.length; offset += VERTEX_STRIDE) {
				width = Math.max(width, Math.abs(vertices[offset]) * 2);
				height = Math.max(height, Math.abs(vertices[offset + 1]) * 2);
			}
		}

		pieces = List.copyOf(preparedPieces);
		createBatches();
	}

	private TextGlyphMesh capture(TextRenderable renderable) {
		var capture = new TextGlyphMesh.Capture();
		renderable.render(new Matrix4f(), capture, FULL_BRIGHT, true);
		return capture.finish(() -> renderable.renderType(Font.DisplayMode.NORMAL, false), 0, 0);
	}

	/// 字体拥有纹理视图，这里只比较借用引用，不能关闭。
	@SuppressWarnings("resource")
	private void refreshMaterials() {
		for (var batch : batches) {
			for (var piece : batch.pieces()) {
				if (piece.source().guiPipeline() != batch.material().pipeline()
						|| piece.source().textureView() != batch.material().texture()) {
					createBatches();
					return;
				}
			}
		}
	}

	private void createBatches() {
		Map<Material, List<Piece>> grouped = new LinkedHashMap<>();
		for (var piece : pieces) {
			var material = new Material(piece.source().guiPipeline(), piece.source().textureView());
			grouped.computeIfAbsent(material, _ -> new ArrayList<>()).add(piece);
		}

		var preparedBatches = new ArrayList<Batch>();
		if (grouped.size() == 1) {
			var entry = grouped.entrySet().iterator().next();
			var ordered = new ArrayList<Piece>();
			appendPieces(ordered, entry.getValue(), true);
			appendPieces(ordered, entry.getValue(), false);
			preparedBatches.add(createBatch(entry.getKey(), ordered));
		} else {
			for (boolean outline : new boolean[]{true, false}) {
				for (var entry : grouped.entrySet()) {
					var selected = new ArrayList<Piece>();
					appendPieces(selected, entry.getValue(), outline);
					if (!selected.isEmpty()) {
						preparedBatches.add(createBatch(entry.getKey(), selected));
					}
				}
			}
		}

		batches = List.copyOf(preparedBatches);
	}

	private void appendPieces(List<Piece> target, List<Piece> source, boolean outline) {
		for (var piece : source) {
			if (piece.outline() == outline) {
				target.add(piece);
			}
		}
	}

	private Batch createBatch(Material material, List<Piece> selected) {
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		return new Batch(material, TextureSetup.singleTextureWithLightmap(material.texture(), sampler), List.copyOf(selected));
	}

	private record Piece(TextRenderable source, TextGlyphMesh mesh, boolean outline) {
	}

	private record Material(RenderPipeline pipeline, GpuTextureView texture) {
	}

	private record Batch(Material material, TextureSetup texture, List<Piece> pieces) {
	}

	private record MeshState(Matrix3x2fc pose, Batch batch, float opacity, @Nullable ScreenRectangle scissorArea,
	                         ScreenRectangle bounds) implements GuiElementRenderState {

		@Override
		public RenderPipeline pipeline() {
			return batch.material().pipeline();
		}

		@Override
		public TextureSetup textureSetup() {
			return batch.texture();
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			for (var piece : batch.pieces()) {
				var mesh = piece.mesh();
				var vertices = mesh.vertices();
				var colors = mesh.colors();
				for (int index = 0; index < colors.length; index++) {
					int offset = index * VERTEX_STRIDE;
					float x = vertices[offset];
					float y = vertices[offset + 1];
					int color = colors[index];
					int alpha = Math.round((color >>> 24) * opacity);
					buffer.addVertex(pose.m00() * x + pose.m10() * y + pose.m20(),
									pose.m01() * x + pose.m11() * y + pose.m21(), 0)
							.setColor(color & 0xFFFFFF | alpha << 24)
							.setUv(vertices[offset + 3], vertices[offset + 4]).setLight(FULL_BRIGHT);
				}
			}
		}
	}
}
