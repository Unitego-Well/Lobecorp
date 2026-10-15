package org.unitego.lobecorp.client.particle.text;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// 排版保留每个可视码点的固定位置，空格计入逐字索引，换行只改变行位置。
@NullMarked
public record TextParticleLayout(List<Glyph> glyphs, int lineHeight, List<Integer> obfuscatedGlyphs) {

	private static final int DEFAULT_TEXT_COLOR = -1;

	public static TextParticleLayout create(Font font, Component text, int outlineColor) {
		var characters = new ArrayList<CharacterData>();
		var widths = new ArrayList<Float>();
		widths.add(0F);
		text.getVisualOrderText().accept((index, style, codepoint) -> {
			if (codepoint == '\n') {
				widths.add(0F);
				return true;
			}

			int line = widths.size() - 1;
			FormattedCharSequence sequence = sink -> sink.accept(index, style, codepoint);
			float advance = font.getSplitter().stringWidth(sequence);
			characters.add(new CharacterData(index, style, codepoint, line, widths.get(line), advance));
			widths.set(line, widths.get(line) + advance);
			return true;
		});
		var glyphs = new ArrayList<Glyph>();
		var obfuscatedGlyphs = new ArrayList<Integer>();

		for (var character : characters) {
			float centerX = character.advance() / 2;
			float centerY = font.lineHeight / 2F;
			var meshes = createMeshes(font, character, centerX, centerY, outlineColor);
			float x = character.x() + centerX - widths.get(character.line()) / 2;
			float y = character.line() * font.lineHeight + centerY - widths.size() * font.lineHeight / 2F;
			double radius = meshes.stream().mapToDouble(TextGlyphMesh::radius).max().orElse(0);
			if (character.style().isObfuscated()) {
				obfuscatedGlyphs.add(glyphs.size());
			}

			glyphs.add(new Glyph(x, y, radius, meshes, character.index(), character.style(),
					character.codepoint(), character.advance()));
		}

		return new TextParticleLayout(List.copyOf(glyphs), font.lineHeight, List.copyOf(obfuscatedGlyphs));
	}

	/// 混淆样式每客户端 tick 更新随机字形，普通字形仍复用原缓存。
	public TextParticleLayout refreshObfuscated(Font font, int outlineColor) {
		if (obfuscatedGlyphs.isEmpty()) {
			return this;
		}

		var refreshed = new ArrayList<>(glyphs);
		for (var index : obfuscatedGlyphs) {
			var glyph = glyphs.get(index);
			var character = new CharacterData(glyph.sourceIndex(), glyph.style(), glyph.codepoint(), 0, 0, glyph.advance());
			var meshes = createMeshes(font, character, glyph.advance() / 2, lineHeight / 2F, outlineColor);
			double radius = meshes.stream().mapToDouble(TextGlyphMesh::radius).max().orElse(0);
			refreshed.set(index, new Glyph(glyph.x(), glyph.y(), radius, meshes, glyph.sourceIndex(),
					glyph.style(), glyph.codepoint(), glyph.advance()));
		}

		return new TextParticleLayout(List.copyOf(refreshed), lineHeight, obfuscatedGlyphs);
	}

	private static List<TextGlyphMesh> createMeshes(Font font, CharacterData character, float centerX,
	                                                float centerY, int outlineColor) {
		var meshes = new ArrayList<TextGlyphMesh>();
		FormattedCharSequence sequence = sink -> sink.accept(character.index(), character.style(), character.codepoint());
		var prepared = font.prepareText(sequence, 0, 0, DEFAULT_TEXT_COLOR, false, false, 0);
		Font.DisplayMode mode = outlineColor == 0 ? Font.DisplayMode.NORMAL : Font.DisplayMode.POLYGON_OFFSET;
		prepared.visit(new Font.GlyphVisitor() {
			@Override
			public void acceptGlyph(TextRenderable.Styled glyph) {
				var mesh = capture(glyph);
				if (outlineColor != 0) {
					meshes.add(mesh.createOutline(() -> glyph.renderType(Font.DisplayMode.NORMAL, false), outlineColor));
				}

				meshes.add(mesh);
			}

			@Override
			public void acceptEffect(TextRenderable effect) {
				meshes.add(capture(effect));
			}

			private TextGlyphMesh capture(TextRenderable renderable) {
				var capture = new TextGlyphMesh.Capture();
				renderable.render(new Matrix4f(), capture, 0, false);
				return capture.finish(() -> renderable.renderType(mode, false), centerX, centerY);
			}
		});
		return List.copyOf(meshes);
	}

	public record Glyph(float x, float y, double radius, List<TextGlyphMesh> meshes, int sourceIndex,
	                    Style style, int codepoint, float advance) {
	}

	private record CharacterData(int index, Style style, int codepoint, int line, float x, float advance) {
	}
}
